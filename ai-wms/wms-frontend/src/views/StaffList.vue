<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><Avatar /></el-icon>
            <span style="font-weight:600">员工管理</span>
            <el-tag size="small" type="info" effect="plain">共 {{ total }} 人</el-tag>
          </div>
          <el-button type="primary" size="small" @click="openCreate">
            <el-icon><Plus /></el-icon> 新增员工
          </el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px">
        <b>工号就是登录名，姓名就是显示名</b> —— 账号都从这个花名册里选人。
        <br/>
        <span style="font-size:12px;color:#909399">
          新人入职先在这里登记，再点「开账号」；离职时标记离职，其账号会一并停用。
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
        <el-select v-model="query.status" placeholder="在职状态" clearable style="width:130px">
          <el-option label="在职" :value="1" />
          <el-option label="离职" :value="0" />
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

        <el-table-column label="在职状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="dark">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="账号" min-width="220">
          <template #default="{ row }">
            <template v-if="row.hasAccount">
              <el-tag size="small" effect="plain">{{ row.roleName }}</el-tag>
              <el-tag :type="row.accountStatus === 1 ? 'success' : 'danger'"
                      size="small" effect="plain" style="margin-left:6px">
                账号{{ row.accountStatusName }}
              </el-tag>
            </template>
            <span v-else style="color:#c0c4cc;font-size:12px">未开账号</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="230" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>

            <!-- ★ 未开账号的人，直接从这里开 —— 新人入职一步到位 -->
            <el-button v-if="!row.hasAccount && row.status === 1"
                       link type="success" size="small" @click="openCreateAccount(row)">
              开账号
            </el-button>

            <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small"
                       @click="doToggleStatus(row)">
              {{ row.status === 1 ? '离职' : '复职' }}
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

    <!-- ============ 新增 / 编辑员工 ============ -->
    <el-dialog v-model="formVisible" :title="editing ? '编辑员工' : '新增员工'" width="480px">
      <el-form :model="form" label-width="80px" size="small">
        <el-form-item label="工号" required>
          <el-input v-model="form.staffCode" :disabled="editing"
                    placeholder="如 OP011、MG004" />
          <div style="font-size:12px;color:#909399;line-height:1.5;margin-top:4px">
            <template v-if="editing">工号是登录名，建成后不可修改</template>
            <template v-else>工号同时是<b>登录名</b>，建好后不可修改，请填准确</template>
          </div>
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.staffName" placeholder="真实姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ editing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 开账号 ============ -->
    <el-dialog v-model="acctVisible" title="开通账号" width="480px">
      <el-alert type="success" :closable="false" show-icon style="margin-bottom:14px">
        为「<b>{{ acctForm.staffCode }} {{ acctForm.staffName }}</b>」开通账号 ——
        登录名就是这个工号
      </el-alert>
      <el-form :model="acctForm" label-width="90px" size="small">
        <el-form-item label="初始密码" required>
          <el-input v-model="acctForm.password" placeholder="6~32 位" show-password />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="acctForm.role" placeholder="选择角色" style="width:100%">
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
        <el-button @click="acctVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAccount">开通</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { staffApi } from '@/api/staff'
import { userApi } from '@/api/user'
import { roleApi } from '@/api/role'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const roles = ref([])

const query = reactive({ pageNum: 1, pageSize: 20, staffCode: '', staffName: '', status: null })

// 新增 / 编辑
const formVisible = ref(false)
const editing = ref(false)
const form = reactive({ id: null, staffCode: '', staffName: '' })

// 开账号
const acctVisible = ref(false)
const acctForm = reactive({ staffId: null, staffCode: '', staffName: '', password: '', role: null })

async function loadData() {
  loading.value = true
  try {
    const data = await staffApi.page(query)
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
  query.status = null
  query.pageNum = 1
  loadData()
}

// ---------- 新增 / 编辑 ----------
function openCreate() {
  editing.value = false
  form.id = null
  form.staffCode = ''
  form.staffName = ''
  formVisible.value = true
}

function openEdit(row) {
  editing.value = true
  form.id = row.id
  form.staffCode = row.staffCode
  form.staffName = row.staffName
  formVisible.value = true
}

async function submitForm() {
  if (!form.staffCode || !form.staffName) {
    ElMessage.warning('工号和姓名都是必填')
    return
  }
  submitting.value = true
  try {
    if (editing.value) {
      await staffApi.update(form.id, { staffCode: form.staffCode, staffName: form.staffName })
      ElMessage.success('员工信息已更新')
    } else {
      await staffApi.create({ staffCode: form.staffCode, staffName: form.staffName })
      ElMessage.success(`员工已创建：登录名 ${form.staffCode}`)
    }
    formVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ---------- 开账号 ----------
function openCreateAccount(row) {
  acctForm.staffId = row.id
  acctForm.staffCode = row.staffCode
  acctForm.staffName = row.staffName
  acctForm.password = ''
  acctForm.role = null
  acctVisible.value = true
}

async function submitAccount() {
  if (!acctForm.password || !acctForm.role) {
    ElMessage.warning('初始密码和角色都是必填')
    return
  }
  submitting.value = true
  try {
    await userApi.create({
      staffId: acctForm.staffId,
      password: acctForm.password,
      role: acctForm.role
    })
    ElMessage.success(`账号已开通，登录名：${acctForm.staffCode}`)
    acctVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ---------- 离职 / 复职 ----------
async function doToggleStatus(row) {
  const leave = row.status === 1
  if (leave) {
    try {
      await ElMessageBox.confirm(
        row.hasAccount
          ? `确认把「${row.staffCode} ${row.staffName}」标记为离职？其账号会一并停用，已登录的会立即失效。`
          : `确认把「${row.staffCode} ${row.staffName}」标记为离职？`,
        '标记离职', { type: 'warning' }
      )
    } catch { return }
  }
  await staffApi.updateStatus(row.id, leave ? 0 : 1)
  ElMessage.success(leave ? '已标记离职' : '已复职')
  loadData()
}

onMounted(async () => {
  roles.value = await roleApi.list()
  loadData()
})
</script>
