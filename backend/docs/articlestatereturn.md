# 文章生成状态返回（SSE）实现分析

## 一、整体架构

```
┌─────────┐  SSE (EventSource)   ┌──────────────────────────────────────────────┐
│  前端   │ ◄────────────────── │              Spring Boot 后端                   │
│         │                      │                                               │
│         │  GET /article/       │  ArticleController  (SSE 入口 + 状态快照)       │
│         │  progress/{taskId}   │        │                                       │
│         │                      │        ▼                                       │
│         │                      │  SseEmitterManager  (SSE 基础设施)              │
│         │                      │   · emitter 管理   · 消息缓冲/回放              │
│         │                      │   · seq 序号管理   · 流式累积器                 │
│         │                      │        ▲                                       │
│         │                      │        │                                       │
│         │                      │  ArticleAsyncService  (异步任务 + 消息构建)     │
│         │                      │        │                                       │
│         │                      │        ▼                                       │
│         │                      │  ArticleAgentService  (5个智能体编排)           │
│         │                      │   · Agent1 标题  · Agent2 大纲  · Agent3 正文   │
│         │                      │   · Agent4 配图需求 · Agent5 生成配图           │
└─────────┘                      └──────────────────────────────────────────────┘
```

**核心文件清单：**

| 文件 | 行号 | 职责 |
|------|------|------|
| `controller/ArticleController.java` | 103-204 | SSE 连接端点 + `sendStateSnapshot` 状态快照 |
| `manager/SseEmitterManager.java` | 全文件 | emitter 创建/激活/发送/回放/完成，缓冲区和序号 |
| `service/ArticleAsyncService.java` | 全文件 | 3个异步阶段编排，消息构建与推送 |
| `service/ArticleAgentService.java` | 全文件 | 5个智能体调用，流式回调产生 |
| `model/dto/article/ArticleState.java` | 全文件 | 智能体间共享状态对象 |
| `model/enums/SseMessageTypeEnum.java` | 全文件 | 15种 SSE 消息类型定义 |
| `model/enums/ArticlePhaseEnum.java` | 全文件 | 6个文章生成阶段定义 |

---

## 二、数据结构

### 2.1 SseEmitterManager 四大核心 Map

```
emitterMap:              Map<taskId, SseEmitter>         活跃的 SSE 连接
messageBufferMap:        Map<taskId, List<BufferedMessage>>  缓冲消息（最多300条）
streamingAccumulatorMap: Map<taskId, StringBuilder>       流式内容累积文本
seqMap:                  Map<taskId, AtomicLong>          自增消息序号
taskLockMap:             Map<taskId, Object>               每个 taskId 的锁对象
```

### 2.2 文章状态对象 (ArticleState)

共享状态，贯穿5个智能体的输入输出：

```
taskId → topic → style → userDescription → phase
titleOptions (Agent1 输出) → title (用户选择后)
outline (Agent2 输出)
content (Agent3 输出)
imageRequirements (Agent4 输出) → images (Agent5 输出)
fullContent (图文合成结果)
```

### 2.3 状态与阶段分离

- **ArticleStatusEnum**（任务状态）: `PENDING → PROCESSING → COMPLETED / FAILED`
- **ArticlePhaseEnum**（生成阶段）: `PENDING → TITLE_GENERATING → TITLE_SELECTING → OUTLINE_GENERATING → OUTLINE_EDITING → CONTENT_GENERATING`

状态和阶段独立管理：`updateArticleStatus()` 和 `updatePhase()` 分别更新。

---

## 三、完整流程

### 3.1 创建任务 → 开始生成

```
用户 → POST /article/create
  │
  ▼
ArticleController.createArticle()
  ├── 校验权限、消耗配额、创建文章记录 (status=PENDING, phase=PENDING)
  └── articleAsyncService.executePhase1(taskId, topic, style)  ← @Async 异步执行
```

### 3.2 SSE 连接建立（前端 EventSource 连接）

