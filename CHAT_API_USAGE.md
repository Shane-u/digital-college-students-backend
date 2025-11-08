# 聊天对话功能使用说明

## 基础信息

- **基础URL**: `http://localhost:8121/api` (根据你的配置)
- **Content-Type**: `application/json`

## API接口列表

### 1. 非流式聊天（等待完整响应）

**接口**: `POST /api/chat/completions`

**请求示例**:
```json
{
  "userId": 1,
  "sessionId": "可选，如果不提供会自动创建新会话",
  "messages": [
    {
      "role": "user",
      "content": "你好，请介绍一下自己"
    }
  ],
  "model": "doubao-seed-1-6-251015",
  "temperature": 0.7,
  "maxTokens": 2000
}
```

**响应示例**:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "chatcmpl-xxx",
    "object": "chat.completion",
    "created": 1234567890,
    "model": "doubao-seed-1-6-251015",
    "choices": [
      {
        "index": 0,
        "message": {
          "role": "assistant",
          "content": "你好！我是豆包AI助手..."
        },
        "finish_reason": "stop"
      }
    ],
    "usage": {
      "prompt_tokens": 10,
      "completion_tokens": 20,
      "total_tokens": 30
    }
  }
}
```

---

### 2. 流式聊天（SSE格式）

**接口**: `POST /api/chat/completions/stream`

**请求示例**:
```json
{
  "userId": 1,
  "sessionId": "session-id-123",
  "messages": [
    {
      "role": "user",
      "content": "写一首关于春天的诗"
    }
  ],
  "stream": true
}
```

**响应格式** (Server-Sent Events):
```
data: {"id":"xxx","object":"chat.completion.chunk","created":1234567890,"model":"doubao-seed-1-6-251015","choices":[{"index":0,"delta":{"content":"春"},"finish_reason":null}]}

data: {"id":"xxx","object":"chat.completion.chunk","created":1234567890,"model":"doubao-seed-1-6-251015","choices":[{"index":0,"delta":{"content":"天"},"finish_reason":null}]}

data: [DONE]
```

**前端JavaScript示例**:
```javascript
const eventSource = new EventSource('/api/chat/completions/stream', {
  method: 'POST',
  body: JSON.stringify({
    userId: 1,
    messages: [{ role: 'user', content: '你好' }]
  })
});

eventSource.onmessage = (event) => {
  if (event.data === '[DONE]') {
    eventSource.close();
    return;
  }
  const data = JSON.parse(event.data);
  console.log('收到内容:', data.choices[0].delta.content);
};
```

---

### 3. 流式聊天（纯文本格式）

**接口**: `POST /api/chat/completions/stream/text`

**请求**: 同流式聊天接口

**响应**: 纯文本流，每行一个数据块
```
data: 你
data: 好
data: ！
data: [DONE]
```

---

### 4. 多模态输入（图片+文字）

**接口**: `POST /api/chat/completions`

**请求示例**:
```json
{
  "userId": 1,
  "messages": [
    {
      "role": "user",
      "content": "这张图片里有什么？",
      "imageUrls": [
        "http://example.com/image.jpg",
        "http://example.com/image2.jpg"
      ]
    }
  ]
}
```

**或者使用视频**:
```json
{
  "userId": 1,
  "messages": [
    {
      "role": "user",
      "content": "这个视频的主要内容是什么？",
      "videoUrls": [
        "http://example.com/video.mp4"
      ]
    }
  ]
}
```

---

## 会话管理

### 1. 创建会话

**接口**: `POST /api/chat/sessions`

**请求示例**:
```json
{
  "userId": 1,
  "title": "我的对话"  
}
```

**响应示例**:
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "session-id-123",
    "userId": 1,
    "title": "我的对话",
    "createTime": "2024-01-01T10:00:00",
    "updateTime": "2024-01-01T10:00:00"
  }
}
```

---

### 2. 获取会话列表

**接口**: `GET /api/chat/sessions?userId=1`

**响应示例**:
```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": "session-id-123",
      "userId": 1,
      "title": "我的对话",
      "createTime": "2024-01-01T10:00:00",
      "updateTime": "2024-01-01T10:00:00"
    }
  ]
}
```

---

### 3. 获取会话消息列表

**接口**: `GET /api/chat/sessions/{sessionId}/messages?userId=1`

