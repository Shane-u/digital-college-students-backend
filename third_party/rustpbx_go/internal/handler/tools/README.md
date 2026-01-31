# Function Tools 模块（数字大学生语音/文字指令链路）

## 整体链路

```
语音/文字指令 → 后端解析并调用对应 function → 后端向前端发送指令 → 前端执行具体操作
     → 前端反馈执行结果 → 后端：文字转语音(TTS)反馈
```

- **后端**：`siliconflow.go` 只负责 LLM 请求、`tools.AllTools()` 聚合、`tools.Dispatch()` 分发；各功能的**定义 + 实现**在 `tools/<功能名>/` 下，与 siliconflow 完全解耦。
- **前端控制**：需要控制前端的工具（如 `switchNavTab`）通过 `ToolContext.FrontendSender` 发指令；前端执行后把结果通过该接口返回，后端用该结果做 TTS。

## 目录结构（一功能一文件夹）

```
internal/handler/tools/
├── types.go       # SFTool / SFTools / ToolHandler
├── context.go     # ToolContext / ToolConfig / FrontendSender 接口
├── registry.go    # Register / AllTools / Dispatch
├── README.md
├── weather/       # 天气查询 queryWeather
│   ├── definition.go
│   ├── handler.go
│   └── init.go
├── search/        # 联网搜索 searchOnline
│   ├── definition.go
│   ├── handler.go
│   ├── types.go
│   └── init.go
├── image/         # 图像生成 generateImage、艺术字 transformText
│   ├── definition.go
│   ├── handler.go
│   ├── types.go
│   └── init.go
└── nav/           # 前端 nav 切换 switchNavTab（数字大学生界面）
    ├── definition.go
    ├── handler.go
    └── init.go
```

## 新增一个功能

1. 在 `internal/handler/tools/` 下新建目录，如 `myfeature/`。
2. `definition.go`：实现 `func Definition() tools.SFTool`，描述 name、description、parameters。
3. `handler.go`：实现 `func Handle(ctx *tools.ToolContext, args string) (string, error)`；需要发前端指令时使用 `ctx.FrontendSender.SendCommand(payload)`，返回值将用于 TTS。
4. `init.go`：`func init() { tools.Register("myToolName", Definition(), Handle) }`。
5. 在 `handler/siliconflow.go` 的 import 里增加：`_ "pbx_back_end/internal/handler/tools/myfeature"`，以触发 init 注册。

无需改 `siliconflow.go` 的 switch 或其它业务逻辑。

## 前端指令与反馈（FrontendSender）

- 在创建或配置 `SiliconFlowHandler` 后调用 `SetFrontendSender(impl)` 注入实现。
- `FrontendSender.SendCommand(payload)`：`payload` 一般为 JSON 或 `map[string]string`，例如 `{"type":"switchNavTab","tab":"home"}`；实现方通过 WebSocket/信令发给前端，并**阻塞或异步拿到前端执行结果**，将结果字符串返回，供后端 TTS 使用。
