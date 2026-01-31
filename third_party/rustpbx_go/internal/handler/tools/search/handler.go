package search

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"

	"pbx_back_end/internal/handler/tools"
)

func searchResultToString(results []SearchResult) string {
	var buf bytes.Buffer
	for _, r := range results {
		buf.WriteString(fmt.Sprintf("Title: %s\nContent: %s\nLink: %s\n\n", r.Title, r.Content, r.Link))
	}
	return buf.String()
}

// Handle 执行联网搜索，返回可读文案供 TTS
func Handle(ctx *tools.ToolContext, args string) (string, error) {
	var params struct {
		Query string `json:"query"`
	}
	if err := json.Unmarshal([]byte(args), &params); err != nil {
		if ctx.Logger != nil {
			ctx.Logger.Errorf("searchOnline: unmarshal arguments: %v", err)
		}
		return "", err
	}

	if ctx.Config == nil || ctx.Config.SearchAPIUrl == "" {
		return "", fmt.Errorf("search API not configured")
	}

	results, err := doSearch(ctx.Config.SearchAPIUrl, ctx.Config.SearchAPIKey, params.Query)
	if err != nil {
		return "", err
	}
	if len(results) == 0 {
		return "", fmt.Errorf("no search results found")
	}
	return searchResultToString(results), nil
}

func doSearch(apiUrl, apiKey, query string) ([]SearchResult, error) {
	body := map[string]interface{}{
		"assistant_id":  "659e54b1b8006379b4b2abd6",
		"model":         "glm-4v-flash",
		"messages":      []map[string]interface{}{{"role": "user", "content": []map[string]interface{}{{"type": "text", "text": query}}}},
		"stream":        true,
		"temperature":   0.2,
	}
	marshal, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequest("POST", apiUrl, bytes.NewReader(marshal))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+apiKey)

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	reader := bufio.NewReader(resp.Body)
	webBrowserOutputRegex := regexp.MustCompile(`"web_browser":\s*\{"outputs":\s*(\[.*?\])\}`)
	var combinedData string
	var searchResults []SearchResult

	for {
		line, err := reader.ReadBytes('\n')
		if err != nil {
			if err == io.EOF {
				break
			}
			return nil, err
		}
		lineStr := strings.TrimSpace(string(line))
		if lineStr == "" || !strings.HasPrefix(lineStr, "data: ") {
			continue
		}
		data := strings.TrimPrefix(lineStr, "data: ")
		if data == "[DONE]" {
			break
		}
		combinedData += data
	}

	matches := webBrowserOutputRegex.FindStringSubmatch(combinedData)
	if len(matches) > 1 {
		outputsStr := matches[1]
		cleanStr := strings.ReplaceAll(outputsStr, "\\\"", "\"")
		var outputs []map[string]interface{}
		if err := json.Unmarshal([]byte(cleanStr), &outputs); err != nil {
			searchResults = []SearchResult{{Title: "搜索结果原始内容", Content: cleanStr, Link: ""}}
		} else {
			for _, item := range outputs {
				r := SearchResult{}
				if v, ok := item["title"].(string); ok {
					r.Title = v
				}
				if v, ok := item["content"].(string); ok {
					r.Content = v
				}
				if v, ok := item["link"].(string); ok {
					r.Link = strings.TrimSpace(v)
				}
				if v, ok := item["index"].(float64); ok {
					r.Index = int(v)
				}
				if v, ok := item["icon"].(string); ok {
					r.Icon = v
				}
				if v, ok := item["media"].(string); ok {
					r.Media = v
				}
				if v, ok := item["refer"].(string); ok {
					r.Refer = v
				}
				searchResults = append(searchResults, r)
			}
		}
	}

	return searchResults, nil
}
