# wms-frontend（Vue3 前端）

## 技术栈

- Vue 3 + Vite
- Element Plus（UI 组件）
- ECharts（图表 / 路径可视化）
- Vue Router（路由）
- Axios（HTTP，统一封装）

## 页面清单（11 个）

| 页面 | 路由 | 说明 |
|---|---|---|
| 首页看板 | `/dashboard` | 6 个 KPI + 4 个图表 |
| 商品管理 | `/product` | 208 款商品 + 尺码详情弹窗 |
| 库位管理 | `/location` | 2,314 个库位（坐标 / 容量 / 占用率） |
| **库位地图** | `/location-map` | 仓库分布可视化（真实坐标散点图） |
| 入库管理 | `/inbound` | 入库单 / 收货 / 上架 |
| 出库管理 | `/outbound` | 订单 / 分配库存 |
| 波次拣货 | `/wave` | 波次 / 拣货任务 / 拣货 / 发货 |
| 库存管理 | `/inventory` | 五字段查询 / 流水 / 对账 / 冻结解冻 |
| **路径优化** | `/routing` | 3 策略对比 + 路径可视化 |
| **智能助手** | `/agent` | Agent 监控面板 + 自然语言查询 |
| （布局） | — | 左侧菜单 + 顶栏 |

## 两个后端服务的代理

```js
// vite.config.js
proxy: {
  '/api':        → http://localhost:8080   // Java 业务服务
  '/agent-api':  → http://localhost:8000   // Python Agent
}
```

> ⚠️ Python 服务的代理前缀**不能叫 `/agent`**——
> 前端有个页面路由也叫 `/agent`，会被代理规则拦截导致页面 404。
> （这个坑踩过，见项目文档）

## 关键实现

### HTTP 统一封装（`src/utils/request.js`）

后端返回 `{code, message, data}`，拦截器自动剥出 `data`，
业务代码直接用，不用每次都 `.data.data`。

### API 层分离

页面不直接调 axios，统一走 `src/api/*.js`，
接口变了只改一个文件。

### 图表规范

配色经过可访问性验证（CVD 色盲友好），
颜色跟随「实体」而非「排名」（切换筛选不重新着色）。

## 启动

```bash
npm install
npm run dev
```

访问 http://localhost:5173
