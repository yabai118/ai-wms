# -*- coding: utf-8 -*-
"""
Agent 编排器（Orchestrator）

## 定位

本项目的 Python 层分两个概念，**必须分清楚**：

| 层 | 职责 | 是否用 LLM |
|---|---|---|
| **决策层（OR）** | 路径优化、需求预测、货位分配 | ❌ 用运筹优化算法 |
| **Agent 层** | 异常诊断（检测用统计、解释用 LLM） | ✅ 解释环节用 LLM |
| **编排层（本文件）** | 任务路由、上下文管理、结果校验、降级决策 | ❌ 确定性逻辑 |

## 为什么编排器用 Workflow 而不是 Agent

参考 Anthropic《Building Effective Agents》的核心原则：
> "find the simplest solution possible, and only increase complexity when needed"

本系统的任务路由是**确定性的**：
    收到"routing"任务 → 交给路径优化模块
    收到"forecast"任务 → 交给需求预测模块

这是 **Workflow 的 Routing 模式**，不是 Agent（Agent 需要 LLM 动态决定流程）。
用 Agent 反而增加不可控性，没有收益。

## 编排器的四个职责

1. **路由**：按任务类型分发（确定性）
2. **上下文管理**：统一组装输入，避免各模块重复查库
3. **结果校验**：算法输出必须检查合法性，**不能盲信**
4. **降级决策**：超时 / 异常 / 结果不合法 → 切换到规则桩

## 三级降级管线

    ① 算法正常        → SUCCESS
    ② 规则桩兜底      → DEGRADED（可用但质量下降）
    ③ 都失败          → FAILED（生成人工工单）
"""
from __future__ import annotations

import logging
import time
import uuid
from collections import deque
from dataclasses import dataclass, field
from typing import Any, Callable, Deque, Dict, List, Optional

from app.services import fallback, routing

logger = logging.getLogger(__name__)

# =====================================================================
#  超时阈值（毫秒）—— 超过则触发降级
# =====================================================================
DEFAULT_TIMEOUT_MS = 5000


# =====================================================================
#  数据结构
# =====================================================================

@dataclass
class TaskRequest:
    """编排任务请求"""
    task_type: str                  # routing / forecast / slotting / anomaly
    payload: Dict[str, Any] = field(default_factory=dict)


@dataclass
class TaskResult:
    """编排任务结果"""
    task_id: str
    task_type: str
    status: str                     # SUCCESS / DEGRADED / FAILED
    handler: str                    # 实际由哪个处理器完成
    result: Any = None
    fallback_reason: Optional[str] = None
    elapsed_ms: int = 0
    trace: List[str] = field(default_factory=list)   # 执行轨迹（便于排查）

    @property
    def status_name(self) -> str:
        return {"SUCCESS": "正常", "DEGRADED": "已降级", "FAILED": "失败"}.get(self.status, self.status)


# =====================================================================
#  上下文（统一组装各模块需要的输入）
# =====================================================================

class Context:
    """
    执行上下文

    作用：算法模块需要的数据（波次任务、库位信息等）在这里**统一组装**，
    避免每个模块各自查库；同时缓存已加载的数据，减少重复查询。
    """
    def __init__(self, payload: Dict[str, Any]):
        self.payload = payload
        self._cache: Dict[str, Any] = {}

    def get_wave_tasks(self, wave_id: int) -> List[routing.PickTask]:
        """加载并缓存波次任务"""
        key = f"wave_tasks:{wave_id}"
        if key not in self._cache:
            from app.clients.db import load_wave_tasks
            self._cache[key] = load_wave_tasks(wave_id)
        return self._cache[key]

    def get(self, key: str, default=None):
        return self.payload.get(key, default)


# =====================================================================
#  结果校验器
# =====================================================================

class ValidationError(Exception):
    """结果校验失败"""
    pass


