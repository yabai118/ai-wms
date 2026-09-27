import { getPermissions } from '@/utils/authStorage'

/**
 * 前端权限判断
 *
 * <p><b>★ 这里是「表驱动」在前端的落点</b>：判断依据是**后端下发的权限点**，
 * 不是写死的角色名。所以后端新建一个角色、勾几个权限，
 * 这个角色的人登录进来该看到什么、能点什么，**前端一行都不用改**。
 *
 * <p>用法：
 * <pre>
 *   &lt;el-button v-if="hasPermission('inbound:shelve')"&gt;上架&lt;/el-button&gt;
 * </pre>
 *
 * <p><b>⚠️ 这只是体验层，不是安全边界。</b>
 * 用户改 localStorage 就能让按钮显示出来——但点了照样被后端拦下（403）。
 * 真正的权限控制在后端 {@code @RequirePermission}，两边都要有：
 * 前端管"别让用户看到点了会失败的东西"，后端管"真的不让你干"。
 */
export function hasPermission(permission) {
  if (!permission) {
    return true
  }
  return getPermissions().includes(permission)
}

/** 任意一个满足即可（对应后端「满足其一」的语义） */
export function hasAnyPermission(...permissions) {
  return permissions.some(p => hasPermission(p))
}
