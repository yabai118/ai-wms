<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <el-icon :size="34" color="#1f4e79"><Box /></el-icon>
        <h1>AI-WMS</h1>
        <p>智能仓储管理系统</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="submit"
      >
        <el-form-item label="登录名" prop="username">
          <el-input v-model="form.username" placeholder="工号 / 账号" clearable>
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          style="width:100%"
          :loading="loading"
          @click="submit"
        >
          登 录
        </el-button>
      </el-form>

      <el-divider content-position="center">
        <span style="font-size:12px;color:#909399">演示账号（点击标签自动填充）</span>
      </el-divider>

      <div class="demo-accounts">
        <el-tag
          v-for="a in demoAccounts"
          :key="a.username"
          size="small"
          effect="plain"
          style="cursor:pointer"
          @click="fill(a.username)"
        >
          {{ a.username }}（{{ a.roleName }}）
        </el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { setLogin } from '@/utils/authStorage'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入登录名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 演示账号密码从环境变量注入（wms-frontend/.env.local，已 gitignore），
// 不写死在代码里——代码要提交到公开仓库。
// 未配置时只填账号，密码需要手工输入。
const demoPassword = import.meta.env.VITE_DEMO_PASSWORD || ''

const demoAccounts = [
  { username: 'admin', roleName: '管理员' },
  { username: 'receiver', roleName: '收货员' },
  { username: 'picker', roleName: '拣货员' },
  { username: 'supervisor', roleName: '主管' }
]

function fill(username) {
  form.username = username
  if (demoPassword) {
    form.password = demoPassword
  }
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await authApi.login({ ...form })
    setLogin(data)
    ElMessage.success(`欢迎，${data.displayName || data.username}`)
    // 回原本要去的页面；没有就进首页。
    // 用 replace 而不是 push，免得用户后退又退回登录页
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' && redirect ? redirect : '/dashboard')
  } catch (e) {
    // 错误提示已由 axios 拦截器弹出，这里不再重复
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f4e79 0%, #2c5f8d 45%, #3a7ca5 100%);
}

.login-card {
  width: 400px;
  border-radius: 10px;
  padding: 8px 12px;
}

.brand {
  text-align: center;
  margin-bottom: 22px;
}

.brand h1 {
  margin: 8px 0 2px;
  font-size: 22px;
  letter-spacing: 2px;
  color: #1f4e79;
}

.brand p {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.demo-accounts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}
</style>
