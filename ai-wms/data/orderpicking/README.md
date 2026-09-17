# UFMG · 订单拣货问题测试实例（Order Picking Instances）

> **来源**：巴西米纳斯吉拉斯联邦大学（UFMG），Cristiano Arbex Valle 等
> **论文**：*Optimally solving the joint order batching and picker routing problem*（European Journal of Operational Research, 2017）
> **原始数据**：商品信息来自 Foodmart 零售数据库（真实商品目录）
> **获取地址**：http://homepages.dcc.ufmg.br/~arbex/orderpicking.html

## ⭐ 这份数据补上了什么缺口

菜鸟数据集只有**需求侧**数据（销量、成本），缺**作业侧**数据。这份恰好相反——它提供**仓库布局、货位坐标、订单篮子**：

| 你的模块 | 菜鸟数据 | **本数据集** |
|---|---|---|
| 需求预测 | ✅ 有 | — |
| 分仓备货 | ✅ 有 | — |
| **货位分配** | ❌ 无货位 | ✅ **1584 个货位 + 坐标 + 商品映射** |
| **拣货路径** | ❌ 无布局 | ✅ **8/16 巷道布局 + 图表示 + 行走距离** |
| **商品共现** | ❌ 无订单篮子 | ✅ **订单篮子（商品+数量）** |
| 压测 | ❌ | ✅ **O=5000 的大实例** |

---

## 一、数据文件

### 1. `raw/warehouse_8_1_3_1560` — 仓库布局 + 图表示

一份**可直接计算行走距离的仓库模型**。

**仓库参数**：
| 参数 | 值 |
|---|---|
| 巷道数（aisles） | 8 |
| 横通道（cross-aisles） | 3（顶部 + 底部 + 1 条中间） |
| 货架层数（shelves） | 3 |
| 总货位数 | 1584 |
| 图顶点数 | 289（264 个商品顶点 + 24 个人工顶点 + 1 个原点） |
| 每侧货位数 | 33 |

**文件内四段数据**：
| 段落 | 内容 |
|---|---|
| `INPUT_PARAMETERS` | 布局参数 + 标准行走距离（巷道宽、货架深、通道宽等） |
| `all_locations_X_aislePos_Y_aisleSide_Z_shelf` | **每个货位的坐标**（货位号、巷道位置、巷道侧、货架层） |
| `position_product_vertices_X_aislePos_Y_aisle` | 商品顶点坐标（拣货员在该点可取巷道两侧的货） |
| `position_artificial_vertices_aisle_block` | 人工顶点（用于构建图，不拣货） |
| `vertices_pick_which_locations` | 顶点 ↔ 货位的对应关系 |

另有 `warehouse_8_0_3_1560`（**单区块**、8 巷道、2 横通道）用于对比。

### 2. `raw/productsDB_1560_list` — 商品列表（1560 个）

```csv
id,family,department,category,subcategory,name
266,Drink,Alcoholic Beverages,Beer and Wine,Beer,Good Imported Beer
273,Drink,Alcoholic Beverages,Beer and Wine,Beer,Good Light Beer
```
含**真实零售商品名和四级类目**——可直接作为你 WMS 的商品主数据。

### 3. `raw/productsDB_1560_locations` — ⭐ 商品 → 货位映射

```
1560
id location
266 1
273 2
893 3
```
**这就是"货位分配"的初始状态**——你的货位优化算法正是要调整这个映射。

### 4. `raw/orders/` — 订单实例（141 个文件）

**命名规则**：`instances_d{Delta}_ord{O}`
- `Delta` ∈ {5, 10, 20}（订单中商品种类数的上限参数）
- `O` = 订单数，从 **5 到 75**

**文件格式**：
```
10                      ← 本文件包含的订单数
numProducts product quantity   ← 表头
23 12 3 53 4 120 3 ...   ← 第1个订单：商品种类数=23，然后 (商品ID, 数量) 对
23 39 2 63 3 87 4 ...   ← 第2个订单
```

### 5. `raw/largeInstances/` — 大实例（8 个）

订单数 O = **100 / 200 / 1000 / 2000 / 5000**——**用于压力测试**（测你的路径算法在几千订单下的耗时）。

### 6. `raw/instanceFilesDescription.txt` — 官方文件说明

---

## 二、怎么加载（Python）

```python
# 商品 → 货位映射
with open('raw/productsDB_1560_locations') as f:
    f.readline()          # 1560
    f.readline()          # id location
    prod_loc = {int(a): int(b) for a, b in (l.split() for l in f if l.strip())}

# 商品主数据
import csv
with open('raw/productsDB_1560_list', encoding='utf-8') as f:
    next(f)               # 1560
    products = list(csv.DictReader(f))

# 货位坐标（从 warehouse 文件的 all_locations 段解析）
locs = {}
with open('raw/warehouse_8_1_3_1560') as f:
    lines = f.read().split('\n')
    i = lines.index('all_locations_X_aislePos_Y_aisleSide_Z_shelf') + 1
    i += 1                # 跳过 totalLocations 计数行
    for l in lines[i:i+1584]:
        lid, aisle, side, shelf = map(int, l.split())
        locs[lid] = (aisle, side, shelf)

# 订单
def load_orders(path):
    lines = [l for l in open(path) if l.strip()]
    n = int(lines[0])
    orders = []
    for l in lines[2:2+n]:
        tok = l.split()
        pairs = [(int(tok[i]), int(tok[i+1])) for i in range(1, len(tok), 2)]
        orders.append(pairs)          # [(商品ID, 数量), ...]
    return orders
```

---

## 三、这份数据怎么驱动设计

| 模块 | 用这份数据做什么 |
|---|---|
| **货位分配** | 初始商品→货位映射是"随机存储"；你的算法要重排它，目标是**最小化拣货行走距离**。可对比 随机构造 vs 你的算法 |
| **拣货路径** | 有仓库图 → 可实现 **S形 / 最大间隙 / 返回式** 三种策略，用订单实例跑对比实验 |
| **商品共现** | 订单篮子里统计共现 → 驱动"关联商品放邻近货位"（**菜鸟数据做不到这一点**） |
| **订单波次** | 多个订单合并成波次 → 验证波次合并对行走距离的影响 |
| **压测** | `largeInstances` 的 O=5000 实例测算法耗时 |

---

## 四、和菜鸟数据的分工

```
菜鸟数据集  →  需求侧：卖多少、备多少 → 需求预测 + 分仓备货规划
本数据集    →  作业侧：放哪里、怎么拣 → 货位分配 + 拣货路径
```

**两份数据合起来，才覆盖了你的完整系统。** 面试时可以说：

> "我用了两套真实数据：**菜鸟需求预测赛题的真实脱敏数据**支撑需求侧决策（预测 + 分仓备货），**UFMG 订单拣货基准实例**支撑作业侧决策（货位 + 路径）。"
