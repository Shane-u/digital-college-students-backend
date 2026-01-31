package nav

import (
	"encoding/json"
	"fmt"
	"strings"

	"pbx_back_end/internal/handler/tools"
)

// Handle 执行前端 nav 切换：后端通过 FrontendSender 发指令 → 前端执行 → 前端返回 result → 作为 TTS 反馈
func Handle(ctx *tools.ToolContext, args string) (string, error) {
	var params struct {
		Tab string `json:"tab"`
	}
	if err := json.Unmarshal([]byte(args), &params); err != nil {
		if ctx.Logger != nil {
			ctx.Logger.Errorf("switchNavTab: unmarshal arguments: %v", err)
		}
		return "", err
	}
	tab := strings.TrimSpace(params.Tab)
	if tab == "" {
		return "", fmt.Errorf("tab is required")
	}

	if ctx.FrontendSender != nil {
		// 后端向前端发送指令；前端执行后返回 result，用作 TTS 反馈
		payload := map[string]string{
			"type": "switchNavTab",
			"tab":  tab,
		}
		result, err := ctx.FrontendSender.SendCommand(payload)
		if err != nil {
			if ctx.Logger != nil {
				ctx.Logger.WithError(err).Warn("switchNavTab: frontend SendCommand failed")
			}
			return fmt.Sprintf("已切换到「%s」页面。", tab), nil // 降级：仍做 TTS 反馈
		}
		if result != "" {
			return result, nil
		}
	} else if ctx.Logger != nil {
		ctx.Logger.WithField("tab", tab).Info("[switchNavTab] FrontendSender 未配置，仅返回文案")
	}

	return fmt.Sprintf("已切换到「%s」页面。", tab), nil
}
