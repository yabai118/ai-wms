# -*- coding: utf-8 -*-
"""
拣货路径优化服务

## 仓库布局（已由数据集论文原文 + 官方布局图核实）

数据集论文：de Assis, R.F., de Paula Ferreira, W., Ouhimmou, M. (2025).
*Order picking dataset from a warehouse of a footwear manufacturing company.*
Data in Brief, 61, 111837.  DOI: 10.1016/j.dib.2025.111837

论文给出的布局参数与结构：

- **3 条纵向通道**（vertical aisles），即横通道，x = 66 / 403 / 686，
  中间那条是论文所说的 **central cross aisle**
- **17 条横向拣货通道**（parallel sorting aisles），位于 17 个 y 值上
  （取自导航点，三列导航点的 y 完全一致）
- 货架排**横向**铺开，位于相邻两条拣货通道**之间**
- 两端横通道 + 中央横通道 → 论文所说的 **three main blocks / 双区块布局**
- 双面货架（double-sided shelves，数据里体现为背靠背的 N/M 两组编号）

    俯视示意（x 向右、y 向上）：

        ┌──────────────────────────────────────┐
        │ ▬▬▬▬▬▬▬▬   ▬▬▬▬▬▬▬▬   ← 货架排（横向）  │  ↑ y
        │ ─────────  ─────────   ← 拣货通道      │
        │ ▬▬▬▬▬▬▬▬   ▬▬▬▬▬▬▬▬                  │
        │ ─────────  ─────────                  │
        └──┬──────────┬──────────┬──────────────┘
           x=66      x=403      x=686        → x
         左横通道   中央横通道   右横通道

⚠️ **方向容易搞反**：拣货员是**沿横向拣货通道走（x 方向）**，
经**纵向横通道（x=66/403/686）换道**。不要把它当成"3 条纵向走廊 + 两端横通道"。

## 距离模型

    沿拣货通道走（x 方向）  → aisle_distance
    经横通道换道（y 方向）  → cross_distance
    从通道走到货位取货      → reach_distance（每个货位固定，与路径顺序无关）

同一条拣货通道内直接沿 x 走；**换通道必须经 3 条纵向横通道之一**，
取距离最短的那条：

    cost = min over c in {66,403,686} of  |x1−c| + |x2−c| + |a1−a2|
"""
from __future__ import annotations

import math
from array import array
from dataclasses import dataclass, field
from typing import List, Dict, Tuple


# =====================================================================
#  仓库布局常量（全部来自论文 + 导航点数据）
# =====================================================================
#: 3 条纵向横通道的 x（论文的 "3 vertical aisles"，中间那条是 central cross aisle）
CROSS_AISLES: List[int] = [66, 403, 686]

#: 17 条横向拣货通道的 y（论文的 "17 parallel sorting aisles"，取自导航点）
AISLE_YS: List[int] = [-29, 61, 151, 241, 331, 421, 511, 631, 751, 841,
                       931, 1021, 1111, 1201, 1291, 1381, 1471]

AISLE_MIN_X: int = CROSS_AISLES[0]      # 66
AISLE_MAX_X: int = CROSS_AISLES[-1]     # 686

#: 拣货起点：最左横通道 × 最下拣货通道（出货口）
START_POINT: Tuple[int, int] = (CROSS_AISLES[0], AISLE_YS[0])

#: 左右两个区块：[左块] x∈[66,403]  ／  [右块] x∈[403,686]
#: 中央横通道 x=403 同时是两个区块的边界——**算法必须用它**，
#: 否则每条通道都要从最左端一路走到最右端。
BLOCKS: List[Tuple[int, int]] = [(CROSS_AISLES[0], CROSS_AISLES[1]),
                                 (CROSS_AISLES[1], CROSS_AISLES[2])]

#: 一个拣货波次的容量上限（拣货车 27 件，论文说明）
WAVE_CAPACITY: int = 27


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
    def aisle(self) -> int:
        """该货位归属的**横向拣货通道**（y 最近的那条）"""
        return min(AISLE_YS, key=lambda a: abs(a - self.y))

    @property
    def reach(self) -> int:
        """从拣货通道走到货位的横向距离（每个货位固定，与拣货顺序无关）"""
        return abs(self.y - self.aisle)


