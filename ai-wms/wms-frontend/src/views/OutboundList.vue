<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><Upload /></el-icon>
            <span style="font-weight:600">出库管理</span>
            <el-tag size="small" type="info" effect="plain">共 {{ total }} 个订单</el-tag>
          </div>
          <el-button v-if="hasPermission('outbound:create')" type="primary" size="small"
                     @click="openCreate">
            <el-icon><Plus /></el-icon> 新建出库单
          </el-button>
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
            <el-button v-if="row.status === 0 && hasPermission('outbound:allocate')"
                       link type="success" size="small"
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

    <!-- ============ 新建出库单 ============ -->
    <el-dialog v-model="createVisible" title="新建出库单" width="760px">
      <el-form :model="createForm" label-width="90px" size="small">
        <el-form-item label="客户">
          <el-select
            v-model="createForm.customerId"
            filterable
            remote
            :remote-method="searchCustomer"
            :loading="customerLoading"
            placeholder="搜索客户（输入编码或名称）"
            style="width:100%"
          >
            <el-option
              v-for="c in customerOptions"
              :key="c.id"
              :label="`${c.custCode}  ${c.custName || ''}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">出库明细</el-divider>

        <div v-for="(line, idx) in createForm.lines" :key="idx"
             style="display:flex;gap:8px;margin-bottom:8px;align-items:center">
          <el-select
            v-model="line.skuId"
            filterable
            remote
            :remote-method="searchSku"
            :loading="skuLoading"
            placeholder="搜索 SKU（输入款号，如 8N10W9）"
            style="flex:1"
          >
            <el-option
              v-for="s in skuOptions"
              :key="s.id"
              :label="`${s.skuCode}  (${s.abcClass}类)`"
              :value="s.id"
            />
          </el-select>
          <el-input-number v-model="line.qty" :min="1" :max="9999" style="width:140px" />
          <el-button link type="danger" @click="createForm.lines.splice(idx, 1)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>

        <el-button size="small" @click="createForm.lines.push({ skuId: null, qty: 1 })">
          <el-icon><Plus /></el-icon> 添加一行
        </el-button>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
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
import { customerApi } from '@/api/customer'
import { skuApi } from '@/api/sku'
import { hasPermission } from '@/utils/permission'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, orderNo: '', status: null })

const detailVisible = ref(false)
const detail = ref({})

const resultVisible = ref(false)
const result = ref({})

// 新建出库单
const createVisible = ref(false)
const createForm = reactive({ customerId: null, lines: [] })
const customerOptions = ref([])
const customerLoading = ref(false)
const skuOptions = ref([])
const skuLoading = ref(false)

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

// ---------- 新建出库单 ----------
function openCreate() {
  createForm.customerId = null
  createForm.lines = [{ skuId: null, qty: 1 }]
  customerOptions.value = []
  skuOptions.value = []
  createVisible.value = true
  searchCustomer('')
  searchSku('')
}

async function searchCustomer(keyword) {
  customerLoading.value = true
  try {
    customerOptions.value = await customerApi.search(keyword)
  } catch (e) {
    console.error(e)
  } finally {
    customerLoading.value = false
  }
}

async function searchSku(keyword) {
  skuLoading.value = true
  try {
    skuOptions.value = await skuApi.search(keyword)
  } catch (e) {
    console.error(e)
  } finally {
    skuLoading.value = false
  }
}

async function submitCreate() {
  if (!createForm.customerId) {
    ElMessage.warning('请选择客户')
    return
  }
  const lines = createForm.lines.filter(l => l.skuId && l.qty > 0)
  if (!lines.length) {
    ElMessage.warning('请至少添加一条有效明细')
    return
  }
  submitting.value = true
  try {
    await outboundApi.create({ customerId: createForm.customerId, lines })
    ElMessage.success('出库单创建成功')
    createVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>
