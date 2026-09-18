<template>
  <div v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><Monitor /></el-icon>
            <span style="font-weight:600">Agent 服务监控</span>
            <el-tag size="small" type="success" effect="plain">Python Agent</el-tag>
          </div>
          <el-button size="small" :loading="loading" @click="refresh">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <!-- ========== 服务状态 ========== -->
      <el-row :gutter="14">
        <el-col :span="6">
          <el-card shadow="hover" class="status-card">
            <div class="status-row">
              <span class="status-label">Java 业务服务</span>
              <el-tag :type="services.java ? 'success' : 'danger'" size="small" effect="dark">
                {{ services.java ? 'UP' : 'DOWN' }}
              </el-tag>
            </div>
            <div class="status-sub">localhost:8080</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="status-card">
            <div class="status-row">
              <span class="status-label">Python Agent</span>
              <el-tag :type="services.python ? 'success' : 'danger'" size="small" effect="dark">
                {{ services.python ? 'UP' : 'DOWN' }}
              </el-tag>
            </div>
            <div class="status-sub">localhost:8000</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="status-card">
            <div class="status-row">
              <span class="status-label">LLM 服务</span>
              <el-tag :type="llm.available ? 'success' : 'info'" size="small" effect="dark">
                {{ llm.available ? '已启用' : '未配置' }}
              </el-tag>
            </div>
            <div class="status-sub">
              {{ llm.available ? llm.model : '异常解释走规则桩' }}
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="status-card">
            <div class="status-row">
              <span class="status-label">规则兜底</span>
              <el-tag type="warning" size="small" effect="dark">已就绪</el-tag>
            </div>
            <div class="status-sub">4 个模块均有规则桩</div>
          </el-card>
        </el-col>
      </el-row>

      <el-tabs v-model="tab" style="margin-top:8px">
        <!-- ================= Tab 1：运行监控 ================= -->
        <el-tab-pane label="运行监控" name="monitor">
          <!-- 核心指标 -->
          <el-row :gutter="14">
            <el-col :span="6">
              <el-card shadow="hover" class="kpi">
                <div class="kpi-value">{{ stats.total }}</div>
                <div class="kpi-label">总调用次数</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="kpi">
                <div class="kpi-value" style="color:#006300">{{ stats.successRate }}%</div>
                <div class="kpi-label">成功率</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="kpi">
                <div class="kpi-value"
                     :style="{ color: stats.degradeRate > 10 ? '#d03b3b' : '#e6a23c' }">
                  {{ stats.degradeRate }}%
                </div>
                <div class="kpi-label">降级率（{{ stats.degraded }} 次）</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="kpi">
                <div class="kpi-value">{{ stats.avgElapsedMs }} <span class="unit">ms</span></div>
                <div class="kpi-label">平均耗时</div>
              </el-card>
            </el-col>
          </el-row>

          <el-alert type="info" :closable="false" show-icon style="margin:12px 0">
            <b>降级率</b>是这套设计的关键指标——它证明三级降级<b>真在运行、可观测</b>。
            算法异常/超时/结果不合法时自动切换到规则桩，业务不中断。
          </el-alert>

          <!-- 各任务类型统计 -->
          <el-card shadow="never" style="margin-bottom:14px">
            <template #header><span class="card-title">各任务类型统计</span></template>
            <el-table :data="typeRows" border stripe size="small">
              <el-table-column prop="type" label="任务类型" width="160" />
              <el-table-column prop="count" label="调用次数" width="110" align="center" />
              <el-table-column prop="success" label="成功" width="90" align="center" />
              <el-table-column prop="degraded" label="降级" width="90" align="center">
                <template #default="{ row }">
                  <span :style="{ color: row.degraded > 0 ? '#e6a23c' : '#c0c4cc' }">
                    {{ row.degraded }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="failed" label="失败" width="90" align="center" />
              <el-table-column prop="successRate" label="成功率" width="110" align="center">
                <template #default="{ row }">{{ row.successRate }}%</template>
              </el-table-column>
              <el-table-column prop="avgMs" label="平均耗时" align="center">
                <template #default="{ row }">{{ row.avgMs }} ms</template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 最近调用记录 -->
          <el-card shadow="never">
            <template #header>
              <div class="card-head">
                <span class="card-title">最近调用记录</span>
                <span class="card-sub">最多保留 200 条（内存）</span>
              </div>
            </template>
            <el-table :data="stats.recent" border stripe size="small" max-height="320">
              <el-table-column prop="calledAt" label="时间" width="170" />
              <el-table-column prop="taskId" label="任务 ID" width="130">
                <template #default="{ row }">
                  <span style="font-family:monospace;font-size:12px">{{ row.taskId }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="taskType" label="类型" width="110" />
              <el-table-column prop="status" label="状态" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)" size="small" effect="dark">
                    {{ statusName(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="handler" label="处理器" width="180" />
              <el-table-column prop="elapsedMs" label="耗时" width="90" align="center">
                <template #default="{ row }">{{ row.elapsedMs }} ms</template>
              </el-table-column>
              <el-table-column prop="fallbackReason" label="降级原因" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.fallbackReason" style="color:#d03b3b;font-size:12px">
                    {{ row.fallbackReason }}
                  </span>
                  <span v-else style="color:#c0c4cc">—</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 故障演练 -->
          <el-card shadow="never" style="margin-top:14px">
            <template #header>
              <div class="card-head">
                <span class="card-title">降级链路验证</span>
                <span class="card-sub">主动注入故障，验证降级是否生效（运维常规操作）</span>
              </div>
            </template>
            <el-button size="small" plain type="danger" @click="demoDegradeAlgorithm">
              注入：算法异常
            </el-button>
            <el-button size="small" plain type="danger" @click="demoDegradeValidate">
              注入：参数非法
            </el-button>
            <el-button size="small" plain type="danger" @click="demoFail">
              注入：未知任务类型
            </el-button>
            <el-button size="small" plain @click="demoNormal">
              正常任务（对照组）
            </el-button>
            <span v-if="lastResult" style="margin-left:14px;font-size:13px">
              上次结果：
              <el-tag :type="statusType(lastResult.status)" size="small" effect="dark">
                {{ lastResult.statusName }}
              </el-tag>
              <span style="color:#898781;margin-left:8px">
                {{ lastResult.handler }} · {{ lastResult.elapsedMs }}ms
              </span>
            </span>
          </el-card>
        </el-tab-pane>

        <!-- ================= Tab 2：自然语言查询 ================= -->
        <el-tab-pane label="自然语言查询" name="chat">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
            LLM 理解意图 → 决定调用哪个工具 → 系统查数据库 → LLM 基于<b>真实数据</b>组织回答。
            <b>LLM 不直接算数。</b>
          </el-alert>

          <div style="margin-bottom:12px">
            <span style="font-size:12px;color:#898781;margin-right:8px">试试：</span>
            <el-button v-for="q in samples" :key="q" size="small" plain
                       style="margin:0 6px 6px 0" @click="ask(q)">{{ q }}</el-button>
          </div>

          <div class="chat-box" ref="chatRef">
            <div v-if="!messages.length" class="chat-empty">
              用中文提问，系统会调用对应工具查询真实数据后回答
            </div>
            <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
              <div class="bubble">
                <div class="bubble-text">{{ m.text }}</div>
                <div v-if="m.tools && m.tools.length" class="bubble-tools">
                  <el-tag v-for="t in m.tools" :key="t.tool" size="small"
                          type="warning" effect="plain" style="margin-right:6px">
                    <el-icon><Tools /></el-icon> {{ t.tool }}
                  </el-tag>
                  <span v-for="t in m.tools" :key="t.tool + '_s'" class="tool-sum">
                    {{ t.resultSummary }}
                  </span>
                </div>
              </div>
            </div>
            <div v-if="chatLoading" class="msg assistant">
              <div class="bubble"><el-icon class="is-loading"><Loading /></el-icon> 思考中…</div>
            </div>
          </div>

          <div class="chat-input">
            <el-input v-model="question" placeholder="用中文提问，例如：仓库现在有多少库存？"
                      @keyup.enter="ask()" :disabled="chatLoading" clearable />
            <el-button type="primary" :loading="chatLoading" @click="ask()">
              <el-icon><Promotion /></el-icon> 发送
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { agentApi } from '@/api/agent'

const tab = ref('monitor')
const loading = ref(false)
const llm = ref({ available: false })
const services = ref({ java: false, python: false })
const stats = ref({ total: 0, success: 0, degraded: 0, failed: 0,
                    successRate: 0, degradeRate: 0, avgElapsedMs: 0,
                    byType: {}, recent: [] })
const lastResult = ref(null)
let timer = null

const TYPE_NAME = {
  routing: '拣货路径优化',
  forecast: '需求预测',
  anomaly: '异常检测',
}

/** 各任务类型统计 → 表格行 */
const typeRows = computed(() =>
  Object.entries(stats.value.byType || {}).map(([k, v]) => ({
    type: TYPE_NAME[k] || k,
    ...v,
  }))
)

function statusType(s) {
  return { SUCCESS: 'success', DEGRADED: 'warning', FAILED: 'danger' }[s] || 'info'
}
function statusName(s) {
  return { SUCCESS: '正常', DEGRADED: '已降级', FAILED: '失败' }[s] || s
}

async function refresh() {
  loading.value = true
  try {
    const [st, llmSt] = await Promise.all([
      agentApi.orchestrateStats(),
      agentApi.llmStatus().catch(() => ({ available: false })),
    ])
    stats.value = st
    llm.value = llmSt
    // 探测 Java 服务
    try {
      await fetch('/api/health', { method: 'GET' })
      services.value.java = true
    } catch {
      services.value.java = false
    }
    services.value.python = true
  } catch (e) {
    services.value.python = false
  } finally {
    loading.value = false
  }
}

/* ---------- 故障演练 ---------- */
async function run(type, payload) {
  try {
    lastResult.value = await agentApi.orchestrate(type, payload)
    await refresh()
    if (lastResult.value.status === 'SUCCESS') {
      ElMessage.success('任务正常完成')
    } else if (lastResult.value.status === 'DEGRADED') {
      ElMessage.warning(`已降级：${lastResult.value.fallbackReason}`)
    } else {
      ElMessage.error(`任务失败：${lastResult.value.fallbackReason}`)
    }
  } catch (e) { /* 已提示 */ }
}
const demoNormal = () => run('routing', { waveId: 9709, strategy: 's_shape' })
const demoDegradeAlgorithm = () => run('routing', { waveId: 99999 })
const demoDegradeValidate = () => run('routing', { waveId: 9709, strategy: 'wrong_strategy' })
const demoFail = () => run('unknown_type', {})

/* ---------- 自然语言查询 ---------- */
const messages = ref([])
const question = ref('')
const chatLoading = ref(false)
const chatRef = ref(null)
const samples = [
  '仓库现在有多少库存？',
  '8N10W9-11 这个 SKU 分布在哪些库位？',
  '哪些拣货员作业量最少？',
  '有哪些商品库存偏低了？',
]

async function ask(q) {
  const text = (q || question.value || '').trim()
  if (!text || chatLoading.value) return
  question.value = ''
  messages.value.push({ role: 'user', text })
  chatLoading.value = true
  await scrollBottom()
  try {
    const r = await agentApi.nlQuery(text)
    messages.value.push({ role: 'assistant', text: r.answer, tools: r.toolCalls || [] })
  } catch {
    messages.value.push({ role: 'assistant', text: '查询失败，请稍后重试' })
  } finally {
    chatLoading.value = false
    await scrollBottom()
  }
}

async function scrollBottom() {
  await nextTick()
  const el = chatRef.value
  if (el) el.scrollTop = el.scrollHeight
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, 10000)      // 每 10 秒自动刷新（监控面板的常规行为）
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.status-card { margin-bottom: 0; }
.status-row {
  display: flex; align-items: center; justify-content: space-between;
}
.status-label { font-size: 13px; color: #52514e; }
.status-sub { font-size: 11px; color: #898781; margin-top: 6px; }

.kpi { text-align: center; }
.kpi-value { font-size: 26px; font-weight: 600; color: #0b0b0b; line-height: 1.3; }
.kpi-value .unit { font-size: 13px; font-weight: 400; color: #898781; }
.kpi-label { font-size: 12px; color: #898781; margin-top: 2px; }

.card-title { font-size: 15px; font-weight: 600; color: #0b0b0b; }
.card-sub { font-size: 12px; color: #898781; }
.card-head { display: flex; align-items: baseline; gap: 10px; }

.chat-box {
  height: 360px; overflow-y: auto;
  border: 1px solid #e1e0d9; border-radius: 6px;
  padding: 14px; background: #fafaf8; margin-bottom: 12px;
}
.chat-empty { text-align: center; color: #c0c4cc; font-size: 13px; padding-top: 140px; }
.msg { display: flex; margin-bottom: 12px; }
.msg.user { justify-content: flex-end; }
.bubble {
  max-width: 76%; padding: 10px 14px; border-radius: 8px;
  font-size: 13px; line-height: 1.7; background: #fff; border: 1px solid #e1e0d9;
}
.msg.user .bubble { background: #2a78d6; border-color: #2a78d6; color: #fff; }
.bubble-text { white-space: pre-wrap; }
.bubble-tools {
  margin-top: 8px; padding-top: 8px; border-top: 1px dashed #e1e0d9;
  font-size: 12px; color: #52514e;
}
.tool-sum { color: #898781; }
.chat-input { display: flex; gap: 10px; }
</style>
