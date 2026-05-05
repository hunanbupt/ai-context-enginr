# SSE 重连消息发送实现说明

本文档说明当前项目中 SSE 断线重连后的消息发送与恢复机制，覆盖后端发送、回放、快照、完成态补偿，以及前端如何配合消费消息。

## 目标

SSE 断开后重连时，需要保证：

- 前端已经渲染过的内容不会被清空。
- 断线期间产生的新消息不会丢失。
- 流式大纲、流式正文可以继续渲染。
- 任务完成瞬间断线时，前端仍能收到最终内容和完成事件。
- 浏览器自动重连和页面刷新后手动恢复都可用。

## 消息序号

后端为每个 `taskId` 维护一个自增序号：

```java
Map<String, AtomicLong> seqMap
```

每次调用 `SseEmitterManager.send()` 时：

1. 获取下一个 `seq`。
2. 将 `seq` 注入 JSON 消息体。
3. 使用 SSE `id` 字段发送同一个序号。
4. 将消息写入短缓冲区。

这样同一条消息同时具备：

- SSE 协议层的 `id`。
- JSON 数据层的 `seq`。

前端收到消息后保存最大的 `seq`，重连时通过 `?lastSeq=N` 告诉后端自己最后收到的位置。

浏览器自动重连时，也可能带上 `Last-Event-ID` 请求头。后端会取：

```java
effectiveLastSeq = max(lastSeq, Last-Event-ID)
```

以较新的位置作为回放起点。

## 后端短缓冲

`SseEmitterManager` 为每个任务维护短缓冲：

```java
Map<String, List<BufferedMessage>> messageBufferMap
```

缓冲元素包含：

```java
record BufferedMessage(long seq, String content)
```

当前策略是：所有通过 `send()` 发送的实时事件都会进入缓冲，包括：

- `AGENT2_STREAMING`
- `AGENT3_STREAMING`
- `IMAGE_COMPLETE`
- `MERGE_COMPLETE`
- `ERROR`
- `ALL_COMPLETE`
- 其他智能体完成事件

缓冲上限为 `MAX_BUFFER_SIZE = 300`。超过后丢弃最早消息。

这样设计是为了关闭断线窗口：即使某个流式碎片发生在连接断开和新连接建立之间，也可以通过 `seq > lastSeq` 回放补给前端。

## 流式内容累积器

除了短缓冲，后端还维护流式累积器：

```java
Map<String, StringBuilder> streamingAccumulatorMap
```

它只累积：

- `AGENT2_STREAMING` 的大纲流式文本。
- `AGENT3_STREAMING` 的正文流式文本。

作用是生成 `STREAMING_SNAPSHOT`。如果客户端断线较久，短缓冲可能已经滚动覆盖，快照仍能给出当前阶段已经累计的完整流式内容。

因此当前恢复机制是双保险：

- 短缓冲回放：补齐断线期间错过的事件。
- `STREAMING_SNAPSHOT`：提供当前阶段完整流式状态。

## 重连建立顺序

后端 `GET /article/progress/{taskId}` 建立连接时，会在同一个 task 级锁内执行：

1. 创建新的 `SseEmitter`。
2. 回放 `seq > effectiveLastSeq` 的缓冲消息。
3. 发送状态快照。
4. 激活新的 emitter。

伪流程：

```java
sseEmitterManager.runWithTaskLock(taskId, () -> {
    SseEmitter emitter = createEmitter(taskId, effectiveLastSeq);
    sendStateSnapshot(taskId, articleVO, emitter);
    activateEmitter(taskId, emitter);
});
```

这里的 task 级锁用于防止并发交叉：

- 回放和快照尚未发完时，实时消息不会插到前面。
- 连接替换期间，实时 `send()` 不会发到一个尚未准备好的 emitter。
- `complete()` 与重连不会各自拿不同状态导致漏尾部消息。

## 缓冲回放规则

回放时只发送：

```java
msg.seq > lastSeq
```

这样可以避免重连后重复发送前端已确认收到的消息。

特殊规则：缓冲中的 `ALL_COMPLETE` 会被跳过。

原因是前端收到 `ALL_COMPLETE` 后会关闭 EventSource。如果先回放 `ALL_COMPLETE`，后续快照中的 `MERGE_COMPLETE` 或完整内容可能还没到达，前端就已经关闭连接。

因此最终完成事件由状态快照阶段统一按顺序补发：

1. `MERGE_COMPLETE`
2. `ALL_COMPLETE`

## 状态快照

`sendStateSnapshot()` 会根据数据库状态和内存累积器发送幂等快照。

