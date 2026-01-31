package tools

// SFTool 单个 function_call 定义（SiliconFlow/OpenAI 兼容）
type SFTool struct {
	Type     string `json:"type"`
	Function struct {
		Description string      `json:"description"`
		Name        string      `json:"name"`
		Parameters  interface{} `json:"parameters"`
		Required    []string    `json:"required"`
	} `json:"function"`
}

// SFTools 供 LLM 请求使用的 tools 列表
type SFTools []SFTool
