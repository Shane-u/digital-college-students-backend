package ws

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"pbx_back_end"
	"pbx_back_end/internal/handler"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/sirupsen/logrus"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
)

// FrontendServer 管理前端WebSocket连接
type FrontendServer struct {
	upgrader          websocket.Upgrader
	clients           map[*websocket.Conn]bool
	conn              *websocket.Conn
	RealTimeConn      *websocket.Conn
	llm               *handler.LLMHandler
	siliconFlowLLM    *handler.SiliconFlowHandler // shane: siliconflow LLM handler
	backendConn       *websocket.Conn             // shane: 与后端的连接
	backendServer     *BackendServer              // shane: 后端服务实例
	codec             string                      // shane: codec for audio stream
	asrOption         *pbx_back_end.ASROption
	ttsOption         *pbx_back_end.TTSOption
	javaBaseURL       string
	javaToken         string
	currentSessionId  string
	currentUserId     int64
	currentQuestionId string
	mu                sync.Mutex // shane: solve the concurrent write problem
	writeMu           sync.Mutex // shane: 专门用于保护 WebSocket 写入操作的锁
}

func NewFrontendServer(llm *handler.LLMHandler, siliconFlowLLM *handler.SiliconFlowHandler, backendConn *websocket.Conn, backendServer *BackendServer, codec string, asrOption *pbx_back_end.ASROption, ttsOption *pbx_back_end.TTSOption, javaBaseURL string, javaToken string) *FrontendServer {
	// func NewFrontendServer(llm *handler.LLMHandler, siliconFlowLLM *handler.SiliconFlowHandler, codec string, asrOption *pbx_back_end.ASROption, ttsOption *pbx_back_end.TTSOption) *FrontendServer {
	return &FrontendServer{
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool { return true }, // shane: 允许跨域
		}, // http的升级
		clients:        make(map[*websocket.Conn]bool),
		llm:            llm,
		siliconFlowLLM: siliconFlowLLM, // shane: siliconflow LLM handler
		backendConn:    backendConn,
		backendServer:  backendServer,
		codec:          codec,
		asrOption:      asrOption,
		ttsOption:      ttsOption,
		javaBaseURL:    strings.TrimRight(javaBaseURL, "/"),
		javaToken:      javaToken,
	}
}

// Start shane: http 升级为 websocket
func (s *FrontendServer) Start(r *gin.Engine, port string) {
	// shane: 使用gin处理WebSocket连接
	r.GET("/ws", func(c *gin.Context) {
		s.handleWebSocket(c.Writer, c.Request) // shane: http请求升级为WebSocket连接
	})
	// shane: 新增路由 /ws2 处理实时语音
	r.GET("/ws2", func(c *gin.Context) {
		s.handleWebSocket2(c.Writer, c.Request)
	})

	// AI interview dedicated realtime voice WS
	r.GET("/ws/ai-interview", func(c *gin.Context) {
		s.handleAiInterviewWS(c)
	})

	// shane: 监听后端消息
	if s.backendConn != nil {
		go s.receiveBackendMessages()
	}

	go func() {
		if err := r.Run(":" + port); err != nil {
			logrus.Error("Connection failed:", err)
		}
	}() // shane: 开协程防止阻塞
	logrus.Infof("Connected to the front end! Serve on %s", port)
}

type javaBaseResponse[T any] struct {
	Code    int    `json:"code"`
	Data    T      `json:"data"`
	Message string `json:"message"`
}

type javaInternalAuthData struct {
	UserID   int64  `json:"userId"`
	UserRole string `json:"userRole"`
}

func (s *FrontendServer) authorizeByJavaSession(r *http.Request) (*javaInternalAuthData, int, string) {
	if s.javaBaseURL == "" || s.javaToken == "" {
		return nil, http.StatusForbidden, "java auth not configured"
	}

	cookie := r.Header.Get("Cookie")
	if strings.TrimSpace(cookie) == "" {
		return nil, http.StatusUnauthorized, "missing cookie"
	}

	url := s.javaBaseURL + "/internal/auth/session"
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, http.StatusInternalServerError, "create auth request failed"
	}
	req.Header.Set("Cookie", cookie)
	req.Header.Set("X-Internal-Token", s.javaToken)

	client := &http.Client{Timeout: 3 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, http.StatusBadGateway, "java auth request failed"
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	var parsed javaBaseResponse[javaInternalAuthData]
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, http.StatusBadGateway, "java auth response parse failed"
	}
	if parsed.Code != 0 || parsed.Data.UserID <= 0 {
		return nil, http.StatusUnauthorized, "not logged in"
	}
	return &parsed.Data, 0, ""
}

