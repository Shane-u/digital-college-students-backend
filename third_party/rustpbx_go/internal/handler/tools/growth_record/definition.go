package growth_record

import "pbx_back_end/internal/handler/tools"

// DefinitionAddGrowthRecord 定义添加成长记录 function
func DefinitionAddGrowthRecord() tools.SFTool {
	return tools.SFTool{
		Type: "function",
		Function: struct {
			Description string      `json:"description"`
			Name        string      `json:"name"`
			Parameters  interface{} `json:"parameters"`
			Required    []string    `json:"required"`
		}{
			Description: "添加成长记录。当用户说「添加成长记录」「记录一下今天的事情」「帮我添加一个成长记录」等时调用。可以填写事件名称、重要程度（1-5颗星）、个人感悟等信息。重要程度为4及以上的事件将被列入里程碑中。图片和文件需要用户手动上传。",
			Name:        "addGrowthRecord",
			Parameters: struct {
				EventName struct {
					Description string `json:"description"`
					Type        string `json:"type"`
					MaxLength   int    `json:"maxLength,omitempty"`
				} `json:"eventName"`
				Importance struct {
					Description string `json:"description"`
					Type        string `json:"type"`
					Minimum     int    `json:"minimum,omitempty"`
					Maximum     int    `json:"maximum,omitempty"`
				} `json:"importance"`
				PersonalInsight struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				} `json:"personalInsight"`
				Date struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				} `json:"date"`
			}{
				EventName: struct {
					Description string `json:"description"`
					Type        string `json:"type"`
					MaxLength   int    `json:"maxLength,omitempty"`
				}{
					Description: "事件名称，最多30个字符",
					Type:        "string",
					MaxLength:   30,
				},
				Importance: struct {
					Description string `json:"description"`
					Type        string `json:"type"`
					Minimum     int    `json:"minimum,omitempty"`
					Maximum     int    `json:"maximum,omitempty"`
				}{
					Description: "重要程度，1-5颗星，默认为4。重要程度为4及以上的事件将被列入里程碑中",
					Type:        "integer",
					Minimum:     1,
					Maximum:     5,
				},
				PersonalInsight: struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				}{
					Description: "个人感悟，对这次事件的思考和感受",
					Type:        "string",
				},
				Date: struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				}{
					Description: "日期，格式为 YYYY-MM-DD，默认为今天。例如：2026-01-31",
					Type:        "string",
				},
			},
			Required: []string{"eventName"},
		},
	}
}

// DefinitionAddToday 定义添加今日成长记录 function
func DefinitionAddToday() tools.SFTool {
	return tools.SFTool{
		Type: "function",
		Function: struct {
			Description string      `json:"description"`
			Name        string      `json:"name"`
			Parameters  interface{} `json:"parameters"`
			Required    []string    `json:"required"`
		}{
			Description: "直接添加今日的成长记录。当用户说「添加今日记录」「记录今天」等时调用。无需参数。",
			Name:        "addTodayGrowthRecord",
			Parameters:  struct{}{},
			Required:    []string{},
		},
	}
}
