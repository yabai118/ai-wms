<template>
  <div v-loading="loading">
    <!-- ========== 顶部 KPI 卡片 ========== -->
    <el-row :gutter="14">
      <el-col :span="4" v-for="k in kpis" :key="k.label">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-value">{{ k.value }}</div>
          <div class="kpi-label">{{ k.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ========== 趋势 + ABC ========== -->
    <el-row :gutter="14">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">订单趋势</span>
              <span class="card-sub">按天统计（真实数据 2023 年 · 最近 60 天）</span>
            </div>
          </template>
          <div ref="trendRef" class="chart" style="height:280px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">ABC 分类分布</span>
              <span class="card-sub">按商品款数</span>
            </div>
          </template>
          <div ref="abcRef" class="chart" style="height:280px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ========== 库区占用 + 库存 TOP10 ========== -->
    <el-row :gutter="14">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">库区库位分布</span>
              <span class="card-sub">18 个库区 · 拣货区 / 存储区</span>
            </div>
          </template>
          <div ref="areaRef" class="chart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-title">库存 TOP10 SKU</span>
              <span class="card-sub">按库存量</span>
            </div>
          </template>
          <div ref="stockRef" class="chart" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, nextTick } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/dashboard'

/* ============================================================
   配色：来自 dataviz 技能的参考调色板，已通过验证脚本
   - 分类色槽 1/2/3：#2a78d6 / #eb6834 / #1baf7a
   - 验证结果：CVD ΔE 9.2 PASS，正常视觉 ΔE 27.6 PASS
   - 注意：青色 #1baf7a 对比度 2.74:1（低于 3:1），
     按技能的「补偿规则」必须给该系列加**可见数值标签**
   ============================================================ */
const C = {
  s1: '#2a78d6',   // 蓝 —— 主系列 / A类 / 拣货区
  s2: '#eb6834',   // 橙 —— B类 / 存储区
  s3: '#1baf7a',   // 青 —— C类（需数值标签补偿）
  ink: '#0b0b0b',      // 主文字
  ink2: '#52514e',     // 次文字
  muted: '#898781',    // 轴线/标签
  grid: '#e1e0d9',     // 网格线
  axis: '#c3c2b7'      // 基线
}

const FONT = 'system-ui, -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif'

const loading = ref(true)
const trendRef = ref(null)
const abcRef = ref(null)
const areaRef = ref(null)
const stockRef = ref(null)
const charts = []

const kpis = ref([
  { label: '商品款数', value: '-' },
  { label: 'SKU 数', value: '-' },
  { label: '库位数', value: '-' },
  { label: '订单数', value: '-' },
  { label: '拣货波次', value: '-' },
  { label: '库存总量', value: '-' }
])

const fmt = (n) => (n == null ? '-' : Number(n).toLocaleString('en-US'))

function baseOpt() {
  return {
    textStyle: { fontFamily: FONT },
    grid: { left: 56, right: 24, top: 24, bottom: 40, containLabel: false },
    tooltip: {
      backgroundColor: '#fff',
      borderColor: C.axis,
      borderWidth: 1,
      textStyle: { color: C.ink, fontSize: 12, fontFamily: FONT },
      extraCssText: 'box-shadow:0 2px 12px rgba(0,0,0,.08);border-radius:4px;'
    }
  }
}

function axisX(data) {
  return {
    type: 'category',
    data,
    axisLine: { lineStyle: { color: C.axis } },
    axisTick: { show: false },
    axisLabel: { color: C.muted, fontSize: 11, fontFamily: FONT }
  }
}

function axisY(name) {
  return {
    type: 'value',
    name,
    nameTextStyle: { color: C.muted, fontSize: 11, fontFamily: FONT },
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: C.muted, fontSize: 11, fontFamily: FONT },
    splitLine: { lineStyle: { color: C.grid, width: 1 } }
  }
}

/* ---------- ① 订单趋势（折线，单系列，无需图例） ---------- */
function drawTrend(data) {
  const ch = echarts.init(trendRef.value)
  charts.push(ch)
  ch.setOption({
    ...baseOpt(),
    tooltip: { ...baseOpt().tooltip, trigger: 'axis', axisPointer: { type: 'line', lineStyle: { color: C.axis } } },
    grid: { left: 60, right: 24, top: 20, bottom: 34 },
    xAxis: { ...axisX(data.map(d => d.date)), axisLabel: { color: C.muted, fontSize: 10, rotate: 40, fontFamily: FONT } },
    yAxis: axisY('订单数'),
    series: [{
      name: '订单数',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color: C.s1 },
      itemStyle: { color: C.s1 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(42,120,214,0.22)' },
          { offset: 1, color: 'rgba(42,120,214,0.02)' }
        ])
      },
      data: data.map(d => d.orderCount)
    }]
  })
}

