<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><Guide /></el-icon>
            <span style="font-weight:600">拣货路径优化</span>
            <el-tag size="small" type="success" effect="plain">Python Agent</el-tag>
          </div>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="waveId" placeholder="选择待拣货的波次" style="width:240px"
                       filterable size="small" @change="load">
              <el-option
                v-for="w in waves"
                :key="w.id"
                :label="`${w.waveNo}  (${w.totalTasks} 个任务 / ${w.totalQty} 件)`"
                :value="w.id"
              />
            </el-select>
            <el-button type="primary" size="small" :loading="loading" @click="load">
              <el-icon><Refresh /></el-icon> 重新优化
            </el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!result" description="请选择一个波次，查看路径优化对比" />

      <template v-else>
        <!-- ========== 策略对比卡片 ========== -->
        <div style="display:flex;gap:12px;margin-bottom:16px">
          <div v-for="r in result.routes" :key="r.strategy"
               class="strategy-card" :class="{ best: r.strategy === bestKey }">
            <div class="sc-head">
              <span class="sc-name">{{ r.strategyName }}</span>
              <el-tag v-if="r.strategy === bestKey" size="small" type="success" effect="dark">
                最优
              </el-tag>
            </div>
            <div class="sc-value">{{ r.totalDistance }} <span class="sc-unit">米</span></div>
            <div class="sc-diff" :class="diffClass(r)">
              {{ diffText(r) }}
            </div>
            <div class="sc-detail">
              通道 {{ r.corridorDistance }} · 横向 {{ r.horizontalDistance }} · 取货 {{ r.depthDistance }}
            </div>
          </div>
        </div>

        <!-- ========== 路径可视化 + 对比图 ========== -->
        <el-row :gutter="14">
          <el-col :span="13">
            <el-card shadow="never">
              <template #header>
                <div class="card-head">
                  <span class="card-title">最优路径</span>
                  <span class="card-sub">{{ bestRoute.strategyName }} · 按拣货顺序连线</span>
                </div>
              </template>
              <div ref="pathRef" style="height:420px"></div>
            </el-card>
          </el-col>
          <el-col :span="11">
            <el-card shadow="never">
              <template #header>
                <div class="card-head">
                  <span class="card-title">策略对比</span>
                  <span class="card-sub">总行走距离（米）</span>
                </div>
              </template>
              <div ref="barRef" style="height:420px"></div>
            </el-card>
          </el-col>
        </el-row>

        <!-- ========== 拣货顺序表 ========== -->
        <el-card shadow="never" style="margin-top:14px">
          <template #header>
            <div class="card-head">
              <span class="card-title">最优拣货顺序</span>
              <span class="card-sub">{{ bestRoute.sequence.length }} 个任务</span>
            </div>
          </template>
          <el-table :data="bestRoute.sequence" border stripe size="small" max-height="320">
            <el-table-column prop="seq" label="顺序" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.seq === 1 ? 'success' : 'info'" effect="plain">
                  {{ row.seq }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="locationCode" label="库位号" width="120" />
            <el-table-column label="坐标" width="140" align="center">
              <template #default="{ row }">
                <span style="font-family:monospace">({{ row.x }}, {{ row.y }})</span>
              </template>
            </el-table-column>
            <el-table-column prop="corridor" label="归属通道" width="110" align="center">
              <template #default="{ row }">x = {{ row.corridor }}</template>
            </el-table-column>
            <el-table-column prop="depth" label="取货深度" width="100" align="center">
              <template #default="{ row }">{{ row.depth }} 米</template>
            </el-table-column>
            <el-table-column prop="skuCode" label="SKU 编码" />
            <el-table-column prop="qty" label="数量" width="80" align="center" />
          </el-table>
        </el-card>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, nextTick } from 'vue'
import * as echarts from 'echarts'
import { agentApi } from '@/api/agent'
import { waveApi } from '@/api/wave'

