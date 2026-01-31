package tools

import "fmt"

// ToolHandler 单工具执行函数：参数为 args JSON 字符串，返回给用户的文案（供 TTS）或错误
type ToolHandler func(ctx *ToolContext, args string) (string, error)

type entry struct {
	tool    SFTool
	handler ToolHandler
}

var registry = make(map[string]entry)

// Register 注册一个 function_tool：名称、定义、执行函数（name 需与 def.Function.Name 一致）
func Register(name string, def SFTool, handler ToolHandler) {
	registry[name] = entry{tool: def, handler: handler}
}

// AllTools 返回所有已注册工具的 SFTools，供 LLM 请求使用
func AllTools() SFTools {
	out := make(SFTools, 0, len(registry))
	for _, e := range registry {
		out = append(out, e.tool)
	}
	return out
}

// Dispatch 根据名称分发到对应 handler，返回执行结果文案或错误
func Dispatch(name string, args string, ctx *ToolContext) (string, error) {
	e, ok := registry[name]
	if !ok {
		return "", fmt.Errorf("unknown function: %s", name)
	}
	return e.handler(ctx, args)
}
