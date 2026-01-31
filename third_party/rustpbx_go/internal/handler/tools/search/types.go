package search

// SearchResult 单条搜索结果
type SearchResult struct {
	Content string `json:"content"`
	Icon    string `json:"icon"`
	Index   int    `json:"index"`
	Link    string `json:"link"`
	Media   string `json:"media"`
	Refer   string `json:"refer"`
	Title   string `json:"title"`
}

// SearchOnlineStruct 联网搜索 API 返回结构
type SearchOnlineStruct struct {
	Choices []struct {
		Message struct {
			ToolCalls []struct {
				Id           string         `json:"id"`
				SearchResult []SearchResult `json:"search_result"`
			} `json:"tool_calls"`
		} `json:"message"`
	} `json:"choices"`
}
