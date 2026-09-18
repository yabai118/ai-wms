import { createRouter, createWebHistory } from 'vue-router'
import Layout from '@/layout/index.vue'

/**
 * 路由配置
 *
 * 采用「布局 + 子路由」结构：
 * Layout 包含侧边栏和顶栏，子页面渲染在右侧内容区
 */
const routes = [
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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
