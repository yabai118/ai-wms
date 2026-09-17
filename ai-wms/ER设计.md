# AI-WMS 数据库设计（ER 图）

> 依据：[功能清单](功能清单.md) 的 22 个功能点反推
> 数据集：[鞋厂真实 WMS 数据](data/footwear/README.md)（字段直接对应）
> 日期：2026-09-17

---

## 一、表清单（19 张）

| 分组 | 表名 | 说明 | 数据来源 |
|---|---|---|---|
| **基础数据** | `product` | 商品款（208 个） | ✅ 真实 |
| | `product_sku` | SKU = 款 × 尺码（2,515 个） | ✅ 真实 |
| | `warehouse_area` | 库区（18 个 A~R） | ✅ 真实 |
| | `location` | 库位（2,292 个，含坐标） | ✅ 真实 |
| | `customer` | 客户（588 个） | ✅ 真实 |
| | `operator` | 拣货员（24 个） | ✅ 真实 |
| **入库** | `inbound_order` | 入库单 | ⚠️ 构造 |
| | `inbound_order_line` | 入库明细 | ⚠️ 构造 |
| **出库** | `outbound_order` | 出库单（客户订单 32,634 个） | ✅ 真实 |
| | `outbound_order_line` | 出库明细 | ✅ 真实 |
| | `outbound_allocation` | **分配明细**（订单 × 库位）★ | ✅ 可推导 |
| | `picking_wave` | 拣货波次（9,707 个） | ✅ 真实 |
| | `picking_task` | **拣货任务**（库位视角）★ | ✅ 真实 |
| | `shipment` | 发货单 | ⚠️ 构造 |
| **库存** | `inventory` | **即时库存（五字段）**★ | ✅ 可推导 |
| | `inventory_transaction` | **库存流水**（可追溯）★ | ✅ 生成 |
| | `stocktake_order` | 盘点单 | ⚠️ 构造 |
| | `stocktake_line` | 盘点明细 | ⚠️ 构造 |
| **其他** | `alert` | 告警表 | 生成 |

---

## 二、表结构详解

### 1. 基础数据

```sql
-- 商品款
CREATE TABLE product (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  reference    VARCHAR(32)  NOT NULL COMMENT '款号，如 8N10W9',
  abc_class    CHAR(1)      NOT NULL COMMENT 'ABC 分类',
  sector       VARCHAR(16)  COMMENT '分区',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_reference (reference)
) COMMENT='商品款';

-- SKU = 款 × 尺码（库存的真正单位）
CREATE TABLE product_sku (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id  BIGINT       NOT NULL,
  size_us     DECIMAL(4,1) NOT NULL COMMENT '美国码',
  sku_code    VARCHAR(48)  NOT NULL COMMENT '如 8N10W9-41',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sku_code (sku_code),
  KEY idx_product (product_id)
) COMMENT='SKU（款×尺码）';

-- 库区
CREATE TABLE warehouse_area (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  area_code   CHAR(2)     NOT NULL COMMENT 'A~R',
  area_name   VARCHAR(32),
  UNIQUE KEY uk_area_code (area_code)
) COMMENT='库区';

-- 库位（含真实坐标）
CREATE TABLE location (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  location_code VARCHAR(16) NOT NULL COMMENT '如 A-14-11',
  area_id       BIGINT      NOT NULL,
  x_coord       INT         NOT NULL COMMENT 'X 坐标（米）',
  y_coord       INT         NOT NULL COMMENT 'Y 坐标（米）',
  z_coord       INT         NOT NULL COMMENT '层（1~4）',
  capacity      INT         NOT NULL DEFAULT 18 COMMENT '容量：18 个商品位',
  status        TINYINT     NOT NULL DEFAULT 0 COMMENT '0空闲 1占用 2锁定',
  UNIQUE KEY uk_location_code (location_code),
  KEY idx_area (area_id)
) COMMENT='库位';

-- 客户 / 拣货员（简单表）
CREATE TABLE customer (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  cust_code  VARCHAR(32) NOT NULL,
  cust_name  VARCHAR(64),
  UNIQUE KEY uk_cust_code (cust_code)
) COMMENT='客户';

CREATE TABLE operator (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  op_code    VARCHAR(32) NOT NULL,
  op_name    VARCHAR(64),
  UNIQUE KEY uk_op_code (op_code)
) COMMENT='拣货员';
```

### 2. 入库

```sql
CREATE TABLE inbound_order (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no       VARCHAR(32) NOT NULL COMMENT '入库单号',
  order_type     TINYINT     NOT NULL COMMENT '1生产入库 2退货入库',
  source_no      VARCHAR(32) COMMENT '来源工单号',
  status         TINYINT     NOT NULL DEFAULT 0 COMMENT '0待收货 1待上架 2已完成',
  expected_date  DATE,
  created_by     VARCHAR(32),
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status (status)
) COMMENT='入库单';

CREATE TABLE inbound_order_line (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id       BIGINT NOT NULL,
  sku_id         BIGINT NOT NULL,
  plan_qty       INT    NOT NULL COMMENT '计划数量',
  received_qty   INT    DEFAULT 0 COMMENT '实收数量',
  location_id    BIGINT COMMENT '上架库位（上架后填）',
  status         TINYINT NOT NULL DEFAULT 0 COMMENT '0待收货 1已收货 2已上架',
  KEY idx_order (order_id),
  KEY idx_sku (sku_id)
) COMMENT='入库明细';
```

