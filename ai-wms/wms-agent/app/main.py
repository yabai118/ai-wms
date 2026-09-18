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

from app.config import settings
from app.clients.db import load_wave_tasks, get_wave_info
from app.models.schemas import (
    CompareOut, HealthOut, OptimizeIn, RouteOut, TaskOut,
)
from app.services import routing

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
        corridor=t.corridor, depth=t.depth,
    )


def to_route_out(r: routing.RouteResult) -> RouteOut:
    return RouteOut(
        strategy=r.strategy,
        strategy_name=r.strategy_name,
        total_distance=r.total_distance,
        corridor_distance=r.corridor_distance,
        horizontal_distance=r.horizontal_distance,
        depth_distance=r.depth_distance,
        task_count=len(r.sequence),
        sequence=[to_task_out(i, t) for i, t in enumerate(r.sequence, 1)],
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
