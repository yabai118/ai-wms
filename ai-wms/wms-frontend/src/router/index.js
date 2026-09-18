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

export default router
