# 数据库与数据生成

## 一、三种使用方式

### 方式 A：全新安装（最常用）

```bash
mysql -u root -p < schema.sql                      # ① 建库建表（22 张）
mysql -u root -p < dev/dev_data.sql                # ② 灌开发数据（自造，秒级）
```

跑完得到一个**能直接跑起来的系统**：真实库位布局 + 自造的商品/客户/员工 + 4 个演示账号。

> **开发阶段推荐这条**：自造数据可反复重建，不用每次改表都重导 6.7MB 的真实数据集。
> 关于"为什么只保留库位是真实的"，见文件头的说明。

### 方式 B：导入真实数据集（出简历数字 / 答辩演示用）

```bash
# ① 用生成脚本产出初始化 SQL
cd generator
python gen_base_data.py       # 商品、库位、客户、员工
python gen_outbound_data.py   # 历史订单、波次、拣货任务
python gen_inventory.py       # 期初库存（含流水）

# ② 导入（生成的文件在 ../data/footwear/init-data/）
mysql -u root -p < ../data/footwear/init-data/01_base.sql
mysql -u root -p < ../data/footwear/init-data/02_outbound.sql
mysql -u root -p < ../data/footwear/init-data/03_inventory.sql
```

**数据集来源**：巴西某鞋类制造企业真实 WMS 导出（见 [../data/footwear/README.md](../data/footwear/README.md)）

> ⚠️ **方式 A 和方式 B 会互相覆盖**（都是先 DELETE 再 INSERT）。
> 同一时间只用一条，切回来就重跑另一条 —— 这也是当初拆成两条路的原因。

### 方式 C：空系统（不用示例数据）

```bash
mysql -u root -p < schema.sql
```

只建表不灌数据。商品/库位/期初库存可以通过系统的「**数据导入**」页面上传 Excel。

---

## 二、目录说明

```
sql/
├── schema.sql                     ★ 建表脚本（22 张表，当前完整结构）
├── migration/                     ⚠️ 存量库升级用，全新安装【不要跑】
│   ├── 01_add_auth.sql            加登录账号（sys_user）
│   ├── 02_add_rbac.sql            角色从常量改成表（表驱动 RBAC）
│   └── 03_rename_operator_to_staff.sql  账号模型重构 + 表改名
├── generator/                     真实数据集导入（方式 B）
│   ├── gen_base_data.py           商品 / 库位 / 客户 / 员工
│   ├── gen_outbound_data.py       历史订单 / 波次 / 拣货任务
│   └── gen_inventory.py           期初库存（含 RECEIPT 流水）
├── dev/                           开发环境（方式 A）
│   ├── gen_dev_data.py            自造主数据 + 4 个演示账号
│   └── reset_dev_data.sql         只清业务流水，保留主数据
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

### ★ 关于 `migration/` 和 `schema.sql` 的分工

这两者**不是二选一，是给不同场景用的**：

| | 什么时候用 |
|---|---|
| **`schema.sql`** | **全新安装**。它是**当前完整结构**，直接建出 22 张表 |
| **`migration/`** | **已经有一个旧结构的库**，要升级到当前结构。按编号顺序跑 |

**全新安装跑了 migration 会出错** —— 比如 `01` 会建一张老形态的 `sys_user`，
而 `schema.sql` 建的已经是新形态了。

> **维护约定**：改了表结构之后，**两处都要动** ——
> `schema.sql` 更新成最新结构，同时在 `migration/` 加一个编号脚本记录"怎么从旧改到新"。
> 前者给新用户，后者给存量库。

> 生成的初始化 SQL 体积较大（约 6.6MB），**不进 Git**（见 `.gitignore`），
> 需要时跑一遍 `generator/` 下的脚本即可重新产出。
> 同理 `dev/dev_data.sql` 也是生成物，不入库。

---

## 三、覆盖度核对（功能 → 表）

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
| 15 | 超时释放 | ⚠️ **未实现**（定时任务没写） |
| 16 | 盘点 | ⚠️ 表在（stocktake_*），**功能未实现** |
| 17 | 货位分配 | location（算法实时计算，不存表） |
| 18 | 拣货路径 | picking_task.seq_no |
| 19 | 自然语言查询 | 查以上所有表 |
| 20 | 异常解释 | ⚠️ alert 是**空壳表**（零实体、零写入） |
| 21 | 首页看板 | 聚合以上表 |
| 22 | 库位地图 | location.x_coord / y_coord |
| 23 | **登录鉴权** | staff, sys_user, sys_role, sys_role_permission |

> ⚠️ 标注的三项是**如实标注的未完成项**，不要当成已实现功能对外讲。
