<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><Key /></el-icon>
            <span style="font-weight:600">角色管理</span>
            <el-tag size="small" type="info" effect="plain">共 {{ list.length }} 个角色</el-tag>
          </div>
          <el-button type="primary" size="small" @click="openCreate">
            <el-icon><Plus /></el-icon> 新建角色
          </el-button>
        </div>
      </template>

      <el-alert type="success" :closable="false" show-icon style="margin-bottom:14px">
        角色和权限都是**数据**——在这里新建角色、勾选权限，保存后立刻生效，
        <b>不需要改代码、不需要重启服务</b>。
      </el-alert>

      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="角色码" width="130" />
        <el-table-column prop="name" label="角色名" width="130" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.builtin === 1" size="small" type="warning" effect="plain">内置</el-tag>
            <el-tag v-else size="small" type="success" effect="plain">自定义</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限" min-width="300">
          <template #default="{ row }">
            <template v-if="row.permissions && row.permissions.length">
              <el-tag v-for="p in row.permissions" :key="p" size="small"
                      effect="plain" style="margin:0 6px 4px 0">
                {{ permLabel(p) }}
              </el-tag>
            </template>
            <span v-else style="color:#c0c4cc;font-size:12px">无（只读角色）</span>
          </template>
        </el-table-column>
        <el-table-column prop="userCount" label="在用账号" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ fontWeight: 600, color: row.userCount ? '#1f4e79' : '#c0c4cc' }">
              {{ row.userCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditPerm(row)">
              配置权限
            </el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small"
                       @click="doToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ============ 新建角色 / 配置权限 ============ -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px">
      <el-form :model="form" label-width="90px" size="small">
        <template v-if="!editing">
          <el-form-item label="角色码" required>
            <el-input v-model="form.code" placeholder="大写字母+数字/下划线，如 INSPECTOR" />
          </el-form-item>
          <el-form-item label="角色名" required>
            <el-input v-model="form.name" placeholder="如「质检员」" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" placeholder="选填，说明这个岗位干什么" />
          </el-form-item>
        </template>
        <el-form-item v-else label="角色">
          <el-tag effect="plain">{{ form.code }}</el-tag>
          <span style="margin-left:8px;font-weight:600">{{ form.name }}</span>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">权限点</el-divider>

      <div v-for="g in permGroups" :key="g.key" style="margin-bottom:12px">
        <div style="font-size:13px;font-weight:600;color:#606266;margin-bottom:6px">
          {{ g.label }}
        </div>
        <el-checkbox-group v-model="form.permissions">
          <el-checkbox v-for="p in g.items" :key="p.code" :value="p.code"
                       style="min-width:210px;margin-right:12px">
            {{ p.desc }}
            <span style="color:#c0c4cc;font-size:11px">{{ p.code }}</span>
          </el-checkbox>
        </el-checkbox-group>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ editing ? '保存权限' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roleApi } from '@/api/role'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const catalog = ref({})

const dialogVisible = ref(false)
const editing = ref(false)
const form = reactive({ code: '', name: '', remark: '', permissions: [] })

const dialogTitle = computed(() => editing.value ? `配置权限 - ${form.name}` : '新建角色')

/** 权限点按「域」分组，勾选框才不至于糊成一片 */
const GROUP_LABELS = {
  inbound: '入库',
  outbound: '出库',
  wave: '波次与拣货',
  inventory: '库存',
  import: '系统管理',
  user: '系统管理',
  role: '系统管理'
}

const permGroups = computed(() => {
  const groups = new Map()
  for (const [code, desc] of Object.entries(catalog.value)) {
    const key = code.split(':')[0]
    const label = GROUP_LABELS[key] || key
    if (!groups.has(label)) {
      groups.set(label, [])
    }
    groups.get(label).push({ code, desc })
  }
  return [...groups.entries()].map(([label, items]) => ({ key: label, label, items }))
})

function permLabel(code) {
  return catalog.value[code] || code
}

async function loadData() {
  loading.value = true
  try {
    list.value = await roleApi.list()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  form.code = ''
  form.name = ''
  form.remark = ''
  form.permissions = []
  dialogVisible.value = true
}

function openEditPerm(row) {
  editing.value = true
  form.code = row.code
  form.name = row.name
  form.remark = row.remark || ''
  form.permissions = [...(row.permissions || [])]
  dialogVisible.value = true
}

async function submit() {
  submitting.value = true
  try {
    if (editing.value) {
      await roleApi.updatePermissions(form.code, form.permissions)
      ElMessage.success('权限已更新，立即生效')
    } else {
      if (!form.code || !form.name) {
        ElMessage.warning('角色码和角色名都是必填')
        return
      }
      await roleApi.create({ ...form })
      ElMessage.success('角色创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function doToggleStatus(row) {
  const disable = row.status === 1
  try {
    await ElMessageBox.confirm(
      disable
        ? `确认停用角色「${row.name}」？若还有账号在用会被拒绝。`
        : `确认启用角色「${row.name}」？`,
      disable ? '停用角色' : '启用角色',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await roleApi.updateStatus(row.code, disable ? 0 : 1)
  ElMessage.success(disable ? '角色已停用' : '角色已启用')
  loadData()
}

onMounted(async () => {
  catalog.value = await roleApi.permissions()
  loadData()
})
</script>