/* 配色：与项目其它图表一致，已通过 dataviz 验证脚本 */
const C = {
  s1: '#2a78d6', s2: '#eb6834', s3: '#1baf7a', s4: '#eda100',
  ink: '#0b0b0b', ink2: '#52514e', muted: '#898781',
  grid: '#e1e0d9', axis: '#c3c2b7'
}
const FONT = 'system-ui, -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif'
/** 策略 → 固定颜色（颜色跟随「策略」这个实体，不随排名变化） */
const STRATEGY_COLOR = {
  baseline: C.muted,
  return: C.s2,
  s_shape: C.s1,
  largest_gap: C.s3
}

const loading = ref(false)
const waveId = ref(null)
const waves = ref([])
const result = ref(null)
const pathRef = ref(null)
const barRef = ref(null)
let pathChart = null
let barChart = null

const bestKey = computed(() =>
  result.value ? result.value.routes.find(r => r.totalDistance === result.value.bestDistance)?.strategy : '')
const bestRoute = computed(() =>
  result.value ? result.value.routes.find(r => r.strategy === bestKey.value) : null)

function diffText(r) {
  if (!result.value) return ''
  const base = result.value.baselineDistance
  if (r.strategy === 'baseline') return '基准'
  const p = 100 * (r.totalDistance - base) / base
  return `${p > 0 ? '+' : ''}${p.toFixed(1)}%`
}
function diffClass(r) {
  if (!result.value || r.strategy === 'baseline') return 'neutral'
  return r.totalDistance < result.value.baselineDistance ? 'good' : 'bad'
}

async function loadWaves() {
  const data = await waveApi.page({ pageNum: 1, pageSize: 50, status: 0 })
  waves.value = data.records
  if (waves.value.length && !waveId.value) {
    waveId.value = waves.value[0].id
    load()
  }
}

async function load() {
  if (!waveId.value) return
  loading.value = true
  try {
    result.value = await agentApi.compareWave(waveId.value)
    await nextTick()
    drawPath()
    drawBar()
  } finally {
    loading.value = false
  }
}

/* ---------- 路径图：把货位按拣货顺序连起来 ---------- */
function drawPath() {
  if (!bestRoute.value) return
  pathChart = pathChart || echarts.init(pathRef.value)
  const seq = bestRoute.value.sequence

  // 起点
  const points = [[66, 0], ...seq.map(t => [t.x, t.y])]
  const labels = ['起点', ...seq.map(t => `${t.seq}`)]

  pathChart.setOption({
    textStyle: { fontFamily: FONT },
    grid: { left: 66, right: 30, top: 30, bottom: 46 },
    tooltip: {
      backgroundColor: '#fff', borderColor: C.axis, borderWidth: 1,
      textStyle: { color: C.ink, fontSize: 12, fontFamily: FONT },
      formatter: (p) => {
        if (p.seriesType === 'line') return null
        const i = p.dataIndex
        if (i === 0) return '<b>起点</b>'
        const t = seq[i - 1]
        return `<b>第 ${t.seq} 站：${t.locationCode}</b><br/>坐标：(${t.x}, ${t.y})<br/>SKU：${t.skuCode}`
      }
    },
    xAxis: {
      type: 'value', name: 'X 坐标（米）', nameLocation: 'middle', nameGap: 28,
      scale: true, nameTextStyle: { color: C.muted, fontSize: 11, fontFamily: FONT },
      axisLine: { lineStyle: { color: C.axis } },
      axisLabel: { color: C.muted, fontSize: 11, fontFamily: FONT },
      splitLine: { lineStyle: { color: C.grid } }
    },
    yAxis: {
      type: 'value', name: 'Y 坐标（米）', nameLocation: 'middle', nameGap: 44,
      scale: true, nameTextStyle: { color: C.muted, fontSize: 11, fontFamily: FONT },
      axisLine: { lineStyle: { color: C.axis } },
      axisLabel: { color: C.muted, fontSize: 11, fontFamily: FONT },
      splitLine: { lineStyle: { color: C.grid } }
    },
    series: [
      {
        // 拣货路线
        type: 'line',
        data: points,
        symbol: 'none',
        lineStyle: { width: 2, color: C.s1, opacity: 0.75, curveness: 0 },
        z: 2
      },
      {
        // 站点
        type: 'scatter',
        data: points,
        symbolSize: (v, p) => (p.dataIndex === 0 ? 14 : 10),
        itemStyle: {
          color: (p) => (p.dataIndex === 0 ? C.s2 : C.s1),
          borderColor: '#fff', borderWidth: 2      // 2px 背景色描边（技能要求）
        },
        label: {
          show: true,
          position: 'top',
          formatter: (p) => labels[p.dataIndex],
          color: C.ink2, fontSize: 11, fontFamily: FONT
        },
        z: 3
      }
    ],
    // 三条通道参考线
    markLine: undefined
  }, true)
}

