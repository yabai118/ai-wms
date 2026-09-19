# -*- coding: utf-8 -*-
"""API 请求 / 响应模型（Pydantic）"""
from __future__ import annotations

from typing import List, Optional
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """
    统一使用 camelCase 输出 JSON

    为什么：Java 后端的接口用的是 camelCase（taskCount / totalQty），
    前端一套代码要对接两个服务，字段风格必须一致，否则前端要写两套映射。
    """
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,       # 允许用 snake_case 构造（内部代码更方便）
    )


# =====================================================================
#  通用
# =====================================================================

class TaskIn(CamelModel):
    """单条拣货任务（外部传入时用）"""
    task_id: int
    sku_code: str
    location_code: str
    x: int
    y: int
    qty: int = 1


class TaskOut(CamelModel):
    """返回的拣货任务"""
    seq: int = Field(description="拣货顺序")
    task_id: int
    sku_code: str
    location_code: str
    x: int
    y: int
    qty: int
    aisle: int = Field(description="归属的横向拣货通道 y 坐标")
    reach: int = Field(description="从拣货通道走到该货位的取货距离")


class RouteOut(CamelModel):
    """一条路线"""
    strategy: str
    strategy_name: str
    total_distance: int = Field(description="总行走距离（米）")
    aisle_distance: int = Field(description="沿横向拣货通道走的距离")
    cross_distance: int = Field(description="经纵向横通道换道的距离")
    reach_distance: int = Field(description="从通道走到货位取货的距离（与路径顺序无关的常数项）")
    task_count: int
    sequence: List[TaskOut]
    path: List[List[int]] = Field(
        default_factory=list,
        description="实际行走轨迹的折线顶点 [[x,y],...]；前端画路径图用这个，"
                    "直接连货位坐标会画出穿货架的斜线")


class CompareOut(CamelModel):
    """多策略对比结果"""
    wave_id: Optional[int] = None
    wave_no: Optional[str] = None
    task_count: int
    baseline_distance: int = Field(description="基线距离（未优化）")
    best_strategy: str
    best_distance: int
    saved_distance: int
    saved_percent: float = Field(description="节省百分比")
    routes: List[RouteOut]


class OptimizeIn(CamelModel):
    """按按需优化（传入任务列表）"""
    tasks: List[TaskIn]
    strategy: str = Field(default="s_shape",
                          description="策略：baseline / return / s_shape / largest_gap")


# =====================================================================
#  健康检查
# =====================================================================

class HealthOut(CamelModel):
    status: str
    service: str
    version: str
    java_service: str