func (s *FrontendServer) handleAiInterviewWS(c *gin.Context) {
	sessionID := c.Query("sessionId")
	if strings.TrimSpace(sessionID) == "" {
		c.JSON(http.StatusBadRequest, gin.H{"code": 40000, "message": "missing sessionId"})
		return
	}

	// 支持两种模式：
	// 1) 本地 / 调试环境：通过 query userId 直接透传用户身份（不依赖 Java 会话）
	// 2) 生产：无 userId 时，通过 Java Session 做鉴权
	var authData *javaInternalAuthData
	userIDStr := c.Query("userId")
	if strings.TrimSpace(userIDStr) != "" {
		if uid, err := strconv.ParseInt(userIDStr, 10, 64); err == nil && uid > 0 {
			authData = &javaInternalAuthData{
				UserID:   uid,
				UserRole: "user",
			}
		} else {
			c.JSON(http.StatusBadRequest, gin.H{"code": 40000, "message": "invalid userId"})
			return
		}
	} else {
		var status int
		var msg string
		authData, status, msg = s.authorizeByJavaSession(c.Request)
		if status != 0 {
			c.JSON(status, gin.H{"code": status, "message": msg})
			return
		}
	}

	// Upgrade to WebSocket
	conn, err := s.upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		logrus.Error("Upgrade Connection Failed:", err)
		return
	}

	// store as realtime conn (MVP: single active realtime session)
	s.mu.Lock()
	s.clients[conn] = true
	s.RealTimeConn = conn
	s.currentSessionId = sessionID
	s.currentUserId = authData.UserID
	remoteAddr := conn.RemoteAddr().String()
	s.mu.Unlock()

	logrus.Infof("ai-interview ws connected: remote=%s userId=%d sessionId=%s role=%s",
		remoteAddr, authData.UserID, sessionID, authData.UserRole)

	defer func() {
		// Best-effort hangup to avoid orphan calls when browser closes without sending command
		if s.backendConn != nil {
			hangupCmd := pbx_back_end.HangupCommand{
				Command:   "hangup",
				Reason:    "ws_closed",
				Initiator: "caller",
			}
			if cmdBytes, err := json.Marshal(hangupCmd); err == nil {
				_ = s.backendConn.WriteMessage(websocket.TextMessage, cmdBytes)
			}
		}

		conn.Close()
		s.mu.Lock()
		delete(s.clients, conn)
		if s.RealTimeConn == conn {
			s.RealTimeConn = nil
		}
		s.mu.Unlock()
		logrus.Info("ai-interview ws connection closed")
	}()

	// Immediately notify frontend about binding info (optional)
	bindEvent := map[string]interface{}{
		"event":     "aiInterviewBound",
		"userId":    authData.UserID,
		"sessionId": sessionID,
	}
	if eventBytes, err := json.Marshal(bindEvent); err == nil {
		_ = conn.WriteMessage(websocket.TextMessage, eventBytes)
	}

	done := make(chan struct{})
	go s.ReceiveRealTimeMessage(conn, done)
	<-done
}