def validate_routing(result: Any, ctx: Context) -> None:
    """
    校验路径优化结果

    **为什么必须校验**：算法的输出不能盲信——
    如果路径为空、距离为负、任务数对不上，说明算法有问题，
    这时宁可走降级，也不能把错误结果写回业务系统。
    """
    if result is None:
        raise ValidationError("路径结果为空")
    if not result.sequence:
        raise ValidationError("拣货顺序为空")
    if result.total_distance < 0:
        raise ValidationError(f"行走距离为负: {result.total_distance}")

    wave_id = ctx.get("waveId")
    if wave_id is not None:
        expected = len(ctx.get_wave_tasks(int(wave_id)))
        if len(result.sequence) != expected:
            raise ValidationError(
                f"任务数不一致：输入 {expected} 条，输出 {len(result.sequence)} 条")


def validate_forecast(result: Any, ctx: Context) -> None:
    """校验预测结果：不能为负、长度要对"""
    if not result:
        raise ValidationError("预测结果为空")
    if any(v < 0 for v in result):
        raise ValidationError("预测值出现负数")
    horizon = ctx.get("horizon", len(result))
    if len(result) != horizon:
        raise ValidationError(f"预测长度不符：期望 {horizon}，实际 {len(result)}")


def validate_generic(result: Any, ctx: Context) -> None:
    """通用校验：结果不能为空"""
    if result is None:
        raise ValidationError("结果为空")


# =====================================================================
#  处理器注册表
# =====================================================================

@dataclass
class Handler:
    """一个任务处理器"""
    name: str
    run: Callable[[Context], Any]                    # 正常执行（算法）
    fallback: Callable[[Context], Any]               # 降级执行（规则桩）
    validate: Callable[[Any, Context], None]         # 结果校验


# =====================================================================
#  编排器
# =====================================================================

