<template>
  <div>
    <!-- ========== 顶部统计卡片 ========== -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ========== 服务状态 ========== -->
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;gap:8px">
          <el-icon><Monitor /></el-icon>
          <span style="font-weight:600">服务状态</span>
          <el-tag :type="health.status === 'UP' ? 'success' : 'danger'" size="small">
            {{ health.status || '检测中' }}
          </el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="后端服务">{{ health.service || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Java 版本">{{ health.javaVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="服务时间">{{ health.time || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- ========== 数据来源说明 ========== -->
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;gap:8px">
          <el-icon><Document /></el-icon>
          <span style="font-weight:600">数据来源</span>
          <el-tag type="success" size="small" effect="plain">真实企业数据</el-tag>
        </div>
      </template>

      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="数据来源">
          鞋类制造企业 WMS 导出
        </el-descriptions-item>
        <el-descriptions-item label="商品款">208 个</el-descriptions-item>
        <el-descriptions-item label="SKU">2,515 个</el-descriptions-item>
        <el-descriptions-item label="库位">2,314 个</el-descriptions-item>
        <el-descriptions-item label="订单">32,634 个</el-descriptions-item>
        <el-descriptions-item label="拣货波次">9,707 个</el-descriptions-item>
        <el-descriptions-item label="时间跨度">286 天</el-descriptions-item>
        <el-descriptions-item label="拣货员">24 人</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { productApi } from '@/api/product'
import { healthApi } from '@/api/health'

const health = ref({})
const stats = ref([
  { label: '商品款数', value: '-' },
  { label: 'SKU 数', value: '2,515' },
  { label: '库位数', value: '2,314' },
  { label: '订单数', value: '32,634' }
])

onMounted(async () => {
  // 探活
  try {
    health.value = await healthApi.check()
  } catch (e) {
    health.value = { status: 'DOWN' }
  }

  // 商品款数（真实数据）
  try {
    const count = await productApi.count()
    stats.value[0].value = count
  } catch (e) {
    console.error(e)
  }
})
</script>
