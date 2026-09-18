<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;gap:8px">
          <el-icon><Upload /></el-icon>
          <span style="font-weight:600">出库管理</span>
          <el-tag size="small" type="info" effect="plain">共 {{ total }} 个订单</el-tag>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input v-model="query.orderNo" placeholder="订单号" clearable style="width:200px"
                  @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" placeholder="状态" clearable style="width:150px">
          <el-option label="待分配" :value="0" />
          <el-option label="已分配" :value="1" />
          <el-option label="拣货中" :value="2" />
          <el-option label="已发货" :value="3" />
        </el-select>
        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="handleReset"><el-icon><Refresh /></el-icon> 重置</el-button>
        <el-button type="success" plain @click="doExport">
          <el-icon><Download /></el-icon> 导出 Excel
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="orderNo" label="订单号" width="130">
          <template #default="{ row }">
            <el-link type="primary" @click="showDetail(row)">{{ row.orderNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="customerCode" label="客户" width="130" />
        <el-table-column prop="statusName" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="dark">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" label="明细数" width="90" align="center" />
        <el-table-column prop="totalQty" label="总件数" width="90" align="center">
          <template #default="{ row }">
            <span style="font-weight:600;color:#1f4e79">{{ row.totalQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="orderTime" label="下单时间" width="170" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" link type="success" size="small"
                       @click="doAllocate(row)">分配库存</el-button>
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

    <!-- ============ 详情弹窗 ============ -->
    <el-dialog v-model="detailVisible" title="订单详情" width="900px">
      <el-descriptions :column="4" border size="small" style="margin-bottom:14px">
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.customerCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ detail.statusName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="总件数">{{ detail.totalQty }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.lines" border size="small">
        <el-table-column prop="skuCode" label="SKU 编码" width="130" />
        <el-table-column prop="reference" label="款号" width="100" />
        <el-table-column prop="sizeUs" label="尺码" width="70" align="center" />
        <el-table-column prop="qty" label="数量" width="70" align="center" />
        <el-table-column label="分配情况" min-width="280">
          <template #default="{ row }">
            <template v-if="row.allocations && row.allocations.length">
              <el-tag v-for="a in row.allocations" :key="a.id" size="small"
                      type="success" effect="plain" style="margin:0 6px 4px 0">
                {{ a.locationCode }} × {{ a.qtyAllocated }}
              </el-tag>
            </template>
            <span v-else style="color:#c0c4cc;font-size:12px">未分配</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- ============ 分配结果弹窗 ============ -->
    <el-dialog v-model="resultVisible" title="库存分配结果" width="620px">
      <el-result icon="success" title="分配成功"
                 :sub-title="`订单 ${result.orderNo}：${result.allocatedLines} 条明细，共 ${result.allocatedQty} 件`">
      </el-result>
      <el-table :data="result.allocations" border size="small">
        <el-table-column prop="skuCode" label="SKU" width="150" />
        <el-table-column prop="locationCode" label="取货库位" width="130" align="center" />
        <el-table-column prop="qtyAllocated" label="分配数量" align="center" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { outboundApi, exportOrdersUrl } from '@/api/outbound'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, orderNo: '', status: null })

const detailVisible = ref(false)
const detail = ref({})

const resultVisible = ref(false)
const result = ref({})

function statusTagType(s) {
  return { 0: 'warning', 1: 'primary', 2: 'info', 3: 'success' }[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const data = await outboundApi.page(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() { query.pageNum = 1; loadData() }
function handleReset() { query.orderNo = ''; query.status = null; query.pageNum = 1; loadData() }

function doExport() {
  window.open(exportOrdersUrl({ ...query, pageNum: null, pageSize: null }), '_blank')
}

async function showDetail(row) {
  detail.value = await outboundApi.detail(row.id)
  detailVisible.value = true
}

async function doAllocate(row) {
  try {
    await ElMessageBox.confirm(
      `确认为订单 ${row.orderNo} 分配库存？系统会自动选择库位。`,
      '分配库存', { type: 'info' }
    )
  } catch { return }

  try {
    result.value = await outboundApi.allocate(row.id)
    resultVisible.value = true
    loadData()
  } catch (e) {
    // 错误提示已由 axios 拦截器处理
  }
}

onMounted(loadData)
</script>
