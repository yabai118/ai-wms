# -*- coding: utf-8 -*-
"""
AI-WMS Agent 服务（Python 智能决策层）

启动：
    cd wms-agent
    uvicorn app.main:app --reload --port 8000

接口文档：http://localhost:8000/docs
"""
from __future__ import annotations

from typing import List

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import Field

from app.agents import llm
from app.agents.orchestrator import Orchestrator, TaskRequest
from app.config import settings
from app.clients.db import load_wave_tasks, get_wave_info
from app.models.schemas import (
    CamelModel, CompareOut, HealthOut, OptimizeIn, RouteOut, TaskOut,
)
from app.services import routing

# 编排器实例（全局单例）
orchestrator = Orchestrator()

app = FastAPI(
    title=settings.app_name,
    version=settings.version,
    description="AI-WMS 智能决策层：拣货路径优化、货位分配、Agent 编排",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# =====================================================================
#  工具：把内部结果转成响应模型
# =====================================================================

def to_task_out(seq: int, t: routing.PickTask) -> TaskOut:
    return TaskOut(
        seq=seq, task_id=t.task_id, sku_code=t.sku_code,
        location_code=t.location_code, x=t.x, y=t.y, qty=t.qty,
        aisle=t.aisle, reach=t.reach,
    )


def to_route_out(r: routing.RouteResult) -> RouteOut:
    return RouteOut(
        strategy=r.strategy,
        strategy_name=r.strategy_name,
        total_distance=r.total_distance,
        aisle_distance=r.aisle_distance,
        cross_distance=r.cross_distance,
        reach_distance=r.reach_distance,
        task_count=len(r.sequence),
        sequence=[to_task_out(i, t) for i, t in enumerate(r.sequence, 1)],
        path=r.path,
    )


# =====================================================================
#  接口
# =====================================================================

@app.get("/health", response_model=HealthOut, summary="健康检查")
def health():
    return HealthOut(
        status="UP",
        service=settings.app_name,
        version=settings.version,
        java_service=settings.java_base_url,
    )


@app.get("/routing/wave/{wave_id}", response_model=CompareOut,
          summary="对指定波次跑全部策略并对比")
def routing_compare(wave_id: int):
    wave = get_wave_info(wave_id)
    if not wave:
        raise HTTPException(status_code=404, detail=f"波次不存在: {wave_id}")

    tasks = load_wave_tasks(wave_id)
    if not tasks:
        raise HTTPException(status_code=400, detail="该波次没有拣货任务")

    return _build_compare(wave_id, wave.get("wave_no"), tasks)


@app.post("/routing/optimize", response_model=RouteOut,
          summary="给定任务列表，按指定策略优化路径")
def routing_optimize(body: OptimizeIn):
    if not body.tasks:
        raise HTTPException(status_code=400, detail="任务列表不能为空")

    tasks = [
        routing.PickTask(
            task_id=t.task_id, sku_code=t.sku_code,
            location_code=t.location_code, x=t.x, y=t.y, qty=t.qty,
        )
        for t in body.tasks
    ]

    if body.strategy not in routing.STRATEGIES:
        raise HTTPException(
            status_code=400,
            detail=f"未知策略 {body.strategy}，可选: {list(routing.STRATEGIES)}",
        )

    return to_route_out(routing.optimize(tasks, body.strategy))


@app.post("/routing/compare", response_model=CompareOut,
          summary="给定任务列表，跑全部策略并对比")
def routing_compare_by_tasks(body: OptimizeIn):
    if not body.tasks:
        raise HTTPException(status_code=400, detail="任务列表不能为空")

    tasks = [
        routing.PickTask(
            task_id=t.task_id, sku_code=t.sku_code,
            location_code=t.location_code, x=t.x, y=t.y, qty=t.qty,
        )
        for t in body.tasks
    ]
    return _build_compare(None, None, tasks)


@app.get("/routing/strategies", summary="列出可用的路径策略")
def strategies():
    return [
        {"key": k, "name": {
            "baseline": "顺序拣货（基线）",
            "return": "返回式",
            "s_shape": "S形（穿越式）",
            "largest_gap": "最大间隙",
        }.get(k, k)}
        for k in routing.STRATEGIES
    ]


# =====================================================================
#  编排器接口（任务路由 + 结果校验 + 三级降级）
# =====================================================================

class OrchestrateIn(CamelModel):
    """编排任务请求"""
    task_type: str = Field(description="任务类型：routing / forecast / anomaly")
    payload: dict = Field(default_factory=dict, description="任务参数")


@app.post("/orchestrate", summary="提交任务给编排器（自动路由 + 校验 + 降级）")
def orchestrate(body: OrchestrateIn):
    """
    编排器入口

    编排器会：
    1. 按 taskType 路由到对应处理器
    2. 组装上下文
    3. 执行算法并**校验结果**
    4. 失败/超时/结果不合法 → **降级到规则桩**
    5. 规则桩也失败 → 标记为需人工处理
    """
    result = orchestrator.execute(TaskRequest(body.task_type, body.payload))
    return {
        "taskId": result.task_id,
        "taskType": result.task_type,
        "status": result.status,
        "statusName": result.status_name,
        "handler": result.handler,
        "result": _serialize_result(result.result),
        "fallbackReason": result.fallback_reason,
        "elapsedMs": result.elapsed_ms,
        "trace": result.trace,
    }


@app.get("/orchestrate/types", summary="列出编排器支持的任务类型")
def orchestrate_types():
    return [
        {"type": "routing", "name": "拣货路径优化", "payload": {"waveId": 9709, "strategy": "s_shape"}},
        {"type": "forecast", "name": "需求预测", "payload": {"history": [10, 12, 15, 11], "horizon": 7}},
        {"type": "anomaly", "name": "异常检测", "payload": {"records": [{"value": 10}, {"value": 12}]}},
    ]


@app.get("/orchestrate/stats", summary="★ 编排器运行统计（供监控面板用）")
def orchestrate_stats():
    """
    编排器运行状况统计

    核心指标是**降级率**——它证明三级降级设计是「真在跑、可观测」的：
        降级率 = 降级次数 / 总调用次数

    注：统计保存在内存中，服务重启后清空。
    生产环境应该把这些指标打到 Prometheus / 日志系统。
    """
    return orchestrator.stats()


def _serialize_result(r):
    """把内部结果对象转成可序列化的字典"""
    if r is None:
        return None
    if hasattr(r, "strategy") and hasattr(r, "total_distance"):
        return to_route_out(r).model_dump(by_alias=True)
    return r


# =====================================================================
#  LLM Agent 接口
# =====================================================================

class AnomalyExplainIn(CamelModel):
    anomaly_type: str = Field(description="异常类型，如 PICK_TIMEOUT")
    detail: dict = Field(default_factory=dict, description="异常详情")


class NlQueryIn(CamelModel):
    question: str = Field(description="用自然语言提问")


@app.get("/llm/status", summary="LLM 是否可用")
def llm_status():
    return {
        "available": llm.is_available(),
        "model": settings.llm_model if llm.is_available() else None,
        "baseUrl": settings.llm_base_url if llm.is_available() else None,
        "note": "未配置 LLM_API_KEY 时，异常解释会自动降级为预置文案",
    }


@app.post("/llm/explain-anomaly", summary="用 LLM 生成异常诊断建议")
def llm_explain(body: AnomalyExplainIn):
    r = llm.explain_anomaly(body.anomaly_type, body.detail)
    return {
        "anomalyType": body.anomaly_type,
        "suggestion": r.get("suggestion"),
        "source": r.get("source"),
        "reason": r.get("reason"),
    }


@app.post("/llm/query", summary="自然语言查询（Function Calling）")
def llm_query(body: NlQueryIn):
    """
    自然语言查询：LLM 理解意图 → 调用工具查库 → 组织回答

    试试这些问题：
      - 仓库现在有多少库存？
      - 8N10W9-11 这个 SKU 分布在哪些库位？
      - 哪些拣货员作业量最少？
      - 有哪些商品库存偏低了？
    """
    return llm.natural_language_query(body.question)


# =====================================================================
#  内部
# =====================================================================

def _build_compare(wave_id, wave_no, tasks: List[routing.PickTask]) -> CompareOut:
    results = routing.compare_all(tasks)
    baseline = results["baseline"]
    best = min(results.values(), key=lambda r: r.total_distance)

    saved = baseline.total_distance - best.total_distance
    percent = round(100 * saved / baseline.total_distance, 1) if baseline.total_distance else 0.0

    return CompareOut(
        wave_id=wave_id,
        wave_no=wave_no,
        task_count=len(tasks),
        baseline_distance=baseline.total_distance,
        best_strategy=best.strategy_name,
        best_distance=best.total_distance,
        saved_distance=saved,
        saved_percent=percent,
        routes=[to_route_out(r) for r in results.values()],
    )
