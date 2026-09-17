-- =====================================================================
--  AI-WMS 智能仓储系统 · 建表脚本
--  数据库：MySQL 8.0
--  字符集：utf8mb4
--  说明：采用【逻辑外键】——只建索引不建外键约束（避免性能损耗与迁移麻烦）
-- =====================================================================

DROP DATABASE IF EXISTS ai_wms;
CREATE DATABASE ai_wms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai_wms;


-- =====================================================================
--  一、基础数据（6 张表）
--  数据来源：鞋厂数据集（真实）
-- =====================================================================

-- 1. 商品款（208 个）
DROP TABLE IF EXISTS product;
CREATE TABLE product (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  reference   VARCHAR(32)  NOT NULL                COMMENT '款号，如 8N10W9',
  abc_class   CHAR(1)      NOT NULL DEFAULT 'C'    COMMENT 'ABC 分类（A/B/C）',
  sector      VARCHAR(16)           DEFAULT NULL   COMMENT '所属分区',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reference (reference)
) ENGINE=InnoDB COMMENT='商品款（208 个）';


-- 2. SKU = 款 × 尺码（2,515 个）★ 库存的真正单位
DROP TABLE IF EXISTS product_sku;
CREATE TABLE product_sku (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  product_id  BIGINT       NOT NULL                COMMENT '商品款 ID',
  size_us     DECIMAL(4,1) NOT NULL                COMMENT '美国码',
  sku_code    VARCHAR(48)  NOT NULL                COMMENT 'SKU 编码，如 8N10W9-41',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sku_code (sku_code),
  KEY idx_product (product_id)
) ENGINE=InnoDB COMMENT='SKU（款×尺码），库存单位';


-- 3. 库区（18 个，A~R）
DROP TABLE IF EXISTS warehouse_area;
CREATE TABLE warehouse_area (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  area_code  CHAR(2)     NOT NULL                  COMMENT '区号 A~R',
  area_name  VARCHAR(32)          DEFAULT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_area_code (area_code)
) ENGINE=InnoDB COMMENT='库区（18 个）';


