-- =====================================================================
--  迁移 02 · 表驱动 RBAC（角色变成数据）
--
--  背景：上一版把登录鉴权做起来了，但**角色是硬编码常量**（Roles.java），
--        想加一个「质检员」角色就得改 Java 代码 + 重新部署。
--
--  ★ 核心判断：
--      权限的「定义」天然属于代码 —— 权限点对应具体接口，接口就在代码里。
--                                    （见 common/Permissions.java）
--      权限的「分配」才是数据     —— 哪个角色有哪些权限，这才是要能配的。
--                                    （就是本脚本建的这两张表）
--
--  所以本迁移：
--    · 角色 → 数据（sys_role），加角色 = 建一条数据，不改代码不重启
--    · 分配 → 数据（sys_role_permission），管理员在界面上勾选
--    · 权限点仍留在代码里（Permissions 常量），注解引用它，拼错编译不过
--
--  ⚠️ 不建 sys_permission 表：那会产生**第二份真相**，
--     和代码里的常量迟早不一致。前端要的权限清单由接口从常量读出来返回。
--
--  ⚠️ 不建 sys_user_role 多对多：一人多岗是真实需求，但那是独立增量，
--     现在做要连 JWT 载荷、注解、前端过滤一起改。单角色够用。
--
--  ★ sys_user 表**一列都不动** —— 它的 role 值（ADMIN/RECEIVER/…）
--    本来就是角色码，现在只是多了一张表给这些码起名字、挂权限。
--    **零数据迁移。**
--
--  用法：
--    mysql -u root -p"$DB_PASSWORD" --default-character-set=utf8mb4 < sql/migration/02_add_rbac.sql
-- =====================================================================

USE ai_wms;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
--  一、角色表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  code       VARCHAR(16)  NOT NULL                  COMMENT '角色码，如 PICKER（与 sys_user.role 对应）',
  name       VARCHAR(32)  NOT NULL                  COMMENT '中文名，如「拣货员」',
  builtin    TINYINT      NOT NULL DEFAULT 0        COMMENT '1=内置角色（不可删、不可改 code）',
  status     TINYINT      NOT NULL DEFAULT 1        COMMENT '0禁用 1启用',
  remark     VARCHAR(128)          DEFAULT NULL     COMMENT '备注',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (code)
) ENGINE=InnoDB COMMENT='角色（数据驱动，加角色不用改代码）';

INSERT INTO sys_role (code, name, builtin, remark) VALUES
  ('ADMIN',      '系统管理员', 1, '内置超级管理员：拦截器直接放行，防止权限配错把自己锁死'),
  ('RECEIVER',   '收货员',     1, '入库、收货、上架'),
  ('PICKER',     '拣货员',     1, '分配库存、波次、拣货、发货'),
  ('SUPERVISOR', '仓库主管',   1, '只读：看板、报表、库存查询');


-- ---------------------------------------------------------------------
--  二、角色-权限（分配关系 = 数据）
--
--  权限点命名规范：<域>:<动作>  例如 inbound:shelve
--  完整清单见 common/Permissions.java —— 这里只存"分配"，不存"定义"
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
  role_code  VARCHAR(16) NOT NULL                   COMMENT '角色码',
  permission VARCHAR(64) NOT NULL                   COMMENT '权限点，如 inbound:shelve',
  PRIMARY KEY (role_code, permission),
  KEY idx_permission (permission)
) ENGINE=InnoDB COMMENT='角色拥有的权限点（表驱动 RBAC 的核心）';


-- ---------------------------------------------------------------------
--  三、初始分配 —— **完全等价于改造前的行为**
--
--  改造前是 15 处 @RequireRole 硬编码在注解里，这里是把它们翻译成数据。
--  对照关系：
--    InboundController   @RequireRole(RECEIVER) ×3  → inbound:create/receive/shelve
--    OutboundController  @RequireRole(ADMIN)        → outbound:create
--                        @RequireRole(PICKER)       → outbound:allocate
--                        @RequireRole(ADMIN)        → outbound:allocate-naive
--    WaveController      @RequireRole(PICKER) ×4    → wave:generate/pick/ship/sequence
--    InventoryController @RequireRole(ADMIN) ×2     → inventory:freeze/unfreeze
--    ImportController    @RequireRole(ADMIN) ×3     → import:data
-- ---------------------------------------------------------------------

-- 收货员：入库三件套
INSERT INTO sys_role_permission (role_code, permission) VALUES
  ('RECEIVER', 'inbound:create'),
  ('RECEIVER', 'inbound:receive'),
  ('RECEIVER', 'inbound:shelve');

-- 拣货员：分配 + 波次全流程
INSERT INTO sys_role_permission (role_code, permission) VALUES
  ('PICKER', 'outbound:allocate'),
  ('PICKER', 'wave:generate'),
  ('PICKER', 'wave:pick'),
  ('PICKER', 'wave:ship'),
  ('PICKER', 'wave:sequence');

-- 仓库主管：无 —— 只读角色，一条都不给

-- ADMIN 不插入任何行：拦截器对 ADMIN **硬编码放行**（见 AuthInterceptor），
-- 而 getPermissions('ADMIN') 会在服务端展开成全部权限点，供前端渲染菜单。
-- 这样既有防锁死的后门，前端又不用为超管写特例。


-- ---------------------------------------------------------------------
--  四、核对
-- ---------------------------------------------------------------------
SELECT r.code AS 角色, r.name AS 名称, r.builtin AS 内置,
       COUNT(rp.permission) AS 权限数,
       IFNULL(GROUP_CONCAT(rp.permission ORDER BY rp.permission SEPARATOR ', '), '（只读）') AS 权限
FROM sys_role r
LEFT JOIN sys_role_permission rp ON rp.role_code = r.code
GROUP BY r.code, r.name, r.builtin
ORDER BY r.builtin DESC, r.code;

SELECT '仍在使用的角色码（应为 0 个不在 sys_role 里）' AS 检查项, COUNT(*) AS 数量
FROM (SELECT DISTINCT role FROM sys_user) u
LEFT JOIN sys_role r ON r.code = u.role
WHERE r.code IS NULL;