/* ---------- 对比柱状图 ---------- */
function drawBar() {
  barChart = barChart || echarts.init(barRef.value)
  const routes = result.value.routes
  barChart.setOption({
    textStyle: { fontFamily: FONT },
    grid: { left: 70, right: 46, top: 20, bottom: 60 },
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: '#fff', borderColor: C.axis, borderWidth: 1,
      textStyle: { color: C.ink, fontSize: 12, fontFamily: FONT },
      formatter: (ps) => {
        const r = routes[ps[0].dataIndex]
        return `<b>${r.strategyName}</b><br/>总距离：${r.totalDistance} 米<br/>
                通道内：${r.corridorDistance} 米<br/>横向：${r.horizontalDistance} 米<br/>
                取货深度：${r.depthDistance} 米`
      }
    },
    xAxis: {
      type: 'category',
      data: routes.map(r => r.strategyName.replace('（基线）', '\n(基线)')),
      axisLine: { lineStyle: { color: C.axis } },
      axisTick: { show: false },
      axisLabel: { color: C.ink2, fontSize: 11, lineHeight: 15, fontFamily: FONT }
    },
    yAxis: {
      type: 'value', name: '米',
      nameTextStyle: { color: C.muted, fontSize: 11, fontFamily: FONT },
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: C.muted, fontSize: 11, fontFamily: FONT },
      splitLine: { lineStyle: { color: C.grid } }
    },
    series: [{
      type: 'bar',
      barWidth: '44%',
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: (p) => STRATEGY_COLOR[routes[p.dataIndex].strategy]
      },
      label: { show: true, position: 'top', color: C.ink, fontSize: 12, fontWeight: 600, fontFamily: FONT },
      data: routes.map(r => r.totalDistance)
    }]
  }, true)
}

function onResize() {
  pathChart?.resize()
  barChart?.resize()
}

onMounted(() => {
  loadWaves()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  pathChart?.dispose()
  barChart?.dispose()
})
</script>

<style scoped>
.strategy-card {
  flex: 1;
  border: 1px solid #e1e0d9;
  border-radius: 6px;
  padding: 14px 16px;
  background: #fff;
}
.strategy-card.best {
  border-color: #1baf7a;
  box-shadow: 0 0 0 2px rgba(27, 175, 122, 0.12);
}
.sc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.sc-name {
  font-size: 13px;
  color: #52514e;
}
.sc-value {
  font-size: 24px;
  font-weight: 600;
  color: #0b0b0b;
}
.sc-unit {
  font-size: 13px;
  font-weight: 400;
  color: #898781;
}
.sc-diff {
  font-size: 13px;
  font-weight: 600;
  margin-top: 2px;
}
.sc-diff.good { color: #006300; }
.sc-diff.bad { color: #d03b3b; }
.sc-diff.neutral { color: #898781; }
.sc-detail {
  font-size: 11px;
  color: #898781;
  margin-top: 6px;
}
.card-head { display: flex; align-items: baseline; gap: 10px; }
.card-title { font-size: 15px; font-weight: 600; color: #0b0b0b; }
.card-sub { font-size: 12px; color: #898781; }
</style>