```
前端 → GET /article/progress/{taskId}?lastSeq=N
        Header: Last-Event-ID: 123  (浏览器断线重连时自动发送)
  │
  ▼
ArticleController.getProgress()
  │
  ├── 计算 effectiveLastSeq = max(lastSeq参数, Last-Event-ID头)
  ├── 校验权限 (articleService.getArticleDetail)
  │
  └── sseEmitterManager.runWithTaskLock(taskId, ...)  ← 加锁保证原子性
        │
        ├── [1] createEmitter(taskId, effectiveLastSeq)
        │       ├── 关闭旧 emitter（如果存在）
        │       ├── 创建新 SseEmitter(timeout=30min)
        │       ├── 注册 onTimeout / onCompletion / onError 回调
        │       └── replayBufferedMessages(taskId, emitter, lastSeq)
        │            遍历缓冲区，回放 seq > lastSeq 且非 ALL_COMPLETE 的消息
        │
        ├── [2] sendStateSnapshot(taskId, articleVO, emitter)  ← 状态快照
        │       根据当前 phase 发送已完成的数据：
        │       · 标题方案已生成 → TITLES_GENERATED
        │       · 大纲已生成 → OUTLINE_GENERATED
        │       · 正在流式生成中 → STREAMING_SNAPSHOT (累积的流式内容)
        │       · 图文已合成 → MERGE_COMPLETE
        │       · 全部完成 → ALL_COMPLETE
        │
        └── [3] activateEmitter(taskId, emitter)  ← 激活，此后实时消息可以推送

  ▲ 注意: 三步在同一个 taskLock 内完成，保证原子性
  │       回放消息 → 快照 → 激活 的顺序确保无消息丢失和乱序
```

### 3.3 阶段1：生成标题方案

```
ArticleAsyncService.executePhase1() [@Async 线程]
  │
  ├── 状态 → PROCESSING, 阶段 → TITLE_GENERATING
  │
  ├── ArticleAgentService.agent1GenerateTitleOptions(state)
  │     └── LLM 非流式调用，解析 JSON → state.titleOptions (3-5个标题方案)
  │
  ├── articleService.saveTitleOptions(taskId, titleOptions)  → 持久化到 DB
  │
  ├── 阶段 → TITLE_SELECTING
  │
  └── sendSseMessage(taskId, TITLES_GENERATED, {titleOptions})
        └── sseEmitterManager.send() → 加锁 → seq++ → 缓冲 → 推送到 emitter
```

### 3.4 用户确认标题 → 阶段2：生成大纲

```
用户 → POST /article/confirm-title  {taskId, selectedMainTitle, selectedSubTitle, userDescription}
  │
  ├── articleService.confirmTitle() → 保存选择到 DB
  │
  └── articleAsyncService.executePhase2(taskId) [@Async]
        │
        ├── 阶段 → OUTLINE_GENERATING
        │
        ├── ArticleAgentService.agent2GenerateOutline(state, streamHandler)
        │     └── LLM 流式调用
        │           chunk → streamHandler.accept("AGENT2_STREAMING:内容片段")
        │                  │
        │                  ▼
        │           ArticleAsyncService.handleAgentMessage()
        │             ├── sseEmitterManager.accumulateStreaming() → 累积流式文本
        │             └── sseEmitterManager.send() → 推送 AGENT2_STREAMING 到前端
        │
        ├── articleService.updateById() → 保存大纲 JSON 到 DB
        ├── sseEmitterManager.clearStreamingAccumulator() → 清除累积器
        │
        ├── 阶段 → OUTLINE_EDITING
        │
        └── SSE 推送:
              └── AGENT2_COMPLETE (内部完成信号)
              └── sendSseMessage(taskId, OUTLINE_GENERATED, {outline})
```

### 3.5 用户确认大纲 → 阶段3：生成正文+配图

```
用户 → POST /article/confirm-outline  {taskId, outline}
  │
  └── articleAsyncService.executePhase3(taskId) [@Async]
        │
        ├── 阶段 → CONTENT_GENERATING
        │
        ├── [Agent3] agent3GenerateContent(state, streamHandler)
        │     └── LLM 流式生成正文 → 逐片段推送 AGENT3_STREAMING
        │         (同时累积到 streamingAccumulatorMap)
        │
        ├── [Agent4] agent4AnalyzeImageRequirements(state)
        │     └── LLM 分析配图需求 → 在正文中插入 {{IMAGE_PLACEHOLDER_N}} 占位符
        │         SSE 推送: AGENT4_COMPLETE {imageRequirements}
        │
        ├── [Agent5] agent5GenerateImages(state, streamHandler)
        │     └── 逐张获取/生成图片 + 上传 COS
        │         每完成一张 → SSE 推送: IMAGE_COMPLETE {image}
        │
        ├── [Agent6] mergeImagesIntoContent(state)
        │     └── 将占位符替换为 Markdown 图片语法 → state.fullContent
        │         SSE 推送: MERGE_COMPLETE {fullContent}
        │
        ├── articleService.saveArticleContent() → 持久化完整文章
        ├── sseEmitterManager.clearStreamingAccumulator()
        │
        ├── 状态 → COMPLETED
        │
        ├── sendSseMessage(taskId, ALL_COMPLETE, {taskId})
        └── sseEmitterManager.complete(taskId) → 关闭 SSE 连接，清理 emitter 和累积器
```

