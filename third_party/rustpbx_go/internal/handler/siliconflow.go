package handler

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"regexp"
	"strings"
	"sync"

	"github.com/google/uuid"
	"github.com/sirupsen/logrus"

	"pbx_back_end/internal/handler/tools"
	_ "pbx_back_end/internal/handler/tools/growth_record"

	_ "pbx_back_end/internal/handler/tools/image"
	_ "pbx_back_end/internal/handler/tools/nav"
	_ "pbx_back_end/internal/handler/tools/search"
	_ "pbx_back_end/internal/handler/tools/weather"
)

type SiliconFlowHandler struct {
	mutex           sync.Mutex
	APIKey          string
	Endpoint        string
	Model           string
	searchApiUrl    string
	searchApiKey    string
	searchApiModel  string
	dashScopeAPIKey string
	ctx             context.Context
	logger          *logrus.Logger
	history         []SFMessage
	frontendSender  tools.FrontendSender
}

// SiliconFlowRJson shane: Response JSON structure for SiliconFlow
type SiliconFlowRJson struct {
	Choices []struct {
		Message struct {
			Role      string `json:"role"`
			Content   string `json:"content"`
			ToolCalls []struct {
				Id       string `json:"id"`
				Type     string `json:"type"`
				Function struct {
					Name      string `json:"name"`
					Arguments string `json:"arguments"`
				} `json:"function"`
			} `json:"tool_calls"`
		} `json:"message"`
	} `json:"choices"`
}

type SFMessage struct {
	Role       string `json:"role"`
	Content    string `json:"content"`
	ToolCallId string `json:"tool_call_id,omitempty"`
}

type SFRequest struct {
	Model       string        `json:"model"`
	Messages    []SFMessage   `json:"messages"`
	MaxTokens   int           `json:"max_tokens"`
	Stream      bool          `json:"stream,omitempty"`
	Temperature float64       `json:"temperature,omitempty"`
	Tools       tools.SFTools `json:"tools,omitempty"`
}

type SFChoice struct {
	Message struct {
		Role      string `json:"role"`
		Content   string `json:"content"`
		ToolCalls []struct {
			Id       string `json:"id"`
			Type     string `json:"type"`
			Function struct {
				Name      string `json:"name"`
				Arguments string `json:"arguments"`
			} `json:"function"`
		} `json:"tool_calls,omitempty"`
	} `json:"message"`
}

type SFResponse struct {
	Choices []SFChoice `json:"choices"`
}

// RespData shane: Response
type RespData struct {
	Choices []struct {
		Delta struct {
			Content   string `json:"content"`
			ToolCalls []struct {
				Id       string `json:"id"`
				Type     string `json:"type"`
				Function struct {
					Name      string `json:"name"`
					Arguments string `json:"arguments"`
				} `json:"function"`
			} `json:"tool_calls,omitempty"`
		} `json:"delta"`
	} `json:"choices"`
}

func NewSiliconFlowHandler(ctx context.Context, apiKey, endpoint, model string, logger *logrus.Logger, searchApiUrl, searchApiKey, searchApiModel string) *SiliconFlowHandler {
	return &SiliconFlowHandler{
		ctx:            ctx,
		APIKey:         apiKey,
		Endpoint:       endpoint,
		Model:          model,
		logger:         logger,
		searchApiUrl:   searchApiUrl,
		searchApiKey:   searchApiKey,
		searchApiModel: searchApiModel,
	}
}

// SetFrontendSender 设置向前端发送指令的抽象（可选）。设置后，nav 等工具会通过其发指令并收前端反馈，用于 TTS。
func (h *SiliconFlowHandler) SetFrontendSender(sender tools.FrontendSender) {
	h.frontendSender = sender
}

// SetDashScopeAPIKey 设置 DashScope API Key（艺术字等能力）。不设置则 transformText 不可用。
func (h *SiliconFlowHandler) SetDashScopeAPIKey(key string) {
	h.dashScopeAPIKey = key
}

func (h *SiliconFlowHandler) toolContext(userMsg, toolCallID string) *tools.ToolContext {
	return &tools.ToolContext{
		Logger:         h.logger,
		UserMsg:        userMsg,
		ToolCallID:     toolCallID,
		FrontendSender: h.frontendSender,
		Config: &tools.ToolConfig{
			Ctx:             h.ctx,
			SearchAPIUrl:    h.searchApiUrl,
			SearchAPIKey:    h.searchApiKey,
			SearchAPIModel:  h.searchApiModel,
			DashScopeAPIKey: h.dashScopeAPIKey,
		},
	}
}