-- 4. 库位（2,292 个，含真实坐标）★
--    数据发现：48% 的库位从未被拣货使用（A~G 区完全未用）→ 据此区分库位类型
DROP TABLE IF EXISTS location;
CREATE TABLE location (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  location_code VARCHAR(16) NOT NULL               COMMENT '库位号，如 A-14-11',
  area_id       BIGINT      NOT NULL               COMMENT '所属库区',
  location_type TINYINT     NOT NULL DEFAULT 1     COMMENT '0存储区(储备) 1拣货区 2收货区 3发货区',
  x_coord       INT         NOT NULL               COMMENT 'X 坐标（米）',
  y_coord       INT         NOT NULL               COMMENT 'Y 坐标（米）',
  z_coord       INT         NOT NULL               COMMENT '层（1~4）',
  capacity      INT         NOT NULL DEFAULT 18    COMMENT '容量（商品位数，真实为 18）',
  used_slots    INT         NOT NULL DEFAULT 0     COMMENT '已用商品位数',
  status        TINYINT     NOT NULL DEFAULT 0     COMMENT '0空闲 1占用 2锁定',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_location_code (location_code),
  KEY idx_area (area_id),
  KEY idx_type (location_type),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='库位（2,314 个，含坐标，区分存储区/拣货区）';


-- 5. 客户（588 个）
DROP TABLE IF EXISTS customer;
CREATE TABLE customer (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  cust_code  VARCHAR(32) NOT NULL                  COMMENT '客户编号',
  cust_name  VARCHAR(64)          DEFAULT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cust_code (cust_code)
) ENGINE=InnoDB COMMENT='客户（588 个）';


-- 6. 拣货员（24 个）
DROP TABLE IF EXISTS operator;
CREATE TABLE operator (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  op_code    VARCHAR(32) NOT NULL                  COMMENT '工号',
  op_name    VARCHAR(64)          DEFAULT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_op_code (op_code)
) ENGINE=InnoDB COMMENT='拣货员（24 个）';


-- =====================================================================
--  二、入库（2 张表）
--  数据来源：构造（鞋厂成品入库）
-- =====================================================================

-- 7. 入库单
DROP TABLE IF EXISTS inbound_order;
CREATE TABLE inbound_order (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  order_no      VARCHAR(32) NOT NULL               COMMENT '入库单号，如 RK20230105-001',
  order_type    TINYINT     NOT NULL DEFAULT 1     COMMENT '1生产入库 2退货入库 3调拨入库',
  source_no     VARCHAR(32)          DEFAULT NULL  COMMENT '来源工单号',
  status        TINYINT     NOT NULL DEFAULT 0     COMMENT '0待收货 1待上架 2已完成',
  expected_date DATE                 DEFAULT NULL  COMMENT '预计到货日期',
  remark        VARCHAR(255)         DEFAULT NULL,
  created_by    VARCHAR(32)          DEFAULT NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status (status),
  KEY idx_created (created_at)
) ENGINE=InnoDB COMMENT='入库单';


-- 8. 入库明细
DROP TABLE IF EXISTS inbound_order_line;
CREATE TABLE inbound_order_line (
  id           BIGINT   NOT NULL AUTO_INCREMENT,
  order_id     BIGINT   NOT NULL                   COMMENT '入库单 ID',
  sku_id       BIGINT   NOT NULL                   COMMENT 'SKU ID',
  plan_qty     INT      NOT NULL                   COMMENT '计划数量',
  received_qty INT      NOT NULL DEFAULT 0         COMMENT '实收数量',
  location_id  BIGINT            DEFAULT NULL      COMMENT '上架库位（上架后填）',
  status       TINYINT  NOT NULL DEFAULT 0         COMMENT '0待收货 1已收货 2已上架',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_sku (sku_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='入库明细';


-- =====================================================================
--  三、出库（6 张表）★ 核心
--  数据来源：鞋厂数据集（真实）
-- =====================================================================

-- 9. 出库单（客户订单，32,634 个）
DROP TABLE IF EXISTS outbound_order;
CREATE TABLE outbound_order (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  order_no    VARCHAR(32) NOT NULL                 COMMENT '订单号',
  customer_id BIGINT      NOT NULL                 COMMENT '客户 ID',
  status      TINYINT     NOT NULL DEFAULT 0       COMMENT '0待分配 1已分配 2拣货中 3已发货 4已取消',
  order_time  DATETIME             DEFAULT NULL    COMMENT '下单时间（真实数据）',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_customer (customer_id),
  KEY idx_status (status),
  KEY idx_order_time (order_time)
) ENGINE=InnoDB COMMENT='出库单（客户订单）';


-- 10. 出库明细
DROP TABLE IF EXISTS outbound_order_line;
CREATE TABLE outbound_order_line (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  order_id   BIGINT   NOT NULL                     COMMENT '出库单 ID',
  sku_id     BIGINT   NOT NULL                     COMMENT 'SKU ID',
  qty        INT      NOT NULL                     COMMENT '订购数量',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_sku (sku_id)
) ENGINE=InnoDB COMMENT='出库明细';


-- 11. ★ 分配明细（订单视角）
--     回答：这张订单要的货，从哪个库位取
DROP TABLE IF EXISTS outbound_allocation;
CREATE TABLE outbound_allocation (
  id            BIGINT   NOT NULL AUTO_INCREMENT,
  order_line_id BIGINT   NOT NULL                  COMMENT '出库明细 ID',
  sku_id        BIGINT   NOT NULL                  COMMENT 'SKU ID',
  location_id   BIGINT   NOT NULL                  COMMENT '分配的库位',
  qty_allocated INT      NOT NULL                  COMMENT '分配数量',
  wave_id       BIGINT            DEFAULT NULL     COMMENT '所属波次（生成波次后填）',
  task_id       BIGINT            DEFAULT NULL     COMMENT '所属拣货任务（聚合后回填）',
  status        TINYINT  NOT NULL DEFAULT 0        COMMENT '0已分配 1已拣货 2已释放',
  allocated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  released_at   DATETIME          DEFAULT NULL     COMMENT '释放时间（超时释放）',
  PRIMARY KEY (id),
  KEY idx_order_line (order_line_id),
  KEY idx_wave (wave_id),
  KEY idx_task (task_id),
  KEY idx_sku_loc (sku_id, location_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='出库分配明细（订单视角）';


-- 12. 拣货波次（9,707 个）
DROP TABLE IF EXISTS picking_wave;
CREATE TABLE picking_wave (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  wave_no      VARCHAR(32) NOT NULL                COMMENT '波次号',
  operator_id  BIGINT               DEFAULT NULL   COMMENT '拣货员',
  status       TINYINT     NOT NULL DEFAULT 0      COMMENT '0待拣货 1拣货中 2已完成',
  capacity     INT         NOT NULL DEFAULT 27     COMMENT '载具容量（27 件）',
  total_qty    INT         NOT NULL DEFAULT 0      COMMENT '本波次总件数',
  total_tasks  INT         NOT NULL DEFAULT 0      COMMENT '本波次任务数',
  path_distance INT                DEFAULT NULL    COMMENT '路径总距离（优化后填）',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at  DATETIME             DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wave_no (wave_no),
  KEY idx_operator (operator_id),
  KEY idx_status (status),
  KEY idx_created (created_at)
) ENGINE=InnoDB COMMENT='拣货波次';


-- 13. ★ 拣货任务（库位视角）
--     回答：拣货员走哪些库位、各取多少
DROP TABLE IF EXISTS picking_task;
CREATE TABLE picking_task (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  wave_id     BIGINT   NOT NULL                    COMMENT '所属波次',
  sku_id      BIGINT   NOT NULL                    COMMENT 'SKU ID',
  location_id BIGINT   NOT NULL                    COMMENT '从哪个库位取',
  qty_plan    INT      NOT NULL                    COMMENT '计划取货数量',
  qty_picked  INT      NOT NULL DEFAULT 0          COMMENT '实际取货数量',
  seq_no      INT               DEFAULT NULL       COMMENT '拣货顺序（路径优化后填）',
  status      TINYINT  NOT NULL DEFAULT 0          COMMENT '0待拣 1已拣 2缺货',
  picked_at   DATETIME          DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_wave (wave_id),
  KEY idx_location (location_id),
  KEY idx_sku (sku_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='拣货任务（库位视角）';


-- 14. 发货单
DROP TABLE IF EXISTS shipment;
CREATE TABLE shipment (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  shipment_no VARCHAR(32) NOT NULL                 COMMENT '发货单号',
  wave_id     BIGINT               DEFAULT NULL    COMMENT '关联波次',
  total_qty   INT         NOT NULL DEFAULT 0       COMMENT '发货总件数',
  status      TINYINT     NOT NULL DEFAULT 0       COMMENT '0待发货 1已发货',
  shipped_at  DATETIME             DEFAULT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shipment_no (shipment_no),
  KEY idx_wave (wave_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='发货单';


-- =====================================================================
--  四、库存（4 张表）★ 核心
--  数据来源：系统运行产生 + 一次性初始化
-- =====================================================================

-- 15. ★★ 即时库存（五字段模型）
DROP TABLE IF EXISTS inventory;
CREATE TABLE inventory (
  id            BIGINT   NOT NULL AUTO_INCREMENT,
  sku_id        BIGINT   NOT NULL                  COMMENT 'SKU ID',
  location_id   BIGINT   NOT NULL                  COMMENT '库位 ID',
  qty           INT      NOT NULL DEFAULT 0        COMMENT '现有总量（拣货时不变）',
  qty_allocated INT      NOT NULL DEFAULT 0        COMMENT '已分配给订单',
  qty_picked    INT      NOT NULL DEFAULT 0        COMMENT '已拣出未发货',
  qty_onhold    INT      NOT NULL DEFAULT 0        COMMENT '冻结量（质检不合格等）',
  qty_available INT      NOT NULL DEFAULT 0        COMMENT '可用量 = qty - allocated - onhold',
  version       INT      NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sku_location (sku_id, location_id),
  KEY idx_sku (sku_id),
  KEY idx_location (location_id),
  KEY idx_available (sku_id, qty_available)
) ENGINE=InnoDB COMMENT='即时库存（五字段模型）';


-- 16. ★ 库存流水（可追溯）
DROP TABLE IF EXISTS inventory_transaction;
CREATE TABLE inventory_transaction (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  sku_id         BIGINT      NOT NULL              COMMENT 'SKU ID',
  location_id    BIGINT      NOT NULL              COMMENT '库位 ID',
  qty_delta      INT         NOT NULL              COMMENT '变动量（正负）',
  biz_type       VARCHAR(16) NOT NULL              COMMENT 'RECEIPT/PICK/SHIP/ADJUST/FREEZE/RELEASE/ALLOCATE',
  reference_type VARCHAR(24)          DEFAULT NULL COMMENT '来源单据类型',
  reference_id   BIGINT               DEFAULT NULL COMMENT '来源单据 ID（溯源关键）',
  remark         VARCHAR(255)         DEFAULT NULL,
  created_by     VARCHAR(32)          DEFAULT NULL,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sku_loc (sku_id, location_id),
  KEY idx_ref (reference_type, reference_id),
  KEY idx_biz_type (biz_type),
  KEY idx_created (created_at)
) ENGINE=InnoDB COMMENT='库存流水（可追溯、可对账）';


-- 17. 盘点单
DROP TABLE IF EXISTS stocktake_order;
CREATE TABLE stocktake_order (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  stocktake_no  VARCHAR(32) NOT NULL               COMMENT '盘点单号',
  status        TINYINT     NOT NULL DEFAULT 0     COMMENT '0盘点中 1已完成',
  total_lines   INT         NOT NULL DEFAULT 0     COMMENT '盘点明细数',
  diff_lines    INT         NOT NULL DEFAULT 0     COMMENT '有差异的行数',
  created_by    VARCHAR(32)          DEFAULT NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at   DATETIME             DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stocktake_no (stocktake_no),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='盘点单';


-- 18. 盘点明细
DROP TABLE IF EXISTS stocktake_line;
CREATE TABLE stocktake_line (
  id          BIGINT   NOT NULL AUTO_INCREMENT,
  order_id    BIGINT   NOT NULL                    COMMENT '盘点单 ID',
  sku_id      BIGINT   NOT NULL,
  location_id BIGINT   NOT NULL,
  book_qty    INT      NOT NULL                    COMMENT '账面数量',
  actual_qty  INT               DEFAULT NULL       COMMENT '实盘数量',
  diff_qty    INT               DEFAULT NULL       COMMENT '差异（正=盘盈，负=盘亏）',
  status      TINYINT  NOT NULL DEFAULT 0          COMMENT '0待盘 1已盘',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_sku_loc (sku_id, location_id)
) ENGINE=InnoDB COMMENT='盘点明细';


-- =====================================================================
--  五、其他
-- =====================================================================

-- 19. 告警（含 LLM 诊断建议）
DROP TABLE IF EXISTS alert;
CREATE TABLE alert (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  alert_type     VARCHAR(24)  NOT NULL             COMMENT '异常类型（如 PICK_TIMEOUT/SLOW_OPERATOR）',
  alert_level    TINYINT      NOT NULL DEFAULT 1   COMMENT '1提示 2警告 3严重',
  title          VARCHAR(128) NOT NULL,
  detail         TEXT                  DEFAULT NULL COMMENT '结构化详情（JSON）',
  llm_suggestion TEXT                  DEFAULT NULL COMMENT 'LLM 生成的诊断建议',
  status         TINYINT      NOT NULL DEFAULT 0   COMMENT '0未处理 1已处理',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at     DATETIME              DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_type (alert_type),
  KEY idx_level (alert_level),
  KEY idx_status (status),
  KEY idx_created (created_at)
) ENGINE=InnoDB COMMENT='告警（含 LLM 诊断建议）';


-- =====================================================================
--  建表完成：19 张表
-- =====================================================================
SELECT table_name, table_comment
FROM information_schema.tables
WHERE table_schema = 'ai_wms'
ORDER BY table_name;
