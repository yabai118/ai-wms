<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><Download /></el-icon>
            <span style="font-weight:600">入库管理</span>
            <el-tag size="small" type="info" effect="plain">共 {{ total }} 个入库单</el-tag>
          </div>
          <el-button type="primary" size="small" @click="openCreate">
            <el-icon><Plus /></el-icon> 创建入库单
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input v-model="query.orderNo" placeholder="入库单号" clearable style="width:200px"
                  @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
          <el-option label="待收货" :value="0" />
          <el-option label="待上架" :value="1" />
          <el-option label="已完成" :value="2" />
        </el-select>
        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="handleReset"><el-icon><Refresh /></el-icon> 重置</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="orderNo" label="入库单号" width="170">
          <template #default="{ row }">
            <el-link type="primary" @click="showDetail(row)">{{ row.orderNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="orderTypeName" label="类型" width="100" align="center" />
        <el-table-column prop="sourceNo" label="来源工单" width="130" />
        <el-table-column prop="statusName" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="dark">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lineCount" label="明细" width="80" align="center" />
        <el-table-column label="计划/实收" width="120" align="center">
          <template #default="{ row }">
            {{ row.totalPlanQty }} / <b>{{ row.totalReceivedQty }}</b>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" link type="warning" size="small"
                       @click="openReceive(row)">收货</el-button>
            <el-button v-if="row.status === 1" link type="success" size="small"
                       @click="openShelve(row)">上架</el-button>
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
    <el-dialog v-model="detailVisible" title="入库单详情" width="860px">
      <el-descriptions :column="3" border size="small" style="margin-bottom:14px">
        <el-descriptions-item label="单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.orderTypeName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ detail.statusName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源工单">{{ detail.sourceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.lines" border size="small">
        <el-table-column prop="skuCode" label="SKU 编码" width="140" />
        <el-table-column prop="reference" label="款号" width="100" />
        <el-table-column prop="sizeUs" label="尺码" width="70" align="center" />
        <el-table-column prop="planQty" label="计划" width="70" align="center" />
        <el-table-column prop="receivedQty" label="实收" width="70" align="center" />
        <el-table-column prop="locationCode" label="上架货位" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.locationCode" size="small" effect="plain">{{ row.locationCode }}</el-tag>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="90" align="center" />
      </el-table>
    </el-dialog>

    <!-- ============ 创建弹窗 ============ -->
    <el-dialog v-model="createVisible" title="创建入库单" width="760px">
      <el-form :model="createForm" label-width="90px" size="small">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="入库类型">
              <el-select v-model="createForm.orderType" style="width:100%">
                <el-option label="生产入库" :value="1" />
                <el-option label="退货入库" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源工单">
              <el-input v-model="createForm.sourceNo" placeholder="如 WO-2026-001" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" placeholder="选填" />
        </el-form-item>

        <el-divider content-position="left">入库明细</el-divider>

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
          <el-input-number v-model="line.planQty" :min="1" :max="9999" style="width:140px" />
          <el-button link type="danger" @click="createForm.lines.splice(idx, 1)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>

        <el-button size="small" @click="createForm.lines.push({ skuId: null, planQty: 10 })">
          <el-icon><Plus /></el-icon> 添加一行
        </el-button>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- ============ 收货弹窗 ============ -->
    <el-dialog v-model="receiveVisible" :title="`收货 - ${current.orderNo}`" width="700px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        填写实收数量（可以少于计划数量，超出会被拒绝）
      </el-alert>
      <el-table :data="receiveLines" border size="small">
        <el-table-column prop="skuCode" label="SKU" width="140" />
        <el-table-column prop="planQty" label="计划数量" width="100" align="center" />
        <el-table-column label="实收数量" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.receivedQty" :min="0" :max="row.planQty" size="small" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="receiveVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReceive">确认收货</el-button>
      </template>
    </el-dialog>

    <!-- ============ 上架弹窗 ============ -->
    <el-dialog v-model="shelveVisible" :title="`上架 - ${current.orderNo}`" width="760px">
      <el-alert type="success" :closable="false" show-icon style="margin-bottom:12px">
        不选货位时，系统会自动推荐（优先拣货区、占用最少的库位）
      </el-alert>
      <el-table :data="shelveLines" border size="small">
        <el-table-column prop="skuCode" label="SKU" width="140" />
        <el-table-column prop="receivedQty" label="数量" width="80" align="center" />
        <el-table-column label="目标货位" align="center">
          <template #default="{ row, $index }">
            <div style="display:flex;gap:6px;justify-content:center">
              <el-input v-model="row.locationCode" placeholder="留空则自动推荐" size="small"
                        style="width:170px" />
              <el-button size="small" @click="doRecommend(row, $index)">推荐</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="shelveVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitShelve">确认上架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inboundApi } from '@/api/inbound'
import { skuApi } from '@/api/sku'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({ pageNum: 1, pageSize: 10, orderNo: '', status: null })

// 详情
const detailVisible = ref(false)
const detail = ref({})

// 创建
const createVisible = ref(false)
const createForm = reactive({ orderType: 1, sourceNo: '', remark: '', lines: [] })
const skuOptions = ref([])
const skuLoading = ref(false)

// 收货 / 上架
const receiveVisible = ref(false)
const shelveVisible = ref(false)
const current = ref({})
const receiveLines = ref([])
const shelveLines = ref([])

function statusTagType(s) {
  return { 0: 'warning', 1: 'primary', 2: 'success' }[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const data = await inboundApi.page(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() { query.pageNum = 1; loadData() }
function handleReset() { query.orderNo = ''; query.status = null; query.pageNum = 1; loadData() }

async function showDetail(row) {
  detail.value = await inboundApi.detail(row.id)
  detailVisible.value = true
}

// ---------- 创建 ----------
function openCreate() {
  createForm.orderType = 1
  createForm.sourceNo = ''
  createForm.remark = ''
  createForm.lines = [{ skuId: null, planQty: 10 }]
  skuOptions.value = []
  createVisible.value = true
  searchSku('')
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
  const lines = createForm.lines.filter(l => l.skuId && l.planQty > 0)
  if (!lines.length) {
    ElMessage.warning('请至少添加一条有效明细')
    return
  }
  submitting.value = true
  try {
    await inboundApi.create({ ...createForm, lines })
    ElMessage.success('入库单创建成功')
    createVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ---------- 收货 ----------
async function openReceive(row) {
  current.value = row
  const d = await inboundApi.detail(row.id)
  receiveLines.value = d.lines.map(l => ({ ...l, receivedQty: l.planQty }))
  receiveVisible.value = true
}

async function submitReceive() {
  submitting.value = true
  try {
    await inboundApi.receive(current.value.id, {
      items: receiveLines.value.map(l => ({ lineId: l.id, receivedQty: l.receivedQty }))
    })
    ElMessage.success('收货完成')
    receiveVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ---------- 上架 ----------
async function openShelve(row) {
  current.value = row
  const d = await inboundApi.detail(row.id)
  shelveLines.value = d.lines.map(l => ({ ...l, locationCode: '' }))
  shelveVisible.value = true
}

async function doRecommend(row) {
  const ids = await inboundApi.recommend(row.skuId, row.receivedQty, 1)
  if (!ids.length) {
    ElMessage.warning('没有可用货位，请手动填写')
    return
  }
  // 推荐接口返回的是 locationId，这里简单展示提示；用户也可手填库位号
  ElMessage.success(`推荐货位 ID: ${ids[0]}，可留空让系统自动分配`)
}

async function submitShelve() {
  submitting.value = true
  try {
    // 只传 lineId，让后端自动推荐货位（手填库位号需要额外解析，后续增强）
    await inboundApi.shelve(current.value.id, {
      items: shelveLines.value.map(l => ({ lineId: l.id }))
    })
    ElMessage.success('上架完成，库存已更新')
    shelveVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>
