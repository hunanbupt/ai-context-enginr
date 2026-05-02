# SSE 断线重连恢复方案 — 前端接入指南

## 问题

在流式生成大纲 / 正文过程中，用户刷新页面会导致 SSE 连接断开。原先刷新后重新连接只能收到**新产生的消息**，断开期间的消息（含已流式输出的内容）全部丢失，前端无法恢复到刷新前的状态。

## 后端修复内容

### 1. 消息缓冲 + 流式内容累积

`SseEmitterManager` 新增能力：

- **消息缓冲**：所有通过 SSE 发送的消息自动缓存（最多 300 条），新连接建立时自动回放。建立新连接前会先关闭旧连接，避免回调误删
- **流式内容快照**：正在流式输出的文本（大纲、正文）会实时累积，重连时发送 `STREAMING_SNAPSHOT` 事件，包含中断前已输出的**完整文本**

### 2. 新增 SSE 事件类型

| 事件 type | 触发时机 | payload |
|-----------|---------|---------|
| `STREAMING_SNAPSHOT` | 重连时，如果后台正在流式输出 | `{ type, content, phase }` |

### 3. 重连时的消息顺序

```
1. [回放] 所有缓冲的完成事件（TITLES_GENERATED、OUTLINE_GENERATED 等）
2. [快照] 如果后台正在流式输出，发送 STREAMING_SNAPSHOT（含已累积全文）
3. [快照] TITLES_GENERATED / OUTLINE_GENERATED（从 DB 读取的最新状态）
4. [直播] 继续接收实时流式消息（AGENT2_STREAMING / AGENT3_STREAMING / ...）
```

## 前端需要的改动

### 改动一：处理 `STREAMING_SNAPSHOT` 事件

这是**新事件类型**，前端当前不处理它。收到此事件时，应该用它携带的完整文本替换当前流式输出区域。

```javascript
// SSE 事件处理中新增
if (data.type === 'STREAMING_SNAPSHOT') {
  // data.content 是中断前已流式输出的完整文本
  // data.phase 告知当前是哪个阶段在流式输出
  //   - OUTLINE_GENERATING → 更新大纲流式区域
  //   - CONTENT_GENERATING → 更新正文流式区域

  if (data.phase === 'OUTLINE_GENERATING') {
    // 用 data.content 的完整文本替换大纲流式展示区域
    setOutlineStreamingText(data.content);
    // 切换 UI 到"大纲流式生成中"状态
    switchToPhase('OUTLINE_GENERATING');
  } else if (data.phase === 'CONTENT_GENERATING') {
    // 用 data.content 的完整文本替换正文流式展示区域
    setContentStreamingText(data.content);
    // 切换 UI 到"正文流式生成中"状态
    switchToPhase('CONTENT_GENERATING');
  }
}
```

### 改动二：`TITLES_GENERATED` / `OUTLINE_GENERATED` 事件幂等处理

重连时后端会重放这些事件。确保收到重复的完成事件时，前端能正确处理（通常是幂等的——直接覆盖状态即可，不需要检查是否重复）。

### 改动三：重连后根据 `phase` 恢复 UI 状态

收到 `STREAMING_SNAPSHOT` 后，前端应立刻将 UI 切换到正确的阶段界面：

```javascript
function switchToPhase(phase) {
  switch (phase) {
    case 'TITLE_SELECTING':
      showTitleSelectionScreen();  // titleOptions 从文章详情接口获取
      break;
    case 'OUTLINE_GENERATING':
      showOutlineGeneratingScreen(); // 展示流式输出区域
      break;
    case 'OUTLINE_EDITING':
      showOutlineEditingScreen();   // outline 从文章详情接口获取
      break;
    case 'CONTENT_GENERATING':
      showContentGeneratingScreen(); // 展示流式输出区域
      break;
  }
}
```

> 完成数据（titleOptions、outline）可以通过 `GET /article/{taskId}` 获取，该接口返回的 VO 中这些字段在阶段完成后就已写入 DB。

### 改动四：重新连接时主动查询一次文章详情

刷新页面后重新建立 SSE 连接前，先调用 `GET /article/{taskId}` 获取当前状态：

```javascript
async function reconnect(taskId) {
  // 1. 先获取当前状态
  const article = await fetch(`/article/${taskId}`).then(r => r.json());

  // 2. 根据 phase 恢复 UI
  switchToPhase(article.data.phase);

  // 3. 如果有已完成的数据，直接展示
  if (article.data.titleOptions) {
    displayTitleOptions(article.data.titleOptions);
  }
  if (article.data.outline) {
    displayOutline(article.data.outline);
  }

  // 4. 建立 SSE 连接（会自动收到回放消息 + 快照 + 直播）
  const eventSource = new EventSource(`/article/progress/${taskId}`);
  eventSource.onmessage = handleSseMessage;
}
```

## 完整流程示意

```
用户刷新页面
  │
  ├─ 旧 SSE 连接关闭（后端自动清理，不影响新连接）
  │
  ├─ GET /article/{taskId}                    → 获取当前 phase + 已完成数据
  │
  ├─ 建立新 SSE: GET /article/progress/{taskId}
  │   │
  │   ├─ [后端自动回放] 所有缓冲消息
  │   │   例：AGENT1_COMPLETE, AGENT2_STREAMING × N, ...
  │   │
  │   ├─ [后端发送快照] STREAMING_SNAPSHOT       ← 新事件！前端需要处理
  │   │   { content: "已流式输出的完整文本", phase: "OUTLINE_GENERATING" }
  │   │
  │   ├─ [后端发送快照] TITLES_GENERATED / OUTLINE_GENERATED（如有）
  │   │
  │   └─ [继续直播] AGENT2_STREAMING / AGENT3_STREAMING / ...
  │
  └─ 前端恢复到刷新前的状态，无缝继续接收流式内容
```

## 前端改动总结

| 改动项 | 优先级 | 说明 |
|--------|--------|------|
| 处理 `STREAMING_SNAPSHOT` 事件 | **必须** | 用快照中的完整文本替换流式区域 |
| 根据 `phase` 恢复 UI 状态 | **必须** | `STREAMING_SNAPSHOT` 携带 `phase` 字段 |
| 幂等处理完成事件 | 建议 | 防止重放消息导致 UI 异常 |
| 重连时查询文章详情 | 建议 | 先拿已完成数据再连 SSE，体验更好 |
