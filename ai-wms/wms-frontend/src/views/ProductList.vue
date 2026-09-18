<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;gap:8px">
          <el-icon><Goods /></el-icon>
          <span style="font-weight:600">商品管理</span>
          <el-tag size="small" type="info" effect="plain">共 {{ total }} 个商品款</el-tag>
        </div>
      </template>

      <!-- ========== 搜索栏 ========== -->
      <div class="search-bar">
        <el-input
          v-model="query.reference"
          placeholder="款号（支持模糊查询）"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-select v-model="query.abcClass" placeholder="ABC 分类" clearable style="width: 140px">
          <el-option label="A 类（高频）" value="A" />
          <el-option label="B 类（中频）" value="B" />
          <el-option label="C 类（低频）" value="C" />
        </el-select>

        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon> 查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon> 重置
        </el-button>
      </div>

      <!-- ========== 表格 ========== -->
      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="reference" label="款号" width="140">
          <template #default="{ row }">
            <el-link type="primary" @click="showDetail(row)">{{ row.reference }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="abcClass" label="ABC 分类" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="abcTagType(row.abcClass)" size="small" effect="dark">
              {{ row.abcClass }} 类
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sector" label="分区" width="90" align="center" />
        <el-table-column prop="sizeCount" label="尺码数" width="100" align="center">
          <template #default="{ row }">
            <span style="font-weight:600;color:#1f4e79">{{ row.sizeCount }}</span> 个
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">
              查看尺码
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- ========== 分页 ========== -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- ========== 尺码详情弹窗 ========== -->
    <el-dialog v-model="dialogVisible" :title="`${detail.reference} 的全部尺码`" width="560px">
      <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="款号">{{ detail.reference }}</el-descriptions-item>
        <el-descriptions-item label="ABC 分类">{{ detail.abcClass }} 类</el-descriptions-item>
        <el-descriptions-item label="分区">{{ detail.sector }}</el-descriptions-item>
        <el-descriptions-item label="尺码总数">{{ detail.sizeCount }} 个</el-descriptions-item>
      </el-descriptions>

      <el-tag
        v-for="s in detail.sizes"
        :key="s"
        size="small"
        style="margin: 0 6px 6px 0"
        effect="plain"
      >
        {{ s }}
      </el-tag>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { productApi } from '@/api/product'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const detail = ref({})

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  reference: '',
  abcClass: ''
})

/** ABC 分类对应的标签颜色 */
function abcTagType(abc) {
  return { A: 'danger', B: 'warning', C: 'info' }[abc] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const data = await productApi.page(query)
    list.value = data.records
    total.value = data.total
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.reference = ''
  query.abcClass = ''
  query.pageNum = 1
  loadData()
}

async function showDetail(row) {
  try {
    detail.value = await productApi.detail(row.id)
    dialogVisible.value = true
  } catch (e) {
    console.error(e)
  }
}

onMounted(loadData)
</script>