@dataclass
class RouteResult:
    """路径优化结果"""
    strategy: str                       # 策略名
    strategy_name: str                  # 策略中文名
    sequence: List[PickTask] = field(default_factory=list)   # 拣货顺序
    total_distance: int = 0             # 总行走距离（米）
    aisle_distance: int = 0             # 沿横向拣货通道走的距离
    cross_distance: int = 0             # 经纵向横通道换道的距离
    reach_distance: int = 0             # 从通道到货位取货的距离（常数项）
    # 实际行走轨迹的折线顶点 [[x, y], ...]
    path: List[List[int]] = field(default_factory=list)


# =====================================================================
#  行走模型
# =====================================================================

def cross_cost(x1: int, x2: int) -> int:
    """换拣货通道时，沿 x 方向需要额外走的距离（取最短的纵向横通道）"""
    return min(abs(x1 - c) + abs(x2 - c) for c in CROSS_AISLES)


def best_cross(x1: int, x2: int) -> int:
    """换拣货通道时走哪条纵向横通道最省"""
    return min(CROSS_AISLES, key=lambda c: abs(x1 - c) + abs(x2 - c))


class Walk:
    """
    拣货员的行走状态机 —— 全项目唯一的距离模型

    位置 = (沿拣货通道的 x, 拣货通道的 y)。
    沿同一条拣货通道走只改变 x；换通道要经 x=66/403/686 的纵向横通道。
    """

    def __init__(self) -> None:
        self.x, self.a = START_POINT
        self.aisle_dist = 0        # 沿横向拣货通道（x 方向）
        self.cross_dist = 0        # 经纵向横通道换道（y 方向）
        self.reach_dist = 0        # 从通道走到货位
        self.seq: List[PickTask] = []
        self.path: List[List[int]] = [[self.x, self.a]]

    def _emit(self, x: int, a: int) -> None:
        if self.path[-1] != [x, a]:
            self.path.append([x, a])

    def goto(self, x: int, a: int) -> None:
        """走到「第 a 条拣货通道上的 x 位置」"""
        if a == self.a:
            if x != self.x:
                self.aisle_dist += abs(x - self.x)
                self.x = x
                self._emit(x, a)
            return
        c = best_cross(self.x, x)
        self.aisle_dist += abs(self.x - c) + abs(x - c)
        self.cross_dist += abs(a - self.a)
        self._emit(c, self.a)          # 沿本通道走到横通道
        self._emit(c, a)               # 沿横通道换到目标通道
        self._emit(x, a)               # 沿目标通道走到位
        self.x, self.a = x, a

    def pick(self, t: PickTask) -> None:
        """走到货位所在的拣货通道位置，取货"""
        self.goto(t.x, t.aisle)
        self.reach_dist += t.reach
        self.seq.append(t)

    def back_to_start(self) -> None:
        """返回起点（出货口）"""
        self.goto(*START_POINT)

    def finish(self, strategy: str, name: str) -> RouteResult:
        r = RouteResult(strategy, name, list(self.seq))
        r.aisle_distance = self.aisle_dist
        r.cross_distance = self.cross_dist
        r.reach_distance = self.reach_dist
        r.total_distance = self.aisle_dist + self.cross_dist + self.reach_dist
        r.path = list(self.path)
        return r


def group_by_aisle(tasks: List[PickTask]) -> Dict[int, List[PickTask]]:
    by: Dict[int, List[PickTask]] = {}
    for t in tasks:
        by.setdefault(t.aisle, []).append(t)
    for a in by:
        by[a].sort(key=lambda t: t.x)
    return by


# =====================================================================
#  策略一：顺序拣货（基线，复现原系统做法）
# =====================================================================

def baseline_route(tasks: List[PickTask]) -> RouteResult:
    """基线：按任务生成顺序拣货（原系统"无分组、无优化"的做法）"""
    w = Walk()
    if not tasks:
        return w.finish("baseline", "顺序拣货（基线）")
    for t in tasks:
        w.pick(t)
    w.back_to_start()
    return w.finish("baseline", "顺序拣货（基线）")


# =====================================================================
#  策略二：返回式 Return（Hall 1993）
# =====================================================================

def return_route(tasks: List[PickTask]) -> RouteResult:
    """
    返回式：每个**区块段**都从同一端进、取到最远处再原路退回。

    按区块（左块 66–403 / 右块 403–686）分别处理，因此可以用中央横通道
    作为入口——四种策略都用同一套可得的基础设施，比较才公平。

    代价 = 每段 2 × 最远货位到入口那一端的距离
    """
    w = Walk()
    if not tasks:
        return w.finish("return", "返回式")

    by = group_by_aisle(tasks)
    for a in sorted(by.keys()):
        for (c_lo, c_hi) in BLOCKS:
            seg = [t for t in by[a] if c_lo <= t.x <= c_hi]
            if not seg:
                continue
            entry = c_lo if abs(c_lo - w.x) <= abs(c_hi - w.x) else c_hi
            w.goto(entry, a)
            for t in sorted(seg, key=lambda t: abs(t.x - entry)):
                w.goto(t.x, a)
                w.reach_dist += t.reach
                w.seq.append(t)
            w.goto(entry, a)            # 原路退回入口
    w.back_to_start()
    return w.finish("return", "返回式")


