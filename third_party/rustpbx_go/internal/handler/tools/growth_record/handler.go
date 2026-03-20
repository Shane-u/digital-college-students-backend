package growth_record

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"pbx_back_end/internal/handler/tools"
)

// Handle 执行添加成长记录：先切换到成长轨迹页面，然后打开添加对话框并填写信息
func Handle(ctx *tools.ToolContext, args string) (string, error) {
	var params struct {
		EventName       string `json:"eventName"`
		// importance 可能由模型以 number(4) 或 string("4") 两种形式返回，这里先用 RawMessage 兼容解析
		Importance      json.RawMessage `json:"importance"`
		PersonalInsight string `json:"personalInsight"`
		Date            string `json:"date"`
	}
	if err := json.Unmarshal([]byte(args), &params); err != nil {
		if ctx.Logger != nil {
			ctx.Logger.Errorf("addGrowthRecord: unmarshal arguments: %v", err)
		}
		return "", err
	}

	eventName := strings.TrimSpace(params.EventName)
	if eventName == "" {
		return "", fmt.Errorf("eventName is required")
	}

	// 重要程度：默认为 4。兼容 number/string。
	importance := 4
	if len(params.Importance) > 0 {
		// 1) 先尝试按 int 解析
		var asInt int
		if err := json.Unmarshal(params.Importance, &asInt); err == nil {
			importance = asInt
		} else {
			// 2) 再尝试按 string 解析（如 "4"）
			var asStr string
			if err2 := json.Unmarshal(params.Importance, &asStr); err2 == nil {
				if v, err3 := parseImportanceString(asStr); err3 == nil {
					importance = v
				}
			}
		}
	}
	if importance < 1 || importance > 5 {
		importance = 4
	}

	// 默认日期为今天
	date := strings.TrimSpace(params.Date)
	if date == "" {
		date = time.Now().Format("2006-01-02")
	}

	personalInsight := strings.TrimSpace(params.PersonalInsight)

	if ctx.Logger != nil {
		ctx.Logger.WithFields(map[string]interface{}{
			"eventName":       eventName,
			"importance":      importance,
			"personalInsight": personalInsight,
			"date":            date,
		}).Info("[addGrowthRecord] Handler called")
	}

	if ctx.FrontendSender != nil {
		// 构建 payload，包含所有需要填写的信息
		payload := map[string]interface{}{
			"type":            "addGrowthRecord",
			"eventName":       eventName,
			"importance":      importance,
			"personalInsight": personalInsight,
			"date":            date,
		}

		if ctx.Logger != nil {
			ctx.Logger.WithField("payload", payload).Info("[addGrowthRecord] Sending command to frontend")
		}

		result, err := ctx.FrontendSender.SendCommand(payload)
		if err != nil {
			if ctx.Logger != nil {
				ctx.Logger.WithError(err).Warn("addGrowthRecord: frontend SendCommand failed")
			}
			// 降级：返回提示信息
			msg := fmt.Sprintf("已为您打开添加成长记录界面，事件名称：%s，重要程度：%d颗星", eventName, importance)
			if personalInsight != "" {
				msg += fmt.Sprintf("，个人感悟：%s", personalInsight)
			}
			msg += "。请手动上传图片和文件，然后点击保存。"
			return msg, nil
		}
		if result != "" {
			return result, nil
		}
	} else if ctx.Logger != nil {
		ctx.Logger.WithFields(map[string]interface{}{
			"eventName":       eventName,
			"importance":      importance,
			"personalInsight": personalInsight,
			"date":            date,
		}).Info("[addGrowthRecord] FrontendSender 未配置，仅返回文案")
	}

	// 生成返回消息
	msg := fmt.Sprintf("已为您打开添加成长记录界面，事件名称：%s，重要程度：%d颗星", eventName, importance)
	if personalInsight != "" {
		msg += fmt.Sprintf("，个人感悟：%s", personalInsight)
	}
	msg += "。请手动上传图片和文件，然后点击保存。"
	return msg, nil
}

func parseImportanceString(s string) (int, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return 0, fmt.Errorf("importance empty")
	}
	// 允许 "4" / "4.0" 等简单情况
	if strings.Contains(s, ".") {
		var f float64
		if err := json.Unmarshal([]byte(s), &f); err != nil {
			return 0, err
		}
		return int(f), nil
	}
	var n int
	if err := json.Unmarshal([]byte(s), &n); err != nil {
		return 0, err
	}
	return n, nil
}

// HandleAddToday 执行添加今日成长记录：发送指令到前端，让前端触发添加今日记录功能
func HandleAddToday(ctx *tools.ToolContext, args string) (string, error) {
	if ctx.FrontendSender != nil {
		payload := map[string]string{
			"type": "addTodayGrowthRecord",
		}
		result, err := ctx.FrontendSender.SendCommand(payload)
		if err != nil {
			if ctx.Logger != nil {
				ctx.Logger.WithError(err).Warn("addTodayGrowthRecord: frontend SendCommand failed")
			}
			return "已发送添加今日记录的指令。", nil // 降级：仍做 TTS 反馈
		}
		if result != "" {
			return result, nil
		}
	} else if ctx.Logger != nil {
		ctx.Logger.Info("[addTodayGrowthRecord] FrontendSender 未配置，仅返回文案")
	}

	return "已发送添加今日记录的指令。", nil
}

// HandleSave 执行保存成长记录：发送指令到前端，让前端触发保存功能
func HandleSave(ctx *tools.ToolContext, args string) (string, error) {
	if ctx.Logger != nil {
		ctx.Logger.Info("[saveGrowthRecord] Handler called")
	}

	if ctx.FrontendSender != nil {
		payload := map[string]string{
			"type": "saveGrowthRecord",
		}
		result, err := ctx.FrontendSender.SendCommand(payload)
		if err != nil {
			if ctx.Logger != nil {
				ctx.Logger.WithError(err).Warn("saveGrowthRecord: frontend SendCommand failed")
			}
			return "已发送保存记录的指令。", nil
		}
		if result != "" {
			return result, nil
		}
	} else if ctx.Logger != nil {
		ctx.Logger.Info("[saveGrowthRecord] FrontendSender 未配置，仅返回文案")
	}

	return "已发送保存记录的指令。", nil
}

// HandleCancel 执行取消成长记录：发送指令到前端，让前端触发取消功能
func HandleCancel(ctx *tools.ToolContext, args string) (string, error) {
	if ctx.Logger != nil {
		ctx.Logger.Info("[cancelGrowthRecord] Handler called")
	}

	if ctx.FrontendSender != nil {
		payload := map[string]string{
			"type": "cancelGrowthRecord",
		}
		result, err := ctx.FrontendSender.SendCommand(payload)
		if err != nil {
			if ctx.Logger != nil {
				ctx.Logger.WithError(err).Warn("cancelGrowthRecord: frontend SendCommand failed")
			}
			return "已发送取消记录的指令。", nil
		}
		if result != "" {
			return result, nil
		}
	} else if ctx.Logger != nil {
		ctx.Logger.Info("[cancelGrowthRecord] FrontendSender 未配置，仅返回文案")
	}

	return "已发送取消记录的指令。", nil
}
