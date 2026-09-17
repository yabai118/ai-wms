# 数据库设计

## 文件
- `01_schema.sql` — 建表语句（19 张表）
- `02_init_data.sql` — 初始化数据（后续生成）
- `03_construct.sql` — 构造数据脚本（入库单/期初库存，后续生成）

## 覆盖度核对（22 个功能 → 表）

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
