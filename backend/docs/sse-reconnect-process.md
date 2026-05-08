# SSE 重连过程详解

## 核心数据结构（`SseEmitterManager`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `emitterMap` | `Map<String, SseEmitter>` | 当前活跃的 SSE 连接 |
| `messageBufferMap` | `Map<String, List<BufferedMessage>>` | 缓冲已发送的消息，最多 300 条 |
| `streamingAccumulatorMap` | `Map<String, StringBuilder>` | 流式内容的累积文本 |
| `seqMap` | `Map<String, AtomicLong>` | 每个 taskId 的自增序号 |
| `taskLockMap` | `Map<String, Object>` | 每个 taskId 的锁对象 |

---

## 消息发送（正常流程）

每次 `SseEmitterManager.send(taskId, message)` 执行：

1. **分配序号** — `getNextSeq(taskId)` → `seqMap` 中 `AtomicLong.incrementAndGet()`，从 1 开始自增
2. **注入 seq** — 将 seq 写入消息 JSON 的 `"seq"` 字段
3. **写入缓冲区** — 存入 `messageBufferMap`，超过 300 条时淘汰最早的
4. **实时推送** — 如果 `emitterMap` 中有活跃连接，立即 SSE 推送；没有则只缓冲

```java
// SseEmitterManager.java:81
public void send(String taskId, String message) {
    synchronized (getTaskLock(taskId)) {
        long seq = getNextSeq(taskId);                    // ①
        String enrichedMessage = injectSeq(message, seq); // ②
        bufferMessage(taskId, new BufferedMessage(seq, enrichedMessage)); // ③

        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter == null) return;  // 无活跃连接，只缓冲

        emitter.send(SseEmitter.event()                   // ④
                .id(String.valueOf(seq))
                .data(enrichedMessage)
                .reconnectTime(SSE_RECONNECT_TIME_MS));
    }
}
```

---

## 重连流程（6 步）

### 第 1 步：前端断开重连

浏览器 `EventSource` 断开后自动重连，请求中带两个信息：
- **URL 参数** `?lastSeq=N` — 手动传入
- **请求头** `Last-Event-ID` — 浏览器自动携带（值为最后收到的 `event.id`）

### 第 2 步：Controller 解析客户端进度

```java
// ArticleController.java:105
@GetMapping("/progress/{taskId}")
public SseEmitter getProgress(@PathVariable String taskId,
        @RequestParam(required = false, defaultValue = "0") long lastSeq,
        HttpServletRequest request) {

    String lastEventIdHeader = request.getHeader("Last-Event-ID");
    long lastEventId = parseOrZero(lastEventIdHeader);

    // 取最大值，确保不丢消息
    long effectiveLastSeq = Math.max(lastSeq, lastEventId);
    ...
}
```

`effectiveLastSeq` = 客户端确认已收到的最后一条消息序号。

### 第 3 步：在锁内依次执行三个操作

```java
// ArticleController.java:131
sseEmitterManager.runWithTaskLock(taskId, () -> {

    // 3a. 创建 emitter（内部回放缓冲消息）
    SseEmitter emitter = sseEmitterManager.createEmitter(taskId, effectiveLastSeq);

    // 3b. 发送状态快照
    sendStateSnapshot(taskId, articleVO, emitter);

    // 3c. 激活 emitter，后续实时消息才能推送
    sseEmitterManager.activateEmitter(taskId, emitter);
});
```

加锁保证这期间不会插入新的 `send()` 调用，防止回放和实时消息乱序。

### 第 4 步：回放缓冲消息

```java
// SseEmitterManager.java:192
private void replayBufferedMessages(String taskId, SseEmitter emitter, long lastSeq) {
    List<BufferedMessage> buffer = messageBufferMap.get(taskId);
    for (BufferedMessage msg : buffer) {
        if (msg.seq() <= lastSeq)  →  skip  // 客户端已收到
        if (msg.content().contains("\"ALL_COMPLETE\""))  →  skip  // 终态不重放
        emitter.send(...)  // 按 seq 顺序补发
    }
}
```

### 第 5 步：发送状态快照

```java
// ArticleController.java:162
private void sendStateSnapshot(String taskId, ArticleVO articleVO, SseEmitter emitter) {
    // 标题已生成 → TITLES_GENERATED
    // 大纲已生成 → OUTLINE_GENERATED
    // 正在流式中 → STREAMING_SNAPSHOT（含已累积的流式内容）
    // 正文已完成 → MERGE_COMPLETE
    // 全部完成   → ALL_COMPLETE
}
```

快照使用 `sendDirect()` — 不走缓冲区、不分配全局 seq，直接发到当前 emitter。

### 第 6 步：激活 emitter

```java
// SseEmitterManager.java:76
public void activateEmitter(String taskId, SseEmitter emitter) {
    emitterMap.put(taskId, emitter);
}
```

激活后 `send()` 能找到 emitter，实时消息恢复推送。

---

## 时序图

```
前端断开 → 浏览器自动重连（带 Last-Event-ID）
              │
              ▼
      Controller.getProgress()
         effectiveLastSeq = max(lastSeq, Last-Event-ID)
              │
              ▼
         runWithTaskLock(taskId) {
              │
              ├─① createEmitter(taskId, effectiveLastSeq)
              │     移除旧 emitter
              │     回放 buffer 中 seq > effectiveLastSeq 的消息
              │
              ├─② sendStateSnapshot()
              │     推送已完成数据快照 + 流式累积内容
              │
              └─③ activateEmitter()
                    将新 emitter 放入 emitterMap，实时消息恢复推送
         }
```

---

## 关键设计点

1. **seq 去重**：每条消息带自增序号，重连时客户端告诉服务端收到哪了，精确补发不漏不重
2. **锁保护**：创建 emitter + 回放 + 快照 + 激活在同个锁内完成，防止实时消息插队
3. **双层兜底**：增量回放（buffer 中的消息）+ 全量快照（已完成数据 + 流式累积），确保任何断线场景都能恢复
4. **AL_COMPLETE 不重放**：终态消息被过滤，避免客户端重连后误判任务结束