package image

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"time"

	"pbx_back_end/internal/handler/tools"
)

func getStylePrompt(userPrompt string) string {
	return userPrompt
}

func getFontName(style string) string {
	fontMap := map[string]string{
		"dongfangdakai":  "dongfangdakai",
		"puhuiti":        "puhuiti_m",
		"shuheiti":       "shuheiti",
		"jinbu":          "jinbu1",
		"kuhei":          "kuheti1",
		"kuailei":        "kuailei1",
		"wenyiti":        "wenyiti1",
		"logoti":         "logoti",
		"cangeryuyangti": "cangeryuyangti_m",
		"siyuansongti":   "siyuansongti_b",
		"siyuanheiti":    "siyuanheiti_m",
		"fangzhengkaiti": "fangzhengkaiti",
		"flower":         "dongfangdakai",
		"art":            "puhuiti_m",
		"gothic":         "siyuanheiti_m",
		"modern":         "siyuansongti_b",
	}
	if name, ok := fontMap[style]; ok {
		return name
	}
	return "dongfangdakai"
}

func getTtfUrl(style string) string {
	customFontMap := map[string]string{
		"custom_font1": "https://example.com/fonts/custom1.ttf",
		"custom_font2": "https://example.com/fonts/custom2.ttf",
	}
	if url, ok := customFontMap[style]; ok {
		return url
	}
	return ""
}

// HandleGenerateImage 根据 prompt 生成图片，返回 markdown 图片链接文案供 TTS
func HandleGenerateImage(ctx *tools.ToolContext, args string) (string, error) {
	var params struct {
		Prompt string `json:"prompt"`
	}
	if err := json.Unmarshal([]byte(args), &params); err != nil {
		if ctx.Logger != nil {
			ctx.Logger.Errorf("generateImage: unmarshal arguments: %v", err)
		}
		return "", err
	}
	imageUrl := fmt.Sprintf("https://image.pollinations.ai/prompt/%s?width=1024&height=1024&seed=100&model=flux&nologo=true", params.Prompt)
	return fmt.Sprintf("![%s](%s)", params.Prompt, imageUrl), nil
}

// HandleTransformText 艺术字变形，返回结果描述或图片 markdown 供 TTS
func HandleTransformText(ctx *tools.ToolContext, args string) (string, error) {
	var params struct {
		Text   string `json:"text"`
		Prompt string `json:"prompt"`
		Style  string `json:"style"`
	}
	if err := json.Unmarshal([]byte(args), &params); err != nil {
		if ctx.Logger != nil {
			ctx.Logger.Errorf("transformText: unmarshal arguments: %v", err)
		}
		return "", err
	}
	if params.Prompt == "" {
		return "", fmt.Errorf("prompt is required and cannot be empty")
	}
	if ctx.Config == nil || ctx.Config.DashScopeAPIKey == "" {
		return "", fmt.Errorf("DashScope API not configured")
	}

	req := TextTransformRequest{
		Model: "wordart-semantic",
		Input: struct {
			Text   string `json:"text"`
			Prompt string `json:"prompt"`
		}{
			Text:   params.Text,
			Prompt: getStylePrompt(params.Prompt),
		},
		Parameters: struct {
			Steps            int    `json:"steps"`
			N                int    `json:"n"`
			FontName         string `json:"font_name,omitempty"`
			TtfUrl           string `json:"ttf_url,omitempty"`
			OutputImageRatio string `json:"output_image_ratio"`
		}{
			Steps:            60,
			N:                2,
			OutputImageRatio: "1280x720",
			FontName:         getFontName(params.Style),
			TtfUrl:           getTtfUrl(params.Style),
		},
	}
	body, err := json.Marshal(req)
	if err != nil {
		return "", err
	}

	httpReq, err := http.NewRequest("POST", "https://dashscope.aliyuncs.com/api/v1/services/aigc/wordart/semantic", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	httpReq.Header.Set("X-DashScope-Async", "enable")
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")
	httpReq.Header.Set("Authorization", "Bearer "+ctx.Config.DashScopeAPIKey)

	client := &http.Client{}
	resp, err := client.Do(httpReq)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	respBody, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	if resp.StatusCode != 200 {
		return "", fmt.Errorf("transform text API: status %d, body: %s", resp.StatusCode, string(respBody))
	}

	var tfResp TextTransformResponse
	if err := json.Unmarshal(respBody, &tfResp); err != nil {
		return "", fmt.Errorf("unmarshal transform response: %v", err)
	}
	taskId := tfResp.Output.TaskId
	if taskId == "" {
		return "", fmt.Errorf("no task_id in response")
	}

	result, err := queryTaskResult(taskId, ctx.Config.DashScopeAPIKey)
	if err != nil {
		return "", err
	}
	return result, nil
}

func queryTaskResult(taskId, apiKey string) (string, error) {
	url := fmt.Sprintf("https://dashscope.aliyuncs.com/api/v1/tasks/%s", taskId)
	maxAttempts := 30
	for attempt := 0; attempt < maxAttempts; attempt++ {
		req, err := http.NewRequest("GET", url, nil)
		if err != nil {
			return "", err
		}
		req.Header.Set("Authorization", "Bearer "+apiKey)

		client := &http.Client{}
		resp, err := client.Do(req)
		if err != nil {
			return "", err
		}
		body, _ := ioutil.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			return "", err
		}
		if resp.StatusCode != 200 {
			return "", fmt.Errorf("query task API: status %d, body: %s", resp.StatusCode, string(body))
		}

		var q TaskQueryResponse
		if err := json.Unmarshal(body, &q); err != nil {
			return "", fmt.Errorf("unmarshal task response: %v", err)
		}

		switch q.Output.TaskStatus {
		case "SUCCEEDED":
			if len(q.Output.Results) > 0 {
				return fmt.Sprintf("![艺术字](%s)", q.Output.Results[0].PngUrl), nil
			}
			return "", fmt.Errorf("task succeeded but no results")
		case "FAILED":
			return "", fmt.Errorf("task failed")
		case "PENDING", "RUNNING":
			time.Sleep(10 * time.Second)
			continue
		default:
			return "", fmt.Errorf("unknown task status: %s", q.Output.TaskStatus)
		}
	}
	return "", fmt.Errorf("task timeout after %d attempts", maxAttempts)
}
