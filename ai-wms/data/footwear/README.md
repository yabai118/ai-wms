# 鞋厂仓库订单拣货数据集 ⭐ 项目主数据集

> **来源**：巴西鞋类制造企业**真实 WMS 系统用 SQL 导出**的数据
> **论文**：*Order picking dataset from a warehouse of a footwear manufacturing company*（Data in Brief, 2025）
> **作者**：Rodrigo Furlan de Assis, William de Paula Ferreira, Mustapha Ouhimmou（ÉTS Montréal）
> **许可**：CC BY 4.0
> **下载**：https://data.mendeley.com/datasets/pf2w725pw3/1

---

## ⭐ 为什么它是主数据集

**它同时覆盖"需求侧"和"作业侧"——一份顶两套。**

| 维度 | 数据 |
|---|---|
| 商品 | ✅ 208 个（有 ABC 分类、分区） |
| 货位 | ✅ 2,292 个（有 XYZ 坐标） |
| **订单** | ✅ 32,634 个订单 / 122,370 行，**含 creationDate 时间戳** |
| **拣货波次** | ✅ 9,707 个波次 / 215,192 行，**含实际拣货货位** |
| 时间序列 | ✅ 286 天（178 个工作日） |
| 存储策略对照 | ✅ 4 种策略的货位分配方案 |
| 仓库布局图 | ✅ 4 层 SVG/PDF + DWG |

**这是真实的 WMS 生产数据**，不是学术构造——所有流程都能对上企业实际运作。

---

## 一、数据文件详解

### 1. `Product.csv` — 商品主数据（208 个）

```csv
Reference;ABCCOD;Sector
O9YFO8;A;PF
I1X92B;A;PF
HOUGRO;B;PF
```
| 字段 | 说明 |
|---|---|
| Reference | 商品编号（如 `8N10W9`） |
| ABCCOD | **ABC 分类**（A/B/C，按重要性分级） |
| Sector | 所属仓库分区 |

> 只有 208 个商品，但**每个都是真实商品、带 ABC 分类**——正好用于货位分配的 ABC 策略。

### 2. `Storage_Location.csv` — 货位主数据（2,292 个）⭐

```csv
originalLocation,position,x,y,z
A-14-11,"368, 0, 1",368,0,1
A-14-12,"352, 0, 1",352,0,1
```
| 字段 | 说明 |
|---|---|
| originalLocation | 货位编号，格式 `区-排-位`（如 `A-14-11`） |
| x / y / z | **三维坐标（米）**——可直接算拣货行走距离 |

**这解决了 UFMG 数据才能提供的问题：真实货位坐标。**

### 3. `Customer_Order.csv` — 客户订单（122,370 行）⭐

```csv
codCustomer;orderNumber;orderToCollect;Reference;Size (US);quantity (units);creationDate;waveNumber;operator
C0000016;124438;8;8N10W9;9.0;6;19/10/2023 07:18;43175;Operator_1
```
| 字段 | 唯一值 | 说明 |
|---|---|---|
| codCustomer | 588 | 客户编号 |
| **orderNumber** | **32,634** | 订单号 |
| orderToCollect | 340 | 订单拣货序号 |
| Reference | 208 | 商品编号 |
| Size (US) | 45 | 鞋码 |
| quantity (units) | 27 | 数量 |
| **creationDate** | **768** | **下单时间**（格式 `dd/mm/yyyy HH:MM`） |
| waveNumber | 9,784 | 所属拣货波次 |
| operator | 24 | 拣货员 |

**实测统计**：
- 时间跨度：**2023-01-05 ~ 2023-10-19（286 天）**，其中 **178 天有订单**
- 日均订单：**183 单**（中位 163，最大 823）
- 商品销量 Top5：`8N10W9`(18070)、`PY5UPB`(13400)、`WRRW1W`(9521)、`I1KDJ0`(9491)、`05W6TK`(8687)
- **商品-日覆盖率仅 35.2%** → 存在间歇性需求特征

### 4. `Picking_Wave.csv` — 拣货波次（215,192 行）⭐

```csv
waveNumber;reference;Size (US);quantityToPick (units);locations;operator
43175;8N10W9;9.0;1;H-06-13;Operator_1
```
| 字段 | 唯一值 | 说明 |
|---|---|---|
| waveNumber | 9,707 | 波次号 |
| reference | 198 | 商品编号 |
| **locations** | **1,221** | **实际拣货货位**（如 `H-06-13`） |
| operator | 22 | 拣货员 |

**实测统计**：平均每波次 **22.2 行**；每个商品平均被拣货 **1,086.8 次**。

