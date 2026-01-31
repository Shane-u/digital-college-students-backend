package weather

import "pbx_back_end/internal/handler/tools"

// Definition 天气查询 function 定义
func Definition() tools.SFTool {
	return tools.SFTool{
		Type: "function",
		Function: struct {
			Description string      `json:"description"`
			Name        string      `json:"name"`
			Parameters  interface{} `json:"parameters"`
			Required    []string    `json:"required"`
		}{
			Description: "查询指定地点的天气信息,地点必须是中文，不能是英文！需要剥离出省份信息放到sheng参数里面,并且需要剥离出地点的信息放到place参数里面",
			Name:        "queryWeather",
			Parameters: struct {
				Sheng string `json:"sheng"`
				Place string `json:"place"`
			}{
				Sheng: "",
				Place: "",
			},
			Required: []string{"sheng", "place"},
		},
	}
}