# =====================================================================
#  策略三：S形 / 穿越式 S-shape（Hall 1993）
# =====================================================================

def s_shape_route(tasks: List[PickTask]) -> RouteResult:
    """
    S形（穿越式）：在每个**区块段**内单向走满，段间在区块两端交替衔接。

    分左右两个区块各自扫一遍，段内不回头。同样用上了中央横通道。
    """
    w = Walk()
    if not tasks:
        return w.finish("s_shape", "S形")

    by = group_by_aisle(tasks)

    for (c_lo, c_hi) in BLOCKS:
        going_hi = True
        for a in sorted(by.keys()):
            seg = [t for t in by[a] if c_lo <= t.x <= c_hi]
            if not seg:
                continue
            seg = seg if going_hi else list(reversed(seg))
            entry = c_lo if going_hi else c_hi
            exit_ = c_hi if going_hi else c_lo
            w.goto(entry, a)
            for t in seg:
                w.goto(t.x, a)
                w.reach_dist += t.reach
                w.seq.append(t)
            w.goto(exit_, a)            # S形的关键：走满这一整段
            going_hi = not going_hi

    w.back_to_start()
    return w.finish("s_shape", "S形")


# =====================================================================
#  策略四：最大间隙 Largest Gap（Hall 1993）
# =====================================================================

def _segment_split(picks: List[PickTask], c_lo: int, c_hi: int):
    """
    在一个区块段 [c_lo, c_hi] 内找**最大空隙**，把货位切成两组。

    空隙候选含段的两端。返回 (gap_lo, gap_hi, near_lo, near_hi)：
        near_lo = 靠 c_lo 端那组（从 c_lo 进，取完退回 c_lo）
        near_hi = 靠 c_hi 端那组（从 c_hi 进，取完退回 c_hi）
        **gap_lo ~ gap_hi 那一段自始至终不踏入**
    """
    if not picks:
        return c_lo, c_hi, [], []
    xs = sorted(t.x for t in picks)
    points = [c_lo] + xs + [c_hi]
    gaps = [(points[i + 1] - points[i], points[i], points[i + 1])
            for i in range(len(points) - 1)]
    _, gap_lo, gap_hi = max(gaps, key=lambda g: g[0])
    if gap_hi <= gap_lo:                       # 退化保护：避免重复取货
        return gap_lo, gap_hi, list(picks), []
    near_lo = [t for t in picks if t.x <= gap_lo]
    near_hi = [t for t in picks if t.x >= gap_hi]
    return gap_lo, gap_hi, near_lo, near_hi


