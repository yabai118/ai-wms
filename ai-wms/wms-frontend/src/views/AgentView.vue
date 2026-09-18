<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px">
            <el-icon><MagicStick /></el-icon>
            <span style="font-weight:600">智能助手</span>
            <el-tag size="small" type="success" effect="plain">Python Agent</el-tag>
          </div>
          <el-tag :type="llm.available ? 'success' : 'info'" size="small" effect="dark">
            LLM {{ llm.available ? '已启用' : '未配置（走规则桩）' }}
          </el-tag>
        </div>
      </template>

      <el-tabs v-model="tab">
        <!-- ================= Tab 1：自然语言查询 ================= -->
        <el-tab-pane label="自然语言查询" name="chat">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px">
            <b>原理</b>：LLM 理解你的意图 → 决定调用哪个工具 → 系统查数据库 →
            LLM 基于<b>真实数据</b>组织回答。
            <br />
            <b>注意</b>：LLM 不直接算数，它只负责「理解意图」和「表达结果」。
          </el-alert>

          <!-- 示例问题 -->
          <div style="margin-bottom:12px">
            <span style="font-size:12px;color:#898781;margin-right:8px">试试：</span>
            <el-button v-for="q in samples" :key="q" size="small" plain
                       style="margin:0 6px 6px 0" @click="ask(q)">{{ q }}</el-button>
          </div>

          <!-- 对话区 -->
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
                    <el-icon><Tools /></el-icon> 调用 {{ t.tool }}
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

          <!-- 输入 -->
          <div class="chat-input">
            <el-input v-model="question" placeholder="用中文提问，例如：仓库现在有多少库存？"
                      @keyup.enter="ask()" :disabled="chatLoading" clearable />
            <el-button type="primary" :loading="chatLoading" @click="ask()">
              <el-icon><Promotion /></el-icon> 发送
            </el-button>
          </div>
        </el-tab-pane>

        <!-- ================= Tab 2：编排器 ================= -->
        <el-tab-pane label="任务编排器" name="orch">
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px">
            <b>编排器四职责</b>：① 按任务类型路由　② 统一组装上下文　③
            <b>校验算法结果</b>（不能盲信）　④ 失败则<b>降级到规则桩</b>
          </el-alert>

          <div class="search-bar">
            <el-select v-model="orchType" style="width:220px" @change="onTypeChange">
              <el-option v-for="t in orchTypes" :key="t.type"
                         :label="t.name" :value="t.type" />
            </el-select>
            <el-input v-model="orchPayload" style="flex:1"
                      placeholder='任务参数（JSON）' />
            <el-button type="primary" :loading="orchLoading" @click="runOrchestrate">
              <el-icon><VideoPlay /></el-icon> 提交任务
            </el-button>
          </div>

          <div style="margin-bottom:12px">
            <span style="font-size:12px;color:#898781;margin-right:8px">降级演示：</span>
            <el-button size="small" plain type="danger" @click="demoDegradeAlgorithm">
              ① 算法异常（不存在的波次）
            </el-button>
            <el-button size="small" plain type="danger" @click="demoDegradeValidate">
              ② 参数非法（错误的策略名）
            </el-button>
            <el-button size="small" plain type="danger" @click="demoFail">
              ③ 未知任务类型（直接失败）
            </el-button>
          </div>

          <template v-if="orchResult">
            <el-descriptions :column="4" border size="small" style="margin-bottom:12px">
              <el-descriptions-item label="任务 ID">
                <span style="font-family:monospace">{{ orchResult.taskId }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="任务类型">{{ orchResult.taskType }}</el-descriptions-item>
              <el-descriptions-item label="执行状态">
                <el-tag :type="statusType(orchResult.status)" size="small" effect="dark">
                  {{ orchResult.statusName }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="耗时">{{ orchResult.elapsedMs }} ms</el-descriptions-item>
              <el-descriptions-item label="实际处理器" :span="2">
                {{ orchResult.handler }}
              </el-descriptions-item>
              <el-descriptions-item label="降级原因" :span="2">
                <span v-if="orchResult.fallbackReason" style="color:#d03b3b">
                  {{ orchResult.fallbackReason }}
                </span>
                <span v-else style="color:#898781">—</span>
              </el-descriptions-item>
            </el-descriptions>

            <el-row :gutter="14">
              <el-col :span="10">
                <el-card shadow="never">
                  <template #header><span class="card-title">执行轨迹</span></template>
                  <el-timeline>
                    <el-timeline-item v-for="(t, i) in orchResult.trace" :key="i"
                                      :timestamp="`步骤 ${i + 1}`" placement="top">
                      {{ t }}
                    </el-timeline-item>
                  </el-timeline>
                </el-card>
              </el-col>
              <el-col :span="14">
                <el-card shadow="never">
                  <template #header><span class="card-title">返回结果</span></template>
                  <pre class="result-pre">{{ prettyResult }}</pre>
                </el-card>
              </el-col>
            </el-row>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { agentApi } from '@/api/agent'

const tab = ref('chat')
const llm = ref({ available: false })

// ---------------- 自然语言查询 ----------------
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
    messages.value.push({
      role: 'assistant',
      text: r.answer,
      tools: r.toolCalls || []
    })
  } catch (e) {
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

// ---------------- 编排器 ----------------
const orchTypes = ref([])
const orchType = ref('routing')
const orchPayload = ref('{"waveId": 9709, "strategy": "s_shape"}')
const orchLoading = ref(false)
const orchResult = ref(null)

const prettyResult = computed(() =>
  orchResult.value ? JSON.stringify(orchResult.value.result, null, 2) : '')

function statusType(s) {
  return { SUCCESS: 'success', DEGRADED: 'warning', FAILED: 'danger' }[s] || 'info'
}

function onTypeChange(t) {
  const found = orchTypes.value.find(x => x.type === t)
  if (found) orchPayload.value = JSON.stringify(found.payload)
}

async function runOrchestrate() {
  let payload
  try {
    payload = JSON.parse(orchPayload.value || '{}')
  } catch {
    ElMessage.error('任务参数不是合法的 JSON')
    return
  }
  orchLoading.value = true
  try {
    orchResult.value = await agentApi.orchestrate(orchType.value, payload)
  } finally {
    orchLoading.value = false
  }
}

function demoDegradeAlgorithm() {
  orchType.value = 'routing'
  orchPayload.value = '{"waveId": 99999}'
  runOrchestrate()
}
function demoDegradeValidate() {
  orchType.value = 'routing'
  orchPayload.value = '{"waveId": 9709, "strategy": "wrong_strategy"}'
  runOrchestrate()
}
function demoFail() {
  orchType.value = 'unknown_type'
  orchPayload.value = '{}'
  runOrchestrate()
}

onMounted(async () => {
  try { llm.value = await agentApi.llmStatus() } catch { /* ignore */ }
  try { orchTypes.value = await agentApi.orchestrateTypes() } catch { /* ignore */ }
})
</script>

<style scoped>
.chat-box {
  height: 380px;
  overflow-y: auto;
  border: 1px solid #e1e0d9;
  border-radius: 6px;
  padding: 14px;
  background: #fafaf8;
  margin-bottom: 12px;
}
.chat-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding-top: 150px;
}
.msg { display: flex; margin-bottom: 12px; }
.msg.user { justify-content: flex-end; }
.msg.assistant { justify-content: flex-start; }
.bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
  background: #fff;
  border: 1px solid #e1e0d9;
}
.msg.user .bubble {
  background: #2a78d6;
  border-color: #2a78d6;
  color: #fff;
}
.bubble-text { white-space: pre-wrap; }
.bubble-tools {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e1e0d9;
  font-size: 12px;
  color: #52514e;
}
.tool-sum { color: #898781; }
.chat-input { display: flex; gap: 10px; }
.card-title { font-size: 15px; font-weight: 600; color: #0b0b0b; }
.result-pre {
  max-height: 420px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  background: #fafaf8;
  border: 1px solid #e1e0d9;
  border-radius: 4px;
  padding: 10px;
  margin: 0;
  font-family: Consolas, Monaco, monospace;
}
</style>
