package weather

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"

	"pbx_back_end/internal/handler/tools"
)

type weatherResponse struct {
	Precipitation        float64 `json:"precipitation"`
	Temperature          float64 `json:"temperature"`
	Pressure             int     `json:"pressure"`
	Humidity             int     `json:"humidity"`
	WindDirection        string  `json:"windDirection"`
	WindDirectionDegree  int     `json:"windDirectionDegree"`
	WindSpeed            float64 `json:"windSpeed"`
	WindScale            string  `json:"windScale"`
	Feelst               float64 `json:"feelst"`
	Code                 int     `json:"code"`
	Place                string  `json:"place"`
	Weather1             string  `json:"weather1"`
	Weather2             string  `json:"weather2"`
	Weather1img          string  `json:"weather1img"`
	Weather2img          string  `json:"weather2img"`
	Uptime               string  `json:"uptime"`
	Jieqi                string  `json:"jieqi"`
}

// Handle 执行天气查询，返回可读文案供 TTS
func Handle(ctx *tools.ToolContext, args string) (string, error) {
	var params struct {
		Sheng string `json:"sheng"`
		Place string `json:"place"`
	}
	if err := json.Unmarshal([]byte(args), &params); err != nil {
		if ctx.Logger != nil {
			ctx.Logger.Errorf("queryWeather: unmarshal arguments: %v", err)
		}
		return "", err
	}

	url := "https://cn.apihz.cn/api/tianqi/tqyb.php"
	data := fmt.Sprintf("id=10006512&key=512b69d6b44c1c59a1a698da8d3cb1a7&sheng=%s&place=%s", params.Sheng, params.Place)
	req, err := http.NewRequest("POST", url, bytes.NewBufferString(data))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}

	var wr weatherResponse
	if err := json.Unmarshal(body, &wr); err != nil {
		return "", fmt.Errorf("parse weather response: %v, body: %s", err, string(body))
	}

	text := fmt.Sprintf("当前时间：%s，%s 的天气情况如下：温度为 %.1f 摄氏度，天气状况为 %s，风力为 %s，相对湿度为 %d%%。",
		wr.Uptime, wr.Place, wr.Temperature, wr.Weather1, wr.WindScale, wr.Humidity)
	return text, nil
}
