package com.aiwms.service;

import com.aiwms.common.Permissions;
import com.aiwms.common.Roles;
import com.aiwms.entity.SysRolePermission;
import com.aiwms.mapper.SysRolePermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限判断（表驱动 RBAC 的执行者）
 *
 * <p>每收到一个带 {@code @RequirePermission} 的请求，拦截器都会问这里
 * 「这个用户所属的角色，有没有这个权限点」。
 *
 * <h3>为什么加缓存</h3>
 *
 * <p>权限判断在**每个请求**上都要做。虽然 {@code sys_role_permission} 很小，
 * 但每次都查库不值当。所以照 {@code StockCacheService} 的 Cache-Aside 写法：
 * 先查缓存 → 未命中查库 → 回填。
 *
 * <h3>为什么用 Redis 而不是本地 Map</h3>
 *
 * <p>角色权限改完要<b>立刻生效</b>。本地 Map 在多实例部署下得靠广播通知其他节点，
 * Redis 天然共享。而且本项目已经在用 Redis（库存缓存），不引入新技术。
 *
 * <h3>★ Redis 挂了会怎样</h3>
 *
 * <p><b>降级为直接查库</b>，而不是放行也不是拒绝 —— 读缓存失败就回源，
 * 权限判断的<b>结果始终正确</b>，只是慢一点。这与令牌撤销的取舍不同
 * （那边无法降级，只能放行，见 {@code TokenRevocationService}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SysRolePermissionMapper rolePermissionMapper;

    private static final String KEY_PREFIX = "wms:auth:perm:";
    private static final Duration TTL = Duration.ofMinutes(10);

    /**
     * 角色是否拥有该权限点
     *
     * <p><b>ADMIN 硬编码放行</b>——这是刻意保留的后门：管理员如果把某个角色的权限
     * 配错了，至少超管还能进去改回来。没有这个后门，一次误操作就可能把所有人锁在门外。
     */
    public boolean has(String roleCode, String permission) {
        if (!StringUtils.hasText(roleCode) || !StringUtils.hasText(permission)) {
            return false;
        }
        if (Roles.ADMIN.equals(roleCode)) {
            return true;
        }
        return permissionsOf(roleCode).contains(permission);
    }

    /**
     * 角色的全部权限点
     *
     * <p>ADMIN 在这里**展开成全部权限点**返回（而不是返回空集）——
     * 这样前端拿到的永远是"我实际能做什么"，菜单和按钮不用为超管写特例。
     */
    public Set<String> permissionsOf(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return new LinkedHashSet<>();
        }
        if (Roles.ADMIN.equals(roleCode)) {
            return new LinkedHashSet<>(Permissions.all());
        }

        String key = KEY_PREFIX + roleCode;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof String s) {
                // 存成逗号分隔的字符串而不是 Set：Jackson 的默认类型信息在集合上容易出意外，
                // 字符串最稳，而且在 redis-cli 里直接看得懂
                return s.isEmpty()
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(Arrays.asList(s.split(",")));
            }
        } catch (Exception e) {
            log.warn("读权限缓存失败，回源查库: role={} err={}", roleCode, e.getMessage());
        }

        List<String> perms = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<SysRolePermission>()
                                .eq(SysRolePermission::getRoleCode, roleCode))
                .stream().map(SysRolePermission::getPermission).toList();

        try {
            redisTemplate.opsForValue().set(key, String.join(",", perms), TTL);
        } catch (Exception e) {
            log.warn("写权限缓存失败: role={} err={}", roleCode, e.getMessage());
        }
        return new LinkedHashSet<>(perms);
    }

    /**
     * 让某角色的权限缓存失效
     *
     * <p><b>角色管理接口改完权限必须调这个</b>，否则最多 10 分钟内不生效。
     */
    public void evict(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + roleCode);
            log.debug("权限缓存已失效: {}", roleCode);
        } catch (Exception e) {
            log.warn("清权限缓存失败: role={} err={}", roleCode, e.getMessage());
        }
    }
}
