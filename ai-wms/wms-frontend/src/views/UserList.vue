<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><UserFilled /></el-icon>
            <span style="font-weight:600">账号管理</span>
            <el-tag size="small" type="info" effect="plain">共 {{ total }} 个账号</el-tag>
          </div>
          <el-button type="primary" size="small" @click="openCreate">
            <el-icon><Plus /></el-icon> 新建账号
          </el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px">
        账号与员工<b>一对一</b>：登录名就是<b>工号</b>，显示名就是<b>姓名</b>，都取自员工档案，
        所以新建账号只需选一个人。<br/>
        <span style="font-size:12px;color:#909399">
          每个账号都必须有对应的员工（含管理员）——这样任何操作都查得到责任人。
        </span>
      </el-alert>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input v-model="query.staffCode" placeholder="工号" clearable style="width:150px"
                  @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-input v-model="query.staffName" placeholder="姓名" clearable style="width:140px"
                  @keyup.enter="handleSearch" />

        <el-select v-model="query.role" placeholder="角色" clearable style="width:140px">
          <el-option v-for="r in roles" :key="r.code" :label="r.name" :value="r.code" />
        </el-select>

        <el-select v-model="query.status" placeholder="状态" clearable style="width:110px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>

        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="handleReset"><el-icon><Refresh /></el-icon> 重置</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="staffCode" label="工号" width="110">
          <template #default="{ row }">
            <span style="font-weight:600;color:#1f4e79">{{ row.staffCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="staffName" label="姓名" width="110" />
        <el-table-column prop="roleName" label="角色" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.roleName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" min-width="170">
          <template #default="{ row }">
            <span v-if="row.lastLoginAt">{{ row.lastLoginAt }}</span>
            <span v-else style="color:#c0c4cc;font-size:12px">从未登录</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button link type="warning" size="small" @click="doResetPassword(row)">
              重置密码
            </el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small"
                       @click="doToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- ============ 新建账号 ============ -->
    <el-dialog v-model="createVisible" title="新建账号" width="520px">
      <el-form :model="createForm" label-width="90px" size="small">
        <el-form-item label="选择员工" required>
          <el-select v-model="createForm.staffId" placeholder="选一个人 —— 工号即登录名"
                     filterable style="width:100%">
            <el-option
              v-for="s in staffList"
              :key="s.id"
              :label="`${s.staffCode} ${s.staffName}`"
              :value="s.id"
              :disabled="s.linked"
            >
              <span>{{ s.staffCode }} {{ s.staffName }}</span>
              <span v-if="s.linked" style="float:right;color:#c0c4cc;font-size:12px">
                已有账号
              </span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="初始密码" required>
          <el-input v-model="createForm.password" placeholder="6~32 位" show-password />
        </el-form-item>

        <el-form-item label="角色" required>
          <el-select v-model="createForm.role" placeholder="选择角色" style="width:100%">
            <el-option
              v-for="r in roles"
              :key="r.code"
              :label="`${r.name}（${r.code}）`"
              :value="r.code"
              :disabled="r.status !== 1"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api/user'
import { roleApi } from '@/api/role'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const roles = ref([])
const staffList = ref([])

const query = reactive({
  pageNum: 1, pageSize: 20, staffCode: '', staffName: '', role: null, status: null
})

const createVisible = ref(false)
const createForm = reactive({ staffId: null, password: '', role: null })

/** 选中员工后，把工号姓名显示出来给用户确认 */
const selectedStaff = computed(
  () => staffList.value.find(s => s.id === createForm.staffId) || null
)

async function loadData() {
  loading.value = true
  try {
    const data = await userApi.page(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() { query.pageNum = 1; loadData() }
function handleReset() {
  query.staffCode = ''
  query.staffName = ''
  query.role = null
  query.status = null
  query.pageNum = 1
  loadData()
}

// ---------- 新建 ----------
async function openCreate() {
  createForm.staffId = null
  createForm.password = ''
  createForm.role = null
  createVisible.value = true
  // 每次都重取：员工可能在别处被关联了
  staffList.value = await userApi.staff()
}

async function submitCreate() {
  if (!createForm.staffId || !createForm.password || !createForm.role) {
    ElMessage.warning('员工、初始密码、角色都是必填')
    return
  }
  submitting.value = true
  try {
    await userApi.create({ ...createForm })
    const s = selectedStaff.value
    ElMessage.success(`账号创建成功：登录名 ${s.staffCode}（${s.staffName}）`)
    createVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ---------- 启停 ----------
async function doToggleStatus(row) {
  const disable = row.status === 1
  try {
    await ElMessageBox.confirm(
      disable
        ? `确认停用「${row.staffCode} ${row.staffName}」？停用后该账号将无法登录，已登录的也会立即失效。`
        : `确认启用「${row.staffCode} ${row.staffName}」？`,
      disable ? '停用账号' : '启用账号',
      { type: 'warning' }
    )
  } catch {
    return   // 用户点了取消
  }
  await userApi.updateStatus(row.id, disable ? 0 : 1)
  ElMessage.success(disable ? '账号已停用' : '账号已启用')
  loadData()
}

// ---------- 重置密码 ----------
async function doResetPassword(row) {
  let value
  try {
    const res = await ElMessageBox.prompt(
      `为「${row.staffCode} ${row.staffName}」设置新密码（该账号的登录状态会立即失效）`,
      '重置密码',
      { inputPattern: /^.{6,32}$/, inputErrorMessage: '密码长度需在 6~32 位之间' }
    )
    value = res.value
  } catch {
    return
  }
  await userApi.resetPassword(row.id, value)
  ElMessage.success('密码已重置')
  loadData()
}

onMounted(async () => {
  roles.value = await roleApi.list()
  loadData()
})
</script>
