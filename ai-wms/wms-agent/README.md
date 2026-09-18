# wms-agent（Python 智能 Agent）

## 技术栈

- **框架**：FastAPI 0.115 + Uvicorn
- **数据**：PyMySQL（只读连 MySQL）
- **算法**：numpy
- **LLM**：OpenAI SDK（兼容通义千问 / DeepSeek / 智谱）

## 三层结构

| 层 | 职责 | 用 LLM 吗 |
|---|---|---|
| **决策层（OR）** | 拣货路径优化、需求预测 | ❌ 运筹优化算法 |
| **Agent 层** | 异常解释、自然语言查询 | ✅ LLM |
| **编排层** | 任务路由 / 上下文管理 / 结果校验 / 降级 | ❌ 确定性逻辑 |

## 一、拣货路径优化

### 仓库布局建模（从真实导航点数据推断）

```
x=66   LC通道（左）┐
                    ├─ Block1（货位 x ∈ 86~368）
x=403  CC通道（中）┤
                    ├─ Block2（货位 x ∈ 450~666）
x=686  RC通道（右）┘
```

3 条纵向通道 + 2 个货架区块 = 仓储运筹学中的「双区块布局」。

### 实现的三种行业标准策略

| 策略 | 思想 | 实测距离 | 相比基线 |
|---|---|---|---|
| baseline | 顺序拣货（原系统做法） | 12127 米 | — |
| return | 返回式：每条通道原路返回 | 11586 米 | -4.5% |
| **s_shape** | **S形穿越：通道间蛇形** | **9554 米** | **-21.2%** |
| largest_gap | 最大间隙：找空隙跳过 | 11586 米 | -4.5% |

**一个发现**：学术界说「最大间隙在小订单量下最优」，
但在本仓库布局下 S形更优——因为只有 3 条通道、通道间切换成本高，
最大间隙需要从两端进入，额外纵向移动抵消了优势。

## 二、编排器（Orchestrator）

### 为什么用 Workflow 而不是 Agent

参考 Anthropic《Building Effective Agents》："找最简单的方案，只在必要时增加复杂度"。

本系统任务路由是**确定性**的（routing→路径优化），属于 **Workflow 的 Routing 模式**，
用 Agent 反而增加不可控性。

### 三级降级

```
① 算法正常 → SUCCESS
② 规则桩兜底 → DEGRADED
③ 都失败 → FAILED（人工工单）
```

触发降级的三种情况：**超时 / 抛异常 / 结果校验失败**。

### 结果校验（不能盲信算法输出）

- 路径为空 / 距离为负 / 任务数对不上 → 判定为不可信，走降级
- 宁可降级，也不能把错误结果写回业务系统

### 监控指标

`GET /orchestrate/stats` 返回调用统计，
核心指标是**降级率**（证明三级降级真在运行、可观测）。

## 三、LLM Agent

### LLM 用在哪、不用在哪

| 场景 | 用什么 | 为什么 |
|---|---|---|
| 数值决策 | ❌ LLM | 要可解释、可复现、可验证 |
| 异常检测 | ❌ 统计规则 | 3σ 确定性强 |
| **异常解释** | ✅ LLM | 把异常数据翻译成人话 |
| **自然语言查询** | ✅ LLM | Function Calling 理解意图 |

### 自然语言查询（Function Calling）

```
用户提问 → ① LLM 理解意图，决定调用哪个工具
        → ② 系统执行工具查数据库（LLM 不直接算数）
        → ③ LLM 基于真实数据组织中文回答
```

已实现 4 个工具：仓库统计 / 库存查询 / 拣货员效率 / 低库存查询。

**未配置 API Key 时自动降级为规则桩，不影响其它功能。**

## 接口

| 接口 | 说明 |
|---|---|
| `GET /health` | 健康检查 |
| `GET /routing/wave/{id}` | 对波次跑全部策略并对比 |
| `POST /routing/optimize` | 按指定策略优化 |
| `POST /orchestrate` | 提交任务给编排器 |
| `GET /orchestrate/stats` | ★ 运行统计 |
| `GET /llm/status` | LLM 是否可用 |
| `POST /llm/query` | 自然语言查询 |
| `POST /llm/explain-anomaly` | 异常解释 |

接口文档：http://localhost:8000/docs

## 启动

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

配置：复制 `.env.example` 为 `.env` 并填写。