**响应示例**:
```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": "msg-1",
      "sessionId": "session-id-123",
      "userId": 1,
      "role": "user",
      "content": "你好",
      "imageUrls": null,
      "videoUrls": null,
      "createTime": "2024-01-01T10:00:00"
    },
    {
      "id": "msg-2",
      "sessionId": "session-id-123",
      "userId": 1,
      "role": "assistant",
      "content": "你好！有什么可以帮助你的吗？",
      "createTime": "2024-01-01T10:00:01"
    }
  ]
}
```

---

### 4. 删除会话

**接口**: `DELETE /api/chat/sessions/{sessionId}?userId=1`

**响应示例**:
```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

---

## 完整使用流程示例

### 场景：用户开始一个新对话

**步骤1**: 发送第一条消息（自动创建会话）
```bash
curl -X POST http://localhost:8121/api/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "messages": [
      {
        "role": "user",
        "content": "你好"
      }
    ]
  }'
```

**响应**会包含AI的回复，同时系统会自动创建会话并保存消息。

**步骤2**: 继续对话（使用返回的sessionId）
```bash
curl -X POST http://localhost:8121/api/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "sessionId": "从第一次响应中获取的sessionId",
    "messages": [
      {
        "role": "user",
        "content": "继续刚才的话题"
      }
    ]
  }'
```

系统会自动加载历史消息，保持对话上下文。

**步骤3**: 查看历史对话
```bash
curl http://localhost:8121/api/chat/sessions?userId=1
```

**步骤4**: 查看某个会话的所有消息
```bash
curl http://localhost:8121/api/chat/sessions/{sessionId}/messages?userId=1
```

---

## 参数说明

### ChatRequest 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID，用于隔离聊天记录 |
| sessionId | String | 否 | 会话ID，不提供则自动创建新会话 |
| messages | List<Message> | 是 | 消息列表 |
| model | String | 否 | 模型名称，默认使用配置的模型 |
| temperature | Double | 否 | 温度参数（0-2），控制随机性 |
| maxTokens | Integer | 否 | 最大生成令牌数 |
| topP | Double | 否 | Top-p采样参数（0-1） |
| stream | Boolean | 否 | 是否流式输出，默认true |

### Message 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| role | String | 是 | 消息角色：user/assistant/system |
| content | String | 否* | 文本内容（如果有多模态内容，此字段可选） |
| imageUrls | List<String> | 否 | 图片URL列表 |
| videoUrls | List<String> | 否 | 视频URL列表 |

*注意：content、imageUrls、videoUrls至少需要提供一个

---

## 注意事项

1. **用户隔离**: 每个用户的聊天记录完全隔离，通过userId区分
2. **会话管理**: 如果不提供sessionId，系统会自动创建新会话
3. **历史记录**: 系统会自动加载会话的历史消息，保持对话上下文
4. **多模态支持**: 支持同时发送文字、图片、视频
5. **流式输出**: 流式接口适合需要实时显示AI回复的场景
6. **数据存储**: 所有聊天记录保存在MongoDB中，支持持久化

---

## 错误处理

**错误响应格式**:
```json
{
  "code": 40000,
  "message": "请求参数错误",
  "data": null
}
```

**常见错误码**:
- `40000`: 请求参数错误
- `40100`: 未登录
- `40101`: 无权限
- `40400`: 请求数据不存在
- `50000`: 系统内部异常

---

## 前端集成示例

### React + Axios 示例

```javascript
import axios from 'axios';

// 非流式聊天
async function chat(userId, message, sessionId = null) {
  const response = await axios.post('/api/chat/completions', {
    userId,
    sessionId,
    messages: [{ role: 'user', content: message }]
  });
  return response.data.data.choices[0].message.content;
}

// 流式聊天
function streamChat(userId, message, sessionId, onChunk, onComplete) {
  const eventSource = new EventSource('/api/chat/completions/stream', {
    method: 'POST',
    body: JSON.stringify({
      userId,
      sessionId,
      messages: [{ role: 'user', content: message }],
      stream: true
    })
  });

  eventSource.onmessage = (event) => {
    if (event.data === '[DONE]') {
      eventSource.close();
      onComplete();
      return;
    }
    const data = JSON.parse(event.data);
    const content = data.choices[0]?.delta?.content;
    if (content) {
      onChunk(content);
    }
  };

  eventSource.onerror = (error) => {
    console.error('SSE错误:', error);
    eventSource.close();
  };
}
```

---

## 测试建议

1. 使用Postman或curl测试各个接口
2. 测试多模态输入（图片+文字）
3. 测试会话管理和历史记录加载
4. 测试流式输出的实时性
5. 验证用户隔离是否正常工作