func (h *SiliconFlowHandler) QueryStream(userMsg string, ttsCallback func(segment string, playID string, autoHangup bool) error) (string, error) {
	h.mutex.Lock()
	defer h.mutex.Unlock()

	h.history = append(h.history, SFMessage{
		Role:    "user",
		Content: userMsg,
	})

	// shane: 使用 tools 包聚合的各功能包（internal/handler/tools/*）
	toolsList := tools.AllTools()
	reqBody := SFRequest{
		Model:       h.Model,
		Messages:    h.history,
		MaxTokens:   512,
		Stream:      true,
		Temperature: 0.7,
		Tools:       toolsList,
	}
	body, _ := json.Marshal(reqBody)

	// Generate unique playID
	playID := fmt.Sprintf("sf-%s", uuid.New().String())
	h.logger.WithField("playID", playID).Info("Starting SiliconFlow stream with playID")

	req, _ := http.NewRequestWithContext(h.ctx, "POST", h.Endpoint, bytes.NewReader(body))
	req.Header.Set("accept", "application/json")
	req.Header.Set("authorization", "Bearer "+h.APIKey)
	req.Header.Set("content-type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		respBody, _ := ioutil.ReadAll(resp.Body)
		return "", fmt.Errorf("status code: %d, body: %s", resp.StatusCode, string(respBody))
	}

	reader := bufio.NewReader(resp.Body)
	var buffer string
	fullResponse := ""
	var shouldHangup bool
	// shane: tooCallsMap
	toolCallsMap := make(map[string]struct {
		Id       string
		Type     string
		Function struct {
			Name      string
			Arguments string
		}
	})
	// shane: record the order
	var toolCallOrder []string

	// Regular expression to detect punctuation
	punctuationRegex := regexp.MustCompile(`([.,;:!?，。！？；：])\s*`)

	for {
		line, err := reader.ReadBytes('\n')
		if err != nil {
			if err == io.EOF {
				break
			}
			return "", fmt.Errorf("error reading stream: %w", err)
		}

		lineStr := strings.TrimSpace(string(line))
		if lineStr == "" || !strings.HasPrefix(lineStr, "data: ") {
			continue
		}

		data := strings.TrimPrefix(lineStr, "data: ")
		if data == "[DONE]" {
			break
		}

		var respData RespData
		if err := json.Unmarshal([]byte(data), &respData); err != nil {
			continue
		}

		// Process content if available
		if len(respData.Choices) > 0 {
			choice := respData.Choices[0]
			// shane: process content
			if choice.Delta.Content != "" {
				content := choice.Delta.Content
				buffer += content
				fullResponse += content

				// Check for punctuation in the buffer
				matches := punctuationRegex.FindAllStringSubmatchIndex(buffer, -1)
				if len(matches) > 0 {
					lastIdx := 0
					for _, match := range matches {
						segment := buffer[lastIdx:match[1]]
						if segment != "" {
							if err := ttsCallback(segment, playID, false); err != nil {
								h.logger.WithError(err).Error("Failed to send TTS segment")
							}
						}
						lastIdx = match[1]
					}
					if lastIdx < len(buffer) {
						buffer = buffer[lastIdx:]
					} else {
						buffer = ""
					}
				}
			}

			// shane: construct tool calls
			if len(choice.Delta.ToolCalls) > 0 {
				for _, tc := range choice.Delta.ToolCalls {
					if tc.Id != "" {
						if tool, ok := toolCallsMap[tc.Id]; ok {
							if tc.Function.Name != "" {
								tool.Function.Name = tc.Function.Name
							}
							if tc.Function.Arguments != "" {
								tool.Function.Arguments += tc.Function.Arguments
							}
							toolCallsMap[tc.Id] = tool
						} else {
							toolCallsMap[tc.Id] = struct {
								Id       string
								Type     string
								Function struct {
									Name      string
									Arguments string
								}
							}{
								Id:   tc.Id,
								Type: tc.Type,
								Function: struct {
									Name      string
									Arguments string
								}{
									Name:      tc.Function.Name,
									Arguments: tc.Function.Arguments,
								},
							}
							toolCallOrder = append(toolCallOrder, tc.Id)
						}
					} else {
						if tc.Function.Arguments != "" && len(toolCallOrder) > 0 {
							lastToolId := toolCallOrder[len(toolCallOrder)-1]
							if tool, ok := toolCallsMap[lastToolId]; ok {
								tool.Function.Arguments += tc.Function.Arguments
								toolCallsMap[lastToolId] = tool
							}
						}
					}
				}
			}
		}
	}

	// shane: Send any remaining text in the buffer
	if err := ttsCallback(buffer, playID, shouldHangup); err != nil {
		h.logger.WithError(err).Error("Failed to send final TTS segment")
	}

	// shane: Add assistant's response to history
	h.history = append(h.history, SFMessage{
		Role:    "assistant",
		Content: fullResponse,
	})

	// shane:
	for _, toolCall := range toolCallsMap {
		if toolCall.Id != "" && toolCall.Function.Name != "" && toolCall.Function.Arguments != "" {
			thinkingMsg := "正在思考，请稍等片刻"
			if err := ttsCallback(thinkingMsg, playID, false); err != nil {
				h.logger.WithError(err).Error("Failed to send thinking TTS")
			}

			// shane: 异步
			go func(tc struct {
				Id       string
				Type     string
				Function struct {
					Name      string
					Arguments string
				}
			}) {
				ctx := h.toolContext(userMsg, tc.Id)
				result, err := tools.Dispatch(tc.Function.Name, tc.Function.Arguments, ctx)
				if err != nil {
					h.logger.WithError(err).Error("Tool call failed")
					ttsCallback("查询失败，请稍后重试", playID, false)
				} else {
					ttsCallback(result, playID, false)
				}
			}(toolCall)
		}
	}

	h.logger.WithFields(logrus.Fields{
		"responseLength": len(fullResponse),
		"hangup":         shouldHangup,
	}).Info("SiliconFlow stream completed")

	return fullResponse, nil
}

