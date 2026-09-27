package com.aiwms.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限点常量 —— <b>权限的「定义」</b>
 *
 * <p><b>★ 为什么权限点留在代码里，而角色放进数据库：</b>
 * <pre>
 *   权限点 = 某个具体接口能不能调   → 接口本来就在代码里，所以定义也在代码里
 *   角色   = 哪些权限打成一包发给谁 → 这是业务策略，应该能配，所以放进 sys_role 表
 * </pre>
 *
 * <p>所以本类就是权限点的<b>唯一真相</b>。数据库里的 {@code sys_role_permission}
 * 只存「哪个角色有哪些」，不存「有哪些权限点」—— 那样会有第二份真相，迟早不一致。
 *
 * <p>注解引用这里的常量（{@code @RequirePermission(Permissions.INBOUND_SHELVE)}），
 * <b>拼错编译不过</b>；如果写成裸字符串，拼错只会在运行时静默失效。
 *
 * <p>命名规范：{@code <域>:<动作>}，例如 {@code inbound:shelve}。
 */
public final class Permissions {

    private Permissions() {
    }

    // ---------- 入库 ----------
    public static final String INBOUND_CREATE = "inbound:create";
    public static final String INBOUND_RECEIVE = "inbound:receive";
    public static final String INBOUND_SHELVE = "inbound:shelve";

    // ---------- 出库 ----------
    public static final String OUTBOUND_CREATE = "outbound:create";
    public static final String OUTBOUND_ALLOCATE = "outbound:allocate";
    /** ⚠️ 仅供并发压测的负面对照实验使用，业务上不要分配 */
    public static final String OUTBOUND_ALLOCATE_NAIVE = "outbound:allocate-naive";

    // ---------- 波次与拣货 ----------
    public static final String WAVE_GENERATE = "wave:generate";
    public static final String WAVE_PICK = "wave:pick";
    public static final String WAVE_SHIP = "wave:ship";
    public static final String WAVE_SEQUENCE = "wave:sequence";

    // ---------- 库存 ----------
    public static final String INVENTORY_FREEZE = "inventory:freeze";
    public static final String INVENTORY_UNFREEZE = "inventory:unfreeze";

    // ---------- 系统管理 ----------
    public static final String IMPORT_DATA = "import:data";
    public static final String USER_MANAGE = "user:manage";
    public static final String ROLE_MANAGE = "role:manage";

    /**
     * 权限点清单：code → 中文说明。
     *
     * <p>用 {@link LinkedHashMap} 是<b>刻意</b>的——角色管理页的勾选框要按业务顺序排，
     * 不能每次刷新都变个样。{@code Map.of} 的顺序是不保证的。
     */
    private static final Map<String, String> CATALOG;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(INBOUND_CREATE, "创建入库单");
        m.put(INBOUND_RECEIVE, "收货（填实收数量）");
        m.put(INBOUND_SHELVE, "上架（增加库存）");
        m.put(OUTBOUND_CREATE, "创建出库单");
        m.put(OUTBOUND_ALLOCATE, "分配库存");
        m.put(OUTBOUND_ALLOCATE_NAIVE, "【压测对照】先查后扣（勿分配）");
        m.put(WAVE_GENERATE, "生成波次");
        m.put(WAVE_PICK, "拣货确认");
        m.put(WAVE_SHIP, "发货确认");
        m.put(WAVE_SEQUENCE, "应用路径优化顺序");
        m.put(INVENTORY_FREEZE, "冻结库存");
        m.put(INVENTORY_UNFREEZE, "解冻库存");
        m.put(IMPORT_DATA, "数据导入");
        m.put(USER_MANAGE, "账号管理");
        m.put(ROLE_MANAGE, "角色管理");
        CATALOG = Collections.unmodifiableMap(m);
    }

    /** 全部权限点（有序） */
    public static List<String> all() {
        return List.copyOf(CATALOG.keySet());
    }

    /** 权限点清单（code → 中文说明），供角色管理页渲染勾选框 */
    public static Map<String, String> catalog() {
        return CATALOG;
    }

    public static boolean isValid(String permission) {
        return permission != null && CATALOG.containsKey(permission);
    }
}
