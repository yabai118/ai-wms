/**
 * 登录态的本地存储
 *
 * <p>**刻意做成不依赖任何模块的纯工具**——它同时被 router（路由守卫）、
 * request（请求拦截器）和 api/auth 引用。如果把这些函数放在 api/auth.js 里，
 * 就会形成 router → api/auth → utils/request → router 的循环依赖。
 */
const TOKEN_KEY = 'wms_token'
const USER_KEY = 'wms_user'

/** 取 token——请求拦截器每次请求都要读 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

/** 登录成功后写入 */
export function setLogin(loginVO) {
  localStorage.setItem(TOKEN_KEY, loginVO.token)
  localStorage.setItem(USER_KEY, JSON.stringify({
    username: loginVO.username,
    displayName: loginVO.displayName,
    role: loginVO.role,
    roleName: loginVO.roleName,
    operatorId: loginVO.operatorId
  }))
}

/** 取当前用户信息（顶栏展示用） */
export function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY)) || {}
  } catch {
    return {}
  }
}

/** 退出登录 / token 失效时清除 */
export function clearLogin() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