// safeWriteToRealTimeConn 线程安全地向 RealTimeConn 写入消息
func (s *FrontendServer) safeWriteToRealTimeConn(messageType int, data []byte) error {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()

	s.mu.Lock()
	conn := s.RealTimeConn
	s.mu.Unlock()

	if conn == nil {
		logrus.Warn("safeWriteToRealTimeConn: RealTimeConn is nil, message not sent")
		return fmt.Errorf("RealTimeConn is nil")
	}

	// 检查连接状态
	if conn.RemoteAddr() == nil {
		logrus.Warn("safeWriteToRealTimeConn: RealTimeConn RemoteAddr is nil, connection may be closed")
		return fmt.Errorf("RealTimeConn RemoteAddr is nil")
	}

	remoteAddrStr := "unknown"
	if conn.RemoteAddr() != nil {
		remoteAddrStr = conn.RemoteAddr().String()
	}
	logrus.Infof("safeWriteToRealTimeConn: Writing message to RealTimeConn (remote: %s), size: %d bytes, content: %s",
		remoteAddrStr, len(data), string(data))

	// 检查连接状态
	connState := "unknown"
	if conn.RemoteAddr() != nil {
		connState = "connected"
	} else {
		connState = "disconnected"
	}
	logrus.Infof("safeWriteToRealTimeConn: Connection state: %s", connState)

	if err := conn.WriteMessage(messageType, data); err != nil {
		if websocket.IsCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
			// 连接已关闭，清理连接
			s.mu.Lock()
			if s.RealTimeConn == conn {
				s.RealTimeConn = nil
			}
			s.mu.Unlock()
		}
		logrus.WithError(err).Error("safeWriteToRealTimeConn: Failed to write message")
		return err
	}
	logrus.Info("safeWriteToRealTimeConn: Message written successfully to RealTimeConn")
	return nil
}

// sendTextAnswerToJava 将最终 ASR 文本作为回答上报到 Java AI 面试后端
func (s *FrontendServer) sendTextAnswerToJava(text string) {
	s.mu.Lock()
	sessionId := s.currentSessionId
	userId := s.currentUserId
	questionId := s.currentQuestionId
	baseURL := s.javaBaseURL
	token := s.javaToken
	s.mu.Unlock()

	if baseURL == "" || token == "" || sessionId == "" || userId == 0 || questionId == "" {
		logrus.Warnf("sendTextAnswerToJava: missing context baseURL=%s token?%v sessionId=%s userId=%d questionId=%s",
			baseURL, token != "", sessionId, userId, questionId)
		return
	}

	url := fmt.Sprintf("%s/ai-interview/sessions/%s/answers/text?userId=%d",
		baseURL, sessionId, userId)

	payload := map[string]interface{}{
		"questionId":      questionId,
		"textAnswer":      text,
		"durationSeconds": nil,
		"asrConfidence":   nil,
	}
	body, _ := json.Marshal(payload)

	req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		logrus.WithError(err).Error("sendTextAnswerToJava: build request failed")
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Token", token)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		logrus.WithError(err).Error("sendTextAnswerToJava: request failed")
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		respBody, _ := io.ReadAll(resp.Body)
		logrus.Errorf("sendTextAnswerToJava: non-200 status=%d body=%s", resp.StatusCode, string(respBody))
		return
	}

	logrus.Info("sendTextAnswerToJava: success")
}

// sendAssistantMessageToJava 将 AI 回复持久化到 Java MongoDB（面试对话记录）
func (s *FrontendServer) sendAssistantMessageToJava(content string) {
	s.mu.Lock()
	sessionId := s.currentSessionId
	userId := s.currentUserId
	baseURL := s.javaBaseURL
	token := s.javaToken
	s.mu.Unlock()

	if baseURL == "" || token == "" || sessionId == "" || userId == 0 {
		return
	}
	if strings.TrimSpace(content) == "" {
		return
	}

	url := fmt.Sprintf("%s/internal/ai-interview/sessions/%s/chat", baseURL, sessionId)
	payload := map[string]interface{}{
		"userId":  userId,
		"role":    "assistant",
		"content": content,
	}
	body, _ := json.Marshal(payload)

	req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		logrus.WithError(err).Error("sendAssistantMessageToJava: build request failed")
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Token", token)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		logrus.WithError(err).Error("sendAssistantMessageToJava: request failed")
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		respBody, _ := io.ReadAll(resp.Body)
		logrus.Errorf("sendAssistantMessageToJava: non-200 status=%d body=%s", resp.StatusCode, string(respBody))
		return
	}
	logrus.Info("sendAssistantMessageToJava: success")
}

