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
		Importance      int    `json:"importance"`
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

	// 默认重要程度为4
	importance := params.Importance
	if importance == 0 {
		importance = 4
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