def _lg3_with(by: Dict[int, List[PickTask]], info: Dict[int, list],
              t1: int, t2: int, t3: int) -> RouteResult:
    """
    三跨通道版最大间隙：完整模拟一条「左端 → 中央 → 右端 → 回到左端」的回路。

        起点(66)
          │ Phase L：在 x=66 上，取各通道【左块段的靠左组】
          ▼ 整条穿越 t1 通道 → x=403
        x=403
          │ Phase M：取各通道【左块段的靠右组】+【右块段的靠左组】
          ▼ 整条穿越 t2 通道 → x=686
        x=686
          │ Phase R：取各通道【右块段的靠右组】
          ▼ 整条穿越 t3 通道 → x=66
        回起点

    t1 / t2 / t3 是被整条走满的"过渡通道"（顺路取完它在对应区段内的货）。
    **其余通道只走各区块段的靠端组，中间的空隙一律不踏入。**
    """
    w = Walk()
    picked: set = set()

    def take(t: PickTask, a: int) -> None:
        if t.task_id in picked:
            return
        w.goto(t.x, a)
        w.reach_dist += t.reach
        w.seq.append(t)
        picked.add(t.task_id)

    aisles = sorted(by.keys())

    # ---- Phase L：在 x=AISLE_MIN_X 上，取各通道左块段的【靠左组】 ----
    for a in aisles:
        if a == t1 or a == t3:
            continue
        _, _, near_lo, _ = info[a][0]
        if not near_lo:
            continue
        w.goto(AISLE_MIN_X, a)
        for t in near_lo:
            take(t, a)
        w.goto(AISLE_MIN_X, a)

    # ---- 过渡 1：整条穿越 t1 到中央横通道 ----
    w.goto(AISLE_MIN_X, t1)
    for t in by[t1]:
        if t.x <= CROSS_AISLES[1]:
            take(t, t1)
    w.goto(CROSS_AISLES[1], t1)

    # ---- Phase M：在中央横通道上 ----
    for a in aisles:
        if a == t3:
            continue
        if a != t1:
            _, _, _, near_hi = info[a][0]      # 左块段的靠右组（从中央进）
            if near_hi:
                w.goto(CROSS_AISLES[1], a)
                for t in reversed(near_hi):
                    take(t, a)
                w.goto(CROSS_AISLES[1], a)
        if a != t2:
            _, _, near_lo, _ = info[a][1]      # 右块段的靠左组（从中央进）
            if near_lo:
                w.goto(CROSS_AISLES[1], a)
                for t in near_lo:
                    take(t, a)
                w.goto(CROSS_AISLES[1], a)

    # ---- 过渡 2：整条穿越 t2 到右端 ----
    w.goto(CROSS_AISLES[1], t2)
    for t in by[t2]:
        if t.x >= CROSS_AISLES[1]:
            take(t, t2)
    w.goto(CROSS_AISLES[2], t2)

    # ---- Phase R：在 x=AISLE_MAX_X 上，取各通道右块段的【靠右组】 ----
    for a in aisles:
        if a == t2 or a == t3:
            continue
        _, _, _, near_hi = info[a][1]
        if not near_hi:
            continue
        w.goto(AISLE_MAX_X, a)
        for t in reversed(near_hi):
            take(t, a)
        w.goto(AISLE_MAX_X, a)

    # ---- 过渡 3：整条穿越 t3 回到左端（顺路取完它剩下的货） ----
    w.goto(AISLE_MAX_X, t3)
    for t in sorted(by[t3], key=lambda t: -t.x):
        take(t, t3)
    w.goto(AISLE_MIN_X, t3)

    w.back_to_start()
    return w.finish("largest_gap", "最大间隙")


def largest_gap_route(tasks: List[PickTask]) -> RouteResult:
    """
    最大间隙策略（Hall 1993）—— **多区块版**

    仓库有 3 条纵向横通道（x=66/403/686），把空间分成左右两个区块。
    对**每条拣货通道 × 每个区块段**，找出段内相邻取货点之间（含段两端）的
    最大空隙，以它为界把该段切成靠两端的两组：

        |  靠左端组（从 c_lo 进）  |  ← 最大空隙，不走 →  |  靠右端组（从 c_hi 进）  |

    **那段空隙自始至终不踏入**——这才是它省距离的地方。

    因为每段要从两端各进一次，整条路线组织成
    「左端 → 穿一条通道到中央 → 中央 → 穿一条通道到右端 → 右端 → 穿一条通道回左端」
    的回路。三条"过渡通道"取哪三条影响总距离，枚举所有组合取最优。
    """
    w = Walk()
    if not tasks:
        return w.finish("largest_gap", "最大间隙")

    by = group_by_aisle(tasks)
    aisles = sorted(by.keys())

    # 每条通道 × 每个区块段 的切分结果
    info: Dict[int, list] = {}
    for a in aisles:
        segs = []
        for (c_lo, c_hi) in BLOCKS:
            seg = [t for t in by[a] if c_lo <= t.x <= c_hi]
            segs.append(_segment_split(seg, c_lo, c_hi))
        info[a] = segs

    if len(aisles) == 1:
        a = aisles[0]
        return _lg3_with(by, info, a, a, a)

    return min(
        (_lg3_with(by, info, t1, t2, t3)
         for t1 in aisles for t2 in aisles for t3 in aisles),
        key=lambda r: r.total_distance,
    )


# =====================================================================
#  精确最优（作为启发式的评价基准）
# =====================================================================

def _move_cost(p1: Tuple[int, int], p2: Tuple[int, int]) -> int:
    """两个「拣货通道位置」(x, aisle_y) 之间的最短行走距离"""
    (x1, a1), (x2, a2) = p1, p2
    if a1 == a2:
        return abs(x1 - x2)
    return cross_cost(x1, x2) + abs(a1 - a2)


