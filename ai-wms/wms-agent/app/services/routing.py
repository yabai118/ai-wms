# -*- coding: utf-8 -*-
"""
拣货路径优化服务

## 仓库布局模型（从真实数据推断）

导航点数据揭示了仓库的实际结构：

    x=66   LC 通道（左侧通道）┐
                              ├─ Block1（货架区1，货位 x ∈ 86~368）
    x=403  CC 通道（中央通道）┤
                              ├─ Block2（货架区2，货位 x ∈ 450~666）
    x=686  RC 通道（右侧通道）┘

- 3 条**纵向通道**沿 y 方向延伸（y ∈ 0~1440）
- 2 个**货架区块**夹在通道之间
- 拣货员在通道上行走，取货时横向进入货架

这就是仓储运筹学中的 **"双区块"（two-block）仓库布局**。

## 距离模型

    行走距离 = 通道内纵向移动 + 横向移动（通道间切换 + 进入货架取货）

## 实现的三种策略（行业标准启发式）

| 策略 | 核心思想 |
|------|---------|
| **返回式 Return** | 每条通道都从入口进、取完原路返回 |
| **S形 S-shape** | 蛇形穿越：通道1从下往上，通道2从上往下，循环 |
| **最大间隙 Largest Gap** | 找出通道内最大空隙，只走到空隙处折返，不白跑 |
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import List, Dict, Tuple


# =====================================================================
#  仓库布局常量（从真实数据推断）
# =====================================================================
CORRIDORS: List[int] = [66, 403, 686]       # 3 条纵向通道的 x 坐标
AISLE_MIN_Y: int = 0                        # 通道 y 起点
AISLE_MAX_Y: int = 1440                     # 通道 y 终点
START_POINT: Tuple[int, int] = (66, 0)      # 拣货起点（左通道底部）


@dataclass
class PickTask:
    """一条拣货任务（库位视角）"""
    task_id: int
    sku_code: str
    location_code: str
    x: int
    y: int
    qty: int = 1

    @property
    def corridor(self) -> int:
        """该货位归属的通道（取最近的通道）"""
        return min(CORRIDORS, key=lambda c: abs(c - self.x))

    @property
    def depth(self) -> int:
        """货位相对通道的横向深度（取货要走的距离）"""
        return abs(self.x - self.corridor)


@dataclass
class RouteResult:
    """路径优化结果"""
    strategy: str                       # 策略名
    strategy_name: str                  # 策略中文名
    sequence: List[PickTask] = field(default_factory=list)   # 拣货顺序
    total_distance: int = 0             # 总行走距离（米）
    corridor_distance: int = 0          # 通道内纵向距离
    horizontal_distance: int = 0        # 横向距离
    depth_distance: int = 0             # 进入货架深度距离


# =====================================================================
#  距离计算
# =====================================================================

def corridor_travel(y_from: int, y_to: int) -> int:
    """通道内纵向移动距离"""
    return abs(y_to - y_from)


def horizontal_travel(x_from: int, x_to: int) -> int:
    """横向移动距离（通道间切换）"""
    return abs(x_to - x_from)


# =====================================================================
#  策略一：顺序拣货（基线，未优化）
# =====================================================================

def baseline_route(tasks: List[PickTask]) -> RouteResult:
    """
    基线：按任务生成顺序拣货（不做任何优化）

    这是「原系统」的做法——用来做对比基准。
    """
    if not tasks:
        return RouteResult("baseline", "顺序拣货（基线）")

    result = RouteResult("baseline", "顺序拣货（基线）", list(tasks))
    cur_x, cur_y = START_POINT
    corridor_dist = horiz_dist = depth_dist = 0

    for t in result.sequence:
        c = t.corridor
        # 先横向移动到目标通道，再纵向移动到货位 y
        horiz_dist += horizontal_travel(cur_x, c)
        corridor_dist += corridor_travel(cur_y, t.y)
        depth_dist += 2 * t.depth          # 进 + 出
        cur_x, cur_y = t.x, t.y

    # 返回起点
    horiz_dist += horizontal_travel(cur_x, START_POINT[0])
    corridor_dist += corridor_travel(cur_y, START_POINT[1])

    result.corridor_distance = corridor_dist
    result.horizontal_distance = horiz_dist
    result.depth_distance = depth_dist
    result.total_distance = corridor_dist + horiz_dist + depth_dist
    return result


# =====================================================================
#  策略二：返回式（Return）
# =====================================================================

def return_route(tasks: List[PickTask]) -> RouteResult:
    """
    返回式策略：每条通道都从入口进，取完货原路返回入口

        通道:  |↑↓|    |↑↓|    |↑↓|
               入口     入口     入口
        横移:  ────────────────────

    特点：不进入通道深处（除非必须），路径规则简单
    适合：每条通道任务较少的情况
    """
    if not tasks:
        return RouteResult("return", "返回式")

    # 按通道分组
    by_corridor: Dict[int, List[PickTask]] = {}
    for t in tasks:
        by_corridor.setdefault(t.corridor, []).append(t)

    result = RouteResult("return", "返回式")
    corridor_dist = horiz_dist = depth_dist = 0
    cur_x, cur_y = START_POINT

    # 按通道 x 排序，逐条通道访问
    for c in sorted(by_corridor.keys()):
        group = sorted(by_corridor[c], key=lambda t: t.y)
        # 横移到该通道入口
        horiz_dist += horizontal_travel(cur_x, c)
        cur_x, cur_y = c, AISLE_MIN_Y

        # 从入口往上走，取完所有货再返回入口
        for t in group:
            corridor_dist += corridor_travel(cur_y, t.y)
            depth_dist += 2 * t.depth
            cur_y = t.y
            result.sequence.append(t)
        # 返回入口
        corridor_dist += corridor_travel(cur_y, AISLE_MIN_Y)
        cur_y = AISLE_MIN_Y

    # 回到起点
    horiz_dist += horizontal_travel(cur_x, START_POINT[0])

    result.corridor_distance = corridor_dist
    result.horizontal_distance = horiz_dist
    result.depth_distance = depth_dist
    result.total_distance = corridor_dist + horiz_dist + depth_dist
    return result


# =====================================================================
#  策略三：S形（S-shape / Traversal）
# =====================================================================

def s_shape_route(tasks: List[PickTask]) -> RouteResult:
    """
    S形策略（穿越式）：从通道入口进，一直走到通道末端，再横移到下一条通道

        通道:  |↑|    |↓|    |↑|
                └──→  └──→  └──→
        横移:  底/顶交替连接

    特点：通道内不回头，走满整条通道
    适合：巷道内货位密集的情况（走到底也不亏）
    """
    if not tasks:
        return RouteResult("s_shape", "S形")

    by_corridor: Dict[int, List[PickTask]] = {}
    for t in tasks:
        by_corridor.setdefault(t.corridor, []).append(t)

    result = RouteResult("s_shape", "S形")
    corridor_dist = horiz_dist = depth_dist = 0
    cur_x, cur_y = START_POINT

    corridors_sorted = sorted(by_corridor.keys())
    going_up = True        # 当前方向：True=从下往上

    for c in corridors_sorted:
        group = sorted(by_corridor[c], key=lambda t: t.y)
        if not going_up:
            group = list(reversed(group))

        # 横移到该通道
        horiz_dist += horizontal_travel(cur_x, c)
        cur_x = c

        # 沿通道单向走
        for t in group:
            corridor_dist += corridor_travel(cur_y, t.y)
            depth_dist += 2 * t.depth
            cur_y = t.y
            result.sequence.append(t)

        # 走到通道末端（S形的关键：走满整条通道）
        end_y = AISLE_MAX_Y if going_up else AISLE_MIN_Y
        corridor_dist += corridor_travel(cur_y, end_y)
        cur_y = end_y
        going_up = not going_up          # 换方向

    # 回到起点
    horiz_dist += horizontal_travel(cur_x, START_POINT[0])
    corridor_dist += corridor_travel(cur_y, START_POINT[1])

    result.corridor_distance = corridor_dist
    result.horizontal_distance = horiz_dist
    result.depth_distance = depth_dist
    result.total_distance = corridor_dist + horiz_dist + depth_dist
    return result


# =====================================================================
#  策略四：最大间隙（Largest Gap）
# =====================================================================

def largest_gap_route(tasks: List[PickTask]) -> RouteResult:
    """
    最大间隙策略：

    对每条通道，把它看成一条线段，
    找出相邻访问点之间的**最大空隙**，以它为分界：

        |  前半段（从底部进入取）  |  空隙  |  后半段（从顶部进入取）  |
        入口↓                                        入口↑

    好处：不用走到底，只走到空隙处就折返，省下空隙那段的来回距离

    特点：小订单量下平均路径最短（学术界结论）
    代价：路径较复杂，实际作业中不易执行
    """
    if not tasks:
        return RouteResult("largest_gap", "最大间隙")

    by_corridor: Dict[int, List[PickTask]] = {}
    for t in tasks:
        by_corridor.setdefault(t.corridor, []).append(t)

    result = RouteResult("largest_gap", "最大间隙")
    corridor_dist = horiz_dist = depth_dist = 0
    cur_x, cur_y = START_POINT

    for c in sorted(by_corridor.keys()):
        group = sorted(by_corridor[c], key=lambda t: t.y)
        ys = [t.y for t in group]

        # 把通道两端也当作边界点，找最大间隙
        points = [AISLE_MIN_Y] + ys + [AISLE_MAX_Y]
        gaps = [(points[i + 1] - points[i], points[i], points[i + 1])
                for i in range(len(points) - 1)]
        gap_size, gap_lo, gap_hi = max(gaps, key=lambda g: g[0])

        # 前半段：y <= gap_lo 的货位，从底部进入取
        front = [t for t in group if t.y <= gap_lo]
        # 后半段：y >= gap_hi 的货位，从顶部进入取
        back = [t for t in group if t.y >= gap_hi]

        # ★ 关键判断：跳过这个间隙到底划不划算？
        #   不跳（走到底）：2 × 最远货位 y
        #   跳（分两段）  ：2 × gap_lo + 2 × (通道长 - gap_hi)
        # 只有「跳」确实更省时，才分两段走；否则走到底
        cost_no_skip = 2 * max(ys)
        cost_skip = 2 * gap_lo + 2 * (AISLE_MAX_Y - gap_hi)

        if front and back and cost_skip < cost_no_skip:
            # ---- 值得跳：分两段 ----
            horiz_dist += horizontal_travel(cur_x, c)
            cur_x, cur_y = c, AISLE_MIN_Y
            for t in front:
                corridor_dist += corridor_travel(cur_y, t.y)
                depth_dist += 2 * t.depth
                cur_y = t.y
                result.sequence.append(t)
            corridor_dist += corridor_travel(cur_y, AISLE_MIN_Y)
            cur_y = AISLE_MIN_Y

            horiz_dist += horizontal_travel(cur_x, c)
            cur_x = c
            corridor_dist += corridor_travel(cur_y, AISLE_MAX_Y)
            cur_y = AISLE_MAX_Y
            for t in reversed(back):
                corridor_dist += corridor_travel(cur_y, t.y)
                depth_dist += 2 * t.depth
                cur_y = t.y
                result.sequence.append(t)
            corridor_dist += corridor_travel(cur_y, AISLE_MAX_Y)
            cur_y = AISLE_MAX_Y
        else:
            # ---- 不划算：走到底（相当于返回式）----
            horiz_dist += horizontal_travel(cur_x, c)
            cur_x, cur_y = c, AISLE_MIN_Y
            for t in group:
                corridor_dist += corridor_travel(cur_y, t.y)
                depth_dist += 2 * t.depth
                cur_y = t.y
                result.sequence.append(t)
            corridor_dist += corridor_travel(cur_y, AISLE_MIN_Y)
            cur_y = AISLE_MIN_Y

    # 回到起点
    horiz_dist += horizontal_travel(cur_x, START_POINT[0])
    corridor_dist += corridor_travel(cur_y, START_POINT[1])

    result.corridor_distance = corridor_dist
    result.horizontal_distance = horiz_dist
    result.depth_distance = depth_dist
    result.total_distance = corridor_dist + horiz_dist + depth_dist
    return result


# =====================================================================
#  入口：跑全部策略做对比
# =====================================================================

STRATEGIES = {
    "baseline": baseline_route,
    "return": return_route,
    "s_shape": s_shape_route,
    "largest_gap": largest_gap_route,
}


def compare_all(tasks: List[PickTask]) -> Dict[str, RouteResult]:
    """对同一批任务跑所有策略，用于对比实验"""
    return {name: fn(tasks) for name, fn in STRATEGIES.items()}


def optimize(tasks: List[PickTask], strategy: str = "largest_gap") -> RouteResult:
    """按指定策略优化路径"""
    fn = STRATEGIES.get(strategy)
    if fn is None:
        raise ValueError(f"未知策略: {strategy}，可选: {list(STRATEGIES)}")
    return fn(tasks)
