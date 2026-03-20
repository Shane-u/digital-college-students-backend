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
)

// SiliconFlowInterviewer 用于“面试官AI”：无 tools、独立 system prompt / model、独立会话 history
type SiliconFlowInterviewer struct {
	mutex        sync.Mutex
	APIKey       string
	Endpoint     string
	Model        string
	SystemPrompt string
	ctx          context.Context
	logger       *logrus.Logger
	history      []sfInterviewMsg
}

type sfInterviewMsg struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type sfInterviewRequest struct {
	Model       string           `json:"model"`
	Messages    []sfInterviewMsg  `json:"messages"`
	MaxTokens   int              `json:"max_tokens"`
	Stream      bool             `json:"stream,omitempty"`
	Temperature float64          `json:"temperature,omitempty"`
}

type sfInterviewRespData struct {
	Choices []struct {
		Delta struct {
			Content string `json:"content"`
		} `json:"delta"`
	} `json:"choices"`
}

func NewSiliconFlowInterviewer(ctx context.Context, apiKey, endpoint, model, systemPrompt string, logger *logrus.Logger) *SiliconFlowInterviewer {
	h := &SiliconFlowInterviewer{
		ctx:          ctx,
		APIKey:       apiKey,
		Endpoint:     endpoint,
		Model:        model,
		SystemPrompt: systemPrompt,
		logger:       logger,
	}
	h.Reset()
	return h
}

func (h *SiliconFlowInterviewer) Reset() {
	h.mutex.Lock()
	defer h.mutex.Unlock()
	if strings.TrimSpace(h.SystemPrompt) != "" {
		h.history = []sfInterviewMsg{{
			Role:    "system",
			Content: h.SystemPrompt,
		}}
		return
	}
	h.history = []sfInterviewMsg{}
}

func (h *SiliconFlowInterviewer) QueryStream(userMsg string, ttsCallback func(segment string, playID string, autoHangup bool) error) (string, error) {
	h.mutex.Lock()
	defer h.mutex.Unlock()

	h.history = append(h.history, sfInterviewMsg{Role: "user", Content: userMsg})

	reqBody := sfInterviewRequest{
		Model:       h.Model,
		Messages:    h.history,
		MaxTokens:   512,
		Stream:      true,
		Temperature: 0.7,
	}
	body, _ := json.Marshal(reqBody)

	playID := fmt.Sprintf("sf-intv-%s", uuid.New().String())
	h.logger.WithField("playID", playID).Info("Starting SiliconFlow interviewer stream")

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

		var respData sfInterviewRespData
		if err := json.Unmarshal([]byte(data), &respData); err != nil {
			continue
		}
		if len(respData.Choices) == 0 {
			continue
		}
		content := respData.Choices[0].Delta.Content
		if content == "" {
			continue
		}
		buffer += content
		fullResponse += content

		matches := punctuationRegex.FindAllStringSubmatchIndex(buffer, -1)
		if len(matches) > 0 {
			lastIdx := 0
			for _, match := range matches {
				segment := buffer[lastIdx:match[1]]
				if segment != "" {
					_ = ttsCallback(segment, playID, false)
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

	_ = ttsCallback(buffer, playID, false)
	h.history = append(h.history, sfInterviewMsg{Role: "assistant", Content: fullResponse})
	return fullResponse, nil
}

