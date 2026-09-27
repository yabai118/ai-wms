-- =====================================================================
--  迁移 01 · 新增登录账号表 sys_user
--
--  背景：系统此前**零鉴权**——没有登录页、没有登录接口，库存流水里的
--        created_by 写的是 4 个硬编码字符串（"admin"/"system"/"picker"/"import"），
--        跟真实操作人毫无关系。
--
--  ★ 设计要点：账号与人员分离
--    operator   = 作业人员档案（谁在仓库里干活）—— 数据集/HR 的范畴
--    sys_user   = 系统账号（谁登录了系统）      —— 系统策略的范畴
--    两者靠 sys_user.operator_id 关联，可为空。
--
--    为什么分开：这两张表**变化的原因不同**。人员变动改 operator，
--    加管理员/改密码/停用账号改 sys_user。变化原因不同就不该放一张表。
--    （GreaterWMS 也是这么分的：staff 与 userlogin/userprofile 是独立的 app）
--
--    operator_id 为 NULL 的含义不是"这个角色不用挂"，而是
--    「这个账号不代表某个具体的现场作业人员」——管理员、主管是管理岗，
--    本来就不在拣货现场。硬给他们挂一个，会让"这批货是谁拣的"变成假数据。
--
--  用法：
--    mysql -u root -p"$DB_PASSWORD" --default-character-set=utf8mb4 < sql/migration/01_add_auth.sql
--
--  初始密码见下方 @pwd 变量（演示用；生产必须首登强制改密）
-- =====================================================================

USE ai_wms;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  username      VARCHAR(32) NOT NULL                  COMMENT '登录名',
  password      VARCHAR(64) NOT NULL                  COMMENT '密码哈希（BCrypt）',
  display_name  VARCHAR(64)          DEFAULT NULL     COMMENT '显示名（顶栏展示）',
  role          VARCHAR(16) NOT NULL                  COMMENT 'ADMIN/RECEIVER/PICKER/SUPERVISOR',
  operator_id   BIGINT               DEFAULT NULL     COMMENT '关联作业人员（管理岗为 NULL）',
  status        TINYINT     NOT NULL DEFAULT 1        COMMENT '0禁用 1启用',
  last_login_at DATETIME             DEFAULT NULL     COMMENT '最后登录时间',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_operator (operator_id)
) ENGINE=InnoDB COMMENT='系统账号（登录用，与 operator 人员档案分离）';


-- ---------------------------------------------------------------------
--  一、四个角色账号
--     每个角色对应系统里已实现的那部分模块。
-- ---------------------------------------------------------------------
-- BCrypt 哈希（对应演示密码，见 ai-wms/.env.local 的 WMS_DEMO_PASSWORD）
SET @pwd = '$2b$10$x/FFUt5O.diJONTILwBozOxw4surUDo5mFBQmzzcc.QdmoWLMAmZi';

INSERT INTO sys_user (username, password, display_name, role, operator_id) VALUES
  ('admin',      @pwd, '系统管理员', 'ADMIN',      NULL),  -- 全部权限
  ('receiver',   @pwd, '收货员小林', 'RECEIVER',   NULL),  -- 入库 + 上架
  ('picker',     @pwd, '张伟',       'PICKER',        1),  -- 波次 + 拣货（挂 OP001）
  ('supervisor', @pwd, '仓库主管',   'SUPERVISOR', NULL);  -- 只读：看板、报表、库存


-- ---------------------------------------------------------------------
--  二、十个作业人员账号（工号即登录名）
--     让每个现场人员都能登录，演示时可演「张伟登录进来只看到自己的波次」。
--     operator_id 直接取自 operator 表的 id，保证一对一挂上。
-- ---------------------------------------------------------------------
INSERT INTO sys_user (username, password, display_name, role, operator_id)
SELECT o.op_code, @pwd, o.op_name, 'PICKER', o.id
FROM operator o
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.username = o.op_code);


-- ---------------------------------------------------------------------
--  三、核对
-- ---------------------------------------------------------------------
SELECT role AS 角色, COUNT(*) AS 账号数,
       GROUP_CONCAT(username ORDER BY id SEPARATOR ', ') AS 账号
FROM sys_user GROUP BY role ORDER BY role;

SELECT '待挂人员的账号（应为 0）' AS 检查项, COUNT(*) AS 数量
FROM sys_user WHERE role = 'PICKER' AND operator_id IS NULL;
