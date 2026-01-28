# 百炼 AI 接口使用说明

## 概述

百炼 AI 接口提供了流式聊天功能，支持多会话隔离。前端需要先创建会话（获取 chat_id），然后使用该 chat_id 进行聊天。

## 接口列表

### 1. 创建聊天会话

**接口地址**: `POST /api/bailian/chat/create`

**请求参数**: 无

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "chatId": "ea8b1168-dd6d-11f0-9dfb-d28b20064f4d"
  },
  "message": "ok"
}
```

**说明**: 
- 前端需要保存返回的 `chatId`，用于后续的聊天请求
- 每个会话都有独立的 `chatId`，实现会话隔离

---

### 2. 流式聊天（SSE 方式）

**接口地址**: `POST /api/bailian/chat/stream`

**请求头**: 
```
Content-Type: application/json
```

**请求参数**:
```json
{
  "chatId": "ea8b1168-dd6d-11f0-9dfb-d28b20064f4d",
  "question": "用户输入的问题",
  "stream": true
}
```

**响应格式**: Server-Sent Events (SSE)

**响应示例**:
```
event: message
data: {"chat_id":"ea8b1168-dd6d-11f0-9dfb-d28b20064f4d","id":"53106ca7-8872-43fe-a67a-7053f719ba95","answer_type":"workflow_v2_publish_stream","is_end":false,"content":"这里是","node_id":"model","node_name":"大模型",...}

event: message
data: {"chat_id":"ea8b1168-dd6d-11f0-9dfb-d28b20064f4d","id":"53106ca7-8872-43fe-a67a-7053f719ba95","answer_type":"workflow_v2_publish_stream","is_end":false,"content":"回答","node_id":"model","node_name":"大模型",...}

event: done
data: [DONE]
```

**说明**:
- 使用 SSE 协议，前端需要使用 `EventSource` 或类似库来接收流式数据
- 每个数据块包含 `content` 字段（当前内容片段）
- `is_end` 为 `true` 时表示流结束
- 最后会发送 `[DONE]` 标记

---

### 3. 流式聊天（Flux 方式）

**接口地址**: `POST /api/bailian/chat/stream/flux`

**请求头**: 
```
Content-Type: application/json
Accept: text/event-stream
```

**请求参数**:
```json
{
  "chatId": "ea8b1168-dd6d-11f0-9dfb-d28b20064f4d",
  "question": "用户输入的问题",
  "stream": true
}
```

**响应格式**: Server-Sent Events (SSE)

**响应示例**:
```
event: message
data: 这里是

event: message
data: 回答

event: done
data: [DONE]
```

**说明**:
- 基于 WebFlux 的响应式实现
- 内容中的空格会被编码为 `&#32;`，换行会被编码为 `&#92n`
- 前端需要解码这些特殊字符

---

## 前端使用示例

### JavaScript (使用 EventSource)

```javascript
// 1. 创建会话
async function createChat() {
  const response = await fetch('/api/bailian/chat/create', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    }
  });
  const result = await response.json();
  return result.data.chatId;
}

// 2. 流式聊天
function streamChat(chatId, question) {
  // 注意：EventSource 不支持 POST，需要使用 fetch + ReadableStream
  fetch('/api/bailian/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      chatId: chatId,
      question: question,
      stream: true
    })
  }).then(response => {
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    
    function read() {
      reader.read().then(({ done, value }) => {
        if (done) {
          console.log('Stream complete');
          return;
        }
        
        const text = decoder.decode(value);
        const lines = text.split('\n');
        
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.substring(6);
            if (data === '[DONE]') {
              console.log('Chat complete');
              return;
            }
            
            try {
              const json = JSON.parse(data);
              console.log('Content:', json.content);
              // 处理内容，例如追加到页面
            } catch (e) {
              // 忽略解析错误
            }
          }
        }
        
        read();
      });
    }
    
    read();
  });
}

// 使用示例
async function main() {
  const chatId = await createChat();
  console.log('Chat ID:', chatId);
  
  streamChat(chatId, '你好，请介绍一下自己');
}
```

### JavaScript (使用 Flux 接口)

```javascript
function streamChatFlux(chatId, question) {
  const eventSource = new EventSource('/api/bailian/chat/stream/flux');
  
  // 注意：EventSource 不支持 POST，这里需要使用 fetch
  fetch('/api/bailian/chat/stream/flux', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    },
    body: JSON.stringify({
      chatId: chatId,
      question: question,
      stream: true
    })
  }).then(response => {
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    
    function read() {
      reader.read().then(({ done, value }) => {
        if (done) return;
        
        const text = decoder.decode(value);
        const lines = text.split('\n');
        
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.substring(6);
            
            // 解码特殊字符
            const decoded = data
              .replace(/&#32;/g, ' ')
              .replace(/&#92n/g, '\n');
            
            if (decoded === '[DONE]') {
              console.log('Chat complete');
              return;
            }
            
            console.log('Content:', decoded);
            // 处理内容
          }
        }
        
        read();
      });
    }
    
    read();
  });
}
```

---

## 配置说明

在 `application.yml` 中配置百炼相关参数：

```yaml
bailian:
  api-key: b8cc3f295398df338425e91b831d135e  # API密钥
  base-url: https://bailian.cdut.edu.cn/cre_llm  # 基础URL
  application-id: a7169d00-dd66-11f0-b4b9-d28b20064f4d  # 应用ID
  timeout: 60000  # 请求超时时间（毫秒）
```

---

## 注意事项

1. **会话隔离**: 每个 `chatId` 代表一个独立的会话，前端需要管理多个会话的 `chatId`
2. **流式输出**: 响应是流式的，需要使用支持 SSE 的客户端库
3. **超时设置**: 流式请求的超时时间为 5 分钟（300秒）
4. **错误处理**: 如果 `chatId` 或 `question` 为空，会立即返回错误事件
5. **特殊字符**: Flux 接口会对空格和换行进行编码，前端需要解码

---

## 与 ChatController 的区别

- `ChatController`: 使用豆包/DeepSeek 等模型，支持自定义模型参数
- `BaiLianController`: 使用百炼 AI 平台，需要先创建会话，流程更简单

两个控制器互不影响，可以同时使用。
