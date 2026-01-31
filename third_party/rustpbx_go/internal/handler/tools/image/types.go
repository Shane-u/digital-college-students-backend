package image

// TextTransformRequest 艺术字请求
type TextTransformRequest struct {
	Model string `json:"model"`
	Input struct {
		Text   string `json:"text"`
		Prompt string `json:"prompt"`
	} `json:"input"`
	Parameters struct {
		Steps            int    `json:"steps"`
		N                int    `json:"n"`
		FontName         string `json:"font_name,omitempty"`
		TtfUrl           string `json:"ttf_url,omitempty"`
		OutputImageRatio string `json:"output_image_ratio"`
	} `json:"parameters"`
}

// TextTransformResponse 艺术字异步响应
type TextTransformResponse struct {
	Output struct {
		TaskId     string `json:"task_id"`
		TaskStatus string `json:"task_status"`
	} `json:"output"`
	Usage struct {
		ImageCount int `json:"image_count"`
	} `json:"usage"`
	RequestId string `json:"request_id"`
}

// TaskQueryResponse 任务查询响应
type TaskQueryResponse struct {
	Output struct {
		TaskId     string `json:"task_id"`
		TaskStatus string `json:"task_status"`
		Results    []struct {
			SvgUrl string `json:"svg_url"`
			PngUrl string `json:"png_url"`
		} `json:"results"`
	} `json:"output"`
	Usage struct {
		ImageCount int `json:"image_count"`
	} `json:"usage"`
	RequestId string `json:"request_id"`
}
