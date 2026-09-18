<template>
  <div>
    <!-- ========== 说明 ========== -->
    <el-card shadow="never" style="margin-bottom:14px">
      <template #header>
        <div style="display:flex;align-items:center;gap:8px">
          <el-icon><UploadFilled /></el-icon>
          <span style="font-weight:600">数据初始化</span>
          <el-tag size="small" type="warning" effect="plain">WMS 上线第一步</el-tag>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon>
        <b>真实 WMS 的上线流程</b>：部署系统（空库）→ <b>数据初始化（就是本页）</b> → 日常运作
        <br />
        系统本身不带业务数据，商品 / 库位 / 期初库存由用户从 ERP 导出后通过 Excel 导入。
        <br />
        <b>建议顺序</b>：先导商品 → 再导库位 → 最后导期初库存（因为库存要引用前两者的编码）
      </el-alert>
    </el-card>

    <!-- ========== 三个导入卡片 ========== -->
    <el-row :gutter="14">
      <el-col :span="8" v-for="c in cards" :key="c.type">
        <el-card shadow="hover" class="imp-card">
          <template #header>
            <div style="display:flex;align-items:center;gap:8px">
              <el-icon><component :is="c.icon" /></el-icon>
              <span style="font-weight:600">{{ c.title }}</span>
              <el-tag size="small" effect="plain">{{ c.tag }}</el-tag>
            </div>
          </template>

          <div class="imp-desc">{{ c.desc }}</div>
          <div class="imp-fields">
            <div v-for="f in c.fields" :key="f.k" class="imp-field">
              <span class="f-key">{{ f.k }}</span>
              <span class="f-val">{{ f.v }}</span>
            </div>
          </div>

          <div class="imp-actions">
            <el-button size="small" @click="downloadTemplate(c.type)">
              <el-icon><Download /></el-icon> 下载模板
            </el-button>
            <el-upload
              :show-file-list="false"
              :before-upload="(f) => beforeUpload(f, c)"
              accept=".xlsx,.xls"
              style="display:inline-block;margin-left:8px"
            >
              <el-button size="small" type="primary" :loading="uploading === c.type">
                <el-icon><Upload /></el-icon> 上传导入
              </el-button>
            </el-upload>
          </div>

          <!-- 单项结果 -->
          <div v-if="results[c.type]" class="imp-result">
            <el-divider style="margin:12px 0" />
            <div class="res-row">
              <span>成功 <b style="color:#006300">{{ results[c.type].successRows }}</b></span>
              <span>跳过 <b style="color:#898781">{{ results[c.type].skippedRows }}</b></span>
              <span>失败 <b :style="{ color: results[c.type].failedRows ? '#d03b3b' : '#898781' }">
                {{ results[c.type].failedRows }}</b></span>
            </div>
            <el-table v-if="results[c.type].errors?.length" :data="results[c.type].errors"
                      size="small" border max-height="160" style="margin-top:8px">
              <el-table-column prop="row" label="行" width="60" align="center" />
              <el-table-column prop="message" label="原因" show-overflow-tooltip />
            </el-table>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { importApi } from '@/api/import'

const uploading = ref('')
const results = reactive({})

const cards = [
  {
    type: 'product',
    title: '商品导入',
    tag: '款 + 尺码',
    icon: 'Goods',
    desc: '一行一个 SKU。同一个款号多行表示多个尺码；SKU 编码留空则自动生成。',
    fields: [
      { k: '款号*', v: '8N10W9' },
      { k: 'ABC分类*', v: 'A / B / C' },
      { k: '尺码*', v: '11' },
      { k: 'SKU编码', v: '留空自动生成' },
    ],
  },
  {
    type: 'location',
    title: '库位导入',
    tag: '库区 + 坐标',
    icon: 'Grid',
    desc: '库区不存在会自动创建；坐标为可选（用于路径优化和地图可视化）。',
    fields: [
      { k: '库位号*', v: 'A-14-11' },
      { k: '库区*', v: 'A' },
      { k: '类型*', v: '拣货区 / 存储区' },
      { k: '坐标/容量', v: '可选' },
    ],
  },
  {
    type: 'inventory',
    title: '期初库存导入',
    tag: '上线初始化',
    icon: 'Coin',
    desc: '把仓库现有库存录入系统。导入时会自动写一条 RECEIPT 流水，保证库存与流水能对上账。',
    fields: [
      { k: 'SKU编码*', v: '8N10W9-11' },
      { k: '库位号*', v: 'A-14-11' },
      { k: '数量*', v: '100' },
    ],
  },
]

function downloadTemplate(type) {
  window.open(importApi.templateUrl(type), '_blank')
}

async function beforeUpload(file, card) {
  uploading.value = card.type
  try {
    const r = await importApi.upload(card.type, file)
    results[card.type] = r
    if (r.failedRows > 0) {
      ElMessage.warning(`导入完成，但有 ${r.failedRows} 行失败，请看错误明细`)
    } else {
      ElMessage.success(`导入成功：新增 ${r.successRows} 行，跳过 ${r.skippedRows} 行`)
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    uploading.value = ''
  }
  return false      // 阻止 el-upload 自动上传
}
</script>

<style scoped>
.imp-card { height: 100%; }
.imp-desc {
  font-size: 12px;
  color: #52514e;
  line-height: 1.7;
  min-height: 54px;
  margin-bottom: 8px;
}
.imp-fields {
  background: #fafaf8;
  border: 1px solid #e1e0d9;
  border-radius: 4px;
  padding: 8px 10px;
  margin-bottom: 12px;
}
.imp-field {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  line-height: 1.9;
}
.f-key { color: #52514e; }
.f-val { color: #898781; font-family: Consolas, Monaco, monospace; }
.imp-actions { display: flex; align-items: center; }
.res-row {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #52514e;
}
</style>