// sendUserChatToJava 将实时语音识别到的用户发言写入 Java MongoDB（不依赖 questionId/答案上报）
func (s *FrontendServer) sendUserChatToJava(content string) {
	s.mu.Lock()
	sessionId := s.currentSessionId
	userId := s.currentUserId
	baseURL := s.javaBaseURL
	token := s.javaToken
	s.mu.Unlock()

	if baseURL == "" || token == "" || sessionId == "" || userId == 0 {
		return
	}
	if strings.TrimSpace(content) == "" {
		return
	}

	url := fmt.Sprintf("%s/internal/ai-interview/sessions/%s/chat", baseURL, sessionId)
	payload := map[string]interface{}{
		"userId":  userId,
		"role":    "user",
		"content": content,
	}
	body, _ := json.Marshal(payload)

	req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		logrus.WithError(err).Error("sendUserChatToJava: build request failed")
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Token", token)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		logrus.WithError(err).Error("sendUserChatToJava: request failed")
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		respBody, _ := io.ReadAll(resp.Body)
		logrus.Errorf("sendUserChatToJava: non-200 status=%d body=%s", resp.StatusCode, string(respBody))
		return
	}
	logrus.Info("sendUserChatToJava: success")
}

// shane: 处理前端WebSocket连接
func (s *FrontendServer) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		logrus.Error("Upgrade Connection Failed:", err)
		return
	}

	// shane: 加锁保护 clients map 的写入
	s.mu.Lock()
	s.clients[conn] = true // shane: 设置已经连接（状态信息）
	s.conn = conn          // shane: 一定要记住保存连接，后面需要用到
	s.mu.Unlock()

	defer func() {
		conn.Close()
		// shane: 加锁保护 clients map 的删除
		s.mu.Lock()
		delete(s.clients, conn)
		s.mu.Unlock()
		logrus.Info("WebSocket Connection closed")
	}()

	done := make(chan struct{})
	go s.ReceiveMessages(conn, done) // shane: 启动接收消息的协程
	// shane: 阻塞当前函数
	<-done
}

func (s *FrontendServer) handleWebSocket2(w gin.ResponseWriter, r *http.Request) {
	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		logrus.Error("Upgrade Connection Failed:", err)
		return
	}

	// shane: 加锁保护 clients map 和 RealTimeConn 的写入
	s.mu.Lock()
	s.clients[conn] = true // shane: 设置已经连接（状态信息）
	s.RealTimeConn = conn  // shane: 一定要记住保存连接，后面需要用到
	remoteAddr := conn.RemoteAddr().String()
	s.mu.Unlock()

	logrus.Infof("handleWebSocket2: RealTimeConn established, remote: %s", remoteAddr)

	defer func() {
		conn.Close()
		// shane: 加锁保护 clients map 和 RealTimeConn 的删除
		s.mu.Lock()
		delete(s.clients, conn)
		if s.RealTimeConn == conn {
			s.RealTimeConn = nil
		}
		s.mu.Unlock()
		logrus.Info("RealTime WebSocket Connection closed")
	}()

	done := make(chan struct{})
	go s.ReceiveRealTimeMessage(conn, done) // shane: 启动接收实时消息的协程
	<-done
}

// ReceiveMessages shane: 接收前端发送的消息,没有返回值
func (s *FrontendServer) ReceiveMessages(conn *websocket.Conn, done chan struct{}) {
	// TODO: 设计读超时

	// shane: 接收前端发送的消息
	for {
		if conn == nil {
			logrus.Error("Connection is nil, waiting for connection")
			break
		}
		_, msg, err := conn.ReadMessage()
		if err != nil {
			logrus.Error("Receive from frontend failed:", err)
			s.mu.Lock()
			delete(s.clients, s.conn)
			s.mu.Unlock()
			break
		}
		// shane: 主动关闭连接
		if string(msg) == "close" {
			logrus.Info("Frontend requested to close connection")
			break
		}
		logrus.Infof("Receive from frontend: %s", string(msg))
		// s.handleMessage(msg)
		s.SendMessages(conn, msg) // shane: 接收到消息之后发送消息
	}

	close(done) // shane: 关闭done通道，通知主协程结束
}

