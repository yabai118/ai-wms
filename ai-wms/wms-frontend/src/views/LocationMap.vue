<template>
  <div>
    <!-- ========== 统计卡片 ========== -->
    <el-row :gutter="16">
      <el-col :span="4" v-for="s in stats" :key="s.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" style="height:100%">
          <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
            <span style="font-weight:600;font-size:13px">显示：</span>
            <el-radio-group v-model="filterType" size="small" @change="loadMap">
              <el-radio-button :value="null">全部</el-radio-button>
              <el-radio-button :value="1">拣货区</el-radio-button>
              <el-radio-button :value="0">存储区</el-radio-button>
            </el-radio-group>
          </div>
          <div style="margin-top:10px;font-size:12px;color:#909399;line-height:1.7">
            <el-icon><InfoFilled /></el-icon>
            鼠标滚轮缩放，拖拽平移。悬停查看库位号与坐标。
            <br />
            数据来源：企业真实 WMS 导出的三维坐标（单位：米）
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ========== 仓库平面图 ========== -->
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><MapLocation /></el-icon>
            <span style="font-weight:600">仓库库位分布图</span>
            <el-tag size="small" type="success" effect="plain">真实坐标</el-tag>
          </div>
          <span style="font-size:12px;color:#909399">
            共 {{ points.length }} 个点位
          </span>
        </div>
      </template>

      <div ref="chartRef" v-loading="loading" style="width:100%;height:640px"></div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, nextTick } from 'vue'
import * as echarts from 'echarts'
import { locationApi } from '@/api/location'

const chartRef = ref(null)
const loading = ref(false)
const points = ref([])
const filterType = ref(null)

const stats = ref([
  { label: '库位总数', value: '-' },
  { label: '存储区', value: '-' },
  { label: '拣货区', value: '-' },
  { label: '库区数', value: '-' }
])

let chart = null

/** 库位类型 → 颜色与名称 */
const TYPE_META = {
  0: { name: '存储区', color: '#909399' },
  1: { name: '拣货区', color: '#409eff' },
  2: { name: '收货区', color: '#67c23a' },
  3: { name: '发货区', color: '#e6a23c' }
}

async function loadMap() {
  loading.value = true
  try {
    points.value = await locationApi.mapPoints(filterType.value)
    renderChart()
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  const [types, areas] = await Promise.all([
    locationApi.types(),
    locationApi.areas()
  ])
  const total = await locationApi.count()
  const typeMap = Object.fromEntries(types.map(t => [t.type, t.cnt]))
  stats.value[0].value = total
  stats.value[1].value = typeMap[0] || 0
  stats.value[2].value = typeMap[1] || 0
  stats.value[3].value = areas.length
}

function renderChart() {
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  // 按类型分组（每类一个 series，便于图例与配色）
  const grouped = {}
  points.value.forEach(p => {
    const t = p.type ?? 1
    if (!grouped[t]) grouped[t] = []
    grouped[t].push([p.x, p.y, p.code, p.z, p.area])
  })

  const series = Object.entries(grouped).map(([type, data]) => {
    const meta = TYPE_META[type] || { name: '其他', color: '#c0c4cc' }
    return {
      name: `${meta.name} (${data.length})`,
      type: 'scatter',
      symbolSize: 9,
      data,
      itemStyle: { color: meta.color, opacity: 0.85 },
      emphasis: { itemStyle: { borderColor: '#f56c6c', borderWidth: 2 } }
    }
  })

  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const [x, y, code, z, area] = params.data
        return `<b>库位 ${code}</b><br/>
                库区：${area}<br/>
                坐标：(${x}, ${y})　层：${z}`
      }
    },
    legend: { top: 8, left: 'center', itemWidth: 12, itemHeight: 12 },
    grid: { left: 70, right: 40, top: 50, bottom: 60 },
    xAxis: {
      name: 'X 坐标（米）',
      nameLocation: 'middle',
      nameGap: 30,
      type: 'value',
      scale: true,
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    yAxis: {
      name: 'Y 坐标（米）',
      nameLocation: 'middle',
      nameGap: 45,
      type: 'value',
      scale: true,
      splitLine: { lineStyle: { color: '#f0f0f0' } }
    },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0, yAxisIndex: 0, zoomOnMouseWheel: true, moveOnMouseMove: true },
      { type: 'slider', xAxisIndex: 0, bottom: 10, height: 18 },
      { type: 'slider', yAxisIndex: 0, right: 10, width: 18 }
    ],
    series
  }, true)
}

function handleResize() {
  chart?.resize()
}

onMounted(async () => {
  await nextTick()
  await Promise.all([loadStats(), loadMap()])
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>
