<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><Van /></el-icon>
            <span style="font-weight:600">波次与拣货</span>
            <el-tag size="small" type="info" effect="plain">共 {{ total }} 个波次</el-tag>
          </div>
          <el-button type="primary" size="small" @click="openGenerate">
            <el-icon><Plus /></el-icon> 生成波次
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input v-model="query.waveNo" placeholder="波次号" clearable style="width:200px"
                  @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" placeholder="状态" clearable style="width:140px">
          <el-option label="待拣货" :value="0" />
          <el-option label="拣货中" :value="1" />
          <el-option label="已完成" :value="2" />
        </el-select>
        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="handleReset"><el-icon><Refresh /></el-icon> 重置</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="list" v-loading="loading" border stripe size="small">
        <el-table-column prop="waveNo" label="波次号" width="160">
          <template #default="{ row }">
            <el-link type="primary" @click="showDetail(row)">{{ row.waveNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="statusName" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="dark">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderCount" label="订单数" width="90" align="center" />
        <el-table-column prop="totalTasks" label="任务数" width="90" align="center">
          <template #default="{ row }">
            <span style="font-weight:600;color:#1f4e79">{{ row.totalTasks }}</span>
          </template>
        </el-table-column>
        <el-table-column label="件数 / 容量" width="120" align="center">
          <template #default="{ row }">
            {{ row.totalQty }} / {{ row.capacity }}
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="拣货员" width="120" align="center">
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="pathDistance" label="行走距离" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.pathDistance">{{ row.pathDistance }} 米</span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" link type="warning" size="small"
                       @click="doPick(row)">拣货</el-button>
            <el-button v-if="row.status === 1" link type="success" size="small"
                       @click="doShip(row)">发货</el-button>
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
    <el-dialog v-model="detailVisible" width="960px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between;padding-right:30px">
          <span style="font-weight:600">波次详情</span>
          <el-button size="small" type="success" plain @click="exportPickList">
            <el-icon><Download /></el-icon> 导出拣货单
          </el-button>
        </div>
      </template>
      <el-descriptions :column="4" border size="small" style="margin-bottom:14px">
        <el-descriptions-item label="波次号">{{ detail.waveNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ detail.statusName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="订单数">{{ detail.orderCount }}</el-descriptions-item>
        <el-descriptions-item label="件数 / 容量">{{ detail.totalQty }} / {{ detail.capacity }}</el-descriptions-item>
      </el-descriptions>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
        拣货任务已按<b>库位聚合</b>——拣货员按顺序走库位即可，不用来回跑
      </el-alert>

      <el-table :data="detail.tasks" border size="small" max-height="400">
        <el-table-column prop="seqNo" label="序号" width="70" align="center">
          <template #default="{ row }">{{ row.seqNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="locationCode" label="库位号" width="120" />
        <el-table-column label="坐标 (X, Y)" width="150" align="center">
          <template #default="{ row }">
            <span style="font-family:monospace">({{ row.xCoord }}, {{ row.yCoord }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="skuCode" label="SKU 编码" width="140" />
        <el-table-column prop="qtyPlan" label="应拣" width="80" align="center" />
        <el-table-column prop="qtyPicked" label="已拣" width="80" align="center" />
        <el-table-column prop="statusName" label="状态" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- ============ 生成波次弹窗 ============ -->
    <el-dialog v-model="generateVisible" title="生成波次" width="820px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px">
        选择「已分配」状态的订单合并成一批。总件数不能超过载具容量 <b>27 件</b>。
      </el-alert>

      <div class="search-bar">
        <el-input v-model="orderQuery.orderNo" placeholder="搜索订单号" clearable style="width:200px" />
        <el-button type="primary" size="small" @click="loadAllocatedOrders">查询已分配订单</el-button>
        <el-tag :type="selectedQty > 27 ? 'danger' : 'success'" size="small">
          已选 {{ selectedOrders.length }} 单 / {{ selectedQty }} 件
        </el-tag>
      </div>

      <el-table :data="allocatedOrders" border size="small" max-height="360"
                @selection-change="onSelectOrders" ref="orderTableRef">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="orderNo" label="订单号" width="140" />
        <el-table-column prop="customerCode" label="客户" width="130" />
        <el-table-column prop="lineCount" label="明细数" width="90" align="center" />
        <el-table-column prop="totalQty" label="件数" width="80" align="center" />
        <el-table-column prop="orderTime" label="下单时间" />
      </el-table>

      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!selectedOrders.length"
                   @click="submitGenerate">生成波次</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { waveApi, exportPickTasksUrl } from '@/api/wave'
import { outboundApi } from '@/api/outbound'

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, waveNo: '', status: null })

const detailVisible = ref(false)
const detail = ref({})

const generateVisible = ref(false)
const orderTableRef = ref(null)
const orderQuery = reactive({ orderNo: '', status: 1, pageSize: 50, pageNum: 1 })
const allocatedOrders = ref([])
const selectedOrders = ref([])

const selectedQty = computed(() =>
  selectedOrders.value.reduce((s, o) => s + (o.totalQty || 0), 0))

function statusTagType(s) {
  return { 0: 'warning', 1: 'primary', 2: 'success' }[s] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const data = await waveApi.page(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() { query.pageNum = 1; loadData() }
function handleReset() { query.waveNo = ''; query.status = null; query.pageNum = 1; loadData() }

async function showDetail(row) {
  detail.value = await waveApi.detail(row.id)
  detailVisible.value = true
}

/** 导出拣货单——实际仓库会打印出来给拣货员用 */
function exportPickList() {
  window.open(exportPickTasksUrl(detail.value.id), '_blank')
}

// ---------- 拣货 ----------
async function doPick(row) {
  try {
    await ElMessageBox.confirm(
      `确认完成波次 ${row.waveNo} 的拣货？<br/>拣货后库存状态变为「已拣出」，总数不变。`,
      '拣货确认', { type: 'warning', dangerouslyUseHTMLString: true }
    )
  } catch { return }

  try {
    await waveApi.pick(row.id)
    ElMessage.success('拣货完成')
    loadData()
  } catch (e) { /* 拦截器已提示 */ }
}

// ---------- 发货 ----------
async function doShip(row) {
  try {
    await ElMessageBox.confirm(
      `确认对波次 ${row.waveNo} 发货？<br/>这将<b>真正扣减库存总数</b>并生成发货单。`,
      '发货确认', { type: 'warning', dangerouslyUseHTMLString: true }
    )
  } catch { return }

  try {
    await waveApi.ship(row.id)
    ElMessage.success('发货完成，库存已扣减')
    loadData()
  } catch (e) { /* 拦截器已提示 */ }
}

// ---------- 生成波次 ----------
function openGenerate() {
  generateVisible.value = true
  selectedOrders.value = []
  loadAllocatedOrders()
}

async function loadAllocatedOrders() {
  const data = await outboundApi.page({ ...orderQuery, status: 1 })
  allocatedOrders.value = data.records
}

function onSelectOrders(rows) {
  selectedOrders.value = rows
}

async function submitGenerate() {
  if (selectedQty.value > 27) {
    ElMessage.error(`总件数 ${selectedQty.value} 超过载具容量 27 件，请减少订单`)
    return
  }
  submitting.value = true
  try {
    const wave = await waveApi.generate(selectedOrders.value.map(o => o.id))
    ElMessage.success(`波次 ${wave.waveNo} 生成成功：${wave.totalTasks} 个拣货任务`)
    generateVisible.value = false
    loadData()
  } catch (e) { /* 拦截器已提示 */ } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>
