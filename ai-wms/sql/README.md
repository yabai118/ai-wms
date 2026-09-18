# 数据库设计与数据脚本

## 一、快速导入（clone 后必做）

```bash
# 依次导入 4 个 SQL 文件（顺序不能颠倒）
mysql -u root -p < 01_schema.sql          # 建库建表（19 张表）
mysql -u root -p < 02_init_base.sql       # 基础数据
mysql -u root -p < 03_init_outbound.sql   # 出库数据
mysql -u root -p < 04_init_inventory.sql  # 初始库存
```

导入完成后数据库里会有：

| 数据 | 数量 |
|---|---|
| 商品款 / SKU | 208 / 2,515 |
| 库区 / 库位 | 18 / 2,314 |
| 客户 / 拣货员 | 588 / 24 |
| 出库单 / 明细 | 32,621 / 115,654 |
| 拣货波次 / 拣货任务 | 9,707 / 112,977 |
| 库存记录 | 5,065（约 12.6 万件） |

## 二、文件说明

### SQL（按导入顺序）

| 文件 | 内容 | 大小 |
|---|---|---|
| `01_schema.sql` | 建表脚本，19 张表（逻辑外键，只建索引不建约束） | 18 KB |
| `02_init_base.sql` | 商品 / SKU / 库区 / 库位 / 客户 / 拣货员 | 175 KB |
| `03_init_outbound.sql` | 出库单 / 明细 / 波次 / 拣货任务 | 6.4 MB |
| `04_init_inventory.sql` | 初始库存（用真实订单出货量反推） | 127 KB |

### 数据生成脚本（可选）

> ⚠️ 这些脚本需要**原始数据集 CSV**（`../data/footwear/Customer_Order.csv` 等），
> 原始文件体积大未入库。**普通使用不需要跑这些脚本**——
> 直接导入上面的 SQL 即可。

| 脚本 | 作用 |
|---|---|
| `gen_base_data.py` | 从原始数据生成基础数据 SQL |
| `gen_outbound_data.py` | 生成出库数据 SQL |
| `gen_inventory.py` | 用订单出货量反推初始库存 |

### 工具（`tools/`）

| 脚本 | 作用 |
|---|---|
| `数据体检.py` | 逐字段核对类型 / 范围 / 空值 / 唯一性 |
| `库位编码规律分析.py` | 分析库位编码 → 坐标的映射规律 |
| `库位使用率分析.py` | 分析库区冷热分布 |
| `最终核对.py` | 数据库 vs 源文件的一致性核对 |
| `出库数据核对.py` | 关系完整性与业务一致性检查 |
| **`并发压测.py`** | ★ 并发分配压测（验证零超卖） |

## 三、表-功能覆盖度核对

| # | 功能 | 用到哪些表 |
|---|---|---|
| 1 | 商品管理 | product, product_sku |
| 2 | 库位管理 | warehouse_area, location |
| 3 | 入库单 | inbound_order, inbound_order_line |
| 4 | 收货 | inbound_order_line.received_qty |
| 5 | 上架 | inbound_order_line.location_id |
| 6 | 订单管理 | outbound_order, outbound_order_line |
| 7 | 分配库存 | outbound_allocation |
| 8 | 波次生成 | picking_wave |
| 9 | 拣货任务 | picking_task |
| 10 | 拣货确认 | picking_task.status / qty_picked |
| 11 | 发货确认 | shipment |
| 12 | 库存查询 | inventory |
| 13 | 库存流水 | inventory_transaction |
| 14 | 冻结/释放 | inventory.qty_onhold + inventory_transaction |
| 15 | 超时释放 | outbound_allocation.status + 定时任务 |
| 16 | 盘点 | stocktake_order, stocktake_line |
| 17 | 货位分配 | location（算法实时计算，不存表） |
| 18 | 拣货路径 | picking_task.seq_no |
| 19 | 自然语言查询 | 查以上所有表 |
| 20 | 异常解释 | alert |
| 21 | 首页看板 | 聚合以上表 |
| 22 | 库位地图 | location.x_coord / y_coord |

✅ 22 个功能全部有表支撑

## 四、为什么用逻辑外键

只建索引、不建物理外键约束，原因：

1. **性能**：物理约束每次插入/更新都要去关联表检查一遍
2. **灵活**：分库分表、批量导数据时物理约束会变成绊脚石
3. **重复**：应用层本来就会校验（如"先查商品存不存在再插入"）

代价是靠代码保证一致性——所以配套做了**库存对账功能**
（用流水累加重算库存，与库存表比对）。