### 3. 出库 ★

```sql
CREATE TABLE outbound_order (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no     VARCHAR(32) NOT NULL COMMENT '订单号',
  customer_id  BIGINT      NOT NULL,
  status       TINYINT     NOT NULL DEFAULT 0 COMMENT '0待分配 1已分配 2拣货中 3已发货',
  created_at   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_customer (customer_id),
  KEY idx_status (status)
) COMMENT='出库单（客户订单）';

CREATE TABLE outbound_order_line (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id    BIGINT NOT NULL,
  sku_id      BIGINT NOT NULL,
  qty         INT    NOT NULL,
  KEY idx_order (order_id)
) COMMENT='出库明细';

-- ★ 分配明细：订单视角（哪个订单的货从哪个库位取）
CREATE TABLE outbound_allocation (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_line_id BIGINT NOT NULL,
  sku_id        BIGINT NOT NULL,
  location_id   BIGINT NOT NULL,
  qty_allocated INT    NOT NULL,
  wave_id       BIGINT COMMENT '所属波次（生成波次后填）',
  status        TINYINT NOT NULL DEFAULT 0 COMMENT '0已分配 1已拣货 2已释放',
  allocated_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order_line (order_line_id),
  KEY idx_wave (wave_id),
  KEY idx_sku_loc (sku_id, location_id)
) COMMENT='出库分配明细（订单视角）';

CREATE TABLE picking_wave (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  wave_no     VARCHAR(32) NOT NULL,
  operator_id BIGINT COMMENT '拣货员',
  status      TINYINT NOT NULL DEFAULT 0 COMMENT '0待拣货 1拣货中 2已完成',
  capacity    INT NOT NULL DEFAULT 27 COMMENT '载具容量：27 件',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wave_no (wave_no),
  KEY idx_status (status)
) COMMENT='拣货波次';

-- ★ 拣货任务：库位视角（拣货员走哪些库位、各取多少）
CREATE TABLE picking_task (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  wave_id        BIGINT NOT NULL,
  sku_id         BIGINT NOT NULL,
  location_id    BIGINT NOT NULL COMMENT '从哪个库位取',
  qty_plan       INT    NOT NULL COMMENT '计划取货数量',
  qty_picked     INT    DEFAULT 0 COMMENT '实际取货数量',
  seq_no         INT COMMENT '拣货顺序（路径优化后填）',
  status         TINYINT NOT NULL DEFAULT 0 COMMENT '0待拣 1已拣 2缺货',
  KEY idx_wave (wave_id),
  KEY idx_location (location_id)
) COMMENT='拣货任务（库位视角）';

CREATE TABLE shipment (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  shipment_no  VARCHAR(32) NOT NULL,
  order_ids    VARCHAR(512) COMMENT '关联订单号（逗号分隔）',
  status       TINYINT NOT NULL DEFAULT 0,
  shipped_at   DATETIME,
  UNIQUE KEY uk_shipment_no (shipment_no)
) COMMENT='发货单';
```

### 4. 库存 ★★ 核心

```sql
-- ★ 即时库存：五字段模型
CREATE TABLE inventory (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku_id          BIGINT NOT NULL,
  location_id     BIGINT NOT NULL,
  qty             INT NOT NULL DEFAULT 0 COMMENT '现有总量（拣货时不变）',
  qty_allocated   INT NOT NULL DEFAULT 0 COMMENT '已分配给订单',
  qty_picked      INT NOT NULL DEFAULT 0 COMMENT '已拣出未发货',
  qty_onhold      INT NOT NULL DEFAULT 0 COMMENT '冻结（质检不合格等）',
  qty_available   INT NOT NULL DEFAULT 0 COMMENT '可用 = qty - allocated - onhold',
  version         INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sku_location (sku_id, location_id),
  KEY idx_sku (sku_id),
  KEY idx_location (location_id)
) COMMENT='即时库存（五字段模型）';

-- ★ 库存流水：每次变动记录，可追溯
CREATE TABLE inventory_transaction (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku_id         BIGINT NOT NULL,
  location_id    BIGINT NOT NULL,
  qty_delta      INT    NOT NULL COMMENT '变动量（正负）',
  biz_type       VARCHAR(16) NOT NULL COMMENT 'RECEIPT/PICK/SHIP/ADJUST/FREEZE/RELEASE',
  reference_type VARCHAR(24) COMMENT '来源单据类型',
  reference_id   BIGINT      COMMENT '来源单据 ID（溯源关键）',
  created_by     VARCHAR(32),
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sku_loc (sku_id, location_id),
  KEY idx_ref (reference_type, reference_id),
  KEY idx_created (created_at)
) COMMENT='库存流水（可追溯）';
```