### 3.6 用户回退阶段

```
用户 → POST /article/go-back  {taskId, targetPhase}
  │
  ├── articleService.goBackPhase() → 回退 DB 状态
  │
  └── sseEmitterManager.send() → SSE 推送: PHASE_ROLLED_BACK {taskId, phase}
      前端收到后重新渲染对应阶段的 UI
```

### 3.7 异常处理

```
任一阶段抛出异常:
  ├── articleService.updateArticleStatus(taskId, FAILED, errorMessage)
  ├── sendSseMessage(taskId, ERROR, {message})
  └── sseEmitterManager.complete(taskId) → 关闭连接
```

---

## 四、锁机制与并发安全

```
synchronized (taskLockMap.get(taskId))  ← 每个 taskId 一把独立锁

加锁的操作:
  · SseEmitterManager.send()           → 序号分配 + 缓冲 + 推送
  · SseEmitterManager.accumulateStreaming()  → 流式内容累积
  · SseEmitterManager.createEmitter()  → 回放 + 快照 + 激活 (调用方持有锁)
  · SseEmitterManager.complete()       → emitter 关闭 + 累积器清理
  · ArticleController.sendStateSnapshot() → 发送多段快照数据

锁的设计目的:
  1. 保证 seq 序号严格递增
  2. 回放→快照→激活 三步原子化，防止实时消息插队
  3. 流式累积器的并发写入安全
```

---

## 五、断线重连机制

```
SSE 连接断开 (网络波动/超时)
  │
  ▼
浏览器 EventSource 自动重连 (reconnectTime=3000ms)
  │ 自动携带 Last-Event-ID 头 = 最后收到的消息 seq
  │
  ▼
GET /article/progress/{taskId}
  effectiveLastSeq = max(queryParam lastSeq, header Last-Event-ID)
  │
  ▼
lock(taskId) {
  1. createEmitter(taskId, effectiveLastSeq)
     ├── 关闭旧 emitter
     └── replayBufferedMessages()
           遍历 messageBufferMap[taskId]
           跳过 seq <= effectiveLastSeq 的消息
           跳过 ALL_COMPLETE 消息（避免重复触发完成状态）
           逐条重新发送 seq > effectiveLastSeq 的消息

  2. sendStateSnapshot(taskId, articleVO, emitter)
     根据 DB 中当前阶段，发送完整状态快照:
     ├── 标题方案已生成 → TITLES_GENERATED 快照
     ├── 大纲已生成 → OUTLINE_GENERATED 快照
     ├── 正在生成中 → STREAMING_SNAPSHOT (累积的流式文本)
     ├── 图文已合成 → MERGE_COMPLETE 快照
     └── 已完成 → ALL_COMPLETE 快照

  3. activateEmitter(taskId, emitter)
     激活后新的实时消息可正常推送
}
```

**关键设计点：**
- 缓冲区最多保留 **300 条**消息（`MAX_BUFFER_SIZE`），超出时移除最早的
- `ALL_COMPLETE` 消息**不回放**（`replayBufferedMessages` 中跳过），防止重连后立即显示完成
- `STREAMING_SNAPSHOT` 快照让重连用户能看到断线期间累积的流式文本，而不是空白

---

## 六、消息类型一览

