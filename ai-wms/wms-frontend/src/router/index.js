import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/layout/index.vue'
import { getToken, setLogin, clearLogin } from '@/utils/authStorage'
import { hasPermission } from '@/utils/permission'
import { authApi } from '@/api/auth'

/**
 * 路由配置
 *
 * 采用「布局 + 子路由」结构：
 * Layout 包含侧边栏和顶栏，子页面渲染在右侧内容区
 */
const routes = [
  // 登录页是**顶层路由**，不能挂在 Layout 下——它没有侧边栏和顶栏
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '首页看板', icon: 'DataBoard' }
      },
      {
        path: 'data-import',
        name: 'DataImport',
        component: () => import('@/views/DataImport.vue'),
        // meta.perm = 进这个页面需要的权限点（后端下发，前端不写死角色）
        meta: { title: '数据导入', icon: 'UploadFilled', perm: 'import:data' }
      },
      {
        path: 'product',
        name: 'Product',
        component: () => import('@/views/ProductList.vue'),
        meta: { title: '商品管理', icon: 'Goods' }
      },
      {
        path: 'location',
        name: 'Location',
        component: () => import('@/views/LocationList.vue'),
        meta: { title: '库位管理', icon: 'Grid' }
      },
      {
        path: 'location-map',
        name: 'LocationMap',
        component: () => import('@/views/LocationMap.vue'),
        meta: { title: '库位地图', icon: 'MapLocation' }
      },
      {
        path: 'inbound',
        name: 'Inbound',
        component: () => import('@/views/InboundList.vue'),
        meta: { title: '入库管理', icon: 'Download' }
      },
      {
        path: 'outbound',
        name: 'Outbound',
        component: () => import('@/views/OutboundList.vue'),
        meta: { title: '出库管理', icon: 'Upload' }
      },
      {
        path: 'wave',
        name: 'Wave',
        component: () => import('@/views/WaveList.vue'),
        meta: { title: '波次拣货', icon: 'Van' }
      },
      {
        path: 'inventory',
        name: 'Inventory',
        component: () => import('@/views/InventoryList.vue'),
        meta: { title: '库存管理', icon: 'Coin' }
      },
      {
        path: 'routing',
        name: 'Routing',
        component: () => import('@/views/RoutingView.vue'),
        meta: { title: '路径优化', icon: 'Guide' }
      },
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/AgentView.vue'),
        meta: { title: '智能助手', icon: 'MagicStick' }
      },
      // ---------- 系统管理（需要相应权限点才可见/可进）----------
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/UserList.vue'),
        meta: { title: '账号管理', icon: 'UserFilled', perm: 'user:manage' }
      },
      {
        path: 'role',
        name: 'Role',
        component: () => import('@/views/RoleList.vue'),
        meta: { title: '角色管理', icon: 'Key', perm: 'role:manage' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫：没登录的赶去登录页；登录了但没权限的赶回首页
 *
 * <p>注意这只是**体验层**的拦截——真正的权限控制在后端。
 * 前端守卫能被绕过（改 localStorage 或直接调接口），
 * 所以两边都要有：这里管"别让用户看到进不去的页面"，
 * 后端 AuthInterceptor 管"真的不让你干"。
 *
 * <p>权限判断依据是 `meta.perm`（一个权限点字符串）+ 后端下发的权限集合，
 * <b>不是写死的角色名</b>——所以后端加新角色时这里不用改。
 */
/**
 * 本次会话是否已校验过 token
 *
 * 只校验一次（首次导航时），不是每次跳转都打接口 —— 那样太浪费。
 */
let tokenValidated = false

router.beforeEach(async (to) => {
  const token = getToken()

  if (!token && !to.meta?.public) {
    // 记住原本要去哪，登录后可以跳回去
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }

  // ★ 首次导航时校验 token，并**顺带刷新用户信息与权限**
  //
  // 为什么必须做：localStorage 里的 token 可能是很久以前签发的——
  // 那时用户的角色、权限、甚至姓名都可能和现在不一样了。
  // 不校验的话，会出现「拿着一个过期的权限集合在操作」的诡异状态。
  // （曾经就踩过：改造账号模型后旧 token 里的信息已经对不上，页面直接报错）
  if (token && !tokenValidated) {
    tokenValidated = true
    try {
      const me = await authApi.me()
      // me 里没有 token（后端不会重复下发），所以合并一下再存
      setLogin({ ...me, token })
    } catch {
      // /auth/me 失败说明 token 已失效（或用户已被停用/删除），
      // 清掉凭证回登录页。request.js 里也会兜底处理，这里是更快的一层。
      clearLogin()
      return { path: '/login' }
    }
  }

  // 已经登录了还去登录页 → 送回首页
  if (token && to.path === '/login') {
    return { path: '/dashboard' }
  }

  // 已登录但该页面需要权限点、而自己没有 → 拦回首页
  // （菜单里本来就不会显示它，这是防"手动敲 URL"）
  if (token && to.meta?.perm && !hasPermission(to.meta.perm)) {
    ElMessage.warning('没有权限访问该页面')
    return { path: '/dashboard' }
  }

  return true
})

export default router
