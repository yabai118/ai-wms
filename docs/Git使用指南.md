# Git 使用指南

> 本项目的版本管理规范。**每次改代码都要提交，保证随时可回滚。**

---

## 目录

- [一、仓库信息](#一仓库信息)
- [二、日常操作（最常用）](#二日常操作最常用)
- [三、提交信息规范](#三提交信息规范)
- [四、回滚与撤销](#四回滚与撤销)
- [五、代理配置（重要）](#五代理配置重要)
- [六、常见问题](#六常见问题)

---

## 一、仓库信息

| 项 | 值 |
|---|---|
| 远程地址 | https://github.com/yabai118/ai-wms |
| 可见性 | Public |
| 主分支 | master |
| 本地路径 | `d:/job2` |

**已配置的 Git 信息**
```
user.name  = 陈兆兴
user.email = Yabai118@users.noreply.github.com
http.proxy = http://127.0.0.1:7897   （全局，用于访问 GitHub）
```

**被 .gitignore 排除的内容**（不入库）
```
ai-wms/data/cainiao/              95 MB  已弃用的数据集
ai-wms/data/footwear/*.csv        18 MB  订单/波次大文件
ai-wms/data/footwear/布局图        12 MB  CAD/PDF/SVG
ai-wms/data/Warehouse_*.csv       11 MB  备用数据集
ai-wms/sql/02_*.sql 03_*.sql      11 MB  生成的 SQL（可用脚本重跑）

构建产物、IDE 配置、日志、.env 等
```

---

## 二、日常操作（最常用）

### 每天开工前：拉取最新（如果换了电脑）
```bash
cd d:/job2
git pull
```

### 写完一段代码：提交
```bash
cd d:/job2

git status                  # ① 看看改了哪些文件
git add .                   # ② 暂存全部改动
git commit -m "feat: 新增商品管理接口"   # ③ 提交
```

### 一天结束：推送到 GitHub
```bash
git push
```

### 看历史
```bash
git log --oneline           # 简洁版
git log --oneline -10       # 最近 10 条
git log --stat              # 带文件改动
```

---

## 三、提交信息规范

**格式**：`类型: 简短描述`

| 类型 | 用于 | 示例 |
|---|---|---|
| `feat` | 新功能 | `feat: 新增商品管理 CRUD 接口` |
| `fix` | 修 bug | `fix: 修复库存扣减的并发问题` |
| `docs` | 文档 | `docs: 更新数据模块文档` |
| `refactor` | 重构（不改功能） | `refactor: 抽取库存计算为独立服务` |
| `test` | 测试 | `test: 补充库存扣减的单元测试` |
| `chore` | 杂务（配置/依赖） | `chore: 升级 Spring Boot 到 3.2.5` |
| `style` | 格式（不影响逻辑） | `style: 统一代码缩进` |

**❌ 不要这样写**：
```
update          ← 不知道改了什么
修改            ← 太笼统
111             ← 无意义
```

**✅ 应该这样写**：
```
feat: 新增出库单创建接口
fix: 修复波次生成时超过 27 件的校验缺失
docs: 补充库存五字段模型的设计说明
refactor: 把库存扣减逻辑抽到 InventoryService
```

**为什么要规范**：面试官会看你的 GitHub 提交记录。**清晰的提交历史 = 真实开发过程的证明**。

---

## 四、回滚与撤销

### 场景 1：刚改的代码有问题，想撤销（还没提交）
```bash
git checkout -- 文件名        # 撤销单个文件的修改
git checkout -- .            # 撤销所有未提交的修改
```

### 场景 2：提交了但想撤销这次提交（保留代码改动）
```bash
git reset --soft HEAD~1      # 撤销最后一次提交，改动还在
```

### 场景 3：提交了想完全回滚到上一个版本
```bash
git reset --hard HEAD~1      # ⚠️ 会丢弃改动，慎用
```

### 场景 4：想看某个历史版本的文件
```bash
git log --oneline            # 先找到版本号
git show <版本号>:文件路径     # 查看那个版本的文件内容
```

### 场景 5：改动乱了，想回到某个干净的版本
```bash
git log --oneline            # 找到想回去的版本号
git reset --hard <版本号>     # 回到那个版本
```

### 场景 6：误删了文件，想恢复
```bash
git checkout HEAD -- 文件路径
```

**⭐ 最重要的一条**：**只要提交过，就永远能找回。** 这就是版本管理的价值。

---

## 五、代理配置（重要）

**背景**：直连 GitHub 被重置，必须走代理（Clash 端口 7897）。

### 梯子开着时（正常状态）
```bash
git config --global http.proxy http://127.0.0.1:7897
git config --global https.proxy http://127.0.0.1:7897
```

### 梯子关了时（比如在公司/学校）
```bash
git config --global --unset http.proxy
git config --global --unset https.proxy
```

### 查看当前代理配置
```bash
git config --global --get http.proxy
```

### 临时用代理（不改配置）
```bash
git -c http.proxy=http://127.0.0.1:7897 push
```

---

## 六、常见问题

### Q1：`git push` 报 "Connection was reset"
**原因**：代理没配或梯子关了。
**解决**：见第五节。

### Q2：`git push` 报 "Everything up-to-date" 但远程没变化
**原因**：改动没提交。
**解决**：先 `git add .` + `git commit -m "..."`。

### Q3：推送大文件失败
**原因**：GitHub 单文件限制 100MB。
**解决**：本项目已用 `.gitignore` 排除大文件。如果不小心 add 了：
```bash
git rm --cached 大文件路径      # 从暂存区移除（保留本地文件）
```

### Q4：提交时提示 "Please tell me who you are"
```bash
git config user.name "陈兆兴"
git config user.email "Yabai118@users.noreply.github.com"
```

### Q5：想看看某个文件是谁改的
```bash
git log -p 文件路径             # 该文件的完整改动历史
git blame 文件路径              # 逐行显示最后修改者
```

---

## 七、推荐的提交节奏

```
写完一个功能点 → 提交一次
修好一个 bug  → 提交一次
一天结束       → 推送到 GitHub
```

**不要攒一周再提交**——那样回滚时粒度太粗，而且看不出开发过程。

**对于本项目**：
```
① 搭好项目骨架         → chore: 初始化 Spring Boot 项目
② 写完商品管理接口     → feat: 实现商品管理 CRUD
③ 写完入库流程         → feat: 实现入库单/收货/上架
④ 写完出库流程         → feat: 实现订单分配与波次生成
⑤ 写完库存扣减         → feat: 实现库存并发扣减（条件更新）
⑥ 写完算法模块         → feat: 实现货位分配与拣货路径优化
⑦ 联调完成             → chore: Java 与 Python 服务联调完成
```

**这样的提交历史，面试时直接展示，就是一份开发日志。**
