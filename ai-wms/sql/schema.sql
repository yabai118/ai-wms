-- =====================================================================
--  AI-WMS 智能仓储系统 · 建表脚本
--  数据库：MySQL   字符集：utf8mb4
--
--  共 22 张表。本文件是【当前完整结构】，与代码保持一致。
--
--  全新安装：
--    mysql -u root -p < sql/schema.sql          ← 建库建表
--    mysql -u root -p < sql/dev/dev_data.sql    ← 灌开发数据
--    （或改用 sql/generator/ 下的脚本导真实数据集）
--
--  ⚠️ sql/migration/ 下的脚本是【存量库升级用】，全新安装不要跑 ——
--     它们记录的是「从旧结构改到新结构」的过程，新库已经是新结构了。
--
--  设计说明：采用【逻辑外键】——只建索引不建外键约束（避免性能损耗与迁移麻烦）
-- =====================================================================

DROP DATABASE IF EXISTS ai_wms;
CREATE DATABASE ai_wms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai_wms;


-- =====================================================================
--  一、基础数据（5 张表）
--  商品、库位、客户 —— 来自数据集或自造，是业务单据的引用对象
-- =====================================================================

-- 1. product（商品款（208 个））
DROP TABLE IF EXISTS product;
CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `reference` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '款号，如 8N10W9',
  `abc_class` char(1) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'C' COMMENT 'ABC 分类（A/B/C）',
  `sector` varchar(16) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属分区',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reference` (`reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品款（208 个）';

