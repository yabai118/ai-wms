-- =====================================================================
--  迁移 03 · 重构账号模型：operator → staff，账号与员工一对一
--
--  【为什么改】
--
--  迁移 01 建的账号模型有三个冗余字段，且关联关系太松：
--
--    username      登录名     —— 单独发一个登录名，凭空多了一层映射
--    display_name  显示名     —— 和 staff 里的姓名是同一份数据存两遍
--    operator_id   关联人员   —— 可空，导致 admin 这类账号查不出是谁在操作
--
--  【改成什么】
--
--    登录名 = 工号（staff.staff_code）      ← 本来就唯一、稳定、有意义
--    显示名 = 姓名（staff.staff_name）      ← 从档案取，不再单独存
--    staff_id NOT NULL + UNIQUE             ← 一个账号必须对应一个员工
--
--  三个字段塌缩成一个外键。而且账号与员工成为**一对一**。
--
--  【★ 为什么表名要改成 staff】
--
--  改完后"所有账号都要关联人员"（包括管理员）——那张表就得能装管理岗，
--  而它现在叫「拣货员（operator）」，名不副实。所以改名成「员工档案 staff」。
--  （GreaterWMS 那张表也叫 staff，这个名字起对了）
--
--  【★ 为什么强制关联（NOT NULL）】
--
--  管理员权限最大，最该追责。如果 admin 账号不挂人，
--  出了问题查不到"是谁在操作"——审计链断在最需要它的地方。
--  受监管行业（医药 GSP、食品）这条是硬要求。
--
--  【集成账号怎么办】
--
--  对接 ERP / 物流 / BI 的**集成账号**没有工号也没有姓名，天然不满足这条规则。
--  已确认的技术路线：**走 API Key 独立通道，根本不进 sys_user 表**
--  （生命周期、凭证形式、权限模型三者都与人对不上，硬塞进同一张表反而别扭）。
--  详见 项目设计方案.md 10.4 节。当前尚未实现。
--
--  【本脚本会清空所有账号】
--
--  因为 staff_id 改成 NOT NULL，存量账号（admin/receiver/supervisor）没有关联人员。
--  开发环境按「清库重建」处理：删掉账号，由 gen_dev_data.py 重新生成员工档案，
--  再用新接口建账号。迁移脚本不做数据搬运。
--
--  用法：
--    mysql -u root -p"$DB_PASSWORD" --default-character-set=utf8mb4 < sql/migration/03_rename_operator_to_staff.sql
-- =====================================================================

USE ai_wms;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
--  一、作业人员表 → 员工档案表
-- ---------------------------------------------------------------------
RENAME TABLE operator TO staff;

ALTER TABLE staff
  CHANGE COLUMN op_code staff_code VARCHAR(32) NOT NULL COMMENT '工号（同时是登录名）',
  CHANGE COLUMN op_name staff_name VARCHAR(64) DEFAULT NULL COMMENT '姓名（顶栏显示用）';

ALTER TABLE staff COMMENT='员工档案（含现场作业人员与管理岗）';


-- ---------------------------------------------------------------------
--  二、波次引用的 operator_id → staff_id
-- ---------------------------------------------------------------------
ALTER TABLE picking_wave
  CHANGE COLUMN operator_id staff_id BIGINT DEFAULT NULL COMMENT '拣货员（staff.id）';


-- ---------------------------------------------------------------------
--  三、sys_user：去掉冗余字段，staff_id 强制关联
-- ---------------------------------------------------------------------

-- ⚠️ 清空账号：staff_id 要改 NOT NULL，存量账号都没有关联人员。
--    开发环境按清库重建处理，不搬运数据。
DELETE FROM sys_user;

-- 唯一键建在 username 上，列要删了得先把索引去掉
ALTER TABLE sys_user DROP INDEX uk_username;
ALTER TABLE sys_user DROP INDEX idx_operator;

ALTER TABLE sys_user
  DROP COLUMN username,
  DROP COLUMN display_name;

ALTER TABLE sys_user
  CHANGE COLUMN operator_id staff_id BIGINT NOT NULL COMMENT '关联员工（staff.id），一个账号必须对应一个员工';

-- 一个员工只能有一个账号（一对一）
ALTER TABLE sys_user ADD UNIQUE KEY uk_staff (staff_id);

ALTER TABLE sys_user COMMENT='系统账号（登录名=工号，显示名=姓名，均取自 staff 表）';


-- ---------------------------------------------------------------------
--  四、核对
-- ---------------------------------------------------------------------
SELECT 'staff 表结构' AS x;
SELECT COLUMN_NAME AS 列名, COLUMN_TYPE AS 类型, IS_NULLABLE AS 可空, COLUMN_COMMENT AS 说明
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'ai_wms' AND TABLE_NAME = 'staff'
ORDER BY ORDINAL_POSITION;

SELECT 'sys_user 表结构' AS x;
SELECT COLUMN_NAME AS 列名, COLUMN_TYPE AS 类型, IS_NULLABLE AS 可空, COLUMN_COMMENT AS 说明
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'ai_wms' AND TABLE_NAME = 'sys_user'
ORDER BY ORDINAL_POSITION;

SELECT '残留的 operator 命名（应为 0）' AS 检查项, COUNT(*) AS 数量
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'ai_wms'
  AND (COLUMN_NAME LIKE '%operator%' OR TABLE_NAME = 'operator');
