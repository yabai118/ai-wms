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
              <span class="user-name">{{ user.staffName || user.staffCode }}</span>
              <el-tag size="small" type="info" effect="plain">{{ user.roleName }}</el-tag>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>工号：{{ user.staffCode }}</el-dropdown-item>
                <el-dropdown-item command="change-password" divided>
                  <el-icon><Lock /></el-icon> 修改密码
                </el-dropdown-item>
                <el-dropdown-item command="logout">
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

    <!-- ============ 修改密码 ============ -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="440px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:14px">
        修改成功后需要用新密码重新登录
      </el-alert>
      <el-form :model="pwdForm" label-width="90px" size="small">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password
                    placeholder="当前使用的密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password
                    placeholder="6~32 位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password
                    placeholder="再输一次" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitChangePassword">
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUser, clearLogin } from '@/utils/authStorage'
import { hasPermission } from '@/utils/permission'
import { authApi } from '@/api/auth'

const route = useRoute()
const router = useRouter()

/** 当前登录用户（登录后整页/路由跳转进来，此时 localStorage 已写好） */
const user = ref(getUser())

/** 头像里的首字：优先姓名，没有就取工号 */
const avatarText = computed(() => {
  const name = user.value.staffName || user.value.staffCode || '?'
  return name.charAt(0)
})

/**
 * 侧边栏菜单 —— **从路由自动生成**
 *
 * 改造前这里是手写的一份数组，和 router 里的 meta 重复维护，加页面要改两处。
 * 现在只有路由一处真相：加页面 = 加一条路由，菜单自己就出来了。
 *
 * 角色过滤也在这里做：路由 meta.perm 就是「进这个页面需要的权限点」，
 * 用后端下发的权限集合一比即可 —— 所以后端加新角色，前端一行都不用改。
 */
const menus = computed(() => {
  // Layout 那个顶层路由的 children 就是菜单源（/login 是顶层且无 children，天然被排除）
  const root = router.options.routes.find(r => r.children?.length)
  return (root?.children || [])
    .filter(c => c.meta?.title)                                  // 没 title 的不进菜单
    .filter(c => !c.meta.perm || hasPermission(c.meta.perm))     // 按权限点过滤
    .map(c => ({
      // children 里是相对路径（'dashboard'），el-menu 需要绝对路径
      path: '/' + c.path.replace(/^\//, ''),
      title: c.meta.title,
      icon: c.meta.icon
    }))
})

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || 'AI-WMS')

// ---------- 用户下拉 ----------
async function handleCommand(command) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出登录？', '退出', { type: 'warning' })
    } catch {
      return   // 用户点了取消
    }
    clearLogin()
    router.replace('/login')
  } else if (command === 'change-password') {
    openChangePassword()
  }
}

// ---------- 修改密码 ----------
const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

function openChangePassword() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdVisible.value = true
}

async function submitChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码长度不能少于 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  pwdSubmitting.value = true
  try {
    await authApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    pwdVisible.value = false
    ElMessage.success('密码修改成功，请用新密码重新登录')
    // 服务端已撤销旧令牌，下一个请求本来就会 401；
    // 这里主动清凭证跳转是为了体验，而不是让人莫名其妙被踢出去
    clearLogin()
    router.replace('/login')
  } finally {
    pwdSubmitting.value = false
  }
}
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