func (s *FrontendServer) ReceiveRealTimeMessage(conn *websocket.Conn, done chan struct{}) {
	for {
		if conn == nil {
			logrus.Info("Connection is nil, waiting for connection")
			break
		}
		msgType, msg, err := conn.ReadMessage()
		if err != nil {
			logrus.Error("Receive Frontend Message failed:", err)
			break
		}
		// shane: 主动关闭连接
		if string(msg) == "close" {
			logrus.Info("Frontend requested to close connection")
			break
		}

		if msgType == websocket.TextMessage {
			// shane: parse message
			var frontendEvent struct {
				Event      string          `json:"event"`
				Sdp        string          `json:"sdp"`
				Candidate  json.RawMessage `json:"candidate"`
				Command    string          `json:"command"`
				Reason     string          `json:"reason"`
				Initiator  string          `json:"initiator"`
				QuestionId string          `json:"questionId"`
			}
			if err := json.Unmarshal(msg, &frontendEvent); err != nil {
				logrus.Error("parse front end message failed:", err)
				continue
			}

			// bind current question for realtime ASR -> Java 上传文本答案
			if frontendEvent.Event == "bindQuestion" && frontendEvent.QuestionId != "" {
				s.mu.Lock()
				s.currentQuestionId = frontendEvent.QuestionId
				s.mu.Unlock()
				logrus.Infof("bindQuestion: questionId=%s", frontendEvent.QuestionId)
				continue
			}

			// shane: receive offer
			if frontendEvent.Event == "offer" && frontendEvent.Sdp != "" {
				logrus.Infof("receive front end offer message, sdp: %s", frontendEvent.Sdp)

				inviteCmd := pbx_back_end.InviteCommand{
					Command: "invite",
					Option: pbx_back_end.CallOption{
						Offer:  frontendEvent.Sdp,
						Caller: "frontend",
						Callee: "rust",
						ASR:    s.asrOption,
						TTS:    s.ttsOption,
					},
				}

				cmdBytes, err := json.Marshal(inviteCmd)
				if err != nil {
					logrus.Error("marshal invite command failed:", err)
					continue
				}
				if err := s.backendConn.WriteMessage(websocket.TextMessage, cmdBytes); err != nil {
					logrus.Infof("forward candidate command to rust backend err: %v, Command data: %s", err, string(cmdBytes))
					if s.backendConn == nil {
						logrus.Errorf("Backend connection is nil, trying to reconnect")
						err := s.backendServer.reconnect("webrtc")
						if err != nil {
							return
						} else {
							// shane: 重发invite
							logrus.Info("Reconnected to backend successfully, will retry sending invite command")
							s.backendConn = s.backendServer.Conn
							if err := s.backendConn.WriteMessage(websocket.TextMessage, cmdBytes); err != nil {
								logrus.Error("Retrying to forward invite command failed:", err)
							} else {
								logrus.Info("Successfully retried forwarding invite command to rust backend")
							}
						}
					} else {
						logrus.Error("Failed to forward invite command to rust backend, will retry later")
					}
				} else {
					logrus.Info("Forwarded invite command with ASR config to rust backend")
				}
			}
			// shane: handle hangup event
			if frontendEvent.Command == "hangup" {
				hangupCmd := pbx_back_end.HangupCommand{
					Command:   "hangup",
					Reason:    frontendEvent.Reason,
					Initiator: frontendEvent.Initiator,
				}

				cmdBytes, err := json.Marshal(hangupCmd)
				if err != nil {
					log.Println("marshal hangup command failed:", err)
					continue
				}
				if err := s.backendConn.WriteMessage(websocket.TextMessage, cmdBytes); err != nil {
					log.Println("forward hangup command to rust backend err:", err)
					err := s.backendServer.reconnect("webrtc")
					if err != nil {
						return
					} else {
						// shane: 重发hangup
						s.backendConn = s.backendServer.Conn
						if err := s.backendConn.WriteMessage(websocket.TextMessage, cmdBytes); err != nil {
							log.Println("Retrying to forward hangup command failed:", err)
						} else {
							log.Println("Successfully retried forwarding hangup command to rust backend")
						}
					} // shane: 重连后端
				} else {
					log.Println("Forwarded hangup command to rust backend")
				}
			}
		}
	}
	close(done)
}

// SendMessages shane: 接收到消息之后发送消息
func (s *FrontendServer) SendMessages(conn *websocket.Conn, msg []byte) {
	if conn == nil {
		logrus.Error("Connection is nil, waiting for connection")
		return
	}

	// shane: Stream LLM
	ttsCallback := func(segment string, playID string, autoHangup bool) error {
		streamEvent := map[string]interface{}{
			"event":  "llmStream",
			"text":   segment,
			"playID": playID,
			"final":  autoHangup,
		}
		eventBytes, _ := json.Marshal(streamEvent)

		if err := conn.WriteMessage(websocket.TextMessage, eventBytes); err != nil {
			logrus.Errorf("Failed to send stream segment: %v", err)
			return err
		} else {
			logrus.Infof("Stream segment sent: %s", segment)
		}
		return nil
	}

	// shane: Stream Query
	response, err := s.siliconFlowLLM.QueryStream(string(msg), ttsCallback)
	if err != nil {
		logrus.Error("LLM stream query failed:", err)
		return
	}

	// shane: send complete response
	finalEvent := map[string]interface{}{
		"event": "llmFinal",
		"text":  response,
	}
	s.mu.Lock()
	eventBytes, _ := json.Marshal(finalEvent)
	if err := conn.WriteMessage(websocket.TextMessage, eventBytes); err != nil {
		logrus.Error("Failed to send final response:", err)
	}
	s.mu.Unlock()
}

