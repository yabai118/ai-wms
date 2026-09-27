import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/index.vue'
import { getToken } from '@/utils/authStorage'

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
        meta: { title: '数据导入', icon: 'UploadFilled' }
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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫：没登录的一律赶去登录页
 *
 * <p>注意这只是**体验层**的拦截——真正的权限控制在后端。
 * 前端守卫能被绕过（改 localStorage 或直接调接口），
 * 所以两边都要有：这里管"别让用户看到进不去的页面"，
 * 后端 AuthInterceptor 管"真的不让你干"。
 */
router.beforeEach((to) => {
  const token = getToken()

  if (!token && !to.meta?.public) {
    // 记住原本要去哪，登录后可以跳回去
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }

  // 已经登录了还去登录页 → 送回首页
  if (token && to.path === '/login') {
    return { path: '/dashboard' }
  }

  return true
})

export default router