| 枚举值 | 说明 | 携带数据 | 推送时机 |
|--------|------|---------|---------|
| `TITLES_GENERATED` | 标题方案已生成 | `titleOptions` | 阶段1完成 |
| `AGENT2_STREAMING` | 大纲流式片段 | `content` (增量) | 阶段2进行中 |
| `OUTLINE_GENERATED` | 大纲已生成 | `outline` | 阶段2完成 |
| `AGENT3_STREAMING` | 正文流式片段 | `content` (增量) | 阶段3进行中 |
| `AGENT4_COMPLETE` | 配图需求分析完成 | `imageRequirements` | 阶段3 |
| `IMAGE_COMPLETE` | 单张配图完成 | `image` | 阶段3逐张 |
| `MERGE_COMPLETE` | 图文合成完成 | `fullContent` | 阶段3 |
| `ALL_COMPLETE` | 全部完成 | `taskId` | 流程结束 |
| `STREAMING_SNAPSHOT` | 流式内容快照 | `content`, `phase` | 断线重连 |
| `PHASE_ROLLED_BACK` | 阶段已回退 | `taskId`, `phase` | 用户回退 |
| `ERROR` | 错误 | `message` | 异常时 |

内部完成信号（不直接推送给前端，用于 `buildCompleteMessageData` 构建数据后推送）：
`AGENT1_COMPLETE`, `AGENT2_COMPLETE`, `AGENT3_COMPLETE`, `AGENT5_COMPLETE`

---

## 七、关键时序图

```
前端                    Controller              AsyncService           AgentService         SseEmitterManager        DB
 │                          │                       │                      │                      │                    │
 │── POST /create ─────────►│                       │                      │                      │                    │
 │                          │── createArticleTask ────────────────────────────────────────────────────────────────────►│
 │                          │◄── taskId ────────────────────────────────────────────────────────────────────────────│
 │                          │── executePhase1 ──────►│                      │                      │                    │
 │◄── taskId ──────────────│                       │                      │                      │                    │
 │                          │                       │                      │                      │                    │
 │── GET /progress/{id} ──►│                       │                      │                      │                    │
 │                          │── getProgress() ───────────────────────────────────────────────►│ (lock + createEmitter)
 │◄── SSE stream ──────────│                       │                      │                      │                    │
 │                          │                       │                      │                      │                    │
 │                          │                       │── agent1 ───────────►│                      │                    │
 │                          │                       │                      │── LLM call          │                    │
 │                          │                       │◄── titles ──────────│                      │                    │
 │                          │                       │── saveTitleOptions ────────────────────────────────────────────►│
 │                          │                       │── SSE: TITLES_GENERATED ─────────────────►│                    │
 │◄── TITLES_GENERATED ────────────────────────────────────────────────────────────────────────│                    │
 │                          │                       │                      │                      │                    │
 │── POST /confirm-title ─►│                       │                      │                      │                    │
 │                          │── confirmTitle() ───────────────────────────────────────────────────────────────────►│
 │                          │── executePhase2 ──────►│                      │                      │                    │
 │                          │                       │── agent2(stream) ───►│                      │                    │
 │                          │                       │                      │── LLM stream ───────►│                    │
 │                          │                       │◄── "OUTLINE:xxx" ───│                      │                    │
 │                          │                       │── SSE: AGENT2_STREAMING ─────────────────►│                    │
 │◄── AGENT2_STREAMING ────────────────────────────────────────────────────────────────────────│                    │
 │                          │                       │                      │  (逐片段循环)        │                    │
 │                          │                       │── SSE: OUTLINE_GENERATED ────────────────►│                    │
 │◄── OUTLINE_GENERATED ──────────────────────────────────────────────────────────────────────│                    │
 │                          │                       │                      │                      │                    │
 │── POST /confirm-outline─►│                       │                      │                      │                    │
 │                          │── executePhase3 ──────►│                      │                      │                    │
 │                          │                       │── agent3(stream) ───►│                      │                    │
 │◄── AGENT3_STREAMING ──────────────────────────────────────────────────────────────────────│ (逐片段)            │
 │                          │                       │── agent4 ────────────►│                      │                    │
 │◄── AGENT4_COMPLETE ──────────────────────────────────────────────────────────────────────│                    │
 │                          │                       │── agent5(逐张) ──────►│                      │                    │
 │◄── IMAGE_COMPLETE ───────────────────────────────────────────────────────────────────────│ (每张)              │
 │                          │                       │── mergeImages ───────►│                      │                    │
 │◄── MERGE_COMPLETE ───────────────────────────────────────────────────────────────────────│                    │
 │◄── ALL_COMPLETE ─────────────────────────────────────────────────────────────────────────│                    │
 │◄── SSE complete ─────────────────────────────────────────────────────────────────────────│                    │
```