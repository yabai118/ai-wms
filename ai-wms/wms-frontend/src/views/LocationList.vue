<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;gap:8px">
          <el-icon><Grid /></el-icon>
          <span style="font-weight:600">库位管理</span>
          <el-tag size="small" type="info" effect="plain">共 {{ total }} 个库位</el-tag>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="query.locationCode"
          placeholder="库位号（如 A-14）"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-select v-model="query.locationType" placeholder="库位类型" clearable style="width: 140px">
          <el-option label="存储区" :value="0" />
          <el-option label="拣货区" :value="1" />
        </el-select>

        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon> 查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon> 重置
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="locationCode" label="库位号" width="130" />
        <el-table-column prop="areaCode" label="库区" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.areaCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="locationTypeName" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.locationType === 1 ? 'success' : 'info'" size="small" effect="dark">
              {{ row.locationTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="坐标 (X, Y, 层)" width="180" align="center">
          <template #default="{ row }">
            <span style="font-family:monospace;color:#606266">
              ({{ row.xCoord }}, {{ row.yCoord }}, {{ row.zCoord }})
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量" width="80" align="center" />
        <el-table-column prop="usedSlots" label="已用" width="80" align="center" />
        <el-table-column label="占用率" width="140" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="row.usageRate"
              :stroke-width="12"
              :color="row.usageRate > 80 ? '#f56c6c' : row.usageRate > 40 ? '#e6a23c' : '#67c23a'"
            />
          </template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : row.status === 1 ? 'warning' : 'danger'"
                    size="small" effect="plain">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[20, 50, 100, 200]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { locationApi } from '@/api/location'

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  locationCode: '',
  locationType: null
})

async function loadData() {
  loading.value = true
  try {
    const data = await locationApi.page(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.locationCode = ''
  query.locationType = null
  query.pageNum = 1
  loadData()
}

onMounted(loadData)
</script>
