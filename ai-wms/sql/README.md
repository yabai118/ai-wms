# 数据库与数据生成

## 一、两种使用方式

### 方式 A：只要空系统（新用户 / 从零开始）

```bash
mysql -u root -p < schema.sql
```

跑完得到一个**干净的 WMS 系统**（19 张表，全部为空）。

> 这对应真实 WMS 的上线流程：先部署系统，业务数据由用户后续导入。
> 通过系统的「**数据导入**」页面上传 Excel 即可（商品 / 库位 / 期初库存）。

### 方式 B：导入数据集（验证算法用）

```bash
# ① 用生成脚本产出初始化 SQL
cd generator
python gen_base_data.py       # 商品、库位、客户、拣货员
python gen_outbound_data.py   # 历史订单、波次、拣货任务
python gen_inventory.py       # 期初库存（含流水）

# ② 导入（生成的文件在 ../data/footwear/init-data/）
mysql -u root -p < ../data/footwear/init-data/01_base.sql
mysql -u root -p < ../data/footwear/init-data/02_outbound.sql
mysql -u root -p < ../data/footwear/init-data/03_inventory.sql
```

> 这对应真实企业的实施场景：把历史数据批量导进去，用来验证算法效果。

**数据集来源**：巴西某鞋类制造企业真实 WMS 导出（见 [../data/footwear/README.md](../data/footwear/README.md)）

---

## 二、目录说明

```
sql/
├── schema.sql                     建表脚本（19 张表）★ 从这里开始
├── generator/                     ★ 数据生成脚本（产出初始化 SQL）
│   ├── gen_base_data.py           商品 / 库位 / 客户 / 拣货员
│   ├── gen_outbound_data.py       历史订单 / 波次 / 拣货任务
│   └── gen_inventory.py           期初库存（含 RECEIPT 流水）
└── tools/                         分析 / 核对 / 压测工具
    ├── 数据体检.py                 逐字段核对类型/范围/空值/唯一性
    ├── 库位编码规律分析.py          编码 → 坐标映射规律
    ├── 库位使用率分析.py            冷热分区分析
    ├── 最终核对.py                 数据库 vs 源文件
    ├── 出库数据核对.py              关系完整性与业务一致性
    ├── 并发扣减测试.py              小规模超卖验证
    ├── 并发压测.py                 ★ 四组场景压测（产出报告）
    └── _问题排查/                  开发期的 4 个排查脚本
```

> 生成的初始化 SQL 体积较大（约 6.6MB），**不进 Git**（见 `.gitignore`），
> 需要时跑一遍 `generator/` 下的脚本即可重新产出。

---

## 三、覆盖度核对（22 个功能 → 表）

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
