package image

import "pbx_back_end/internal/handler/tools"

// GenerateImageDefinition 图像生成 function 定义
func GenerateImageDefinition() tools.SFTool {
	return tools.SFTool{
		Type: "function",
		Function: struct {
			Description string      `json:"description"`
			Name        string      `json:"name"`
			Parameters  interface{} `json:"parameters"`
			Required    []string    `json:"required"`
		}{
			Description: "Generate an image based on a given prompt",
			Name:        "generateImage",
			Parameters: struct {
				Prompt struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				} `json:"prompt"`
			}{
				Prompt: struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				}{
					Description: "A text prompt describing the image to be generated",
					Type:        "string",
				},
			},
			Required: []string{"prompt"},
		},
	}
}

// TransformTextDefinition 文字变形 function 定义
func TransformTextDefinition() tools.SFTool {
	return tools.SFTool{
		Type: "function",
		Function: struct {
			Description string      `json:"description"`
			Name        string      `json:"name"`
			Parameters  interface{} `json:"parameters"`
			Required    []string    `json:"required"`
		}{
			Description: "对输入的文字进行艺术变形处理，支持多种变形样式如花体字、艺术字等，可以用于装饰性文字展示",
			Name:        "transformText",
			Parameters: struct {
				Text struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				} `json:"text"`
				Prompt struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				} `json:"prompt"`
				Style struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				} `json:"style"`
			}{
				Text: struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				}{
					Description: "需要进行变形处理的文字内容",
					Type:        "string",
				},
				Prompt: struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				}{
					Description: "艺术字风格描述提示词",
					Type:        "string",
				},
				Style: struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				}{
					Description: "变形样式，如flower(花体)、art(艺术字)、gothic(哥特体)等",
					Type:        "string",
				},
			},
			Required: []string{"text"},
		},
	}
}
