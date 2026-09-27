-- =====================================================================
--  迁移 04 · 员工档案加「在职 / 离职」状态
--
--  【为什么要加】
--
--  账号模型改成「每个账号必须关联一个员工」之后，员工档案成了系统的入口：
--  新建账号时先从花名册里挑人。但花名册只增不减 —— 员工离职后还留在里面，
--  下拉框越滚越长，而且分不清哪些是还在岗的。
--
--  【离职怎么处理】
--
--  标记员工为离职时，**如果他有账号，账号一并停用**（并把已签发的令牌撤销）。
--  这两件事在业务上是一次操作，不该让人分两步做、还可能漏掉一步。
--
--  【为什么不删员工】
--
--  和账号一样：单据引用了他（`picking_wave.staff_id`、`inventory_transaction.created_by`），
--  删了审计链就断。用状态表达，历史记录永远查得到"这批货是谁拣的"。
--
--  用法：
--    mysql -u root -p"$DB_PASSWORD" --default-character-set=utf8mb4 < sql/migration/04_add_staff_status.sql
-- =====================================================================

USE ai_wms;
SET NAMES utf8mb4;

ALTER TABLE staff
  ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '0离职 1在职' AFTER staff_name;

ALTER TABLE staff COMMENT='员工档案（含现场作业人员与管理岗；工号=登录名，姓名=显示名）';

-- 核对
SELECT COLUMN_NAME AS 列名, COLUMN_TYPE AS 类型, COLUMN_DEFAULT AS 默认值, COLUMN_COMMENT AS 说明
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'ai_wms' AND TABLE_NAME = 'staff'
ORDER BY ORDINAL_POSITION;

SELECT CONCAT('员工 ', COUNT(*), ' 人，全部默认在职') AS 结果 FROM staff;
