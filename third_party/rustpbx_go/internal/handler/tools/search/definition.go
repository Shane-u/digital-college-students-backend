package search

import "pbx_back_end/internal/handler/tools"

// Definition 联网搜索 function 定义
func Definition() tools.SFTool {
	return tools.SFTool{
		Type: "function",
		Function: struct {
			Description string      `json:"description"`
			Name        string      `json:"name"`
			Parameters  interface{} `json:"parameters"`
			Required    []string    `json:"required"`
		}{
			Description: "The function sends a query to the browser and returns relevant results based on the search terms provided. The model should avoid using this function if it already possesses the required information or can provide a confident answer without external data",
			Name:        "searchOnline",
			Parameters: struct {
				Query struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				} `json:"query"`
			}{
				Query: struct {
					Description string `json:"description"`
					Type        string `json:"type"`
				}{
					Description: "What to search for",
					Type:        "string",
				},
			},
			Required: []string{"query"},
		},
	}
}