> 论文提到波次上限 **27 件**（对应拣货车容量）。

### 5. 存储策略对照数据（4 种）⭐ 现成的对照实验

| 文件 | 形状 | 说明 |
|---|---|---|
| `Random_Storage.csv` | 2292 × 1 | 随机存储 |
| `Class_Based_Storage.csv` | 2341 × 20 | 按 ABC 分类存储 |
| `Dedicated_Storage.csv` | 2340 × 20 | 固定存储 |
| `Hybrid_Storage.csv` | 2340 × 20 | 混合存储 |

格式：`Location | 分类码 | col_1...col_18`，每个 col 是 `商品码;数量`。

**这是现成的四种货位分配方案**——你可以直接用它们做**对比实验**，验证你的算法能不能做得更好。

### 6. `Support_Points_Navigation.csv` — 导航点（44 个）
### 7. `Layout_Z1.0 ~ Z4.0.svg/pdf` — 4 层仓库平面图
### 8. `Full layout.dwg` — CAD 总图
### 9. `README.txt` — 官方字段说明

---

## 二、数据覆盖矩阵 ⭐

| 模块 | 本数据集能支撑吗 | 用什么 |
|---|---|---|
| **需求预测** | ✅ | 订单的日销量时间序列（178 天） |
| **货位分配** | ✅ | 2,292 货位坐标 + 4 种策略对照 |
| **拣货路径** | ✅ | 真实的拣货波次 + 货位坐标 |
| **商品主数据** | ✅ | 208 个商品 + ABC 分类 |
| **订单/波次** | ✅ | 32,634 订单 + 9,707 波次 |
| **异常诊断** | ⚠️ | 本数据含 operator 和波次信息，可推导作业指标；作业日志仍需构造 |
| **成本参数** | ❌ | 无成本数据（可用行业标准公式，或借菜鸟数据的成本分布） |

**结论：4 个核心模块中 3 个直接被支撑，异常诊断部分支撑——比之前"两套数据拼接"好得多。**

---

## 三、怎么加载（Python）

```python
import pandas as pd

# 注意：多数文件用 ';' 分隔
product  = pd.read_csv('Product.csv', sep=';')
location = pd.read_csv('Storage_Location.csv')          # 这个是逗号分隔
order    = pd.read_csv('Customer_Order.csv', sep=';')
wave     = pd.read_csv('Picking_Wave.csv', sep=';')

# 解析时间
order['dt'] = pd.to_datetime(order['creationDate'], format='%d/%m/%Y %H:%M')
order['date'] = order['dt'].dt.date

# 生成商品的日销量序列（驱动需求预测）
daily_sales = (order.groupby(['Reference', 'date'])['quantity (units)']
                    .sum().reset_index())

# 拣货任务的货位坐标（驱动路径优化）
loc_coord = location.set_index('originalLocation')[['x','y','z']].to_dict('index')
wave['coord'] = wave['locations'].map(loc_coord)
```

---

## 四、与其他数据集的取舍

| | 鞋厂数据集 | 菜鸟 | UFMG |
|---|---|---|---|
| 商品 | 208（真名+ABC） | 963（**仅 ID，无名称**） | 1560（有名称） |
| 货位 | ✅ 2292 + 坐标 | ❌ | ✅ 1584 + 坐标 |
| 订单 | ✅ 32,634 | ❌ | ✅ 141 个实例 |
| **时间序列** | ✅ **286 天** | ✅ 14 个月 | ❌ |
| 拣货波次 | ✅ 9,707 | ❌ | ❌ |
| 成本 | ❌ | ✅ **真实补少/补多成本** | ❌ |
| **数据性质** | **真实企业 WMS** | 真实脱敏 | 学术基准 |

**建议**：
- **主数据集 = 鞋厂**（覆盖最全、最真实）
- **菜鸟数据保留**：作为**成本参数的参考来源**（补少 19.23 / 补多 24.41 的真实分布），以及需求预测算法的**第二个验证集**
- **UFMG 可作补充**：如果需要更大规模的路径优化压测（O=5000），它的实例更适合

---

## 五、面试话术

> "我用的是**巴西一家鞋类制造企业真实 WMS 系统导出的订单拣货数据**——包含 208 个商品、2,292 个带三维坐标的货位、32,634 个订单（跨 286 天）、9,707 个拣货波次，以及**四种存储策略的对照方案**。
>
> 这让我的需求预测、货位分配、拣货路径三个模块**全部建立在真实业务数据上**，而且四种存储策略给了我现成的对比基准。"
