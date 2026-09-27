-- =====================================================================
--  开发环境 · 重置业务流水
--
--  用途：把数据库恢复成「真实主数据 + 空流水」的状态，用于功能开发与测试。
--
--  为什么需要它：
--    数据集有 32,621 个订单、导入 SQL 6.7MB，每改一次表结构就要重导一次，
--    迭代太慢。开发阶段改用「真实主数据 + 自造单据」，重建只要几秒。
--
--  用法：
--    mysql -u root -p"$DB_PASSWORD" --default-character-set=utf8mb4 < sql/dev/reset_dev_data.sql
--
--  ⚠️ 这会删除全部业务流水（单据、波次、任务、库存），执行前确认。
--  ⚠️ 绝不要用 sql/schema.sql 代替本脚本 —— 那个脚本开头是
--     DROP DATABASE，会把整个库连同主数据一起删掉。
--
--  保留（真实主数据，来自数据集，不要动）：
--    warehouse_area / location / product / product_sku / customer / operator
--
--  清空（业务流水）：
--    入库 / 出库 / 波次 / 拣货 / 发货 / 库存 / 盘点 / 预警
--
--  清完之后库存是空的 —— 这是预期的。库存要通过真实流程产生：
--    新建入库单 → 收货 → 上架 → inventory 才有记录
-- =====================================================================

USE ai_wms;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
--  一、入库
-- ---------------------------------------------------------------------
DELETE FROM inbound_order_line;
DELETE FROM inbound_order;

-- ---------------------------------------------------------------------
--  二、出库（先子后父）
-- ---------------------------------------------------------------------
DELETE FROM outbound_allocation;
DELETE FROM picking_task;
DELETE FROM picking_wave;
DELETE FROM shipment;
DELETE FROM outbound_order_line;
DELETE FROM outbound_order;

-- ---------------------------------------------------------------------
--  三、库存
-- ---------------------------------------------------------------------
DELETE FROM inventory_transaction;
DELETE FROM inventory;

-- ---------------------------------------------------------------------
--  四、盘点与预警
-- ---------------------------------------------------------------------
DELETE FROM stocktake_line;
DELETE FROM stocktake_order;
DELETE FROM alert;

-- ---------------------------------------------------------------------
--  五、自增主键归零（让手工创建的单号、id 从 1 开始，便于对照）
--     MySQL 不支持把 AUTO_INCREMENT 重置到低于当前最大值的数，
--     表已清空，所以这里能生效。
-- ---------------------------------------------------------------------
ALTER TABLE inbound_order         AUTO_INCREMENT = 1;
ALTER TABLE inbound_order_line    AUTO_INCREMENT = 1;
ALTER TABLE outbound_order        AUTO_INCREMENT = 1;
ALTER TABLE outbound_order_line   AUTO_INCREMENT = 1;
ALTER TABLE outbound_allocation   AUTO_INCREMENT = 1;
ALTER TABLE picking_wave          AUTO_INCREMENT = 1;
ALTER TABLE picking_task          AUTO_INCREMENT = 1;
ALTER TABLE shipment              AUTO_INCREMENT = 1;
ALTER TABLE inventory             AUTO_INCREMENT = 1;
ALTER TABLE inventory_transaction AUTO_INCREMENT = 1;

-- ---------------------------------------------------------------------
--  六、核对结果
--     主数据应当保留（数量为数据集原始值），流水应当全为 0
-- ---------------------------------------------------------------------
SELECT '保留' AS 类别, 'location'    AS 表名, COUNT(*) AS 行数 FROM location
UNION ALL SELECT '保留', 'product',       COUNT(*) FROM product
UNION ALL SELECT '保留', 'product_sku',   COUNT(*) FROM product_sku
UNION ALL SELECT '保留', 'warehouse_area',COUNT(*) FROM warehouse_area
UNION ALL SELECT '保留', 'customer',      COUNT(*) FROM customer
UNION ALL SELECT '保留', 'operator',      COUNT(*) FROM operator
UNION ALL SELECT '清空', 'inbound_order',        COUNT(*) FROM inbound_order
UNION ALL SELECT '清空', 'outbound_order',       COUNT(*) FROM outbound_order
UNION ALL SELECT '清空', 'picking_wave',         COUNT(*) FROM picking_wave
UNION ALL SELECT '清空', 'picking_task',         COUNT(*) FROM picking_task
UNION ALL SELECT '清空', 'inventory',            COUNT(*) FROM inventory
UNION ALL SELECT '清空', 'inventory_transaction',COUNT(*) FROM inventory_transaction;
