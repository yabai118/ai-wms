<template>
  <el-container class="layout">
    <!-- ========== 左侧菜单 ========== -->
    <el-aside width="210px" class="aside">
      <div class="logo">
        <el-icon :size="22"><Box /></el-icon>
        <span>AI-WMS</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        class="menu"
      >
        <template v-for="item in menus" :key="item.path">
          <!-- 有子菜单 -->
          <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>

          <!-- 无子菜单 -->
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <!-- ========== 右侧主体 ========== -->
    <el-container>
      <el-header class="header">
        <div class="breadcrumb">
          <el-icon><Location /></el-icon>
          <span>{{ currentTitle }}</span>
        </div>
        <div class="user-info">
          <el-tag size="small" type="success" effect="plain">开发环境</el-tag>

          <el-dropdown @command="handleCommand">
            <span class="user-trigger">
              <el-avatar :size="28" style="background:#1f4e79">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ user.displayName || user.username }}</span>
              <el-tag size="small" type="info" effect="plain">{{ user.roleName }}</el-tag>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>登录名：{{ user.username }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { getUser, clearLogin } from '@/utils/authStorage'

const route = useRoute()
const router = useRouter()

/** 当前登录用户（登录后整页/路由跳转进来，此时 localStorage 已写好） */
const user = ref(getUser())

/** 头像里的首字：优先显示名，没有就取登录名 */
const avatarText = computed(() => {
  const name = user.value.displayName || user.value.username || '?'
  return name.charAt(0)
})

async function handleCommand(command) {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确认退出登录？', '退出', { type: 'warning' })
  } catch {
    return   // 用户点了取消
  }
  // 轻量版登出只清前端凭证；token 本身在过期前仍有效，
  // 真要做「服务端立即失效」得加黑名单（Redis 已就绪，后续可做）
  clearLogin()
  router.replace('/login')
}

/** 菜单配置（后续新页面往这里加） */
const menus = [
  { path: '/dashboard', title: '首页看板', icon: 'DataBoard' },
  { path: '/data-import', title: '数据导入', icon: 'UploadFilled' },
  { path: '/product', title: '商品管理', icon: 'Goods' },
  { path: '/location', title: '库位管理', icon: 'Grid' },
  { path: '/location-map', title: '库位地图', icon: 'MapLocation' },
  { path: '/inbound', title: '入库管理', icon: 'Download' },
  { path: '/outbound', title: '出库管理', icon: 'Upload' },
  { path: '/wave', title: '波次拣货', icon: 'Van' },
  { path: '/inventory', title: '库存管理', icon: 'Coin' },
  { path: '/routing', title: '路径优化', icon: 'Guide' },
  { path: '/agent', title: '智能助手', icon: 'MagicStick' }
]

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || 'AI-WMS')
</script>

<style scoped>
.layout {
  height: 100vh;
}

/* ---------- 侧边栏 ---------- */
.aside {
  background: #1f2d3d;
  overflow-y: auto;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
  background: #17212e;
}

.menu {
  border-right: none;
}

/* ---------- 顶栏 ---------- */
.header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.user-name {
  font-size: 14px;
  color: #303133;
}

.user-arrow {
  color: #909399;
  font-size: 12px;
}

/* ---------- 内容区 ---------- */
.main {
  background: #f0f2f5;
  padding: 16px;
  overflow-y: auto;
}
</style>
