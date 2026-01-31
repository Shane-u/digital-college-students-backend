package nav

import "pbx_back_end/internal/handler/tools"

// Definition 前端 nav 切换 function 定义（数字大学生界面操作）
// tab 取值与前端路由一致，便于正确触发：home=首页, competition=竞赛活动, career=职业发展, knowledge-graph=技能图谱, twin-study=季季伴学/孪孪伴学, growth=成长轨迹
func Definition() tools.SFTool {
	return tools.SFTool{
		Type: "function",
		Function: struct {
			Description string      `json:"description"`
			Name        string      `json:"name"`
			Parameters  interface{} `json:"parameters"`
			Required    []string    `json:"required"`
		}{
			Description: "切换数字大学生前端顶部导航栏的标签页。当用户说「打开首页」「去竞赛」「切到职业发展」「打开技能图谱」「去孪孪伴学/孪孪伴学」「打开成长轨迹」等时调用。tab 必须为以下之一：home（首页）、competition（竞赛活动）、career（职业发展）、knowledge-graph（技能图谱）、twin-study（季季伴学）、growth（成长轨迹）。",
			Name:        "switchNavTab",
			Parameters: struct {
				Tab struct {
					Description string `json:"description"`
					Type        string `json:"type"`
					Enum        []string `json:"enum,omitempty"`
				} `json:"tab"`
			}{
				Tab: struct {
					Description string   `json:"description"`
					Type        string   `json:"type"`
					Enum        []string `json:"enum,omitempty"`
				}{
					Description: "目标导航页标识。home=首页, competition=竞赛活动, career=职业发展, knowledge-graph=技能图谱, twin-study=季季伴学, growth=成长轨迹",
					Type:        "string",
					Enum:        []string{"home", "competition", "career", "knowledge-graph", "twin-study", "growth"},
				},
			},
			Required: []string{"tab"},
		},
	}
}
