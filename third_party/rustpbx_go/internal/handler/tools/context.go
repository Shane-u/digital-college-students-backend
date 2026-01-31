package tools

import (
	"context"

	"github.com/sirupsen/logrus"
)

// FrontendSender 后端向前端发送指令、并接收前端执行结果的抽象。
// 链路：后端调用 SendCommand → 前端执行 → 前端返回 result → 后端用于 TTS 反馈。
type FrontendSender interface {
	// SendCommand 发送指令到前端；payload 为 JSON 或可序列化结构（如 map[string]string）。
	// 返回前端的执行结果文案，供后端做文字转语音反馈。
	SendCommand(payload interface{}) (result string, err error)
}

// ToolConfig 各工具依赖的外部服务配置（API 地址、密钥等）
type ToolConfig struct {
	Ctx              context.Context
	SearchAPIUrl     string
	SearchAPIKey     string
	SearchAPIModel   string
	DashScopeAPIKey  string // 艺术字等 DashScope 能力
}

// ToolContext 单次 tool 调用的上下文，供各功能包 handler 使用
type ToolContext struct {
	Logger         *logrus.Logger
	UserMsg        string
	ToolCallID     string
	Config         *ToolConfig
	FrontendSender FrontendSender // 可选；nav 等需控制前端时使用
}
