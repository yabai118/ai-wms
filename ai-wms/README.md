# AI-WMS 智能仓储管理系统

> 一个"传统 Java 业务底座 + Python 智能 Agent"的融合系统。
> 项目排期见 [../WMS学习与开发计划.md](../WMS学习与开发计划.md)，每日知识记录见 [../知识库/](../知识库/README.md)。

## 一句话定位
Java（Spring Boot）承载高频出入库与并发一致性；Python（FastAPI）Agent 承担库存预测、货位分配、拣货路径优化三大智能决策。RESTful + MQ 双通道联调。

## 目录结构（两个子项目）
```
ai-wms/
├── wms-backend/   ← Java 后端（Spring Boot + MyBatis + MySQL + Redis + RabbitMQ）
└── wms-agent/     ← Python Agent（FastAPI + SQLAlchemy + 决策算法）
```

## 开发进度（跟着计划走，每阶段打勾）
- [ ] Day 1–2（9/4–9/5）：需求 + ER 设计 + 接口契约（文档在 `../知识库/每日学习/`）
- [ ] Day 3–8（9/6–9/11）：Java 底座（商品→入库→出库→库存→缓存→货位）
- [ ] Day 9–10（9/12–9/13）：单测 + Vue 后台
- [ ] Day 11–15（9/14–9/18）：Python Agent（预测→货位→路径→MQ 联调）
- [ ] Day 16–17（9/19–9/20）：压测 + 文档 + 演示

## 关键设计约定（开发前定死）
1. Java ↔ Agent 边界与接口契约（RESTful + MQ 双通道）
2. 库存扣减用乐观锁 + Redis 分布式锁兜底
3. Agent 决策先纯算法实现，LLM 作为后置加分项