-- 2. product_sku（SKU（款×尺码），库存单位）
DROP TABLE IF EXISTS product_sku;
CREATE TABLE `product_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL COMMENT '商品款 ID',
  `size_us` decimal(4,1) NOT NULL COMMENT '美国码',
  `sku_code` varchar(48) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SKU 编码，如 8N10W9-41',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SKU（款×尺码），库存单位';

-- 3. warehouse_area（库区（18 个））
DROP TABLE IF EXISTS warehouse_area;
CREATE TABLE `warehouse_area` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `area_code` char(2) COLLATE utf8mb4_general_ci NOT NULL COMMENT '区号 A~R',
  `area_name` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_area_code` (`area_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库区（18 个）';

-- 4. location（库位（2,314 个，含坐标，区分存储区/拣货区））
DROP TABLE IF EXISTS location;
CREATE TABLE `location` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `location_code` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '库位号，如 A-14-11',
  `area_id` bigint NOT NULL COMMENT '所属库区',
  `location_type` tinyint NOT NULL DEFAULT '1' COMMENT '0存储区(储备) 1拣货区 2收货区 3发货区',
  `x_coord` int NOT NULL COMMENT 'X 坐标（米）',
  `y_coord` int NOT NULL COMMENT 'Y 坐标（米）',
  `z_coord` int NOT NULL COMMENT '层（1~4）',
  `capacity` int NOT NULL DEFAULT '18' COMMENT '容量（商品位数，真实为 18）',
  `used_slots` int NOT NULL DEFAULT '0' COMMENT '已用商品位数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0空闲 1占用 2锁定',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_location_code` (`location_code`),
  KEY `idx_area` (`area_id`),
  KEY `idx_type` (`location_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库位（2,314 个，含坐标，区分存储区/拣货区）';

-- 5. customer（客户（588 个））
DROP TABLE IF EXISTS customer;
CREATE TABLE `customer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cust_code` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户编号',
  `cust_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cust_code` (`cust_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户（588 个）';



-- =====================================================================
--  二、系统与权限（4 张表）
--  员工档案、登录账号、角色、角色权限（表驱动 RBAC）
-- =====================================================================

-- 6. staff（员工档案（含现场作业人员与管理岗））
DROP TABLE IF EXISTS staff;
CREATE TABLE `staff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `staff_code` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '工号（同时是登录名）',
  `staff_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '姓名（顶栏显示用）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0离职 1在职',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_op_code` (`staff_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工档案（含现场作业人员与管理岗）';

-- 7. sys_user（系统账号（登录名=工号，显示名=姓名，均取自 staff 表））
DROP TABLE IF EXISTS sys_user;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `password` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码哈希（BCrypt）',
  `role` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'ADMIN/RECEIVER/PICKER/SUPERVISOR',
  `staff_id` bigint NOT NULL COMMENT '关联员工（staff.id），一个账号必须对应一个员工',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1启用',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff` (`staff_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统账号（登录名=工号，显示名=姓名，均取自 staff 表）';

-- 8. sys_role（角色（数据驱动，加角色不用改代码））
DROP TABLE IF EXISTS sys_role;
CREATE TABLE `sys_role` (
  `code` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色码，如 PICKER（与 sys_user.role 对应）',
  `name` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '中文名，如「拣货员」',
  `builtin` tinyint NOT NULL DEFAULT '0' COMMENT '1=内置角色（不可删、不可改 code）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0禁用 1启用',
  `remark` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色（数据驱动，加角色不用改代码）';

-- 9. sys_role_permission（角色拥有的权限点（表驱动 RBAC 的核心））
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE `sys_role_permission` (
  `role_code` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色码',
  `permission` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '权限点，如 inbound:shelve',
  PRIMARY KEY (`role_code`,`permission`),
  KEY `idx_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色拥有的权限点（表驱动 RBAC 的核心）';



-- =====================================================================
--  三、入库（2 张表）
--  入库单主表 + 明细
-- =====================================================================

-- 10. inbound_order（入库单）
DROP TABLE IF EXISTS inbound_order;
CREATE TABLE `inbound_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '入库单号，如 RK20230105-001',
  `order_type` tinyint NOT NULL DEFAULT '1' COMMENT '1生产入库 2退货入库 3调拨入库',
  `source_no` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源工单号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待收货 1待上架 2已完成',
  `expected_date` date DEFAULT NULL COMMENT '预计到货日期',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_status` (`status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='入库单';

-- 11. inbound_order_line（入库明细）
DROP TABLE IF EXISTS inbound_order_line;
CREATE TABLE `inbound_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '入库单 ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `plan_qty` int NOT NULL COMMENT '计划数量',
  `received_qty` int NOT NULL DEFAULT '0' COMMENT '实收数量',
  `location_id` bigint DEFAULT NULL COMMENT '上架库位（上架后填）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待收货 1已收货 2已上架',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_sku` (`sku_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='入库明细';



-- =====================================================================
--  四、出库 ★ 核心（6 张表）
--  出库单 → 分配 → 波次 → 拣货任务 → 发货
-- =====================================================================

-- 12. outbound_order（出库单（客户订单））
DROP TABLE IF EXISTS outbound_order;
CREATE TABLE `outbound_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单号',
  `customer_id` bigint NOT NULL COMMENT '客户 ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待分配 1已分配 2拣货中 3已发货 4已取消',
  `order_time` datetime DEFAULT NULL COMMENT '下单时间（真实数据）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_customer` (`customer_id`),
  KEY `idx_status` (`status`),
  KEY `idx_order_time` (`order_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='出库单（客户订单）';

-- 13. outbound_order_line（出库明细）
DROP TABLE IF EXISTS outbound_order_line;
CREATE TABLE `outbound_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '出库单 ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `qty` int NOT NULL COMMENT '订购数量',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='出库明细';

-- 14. outbound_allocation（出库分配明细（订单视角））
DROP TABLE IF EXISTS outbound_allocation;
CREATE TABLE `outbound_allocation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_line_id` bigint NOT NULL COMMENT '出库明细 ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `location_id` bigint NOT NULL COMMENT '分配的库位',
  `qty_allocated` int NOT NULL COMMENT '分配数量',
  `wave_id` bigint DEFAULT NULL COMMENT '所属波次（生成波次后填）',
  `task_id` bigint DEFAULT NULL COMMENT '所属拣货任务（聚合后回填）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0已分配 1已拣货 2已释放',
  `allocated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `released_at` datetime DEFAULT NULL COMMENT '释放时间（超时释放）',
  PRIMARY KEY (`id`),
  KEY `idx_order_line` (`order_line_id`),
  KEY `idx_wave` (`wave_id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_sku_loc` (`sku_id`,`location_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='出库分配明细（订单视角）';

-- 15. picking_wave（拣货波次）
DROP TABLE IF EXISTS picking_wave;
CREATE TABLE `picking_wave` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wave_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '波次号',
  `staff_id` bigint DEFAULT NULL COMMENT '拣货员（staff.id）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待拣货 1拣货中 2已完成',
  `capacity` int NOT NULL DEFAULT '27' COMMENT '载具容量（27 件）',
  `total_qty` int NOT NULL DEFAULT '0' COMMENT '本波次总件数',
  `total_tasks` int NOT NULL DEFAULT '0' COMMENT '本波次任务数',
  `path_distance` int DEFAULT NULL COMMENT '路径总距离（优化后填）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wave_no` (`wave_no`),
  KEY `idx_staff` (`staff_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='拣货波次';

-- 16. picking_task（拣货任务（库位视角））
DROP TABLE IF EXISTS picking_task;
CREATE TABLE `picking_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wave_id` bigint NOT NULL COMMENT '所属波次',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `location_id` bigint NOT NULL COMMENT '从哪个库位取',
  `qty_plan` int NOT NULL COMMENT '计划取货数量',
  `qty_picked` int NOT NULL DEFAULT '0' COMMENT '实际取货数量',
  `seq_no` int DEFAULT NULL COMMENT '拣货顺序（路径优化后填）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待拣 1已拣 2缺货',
  `picked_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_wave` (`wave_id`),
  KEY `idx_location` (`location_id`),
  KEY `idx_sku` (`sku_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='拣货任务（库位视角）';

-- 17. shipment（发货单）
DROP TABLE IF EXISTS shipment;
CREATE TABLE `shipment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shipment_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '发货单号',
  `wave_id` bigint DEFAULT NULL COMMENT '关联波次',
  `total_qty` int NOT NULL DEFAULT '0' COMMENT '发货总件数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待发货 1已发货',
  `shipped_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_no` (`shipment_no`),
  KEY `idx_wave` (`wave_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='发货单';



-- =====================================================================
--  五、库存 ★ 核心（4 张表）
--  五字段库存 + 流水 + 盘点
-- =====================================================================

-- 18. inventory（即时库存（五字段模型））
DROP TABLE IF EXISTS inventory;
CREATE TABLE `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `location_id` bigint NOT NULL COMMENT '库位 ID',
  `qty` int NOT NULL DEFAULT '0' COMMENT '现有总量（拣货时不变）',
  `qty_allocated` int NOT NULL DEFAULT '0' COMMENT '已分配给订单',
  `qty_picked` int NOT NULL DEFAULT '0' COMMENT '已拣出未发货',
  `qty_onhold` int NOT NULL DEFAULT '0' COMMENT '冻结量（质检不合格等）',
  `qty_available` int NOT NULL DEFAULT '0' COMMENT '可用量 = qty - allocated - onhold',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_location` (`sku_id`,`location_id`),
  KEY `idx_sku` (`sku_id`),
  KEY `idx_location` (`location_id`),
  KEY `idx_available` (`sku_id`,`qty_available`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='即时库存（五字段模型）';

-- 19. inventory_transaction（库存流水（可追溯、可对账））
DROP TABLE IF EXISTS inventory_transaction;
CREATE TABLE `inventory_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `location_id` bigint NOT NULL COMMENT '库位 ID',
  `qty_delta` int NOT NULL COMMENT '变动量（正负）',
  `biz_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'RECEIPT/PICK/SHIP/ADJUST/FREEZE/RELEASE/ALLOCATE',
  `reference_type` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源单据类型',
  `reference_id` bigint DEFAULT NULL COMMENT '来源单据 ID（溯源关键）',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sku_loc` (`sku_id`,`location_id`),
  KEY `idx_ref` (`reference_type`,`reference_id`),
  KEY `idx_biz_type` (`biz_type`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存流水（可追溯、可对账）';

-- 20. stocktake_order（盘点单）
DROP TABLE IF EXISTS stocktake_order;
CREATE TABLE `stocktake_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stocktake_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '盘点单号',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0盘点中 1已完成',
  `total_lines` int NOT NULL DEFAULT '0' COMMENT '盘点明细数',
  `diff_lines` int NOT NULL DEFAULT '0' COMMENT '有差异的行数',
  `created_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stocktake_no` (`stocktake_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='盘点单';

-- 21. stocktake_line（盘点明细）
DROP TABLE IF EXISTS stocktake_line;
CREATE TABLE `stocktake_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '盘点单 ID',
  `sku_id` bigint NOT NULL,
  `location_id` bigint NOT NULL,
  `book_qty` int NOT NULL COMMENT '账面数量',
  `actual_qty` int DEFAULT NULL COMMENT '实盘数量',
  `diff_qty` int DEFAULT NULL COMMENT '差异（正=盘盈，负=盘亏）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待盘 1已盘',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_sku_loc` (`sku_id`,`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='盘点明细';



-- =====================================================================
--  六、其他（1 张表）
--  预警（当前为空壳表，见技术文档的诚实说明）
-- =====================================================================

-- 22. alert（告警（含 LLM 诊断建议））
DROP TABLE IF EXISTS alert;
CREATE TABLE `alert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alert_type` varchar(24) COLLATE utf8mb4_general_ci NOT NULL COMMENT '异常类型：PICK_TIMEOUT/SLOW_STAFF',
  `alert_level` tinyint NOT NULL DEFAULT '1' COMMENT '1提示 2警告 3严重',
  `title` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `detail` text COLLATE utf8mb4_general_ci COMMENT '结构化详情（JSON）',
  `llm_suggestion` text COLLATE utf8mb4_general_ci COMMENT 'LLM 生成的诊断建议',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0未处理 1已处理',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `handled_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`alert_type`),
  KEY `idx_level` (`alert_level`),
  KEY `idx_status` (`status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='告警（含 LLM 诊断建议）';



-- =====================================================================
--  建表完成：22 张表
-- =====================================================================

SELECT table_name AS 表名, table_comment AS 说明
FROM information_schema.TABLES WHERE table_schema = 'ai_wms' ORDER BY table_name;
