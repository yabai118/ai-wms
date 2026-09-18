<template>
  <div>
    <!-- ========== 总览卡片 ========== -->
    <el-row :gutter="16">
      <el-col :span="3" v-for="s in statCards" :key="s.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :style="{ color: s.color, fontSize: '22px' }">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </el-card>
      </el-col>
      <el-col :span="9">
        <el-card shadow="never" style="height:100%">
          <div style="display:flex;align-items:center;justify-content:space-between;height:100%">
            <div style="font-size:12px;color:#909399;line-height:1.8">
              <b>五字段库存模型</b><br />
              现有量 = 货架上实际有多少（拣货时不变）<br />
              已分配 = 被订单占住　冻结 = 异常锁住
            </div>
            <el-button type="primary" plain size="small" @click="doReconcile">
              <el-icon><Checked /></el-icon> 库存对账
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ============ Tab 1：库存查询 ============ -->
        <el-tab-pane label="库存查询" name="stock">
          <div class="search-bar">
            <el-input v-model="query.skuCode" placeholder="SKU 编码" clearable style="width:180px"
                      @keyup.enter="handleSearch" />
            <el-input v-model="query.locationCode" placeholder="库位号" clearable style="width:150px"
                      @keyup.enter="handleSearch" />
            <el-checkbox v-model="query.onlyAvailable" @change="handleSearch">仅有可用库存</el-checkbox>
            <el-checkbox v-model="query.onlyAllocated" @change="handleSearch">仅有已分配</el-checkbox>
            <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 查询</el-button>
            <el-button @click="handleReset"><el-icon><Refresh /></el-icon> 重置</el-button>
          </div>

          <el-table :data="list" v-loading="loading" border stripe size="small">
            <el-table-column prop="skuCode" label="SKU 编码" width="140" />
            <el-table-column prop="reference" label="款号" width="100" />
            <el-table-column prop="sizeUs" label="尺码" width="70" align="center" />
            <el-table-column prop="locationCode" label="库位号" width="120" />
            <el-table-column prop="locationTypeName" label="区域" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain"
                        :type="row.locationTypeName === '拣货区' ? 'success' : 'info'">
                  {{ row.locationTypeName }}
                </el-tag>
              </template>
            </el-table-column>
            <!-- ★ 五字段 -->
            <el-table-column prop="qty" label="现有量" width="85" align="center">
              <template #default="{ row }">
                <span style="font-weight:600;color:#1f4e79">{{ row.qty }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="qtyAllocated" label="已分配" width="85" align="center">
              <template #default="{ row }">
                <span :style="{ color: row.qtyAllocated > 0 ? '#e6a23c' : '#c0c4cc' }">
                  {{ row.qtyAllocated }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="qtyPicked" label="已拣出" width="85" align="center">
              <template #default="{ row }">
                <span :style="{ color: row.qtyPicked > 0 ? '#409eff' : '#c0c4cc' }">
                  {{ row.qtyPicked }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="qtyOnhold" label="冻结" width="80" align="center">
              <template #default="{ row }">
                <span :style="{ color: row.qtyOnhold > 0 ? '#f56c6c' : '#c0c4cc' }">
                  {{ row.qtyOnhold }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="qtyAvailable" label="可用量" width="90" align="center">
              <template #default="{ row }">
                <span style="font-weight:600;color:#67c23a">{{ row.qtyAvailable }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button link type="warning" size="small" @click="openFreeze(row)">冻结</el-button>
                <el-button link type="success" size="small"
                           :disabled="row.qtyOnhold === 0"
                           @click="doUnfreeze(row)">解冻</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="query.pageNum"
              v-model:page-size="query.pageSize"
              :page-sizes="[20, 50, 100]"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadData"
              @current-change="loadData"
            />
          </div>
        </el-tab-pane>

        <!-- ============ Tab 2：库存流水 ============ -->
        <el-tab-pane label="库存流水（可追溯）" name="tx">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
            每一次库存变动都有记录，并标明<b>来源单据</b>——出问题时可以顺着查回去
          </el-alert>

          <el-table :data="txList" v-loading="txLoading" border stripe size="small">
            <el-table-column prop="id" label="ID" width="80" align="center" />
            <el-table-column prop="skuCode" label="SKU" width="140" />
            <el-table-column prop="locationCode" label="库位" width="110" />
            <el-table-column prop="qtyDelta" label="变动量" width="90" align="center">
              <template #default="{ row }">
                <span :style="{ color: row.qtyDelta > 0 ? '#67c23a' : row.qtyDelta < 0 ? '#f56c6c' : '#909399',
                                fontWeight: '600' }">
                  {{ row.qtyDelta > 0 ? '+' : '' }}{{ row.qtyDelta }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="bizTypeName" label="业务类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.bizTypeName }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="referenceType" label="来源单据" width="150" />
            <el-table-column prop="referenceId" label="来源ID" width="90" align="center" />
            <el-table-column prop="remark" label="备注" min-width="280" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="时间" width="170" />
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="txQuery.pageNum"
              v-model:page-size="txQuery.pageSize"
              :page-sizes="[20, 50, 100]"
              :total="txTotal"
              layout="total, sizes, prev, pager, next"
              @size-change="loadTx"
              @current-change="loadTx"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 冻结弹窗 -->
    <el-dialog v-model="freezeVisible" title="冻结库存" width="480px">
      <el-form label-width="80px" size="small">
        <el-form-item label="SKU">{{ current.skuCode }}</el-form-item>
        <el-form-item label="库位">{{ current.locationCode }}</el-form-item>
        <el-form-item label="当前可用">{{ current.qtyAvailable }}</el-form-item>
        <el-form-item label="冻结数量">
          <el-input-number v-model="freezeQty" :min="1" :max="current.qtyAvailable || 1" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="freezeReason" placeholder="如：质检不合格" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="freezeVisible = false">取消</el-button>
        <el-button type="warning" @click="submitFreeze">确认冻结</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { inventoryApi } from '@/api/inventory'

const activeTab = ref('stock')
const loading = ref(false)
const txLoading = ref(false)
const list = ref([])
const txList = ref([])
const total = ref(0)
const txTotal = ref(0)

const query = reactive({
  pageNum: 1, pageSize: 20,
  skuCode: '', locationCode: '', onlyAvailable: false, onlyAllocated: false
})
const txQuery = reactive({ pageNum: 1, pageSize: 20, skuCode: '' })

const statCards = ref([
  { label: '库存总量', value: '-', color: '#1f4e79' },
  { label: '已分配', value: '-', color: '#e6a23c' },
  { label: '已拣出', value: '-', color: '#409eff' },
  { label: '冻结', value: '-', color: '#f56c6c' },
  { label: '可用量', value: '-', color: '#67c23a' }
])

const freezeVisible = ref(false)
const current = ref({})
const freezeQty = ref(1)
const freezeReason = ref('')

async function loadStats() {
  const s = await inventoryApi.summary()
  statCards.value[0].value = s.totalQty ?? 0
  statCards.value[1].value = s.totalAllocated ?? 0
  statCards.value[2].value = s.totalPicked ?? 0
  statCards.value[3].value = s.totalOnhold ?? 0
  statCards.value[4].value = s.totalAvailable ?? 0
}

async function loadData() {
  loading.value = true
  try {
    const data = await inventoryApi.page(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadTx() {
  txLoading.value = true
  try {
    const data = await inventoryApi.transactions(txQuery)
    txList.value = data.records
    txTotal.value = data.total
  } finally {
    txLoading.value = false
  }
}

function onTabChange(name) {
  if (name === 'tx' && txList.value.length === 0) loadTx()
}

function handleSearch() { query.pageNum = 1; loadData() }
function handleReset() {
  query.skuCode = ''; query.locationCode = ''
  query.onlyAvailable = false; query.onlyAllocated = false
  query.pageNum = 1; loadData()
}

// ---------- 对账 ----------
async function doReconcile() {
  const r = await inventoryApi.reconcile()
  if (r.consistent) {
    ElNotification.success({ title: '库存对账通过', message: r.message })
  } else {
    ElNotification.warning({
      title: `库存对账发现 ${r.mismatchCount} 条不一致`,
      message: '请检查这些记录的流水',
      duration: 6000
    })
    console.warn('对账不一致明细:', r.mismatches)
  }
}

// ---------- 冻结 / 解冻 ----------
function openFreeze(row) {
  current.value = row
  freezeQty.value = 1
  freezeReason.value = ''
  freezeVisible.value = true
}

async function submitFreeze() {
  await inventoryApi.freeze(current.value.id, freezeQty.value, freezeReason.value)
  ElMessage.success('冻结成功')
  freezeVisible.value = false
  loadData(); loadStats()
}

async function doUnfreeze(row) {
  const { value } = await ElMessageBox.prompt(
    `当前冻结 ${row.qtyOnhold} 件，请输入解冻数量`, '解冻库存',
    { inputValue: String(row.qtyOnhold), inputPattern: /^\d+$/, inputErrorMessage: '请输入正整数' }
  )
  await inventoryApi.unfreeze(row.id, parseInt(value))
  ElMessage.success('解冻成功')
  loadData(); loadStats()
}

onMounted(() => { loadStats(); loadData() })
</script>
