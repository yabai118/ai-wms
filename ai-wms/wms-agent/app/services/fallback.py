# -*- coding: utf-8 -*-
"""
规则桩（Fallback Rules）

## 为什么需要它

算法服务可能因为各种原因不可用：
  - 超时（算法复杂度过高）
  - 抛异常（数据异常）
  - 返回结果不合法（比如预测出负数）

**不能因为算法挂了就让业务停摆**——这时切换到「规则桩」，
用最简单但一定可用的规则给出一个「可接受」的结果。

## 设计原则

- 规则桩必须是**纯计算、无外部依赖、不会失败**的
- 结果质量可以差，但必须「可用」且「正确」
- 每个决策模块都有一个对应的规则桩
"""
from __future__ import annotations

from typing import List

from app.services.routing import PickTask, RouteResult


# =====================================================================
#  拣货路径的规则桩：按库位编码顺序
# =====================================================================

def routing_fallback(tasks: List[PickTask]) -> RouteResult:
    """
    路径优化的规则桩：**按库位编码字典序拣货**

    为什么是字典序：
    - 库位编码如 A-14-11，本身就是按「区-排-位」编排的
    - 字典序 ≈ 空间顺序，比随机顺序好
    - 实现零成本，绝不会失败

    质量：比最优策略差，但比什么都不做强
    """
    if not tasks:
        return RouteResult("fallback", "规则桩（按库位编码）")

    ordered = sorted(tasks, key=lambda t: t.location_code)

    result = RouteResult("fallback", "规则桩（按库位编码）", ordered)
    cur_x, cur_y = 66, 0        # 起点
    corridor_dist = horiz_dist = depth_dist = 0

    for t in ordered:
        c = t.corridor
        horiz_dist += abs(cur_x - c)
        corridor_dist += abs(cur_y - t.y)
        depth_dist += 2 * t.depth
        cur_x, cur_y = t.x, t.y

    horiz_dist += abs(cur_x - 66)
    corridor_dist += abs(cur_y - 0)

    result.corridor_distance = corridor_dist
    result.horizontal_distance = horiz_dist
    result.depth_distance = depth_dist
    result.total_distance = corridor_dist + horiz_dist + depth_dist
    return result


# =====================================================================
#  需求预测的规则桩：移动平均
# =====================================================================

def forecast_fallback(history: List[float], horizon: int = 7) -> List[float]:
    """
    需求预测的规则桩：**最近 7 天移动平均**

    为什么不用更复杂的：
    - 规则桩要保证一定算得出来
    - 移动平均对「平滑需求」效果尚可
    - 对「间歇需求」会低估，但至少有结果

    参数说明：
        history: 历史销量序列（按时间正序）
        horizon: 要预测的天数
    """
    if not history:
        return [0.0] * horizon

    window = min(7, len(history))
    avg = sum(history[-window:]) / window
    return [round(avg, 2)] * horizon


# =====================================================================
#  货位分配的规则桩：ABC 就近
# =====================================================================

def slotting_fallback(abc_class: str, candidate_locations: List[dict]) -> dict:
    """
    货位分配的规则桩：**ABC 分类就近分配**

    规则：
    - A 类（高频）→ 靠近出口的库位
    - B 类 → 中间区域
    - C 类 → 最远的库位

    参数说明：
        abc_class: 商品 ABC 分类
        candidate_locations: 候选库位，每个含 {'id', 'code', 'distance'}
    """
    if not candidate_locations:
        return {}

    # 按距离排序（近 → 远）
    sorted_locs = sorted(candidate_locations, key=lambda l: l.get("distance", 0))

    if abc_class == "A":
        return sorted_locs[0]                                  # 最近的
    elif abc_class == "B":
        mid = len(sorted_locs) // 2
        return sorted_locs[mid]                                # 中间的
    else:
        return sorted_locs[-1]                                 # 最远的


# =====================================================================
#  异常诊断的规则桩：固定文案
# =====================================================================

def anomaly_fallback(anomaly_type: str, detail: dict) -> str:
    """
    异常诊断的规则桩：**按异常类型返回预置文案**

    为什么需要：LLM 调用可能超时或失败，
    但告警必须能显示「建议」，不能留空。
    """
    templates = {
        "PICK_TIMEOUT": "拣货任务超时未完成。建议：检查拣货员是否遇到缺货或库位异常，"
                        "必要时重新分配任务。",
        "SLOW_OPERATOR": "该拣货员作业耗时明显高于平均水平。建议：检查其负责的库位"
                         "是否集中在仓库深处，或核实是否有异常情况。",
        "STOCK_MISMATCH": "账实数量不一致。建议：安排盘点核实，检查是否有未记录的出入库操作。",
        "LOW_STOCK": "库存低于安全水位。建议：尽快补货，避免影响后续订单履约。",
    }
    return templates.get(
        anomaly_type,
        f"检测到异常（类型：{anomaly_type}）。建议：人工核实相关记录。"
    )