// receiveBackendMessages shane: 接收并打印后端发送的消息
func (s *FrontendServer) receiveBackendMessages() {
	callType := "webrtc"
	for {
		if s.backendConn == nil {
			// shane: reconnect the backend connection
			err := s.backendServer.reconnect(callType)
			if err != nil {
				time.Sleep(1 * time.Second)
				continue
			}
			s.backendConn = s.backendServer.Conn
		}
		// shane: read message from backend
		messageType, msg, err := s.backendConn.ReadMessage()
		if err != nil {
			// shane: backend connection is closed, set it to nil, and continue the loop
			s.backendConn = nil
			continue
		}
		// shane: type down message fron rust backend
		logrus.Infof("Received from rust backend (type %d): %s", messageType, string(msg))

		// shane: parse the message
		var event struct {
			Event string `json:"event"`
			Text  string `json:"text"`
		}
		if err := json.Unmarshal(msg, &event); err == nil {
			// shane: handle asrFinal and send ASR result to LLM handler
			if event.Event == "asrFinal" && event.Text != "" {
				logrus.Infof("received ASR response: %s", event.Text)

				// 实时语音：无论是否绑定题目，都先把用户发言写入 MongoDB（用于报告分析）
				go s.sendUserChatToJava(event.Text)

				// 若已绑定题目，则把最终 ASR 文本同步给 Java 作为“本题回答”（用于评分 + 结构化 Q&A）
				go s.sendTextAnswerToJava(event.Text)

				// shane: use LLM to handle ASR result
				if s.siliconFlowLLM != nil {
					IsStreaming := true
					if IsStreaming {
						go s.handleASRWithStream(event.Text)
					} else {
						go s.handleASRWithNormal(event.Text)
					}
				}
			} else if event.Event == "asrDelta" {
				// shane: handle ASR delta event
				logrus.Infof("ASR realtime recognize: %s", event.Text)
			} else if event.Event == "speaking" {
				logrus.Info("detecting speaking")
			} else if event.Event == "silence" {
				logrus.Info("detecting silence")
			} else if event.Event == "trackStart" {
				logrus.Info("track started")
			} else if event.Event == "trackEnd" {
				logrus.Info("track ended")
			}
		}

		// shane: forward the message to the frontend
		s.forwardRustMessageToFrontend(msg)

	}

	// log.Println("Stopped listening for backend messages") // shane: 自动重连监听
}