class Orchestrator:
    """
    任务编排器

    使用方式：
        orch = Orchestrator()
        result = orch.execute(TaskRequest("routing", {"waveId": 9709, "strategy": "s_shape"}))
    """

    def __init__(self, timeout_ms: int = DEFAULT_TIMEOUT_MS):
        self.timeout_ms = timeout_ms
        self.handlers: Dict[str, Handler] = {}
        # 最近 200 条调用记录（内存，服务重启后清空——生产环境应写日志/监控系统）
        self._records: Deque[Dict[str, Any]] = deque(maxlen=200)
        self._register_default_handlers()

    # ------------------------------------------------------------------
    #  ① 路由：注册表（确定性路由，不是 LLM 决策）
    # ------------------------------------------------------------------
    def _register_default_handlers(self):
        self.register(Handler(
            name="routing",
            run=self._run_routing,
            fallback=self._fallback_routing,
            validate=validate_routing,
        ))
        self.register(Handler(
            name="forecast",
            run=self._run_forecast,
            fallback=self._fallback_forecast,
            validate=validate_forecast,
        ))
        self.register(Handler(
            name="anomaly",
            run=self._run_anomaly,
            fallback=self._fallback_anomaly,
            validate=validate_generic,
        ))

    def register(self, handler: Handler):
        self.handlers[handler.name] = handler

    # ------------------------------------------------------------------
    #  主流程
    # ------------------------------------------------------------------
    def execute(self, req: TaskRequest) -> TaskResult:
        task_id = uuid.uuid4().hex[:12]
        started = time.time()
        trace: List[str] = []

        logger.info("[%s] 任务开始: type=%s", task_id, req.task_type)

        # ① 路由
        handler = self.handlers.get(req.task_type)
        if handler is None:
            trace.append(f"未知任务类型: {req.task_type}")
            r = TaskResult(
                task_id=task_id, task_type=req.task_type, status="FAILED",
                handler="none", result=None,
                fallback_reason=f"未知任务类型 {req.task_type}",
                elapsed_ms=int((time.time() - started) * 1000), trace=trace,
            )
            self._record(r, req)
            return r

        # ② 组装上下文
        ctx = Context(req.payload)
        trace.append(f"路由到处理器: {handler.name}")

        # ③ 执行算法
        try:
            result = handler.run(ctx)
            trace.append("算法执行完成")

            # ④ 结果校验（不能盲信算法输出）
            handler.validate(result, ctx)
            trace.append("结果校验通过")

            elapsed = int((time.time() - started) * 1000)
            if elapsed > self.timeout_ms:
                # 超时：虽然算出来了，但已经超时，走降级
                trace.append(f"执行超时 {elapsed}ms > {self.timeout_ms}ms，触发降级")
                return self._degrade(task_id, req, handler, ctx, trace, started,
                                     f"执行超时（{elapsed}ms）")

            logger.info("[%s] 任务成功: %dms", task_id, elapsed)
            r = TaskResult(
                task_id=task_id, task_type=req.task_type, status="SUCCESS",
                handler=handler.name, result=result, elapsed_ms=elapsed, trace=trace,
            )
            self._record(r, req)
            return r

        except ValidationError as e:
            trace.append(f"结果校验失败: {e}")
            return self._degrade(task_id, req, handler, ctx, trace, started,
                                 f"结果校验失败：{e}")
        except Exception as e:
            trace.append(f"算法异常: {type(e).__name__}: {e}")
            logger.warning("[%s] 算法异常，触发降级: %s", task_id, e)
            return self._degrade(task_id, req, handler, ctx, trace, started,
                                 f"算法异常：{type(e).__name__}: {e}")

    # ------------------------------------------------------------------
    #  ② 降级：切换到规则桩
    # ------------------------------------------------------------------
    def _degrade(self, task_id, req, handler, ctx, trace, started, reason) -> TaskResult:
        try:
            fb_result = handler.fallback(ctx)
            trace.append(f"规则桩执行完成: {reason}")
            logger.info("[%s] 已降级为规则桩: %s", task_id, reason)
            r = TaskResult(
                task_id=task_id, task_type=req.task_type, status="DEGRADED",
                handler=f"{handler.name}(fallback)", result=fb_result,
                fallback_reason=reason,
                elapsed_ms=int((time.time() - started) * 1000), trace=trace,
            )
        except Exception as e:
            # ③ 规则桩也失败 → 人工工单
            trace.append(f"规则桩也失败: {e}")
            logger.error("[%s] 规则桩失败，需人工处理: %s", task_id, e)
            r = TaskResult(
                task_id=task_id, task_type=req.task_type, status="FAILED",
                handler="none", result=None,
                fallback_reason=f"算法与规则桩均失败，已生成人工工单（{reason}）",
                elapsed_ms=int((time.time() - started) * 1000), trace=trace,
            )
        self._record(r, req)
        return r

    # ------------------------------------------------------------------
    #  ③ 调用记录与统计（供监控面板使用）
    # ------------------------------------------------------------------
    def _record(self, result: TaskResult, req: TaskRequest):
        """记录一次调用（内存保留最近 200 条）"""
        self._records.append({
            "taskId": result.task_id,
            "taskType": result.task_type,
            "status": result.status,
            "handler": result.handler,
            "elapsedMs": result.elapsed_ms,
            "fallbackReason": result.fallback_reason,
            "calledAt": time.strftime("%Y-%m-%d %H:%M:%S"),
        })

    def stats(self) -> Dict[str, Any]:
        """
        统计编排器的运行状况

        重点指标是**降级率**——它证明降级设计是"真在跑、可观测"的：
            降级率 = 降级次数 / 总调用次数
        """
        records = list(self._records)
        total = len(records)

        if total == 0:
            return {
                "total": 0, "success": 0, "degraded": 0, "failed": 0,
                "degradeRate": 0.0, "successRate": 0.0,
                "avgElapsedMs": 0, "byType": {}, "recent": [],
            }

        success = sum(1 for r in records if r["status"] == "SUCCESS")
        degraded = sum(1 for r in records if r["status"] == "DEGRADED")
        failed = sum(1 for r in records if r["status"] == "FAILED")

        # 按任务类型分组统计
        by_type: Dict[str, Dict[str, Any]] = {}
        for r in records:
            t = r["taskType"]
            d = by_type.setdefault(t, {"count": 0, "success": 0, "degraded": 0,
                                       "failed": 0, "totalMs": 0})
            d["count"] += 1
            d["totalMs"] += r["elapsedMs"]
            if r["status"] == "SUCCESS":
                d["success"] += 1
            elif r["status"] == "DEGRADED":
                d["degraded"] += 1
            else:
                d["failed"] += 1

        for t, d in by_type.items():
            d["avgMs"] = round(d["totalMs"] / d["count"]) if d["count"] else 0
            d["successRate"] = round(100 * d["success"] / d["count"], 1) if d["count"] else 0.0
            del d["totalMs"]

        return {
            "total": total,
            "success": success,
            "degraded": degraded,
            "failed": failed,
            "successRate": round(100 * success / total, 1),
            "degradeRate": round(100 * degraded / total, 1),
            "avgElapsedMs": round(sum(r["elapsedMs"] for r in records) / total),
            "byType": by_type,
            "recent": list(reversed(records[-20:])),      # 最近 20 条，新的在前
        }

    # ------------------------------------------------------------------
    #  具体处理器的「算法」实现
    # ------------------------------------------------------------------
    def _run_routing(self, ctx: Context) -> routing.RouteResult:
        wave_id = ctx.get("waveId")
        strategy = ctx.get("strategy", "s_shape")
        if wave_id is None:
            raise ValueError("缺少 waveId")
        tasks = ctx.get_wave_tasks(int(wave_id))
        if not tasks:
            raise ValueError(f"波次 {wave_id} 没有拣货任务")
        return routing.optimize(tasks, strategy)

    def _run_forecast(self, ctx: Context) -> List[float]:
        """需求预测（暂用统计方法；后续可接 Holt-Winters / Croston）"""
        history = ctx.get("history") or []
        horizon = int(ctx.get("horizon", 7))
        if not history:
            raise ValueError("缺少历史数据")
        # 这里演示用移动平均；真实实现会按 ADI/CV² 分流到 Holt-Winters / Croston
        return fallback.forecast_fallback(history, horizon)

    def _run_anomaly(self, ctx: Context) -> Dict[str, Any]:
        """异常诊断：检测用统计规则，解释用 LLM（LLM 调用见 llm.py）"""
        records = ctx.get("records") or []
        if not records:
            raise ValueError("缺少待检测记录")
        # 简化：统计偏离均值 3σ 的记录
        import statistics
        values = [r.get("value", 0) for r in records]
        if len(values) < 3:
            return {"anomalies": [], "message": "样本量不足，跳过检测"}
        mean = statistics.mean(values)
        stdev = statistics.pstdev(values) or 1
        anomalies = [
            {**r, "deviation": round((r.get("value", 0) - mean) / stdev, 2)}
            for r in records
            if abs((r.get("value", 0) - mean) / stdev) > 3
        ]
        return {"anomalies": anomalies, "mean": round(mean, 2), "stdev": round(stdev, 2)}

    # ------------------------------------------------------------------
    #  具体处理器的「规则桩」实现
    # ------------------------------------------------------------------
    def _fallback_routing(self, ctx: Context) -> routing.RouteResult:
        tasks = ctx.get_wave_tasks(int(ctx.get("waveId")))
        return fallback.routing_fallback(tasks)

    def _fallback_forecast(self, ctx: Context) -> List[float]:
        return fallback.forecast_fallback(
            ctx.get("history") or [], int(ctx.get("horizon", 7)))

    def _fallback_anomaly(self, ctx: Context) -> Dict[str, Any]:
        return {
            "anomalies": [],
            "message": fallback.anomaly_fallback("UNKNOWN", {}),
        }


# 全局单例
orchestrator = Orchestrator()