### 5. 盘点

```sql
CREATE TABLE stocktake_order (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  stocktake_no VARCHAR(32) NOT NULL,
  status       TINYINT NOT NULL DEFAULT 0 COMMENT '0盘点中 1已完成',
  created_by   VARCHAR(32),
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_stocktake_no (stocktake_no)
) COMMENT='盘点单';

CREATE TABLE stocktake_line (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id      BIGINT NOT NULL,
  sku_id        BIGINT NOT NULL,
  location_id   BIGINT NOT NULL,
  book_qty      INT NOT NULL COMMENT '账面数量',
  actual_qty    INT COMMENT '实盘数量',
  diff_qty      INT COMMENT '差异（盘盈+/盘亏-）',
  KEY idx_order (order_id)
) COMMENT='盘点明细';
```

### 6. 告警

```sql
CREATE TABLE alert (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  alert_type      VARCHAR(24) NOT NULL COMMENT '异常类型',
  alert_level     TINYINT NOT NULL COMMENT '1提示 2警告 3严重',
  title           VARCHAR(128),
  detail          TEXT COMMENT '结构化详情（JSON）',
  llm_suggestion  TEXT COMMENT 'LLM 生成的诊断建议',
  status          TINYINT NOT NULL DEFAULT 0 COMMENT '0未处理 1已处理',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_type (alert_type),
  KEY idx_status (status)
) COMMENT='告警（含 LLM 诊断建议）';
```

---

## 三、表关系图

```
product ──1:N── product_sku
                    │
warehouse_area ─1:N─ location
                    │
     ┌──────────────┼──────────────────────┐
     │              │                      │
  inventory    picking_task          inbound_order_line
  (SKU×库位)    (拣货任务)             (入库明细)
     │              │
  inventory_    picking_wave ──N:1── operator
  transaction      │
                   │
            outbound_allocation ──N:1── outbound_order_line ──N:1── outbound_order
                  (分配明细)                                          │
                                                                  customer
```

---

## 四、四个关键设计说明（面试重点）

### ① 为什么 SKU 要拆成两张表（product + product_sku）

鞋类特殊：**同一款鞋的每个尺码是独立库存单位**。
```
product:      8N10W9（款）
product_sku:  8N10W9-41、8N10W9-42 ...（2,515 个）
```
**库存挂到 SKU 上，不是款上**——这是鞋类 WMS 和普通电商的关键区别。

### ② 为什么库存有五个数量字段

| 字段 | 含义 | 解决什么 |
|---|---|---|
| `qty` | 现有总量 | 货架上实际有多少 |
| `qty_allocated` | 已分配 | **防止超卖**（被订单占住） |
| `qty_picked` | 已拣出 | 拣走了但还没发货 |
| `qty_onhold` | 冻结 | 质检不合格等异常占用 |
| `qty_available` | 可用 | = qty - allocated - onhold |

**关键**：`allocated`（订单占用）和 `onhold`（异常冻结）**必须分开**——前者是正常业务，后者是异常状态。

### ③ 为什么"分配明细"和"拣货任务"是两张表

**数据验证过**：同一个库位在一个波次里平均被访问多次（36.7% 有重复），
聚合后**能减少 52% 的行走次数**。

| 表 | 视角 | 回答什么问题 |
|---|---|---|
| `outbound_allocation` | **订单视角** | 这张订单的货从哪个库位取 |
| `picking_task` | **库位视角** | 拣货员走哪些库位、各取多少 |

**少了拣货任务**：拣货员得自己从 20 万条分配记录里算"这个库位一共要取多少"，而且**路径优化没有输入**。

### ④ 为什么库存流水要记 `reference_type + reference_id`

**可追溯 + 可对账**：
```
库存变了 → 查流水 → 知道是哪个操作、哪张单导致的
库存表数字 → 用流水累加重算 → 验证是否一致（对账）
```
**这是排查问题的依据**——真实 WMS 必须有。

---

## 五、和数据集的对应关系

| 表 | 数据集文件 | 对应字段 |
|---|---|---|
| `product` | `Product.csv` | Reference / ABCCOD / Sector |
| `product_sku` | `Customer_Order.csv` | Reference × Size (US) |
| `location` | `Storage_Location.csv` | originalLocation / x / y / z |
| `warehouse_area` | `Storage_Location.csv` | 编号前缀（A~R） |
| `customer` | `Customer_Order.csv` | codCustomer（588 个去重） |
| `operator` | `Picking_Wave.csv` | operator（24 个去重） |
| `outbound_order` | `Customer_Order.csv` | orderNumber（32,634 个去重） |
| `picking_wave` | `Picking_Wave.csv` | waveNumber（9,707 个去重） |
| `picking_task` | `Picking_Wave.csv` | locations + reference + quantityToPick |
| `inventory` | **推导** | 用订单累计出货反推 + 构造期初 |
| `inbound_order` | **构造** | 按鞋厂业务规则构造 |

**结论**：**10 张表的字段直接来自真实数据**，只有库存和入库需要推导/构造。