// handleASRWithStream shane: use stream LLM handle ASR result
func (s *FrontendServer) handleASRWithStream(asrText string) {
	logrus.Info("handle ASR result via streaming LLM...")

	// shane: define TTS callback function
	ttsCallback := func(segment string, playID string, autoHangup bool) error {
		// shane: send TTS command to Rust backend
		ttsCmd := pbx_back_end.TtsCommand{
			Command:     "tts",
			Text:        segment,
			Speaker:     s.ttsOption.Speaker,
			PlayID:      playID,
			AutoHangup:  autoHangup,
			Streaming:   true,
			EndOfStream: false,
			Option:      s.ttsOption,
		}

		cmdBytes, err := json.Marshal(ttsCmd)
		if err != nil {
			logrus.Error("generate TTS Command failed:", err)
			return err
		}

		if s.backendConn == nil {
			logrus.Error("backendConn is nil, attempting to reconnect...")
			if err := s.backendServer.reconnect("webrtc"); err != nil {
				logrus.Errorf("reconnect to backend failed: %v", err)
				return err
			}
			s.backendConn = s.backendServer.Conn
			logrus.Info("reconnected to backend successfully.")
		}

		if err := s.backendConn.WriteMessage(websocket.TextMessage, cmdBytes); err != nil {
			logrus.Error("send TTS command to Rust backend failed:", err)
			return err
		} else {
			logrus.Infof("TTS segment sent to Rust backend: %s", segment)
		}

		// shane: send - 使用线程安全的写入方法
		streamEvent := map[string]interface{}{
			"event":  "llmStream",
			"text":   segment,
			"playID": playID,
			"final":  autoHangup,
		}
		eventBytes, _ := json.Marshal(streamEvent)
		if err := s.safeWriteToRealTimeConn(websocket.TextMessage, eventBytes); err != nil {
			logrus.Errorf("Failed to send stream segment to frontend: %v", err)
		} else {
			logrus.Infof("Stream segment sent to frontend: %s", segment)
		}

		return nil
	}

	// 如果用户说的是“下一题 / 下一个问题”等，则认为是控制语句，不再围绕当前问题深挖
	if strings.Contains(asrText, "下一题") || strings.Contains(asrText, "下一个问题") {
		logrus.Info("detect next-question command in ASR text, skip LLM")
		cannedReply := "好的，我们看下一道题。"
		_ = ttsCallback(cannedReply, fmt.Sprintf("sf-%d", time.Now().UnixNano()), false)
		go s.sendAssistantMessageToJava(cannedReply)
		return
	}

	// 构造面试官专用 prompt：在通用对话前拼上 interviewPrompt 和当前回答
	userMsg := asrText
	if s.siliconFlowLLM != nil {
		if ip := s.siliconFlowLLM.GetInterviewPrompt(); ip != "" {
			userMsg = ip + "\n\n候选人本轮回答：" + asrText
		}
	}

	// shane: use streaming query
	response, err := s.siliconFlowLLM.QueryStream(userMsg, ttsCallback)
	if err != nil {
		logrus.Error("LLM handle ASR result failed:", err)
	} else {
		logrus.Infof("LLM stream response completed: %s", response)
		// 持久化 AI 回复到 Java MongoDB，供报告分析使用
		go s.sendAssistantMessageToJava(response)
		// shane: send final llm response to frontend - 使用线程安全的写入方法
		finalEvent := map[string]interface{}{
			"event": "llmFinal",
			"text":  response,
		}
		eventBytes, _ := json.Marshal(finalEvent)
		if err := s.safeWriteToRealTimeConn(websocket.TextMessage, eventBytes); err != nil {
			logrus.Errorf("Failed to send final response to frontend: %v", err)
		}
	}
}

// handleASRWithNormal shane: use normal LLM handle asr result
func (s *FrontendServer) handleASRWithNormal(asrText string) {
	logrus.Info("handle ASR result via normal LLM...")

	response, err := s.siliconFlowLLM.Query(asrText)
	// response, _, err := s.llm.Query("qwen-turbo", event.Text)
	if err != nil {
		logrus.Error("LLM handle ASR result failed:", err)
	} else {
		logrus.Infof("LLM response: %s", response)
		// shane: 使用线程安全的写入方法
		if err := s.safeWriteToRealTimeConn(websocket.TextMessage, []byte(response)); err != nil {
			logrus.Errorf("Failed to send LLM response to frontend: %v", err)
		}

		// shane: send TTS command to Rust backend
		ttsCmd := pbx_back_end.TtsCommand{
			Command: "tts",
			Text:    response,
			Speaker: s.ttsOption.Speaker,
			Option:  s.ttsOption,
		}

		cmdBytes, err := json.Marshal(ttsCmd)
		if err != nil {
			logrus.Error("generate TTS Command failed:", err)
		} else {
			if err := s.backendConn.WriteMessage(websocket.TextMessage, cmdBytes); err != nil {
				logrus.Error("send TTS command to Rust backend failed:", err)
			} else {
				logrus.Info("TTS command sent to Rust backend successfully")
			}
		}
	}
}

// forwardRustMessageToFrontend shane: 转发后端消息给前端
func (s *FrontendServer) forwardRustMessageToFrontend(msg []byte) {
	if err := s.safeWriteToRealTimeConn(websocket.TextMessage, msg); err != nil {
		logrus.Error("Failed to forward backend message to frontend:", err)
	} else {
		logrus.Info("Successfully forwarded backend message to frontend")
	}
}
