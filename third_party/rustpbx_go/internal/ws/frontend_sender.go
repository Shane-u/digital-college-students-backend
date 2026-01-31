package ws

import (
	"encoding/json"
	"fmt"

	"pbx_back_end/internal/handler/tools"

	"github.com/gorilla/websocket"
	"github.com/sirupsen/logrus"
)

// FrontendCommandSender 实现 tools.FrontendSender，通过 WebSocket 向前端发送指令（如 switchNavTab）
type FrontendCommandSender struct {
	s *FrontendServer
}

// NewFrontendCommandSender 创建向前端发指令的 sender，需在 FrontendServer 创建后注入到 SiliconFlowHandler
func NewFrontendCommandSender(s *FrontendServer) tools.FrontendSender {
	return &FrontendCommandSender{s: s}
}

// SendCommand 将 payload（如 map[string]string{"type":"switchNavTab","tab":"home"}）通过 WebSocket 发给前端
func (f *FrontendCommandSender) SendCommand(payload interface{}) (result string, err error) {
	if f.s == nil {
		return "", fmt.Errorf("FrontendServer is nil")
	}

	// 统一格式：{ event: "frontendCommand", payload: { type, tab, ... } }
	msg := map[string]interface{}{
		"event":   "frontendCommand",
		"payload": payload,
	}
	body, err := json.Marshal(msg)
	if err != nil {
		logrus.WithError(err).Error("FrontendCommandSender: failed to marshal message")
		return "", err
	}

	logrus.WithFields(map[string]interface{}{
		"payload": string(body),
		"type":    getPayloadType(payload),
	}).Info("FrontendCommandSender: Sending command to frontend")

	// shane: 使用线程安全的写入方法
	if err := f.s.safeWriteToRealTimeConn(websocket.TextMessage, body); err != nil {
		logrus.WithError(err).Warn("FrontendCommandSender: send to frontend failed")
		return "", err
	}

	logrus.Info("FrontendCommandSender: Command sent successfully")

	// 从 payload 中生成 TTS 文案，前端无需回传即可播报
	if m, ok := payload.(map[string]interface{}); ok {
		// 处理 switchNavTab
		if cmdType, ok := m["type"].(string); ok {
			if cmdType == "switchNavTab" {
				if tab, ok := m["tab"].(string); ok && tab != "" {
					result = fmt.Sprintf("已切换到「%s」页面。", tabNameForTTS(tab))
				}
			} else if cmdType == "addGrowthRecord" {
				// 处理 addGrowthRecord
				eventName, _ := m["eventName"].(string)
				importance, _ := m["importance"].(float64)
				personalInsight, _ := m["personalInsight"].(string)

				if eventName != "" {
					result = fmt.Sprintf("已为您打开添加成长记录界面，事件名称：%s，重要程度：%.0f颗星", eventName, importance)
					if personalInsight != "" {
						result += fmt.Sprintf("，个人感悟：%s", personalInsight)
					}
					result += "。请手动上传图片和文件，然后点击保存。"
				}
			}
		}
		// 兼容旧的 map[string]string 格式
		if result == "" {
			if mStr, ok := payload.(map[string]string); ok {
				if tab := mStr["tab"]; tab != "" {
					result = fmt.Sprintf("已切换到「%s」页面。", tabNameForTTS(tab))
				}
			}
		}
	}
	if result == "" {
		result = "操作已发送。"
	}
	return result, nil
}

func tabNameForTTS(tab string) string {
	names := map[string]string{
		"home":            "首页",
		"competition":     "竞赛活动",
		"career":          "职业发展",
		"knowledge-graph": "技能图谱",
		"twin-study":      "季季伴学",
		"growth":          "成长轨迹",
	}
	if n, ok := names[tab]; ok {
		return n
	}
	return tab
}

// getPayloadType 辅助函数，用于日志记录
func getPayloadType(payload interface{}) string {
	if m, ok := payload.(map[string]interface{}); ok {
		if t, ok := m["type"].(string); ok {
			return t
		}
	}
	if m, ok := payload.(map[string]string); ok {
		if t, ok := m["type"]; ok {
			return t
		}
	}
	return "unknown"
}