可能发送的事件包括：

### 标题快照

如果 `articleVO.titleOptions` 存在：

```json
{
  "type": "TITLES_GENERATED",
  "titleOptions": []
}
```

### 大纲快照

如果 `articleVO.outline` 存在：

```json
{
  "type": "OUTLINE_GENERATED",
  "outline": []
}
```

### 流式快照

如果 `streamingAccumulatorMap` 中有内容：

```json
{
  "type": "STREAMING_SNAPSHOT",
  "content": "...",
  "phase": "OUTLINE_GENERATING 或 CONTENT_GENERATING"
}
```

### 完成内容快照

如果 `articleVO.fullContent` 存在：

```json
{
  "type": "MERGE_COMPLETE",
  "fullContent": "..."
}
```

### 完成事件快照

如果 `articleVO.status == COMPLETED`：

```json
{
  "type": "ALL_COMPLETE",
  "taskId": "..."
}
```

所有快照事件通过 `sendDirect()` 发送。`sendDirect()` 会分配新的 `seq`，但不会再写入短缓冲，因为这些快照可以由数据库或累积器重新生成。

## 完成连接处理

`SseEmitterManager.complete(taskId)` 当前只做：

- 从 `emitterMap` 移除当前活跃连接。
- 调用 `emitter.complete()`。
- 清理 `streamingAccumulatorMap`。

它不会清理：

- `messageBufferMap`
- `seqMap`
- `taskLockMap`

原因是任务完成瞬间最容易断线。如果立即清理缓冲和序号，客户端刷新或自动重连时会拿不到尾部事件。

完成后的最终恢复依赖：

- 缓冲中保留的尾部消息。
- 数据库里的 `fullContent`。
- 数据库里的 `status=COMPLETED`。

## 正文阶段清理顺序

正文生成完成后，必须先保存完整文章，再清理流式累积器：

```java
articleService.saveArticleContent(taskId, state);
sseEmitterManager.clearStreamingAccumulator(taskId);
```

如果先清理累积器，再保存 DB，那么在这个中间窗口断线重连时：

- 内存没有正文快照。
- DB 还没有完整正文。

就会出现正文内容丢失或恢复不完整。

## 前端配合

前端 `connectSSE(taskId, options, lastSeq)` 会把 `lastSeq` 拼到 URL：

```text
/article/progress/{taskId}?lastSeq=N
```

收到消息时：

1. 如果 `msg.seq` 大于当前 `lastSeq`，更新本地 `lastSeq`。
2. 按 `msg.type` 渲染界面。
3. 将流式草稿保存到 `sessionStorage`。

`STREAMING_SNAPSHOT` 的处理是幂等的：

- 大纲快照只在快照内容长度不小于当前 `outlineRaw` 时覆盖。
- 正文快照只在快照内容长度不小于当前 `article.content` 时覆盖。

这样可以避免较旧快照覆盖前端已经渲染出来的新内容。

## 典型重连场景

### 场景一：大纲流式中断线

1. 前端最后收到 `seq=20`。
2. 断线期间后端继续发送 `seq=21..30`，这些消息进入短缓冲。
3. 前端重连：`?lastSeq=20`。
4. 后端回放 `21..30`。
5. 后端再发送 `STREAMING_SNAPSHOT` 作为完整状态兜底。
6. 新连接激活，后续消息实时推送。

### 场景二：正文生成完成瞬间断线

1. 后端发送 `MERGE_COMPLETE` 和 `ALL_COMPLETE` 附近发生断线。
2. `complete()` 不清理短缓冲。
3. 前端刷新重连。
4. 后端从 DB 读取 `fullContent` 和 `status=COMPLETED`。
5. 快照阶段按顺序发送 `MERGE_COMPLETE`，再发送 `ALL_COMPLETE`。
6. 前端完整渲染后关闭连接。

### 场景三：浏览器自动重连

1. 浏览器 EventSource 自动带上 `Last-Event-ID`。
2. 后端读取该请求头。
3. 与 URL 中的 `lastSeq` 取最大值。
4. 从该位置之后开始回放。

## 注意事项

- `sendDirect()` 快照消息不进入短缓冲，避免快照重复污染回放队列。
- `ALL_COMPLETE` 不从短缓冲回放，避免前端提前关闭连接。
- `messageBufferMap` 当前是内存级别，服务重启后不可恢复。
- `MAX_BUFFER_SIZE` 过小会增加依赖 `STREAMING_SNAPSHOT` 的概率。
- 如果未来要支持长时间离线恢复，应将消息缓冲持久化或扩大任务级状态存储。