def optimal_route(tasks: List[PickTask], max_tasks: int = 12) -> RouteResult:
    """
    精确最优路径（Held-Karp 状态压缩 DP），用作评价基准。

    复杂度 O(2ⁿ·n²)，因此限制任务数；超过 max_tasks 抛异常。
    取货距离 reach 与访问顺序无关，是常数项，单独累加。
    """
    if not tasks:
        return RouteResult("optimal", "精确最优")

    # ★ 先去重：同一个 (x, 拣货通道) 位置上的多个任务其实是**同一个节点**
    #   （拣货员到那一点就一次取完），去重后 DP 规模直接变小，而且结果完全等价。
    groups: Dict[Tuple[int, int], List[PickTask]] = {}
    for t in tasks:
        groups.setdefault((t.x, t.aisle), []).append(t)
    nodes = list(groups.keys())
    n = len(nodes)

    if n > max_tasks:
        raise ValueError(f"去重后位置数 {n} 超过精确求解上限 {max_tasks}")

    constant_reach = sum(t.reach for t in tasks)

    # 预计算距离矩阵：把最短路径算一次存下来，最内层循环就只剩数组访问
    D = [[_move_cost(nodes[i], nodes[j]) for j in range(n)] for i in range(n)]
    d_start = [_move_cost(START_POINT, nodes[i]) for i in range(n)]
    d_end = [_move_cost(nodes[i], START_POINT) for i in range(n)]

    INF = 1 << 60
    full = (1 << n) - 1

    # ★ 两个性能关键点：
    #   ① 用 array('q') 紧凑存整数（8 字节/元素），而不是 Python list of int
    #      —— n=22 时从约 2.6GB 降到 740MB，避免内存换页（那才是真正的时间黑洞）
    #   ② **不存 parent**，路径最后反推。再省一份同量级内存。
    dp = array('q', [INF]) * ((1 << n) * n)

    for i in range(n):
        dp[(1 << i) * n + i] = d_start[i]

    for mask in range(1 << n):
        base = mask * n
        for i in range(n):
            cur = dp[base + i]
            if cur == INF:
                continue
            Di = D[i]
            for j in range(n):
                if mask >> j & 1:
                    continue
                v = cur + Di[j]
                idx = (mask | (1 << j)) * n + j
                if v < dp[idx]:
                    dp[idx] = v

    best, last = INF, -1
    fbase = full * n
    for i in range(n):
        v = dp[fbase + i] + d_end[i]
        if v < best:
            best, last = v, i

    # 反推路径：顺着 dp 关系逐层回溯
    order, mask, cur = [], full, last
    while mask:
        order.append(cur)
        prev_mask = mask ^ (1 << cur)
        if prev_mask == 0:
            break
        target = dp[mask * n + cur]
        pbase = prev_mask * n
        for i in range(n):
            if (prev_mask >> i & 1) and dp[pbase + i] + D[i][cur] == target:
                cur = i
                break
        mask = prev_mask
    order.reverse()

    corridor = cross = 0
    pos = START_POINT
    for i in order + [-1]:
        nxt = START_POINT if i == -1 else nodes[i]
        (x1, a1), (x2, a2) = pos, nxt
        if a1 == a2:
            corridor += abs(x2 - x1)
        else:
            c = best_cross(x1, x2)
            corridor += abs(x1 - c) + abs(x2 - c)
            cross += abs(a2 - a1)
        pos = nxt

    # 把去重后的路径展开回完整任务序列（同一位置上的任务一次取完）
    seq: List[PickTask] = []
    for i in order:
        seq.extend(groups[nodes[i]])
    result = RouteResult("optimal", "精确最优", seq)
    result.aisle_distance = corridor
    result.cross_distance = cross
    result.reach_distance = constant_reach
    result.total_distance = corridor + cross + constant_reach
    return result


# =====================================================================
#  入口
# =====================================================================

STRATEGIES = {
    "baseline": baseline_route,
    "return": return_route,
    "s_shape": s_shape_route,
    "largest_gap": largest_gap_route,
}

STRATEGY_NAMES = {
    "baseline": "顺序拣货（基线）",
    "return": "返回式",
    "s_shape": "S形（穿越式）",
    "largest_gap": "最大间隙",
}


def compare_all(tasks: List[PickTask]) -> Dict[str, RouteResult]:
    """对同一批任务跑所有策略，用于对比实验"""
    return {name: fn(tasks) for name, fn in STRATEGIES.items()}


def optimize(tasks: List[PickTask], strategy: str = "s_shape") -> RouteResult:
    """按指定策略优化路径"""
    fn = STRATEGIES.get(strategy)
    if fn is None:
        raise ValueError(f"未知策略: {strategy}，可选: {list(STRATEGIES)}")
    return fn(tasks)
