# AI-WMS 智能仓储管理系统

> **Java 业务底座 + Python 智能 Agent** 的融合型仓储系统。
> 基于真实企业 WMS 导出的生产数据，聚焦**作业优化**。

完整项目介绍见 [../README.md](../README.md)。

---

## 一句话定位

**Java（Spring Boot）**承载高频出入库、库存事务与并发一致性；
**Python（FastAPI）**Agent 承担拣货路径优化、需求预测、异常诊断等智能决策；
两者通过 **RESTful（同步）+ RocketMQ（异步）** 双通道通信。

---

## 目录结构

```
ai-wms/
├── 项目设计方案.md      完整设计方案（架构 / 功能清单 / 技术选型）
├── ER设计.md            19 张表的完整设计
├── 并发压测报告.md      ★ 并发测试结果（零超卖）
├── README.md            本文档
│
├── data/                数据集
│   └── footwear/        鞋厂数据集（商品 / 库位 / 4 种存储策略 / 字段说明）
│
├── sql/                 数据库脚本
│   ├── 01_schema.sql          建表（19 张表）
│   ├── 02_init_base.sql       基础数据
│   ├── 03_init_outbound.sql   出库数据
│   ├── 04_init_inventory.sql  初始库存
│   ├── gen_*.py               数据生成脚本
│   └── tools/                 分析工具 + ★并发压测脚本
│
├── wms-backend/         Java 后端（Spring Boot 3 + MyBatis-Plus + MySQL + Redis）
├── wms-agent/           Python Agent（FastAPI + Pydantic + OpenAI SDK）
└── wms-frontend/        Vue3 前端（Vite + Element Plus + ECharts）
```

---

## 核心设计

### 1. 库存五字段模型

```sql
qty            现有总量      -- 拣货时不变
qty_allocated  已分配        -- 被订单占住
qty_picked     已拣出        -- 拣货员已取走、未发货
qty_onhold     冻结          -- 异常占用（与 allocated 分离）
qty_available  可用          -- = qty - allocated - onhold
```

「订单占用」与「异常冻结」是两种不同性质的库存占用，必须分开；
拣货时不扣总量，只在发货时扣——这才符合真实作业。

### 2. 并发扣减防超卖

```sql
UPDATE inventory SET ...
WHERE sku_id = ? AND location_id = ?
  AND qty_available >= ?      -- ★ 判断写进 WHERE
```

数据库执行时加行锁，把「判断够不够」和「扣减」变成**一个原子操作**。
压测结果见 [并发压测报告](并发压测报告.md)。

### 3. 拣货路径优化

从导航点数据推断出仓库是 **3 条纵向通道 + 2 个货架区块**的双区块布局，
实现了三种行业标准启发式（S形 / 最大间隙 / 返回式）并做对比实验，
**S形相比顺序拣货节省 21.2%** 行走距离。

### 4. Agent 编排与三级降级

```
① 算法正常 → SUCCESS
② 规则桩兜底 → DEGRADED
③ 都失败 → FAILED（人工工单）
```

编排器职责：任务路由 / 上下文管理 / **结果校验** / 降级决策。

---

## 快速开始

见 [../README.md](../README.md) 的「六、快速开始」。

核心三步：

```bash
# 1. 导入数据库（4 个 SQL，顺序不能颠倒）
mysql -u root -p < sql/01_schema.sql
mysql -u root -p < sql/02_init_base.sql
mysql -u root -p < sql/03_init_outbound.sql
mysql -u root -p < sql/04_init_inventory.sql

# 2. 启动 Java 后端（8080）
cd wms-backend && mvn spring-boot:run

# 3. 启动 Python Agent（8000）
cd wms-agent && pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000

# 4. 启动前端（5173）
cd wms-frontend && npm install && npm run dev
```

访问 http://localhost:5173

> LLM 功能需配置 API Key，见 `wms-agent/.env.example`。
> 未配置时自动降级为规则桩，**不影响其它功能**。

---

## 实测数据

| 指标 | 结果 |
|---|---|
| 并发分配（最高 15.6 倍超额争抢） | **超卖 0 次**，库存精确 |
| 库存扣减 QPS | 436 ~ 908 |
| P99 延迟 | < 380 ms |
| 拣货路径优化 | **节省 21.2%** 行走距离 |
