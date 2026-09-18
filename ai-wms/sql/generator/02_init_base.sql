USE ai_wms;
SET NAMES utf8mb4;

-- 清空（可重复执行）
DELETE FROM product_sku;
DELETE FROM product;
DELETE FROM warehouse_area;
DELETE FROM location;
DELETE FROM customer;
DELETE FROM operator;