func (h *SiliconFlowHandler) Query(userMsg string) (string, error) {
	h.mutex.Lock()
	defer h.mutex.Unlock()

	h.history = append(h.history, SFMessage{
		Role:    "user",
		Content: userMsg,
	})

	toolsList := tools.AllTools()
	reqBody := SFRequest{
		Model:     h.Model,
		Messages:  h.history,
		MaxTokens: 512,
		Stream:    false,
		Tools:     toolsList,
	}
	body, _ := json.Marshal(reqBody)

	// shane: siliconflow request
	req, _ := http.NewRequestWithContext(h.ctx, "POST", h.Endpoint, bytes.NewReader(body))
	req.Header.Set("accept", "application/json")
	req.Header.Set("authorization", "Bearer "+h.APIKey)
	req.Header.Set("content-type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	respBody, _ := ioutil.ReadAll(resp.Body)
	logrus.Infof("LLM respBody:%s", string(respBody))

	if resp.StatusCode != 200 {
		return "", fmt.Errorf("status code: %d, body: %s", resp.StatusCode, string(respBody))
	}

	var sfResp SFResponse
	if err := json.Unmarshal(respBody, &sfResp); err != nil {
		return "", err
	}
	if len(sfResp.Choices) == 0 {
		return "", fmt.Errorf("no choices in response")
	}

	if len(sfResp.Choices) > 0 {
		assistantMsg := sfResp.Choices[0].Message
		h.history = append(h.history, SFMessage{
			Role:    "assistant",
			Content: assistantMsg.Content,
		})
	}

	// shane: Check if the response contains tool calls
	if len(sfResp.Choices[0].Message.ToolCalls) > 0 {
		toolCall := sfResp.Choices[0].Message.ToolCalls[0]
		funcName := toolCall.Function.Name
		arguments := toolCall.Function.Arguments
		toolCallId := toolCall.Id

		ctx := h.toolContext(userMsg, toolCallId)
		return tools.Dispatch(funcName, arguments, ctx)
	}

	// shane: If no tool calls, return the raw content
	return sfResp.Choices[0].Message.Content, nil
}

func (h *SiliconFlowHandler) ResetHistory() {
	h.mutex.Lock()
	defer h.mutex.Unlock()
	h.history = []SFMessage{}
}