/* ---------- ② ABC 分类（柱状，3 色，青色加数值标签补偿） ---------- */
function drawAbc(data) {
  const NAME = { A: 'A 类（高频）', B: 'B 类（中频）', C: 'C 类（低频）' }
  const COLOR = { A: C.s1, B: C.s2, C: C.s3 }
  const ch = echarts.init(abcRef.value)
  charts.push(ch)
  ch.setOption({
    ...baseOpt(),
    grid: { left: 56, right: 24, top: 24, bottom: 34 },
    tooltip: {
      ...baseOpt().tooltip, trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (ps) => {
        const d = data.find(x => x.abc === ps[0].name.slice(0, 1))
        return `<b>${ps[0].name}</b><br/>商品款数：${d.productCount}<br/>SKU 数：${d.skuCount}<br/>库存量：${fmt(d.stockQty)}`
      }
    },
    xAxis: axisX(data.map(d => NAME[d.abc] || d.abc)),
    yAxis: axisY('商品款数'),
    series: [{
      type: 'bar',
      barWidth: '46%',
      itemStyle: {
        // 4px 圆角（技能要求：数据端圆角，底部贴基线）
        borderRadius: [4, 4, 0, 0],
        color: (p) => COLOR[data[p.dataIndex].abc]
      },
      // ★ 数值标签 —— 青色对比度不足的补偿（技能要求：relief rule）
      label: {
        show: true, position: 'top',
        color: C.ink, fontSize: 12, fontWeight: 600, fontFamily: FONT
      },
      data: data.map(d => d.productCount)
    }]
  })
}

/* ---------- ③ 库区占用（堆叠柱，2 系列，含图例） ---------- */
function drawArea(data) {
  const ch = echarts.init(areaRef.value)
  charts.push(ch)
  ch.setOption({
    ...baseOpt(),
    grid: { left: 56, right: 24, top: 40, bottom: 34 },
    legend: {
      top: 6, right: 8, itemWidth: 12, itemHeight: 12,
      textStyle: { color: C.ink2, fontSize: 12, fontFamily: FONT }
    },
    tooltip: {
      ...baseOpt().tooltip, trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (ps) => {
        const d = data.find(x => x.area === ps[0].name)
        return `<b>${d.area} 区</b><br/>库位总数：${d.total}<br/>拣货区：${d.pickCount}<br/>存储区：${d.storeCount}`
      }
    },
    xAxis: axisX(data.map(d => d.area)),
    yAxis: axisY('库位数'),
    series: [
      {
        name: '拣货区', type: 'bar', stack: 'loc', barWidth: '58%',
        itemStyle: { color: C.s1 },
        data: data.map(d => d.pickCount)
      },
      {
        name: '存储区', type: 'bar', stack: 'loc',
        itemStyle: {
          color: C.s2,
          // 堆叠段之间留 2px 背景色间隙（技能要求）
          borderColor: '#fcfcfb', borderWidth: 2, borderType: 'solid'
        },
        data: data.map(d => d.storeCount)
      }
    ]
  })
}

/* ---------- ④ 库存 TOP10（横向柱，单系列单色） ---------- */
function drawStock(data) {
  const ch = echarts.init(stockRef.value)
  charts.push(ch)
  const rev = [...data].reverse()
  ch.setOption({
    ...baseOpt(),
    grid: { left: 100, right: 46, top: 16, bottom: 30 },
    tooltip: {
      ...baseOpt().tooltip, trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (ps) => `<b>${ps[0].name}</b><br/>库存量：${ps[0].value} 件`
    },
    xAxis: { ...axisY(''), splitLine: { lineStyle: { color: C.grid } } },
    yAxis: {
      type: 'category',
      data: rev.map(d => d.skuCode),
      axisLine: { lineStyle: { color: C.axis } },
      axisTick: { show: false },
      axisLabel: { color: C.ink2, fontSize: 11, fontFamily: FONT }
    },
    series: [{
      type: 'bar',
      barWidth: '56%',
      itemStyle: { color: C.s1, borderRadius: [0, 4, 4, 0] },   // 横向柱：右端圆角
      label: { show: true, position: 'right', color: C.ink2, fontSize: 11, fontFamily: FONT },
      data: rev.map(d => d.qty)
    }]
  })
}

async function load() {
  loading.value = true
  try {
    const d = await dashboardApi.get()
    const s = d.summary
    kpis.value[0].value = fmt(s.productCount)
    kpis.value[1].value = fmt(s.skuCount)
    kpis.value[2].value = fmt(s.locationCount)
    kpis.value[3].value = fmt(s.orderCount)
    kpis.value[4].value = fmt(s.waveCount)
    kpis.value[5].value = fmt(s.totalStock)

    await nextTick()
    drawTrend(d.orderTrend || [])
    drawAbc(d.abcDistribution || [])
    drawArea(d.areaUsage || [])
    drawStock(d.topStock || [])
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function onResize() { charts.forEach(c => c.resize()) }

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach(c => c.dispose())
})
</script>

<style scoped>
.kpi-card {
  text-align: center;
  margin-bottom: 14px;
}
.kpi-value {
  font-size: 26px;
  font-weight: 600;
  color: #0b0b0b;
  line-height: 1.3;
}
.kpi-label {
  font-size: 12px;
  color: #898781;
  margin-top: 2px;
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #0b0b0b;
}
.card-sub {
  font-size: 12px;
  color: #898781;
}
.chart {
  width: 100%;
}
</style>
