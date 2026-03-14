package config

import (
	"os"
	"strconv"
)

type Config struct {
	Server   ServerConfig
	Backend  BackendConfig
	Java     JavaConfig
	Audio    AudioConfig
	ASR      ASRConfig
	TTS      TTSConfig
	LLM      LLMConfig
	VAD      VADConfig
	Call     CallConfig
	WebHook  WebHookConfig
	EOU      EOUConfig
	Database DatabaseConfig
	BigModel BigModelConfig
}

type DatabaseConfig struct {
	DSN string `yaml:"dsn"`
}

type ServerConfig struct {
	Port string `yaml:"port"`
}
type BackendConfig struct {
	URL      string `yaml:"url"`
	CallType string `yaml:"call_type"`
}

// JavaConfig is used for calling Java backend internal endpoints.
type JavaConfig struct {
	// BaseURL example: http://localhost:8121/api
	BaseURL string `yaml:"base_url"`
	// InternalToken header value for X-Internal-Token
	InternalToken string `yaml:"internal_token"`
}
type AudioConfig struct {
	Codec string `yaml:"codec"`
}
type ASRConfig struct {
	Provider   string `yaml:"provider"`
	Language   string `yaml:"language"`
	SampleRate uint32 `yaml:"sample_rate"`
	AppID      string `yaml:"app_id"`
	SecretID   string `yaml:"secret_id"`
	SecretKey  string `yaml:"secret_key"`
	Endpoint   string `yaml:"endpoint"`
	ModelType  string `yaml:"model_type"`
}
type TTSConfig struct {
	Provider        string  `yaml:"provider"`
	SampleRate      int32   `yaml:"sample_rate"`
	Speaker         string  `yaml:"speaker"`
	Speed           float32 `yaml:"speed"`
	Volume          int32   `yaml:"volume"`
	EmotionCategory string  `yaml:"emotion"`
	AppID           string  `yaml:"app_id"`
	SecretID        string  `yaml:"secret_id"`
	SecretKey       string  `yaml:"secret_key"`
	Codec           string  `yaml:"codec"`
	Endpoint        string  `yaml:"endpoint"`
}
type LLMConfig struct {
	APIKey       string
	Model        string
	URL          string
	SystemPrompt string
	SiliconFlow  struct {
		APIKey          string
		URL             string
		Model           string
		SystemPrompt    string
		InterviewModel  string
		InterviewSystemPrompt string
		InterviewPrompt string
	}
}

type BigModelConfig struct {
	SearchApiUrl   string `yaml:"search_api_url"`
	SearchApiKey   string `yaml:"search_api_key"`
	SearchApiModel string `yaml:"search_api_model"`
}
type VADConfig struct {
	Model     string `yaml:"model"`
	Endpoint  string `yaml:"endpoint"`
	SecretKey string `yaml:"secret_key"`
}
type CallConfig struct {
	BreakOnVAD bool   `yaml:"break_on_vad"`
	WithSIP    bool   `yaml:"with_sip"`
	Record     bool   `yaml:"record"`
	Caller     string `yaml:"caller"`
	Callee     string `yaml:"callee"`
}
type WebHookConfig struct {
	Addr   string `yaml:"addr"`
	Prefix string `yaml:"prefix"`
}
type EOUConfig struct {
	Type     string `yaml:"type"`
	Endpoint string `yaml:"endpoint"`
}

// getEnv 获取环境变量，如果不存在则返回默认值
func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// getEnvUint32 获取环境变量并转换为 uint32，如果不存在或转换失败则返回默认值
func getEnvUint32(key string, defaultValue uint32) uint32 {
	if value := os.Getenv(key); value != "" {
		if parsed, err := strconv.ParseUint(value, 10, 32); err == nil {
			return uint32(parsed)
		}
	}
	return defaultValue
}

// getEnvInt32 获取环境变量并转换为 int32，如果不存在或转换失败则返回默认值
func getEnvInt32(key string, defaultValue int32) int32 {
	if value := os.Getenv(key); value != "" {
		if parsed, err := strconv.ParseInt(value, 10, 32); err == nil {
			return int32(parsed)
		}
	}
	return defaultValue
}

// getEnvFloat32 获取环境变量并转换为 float32，如果不存在或转换失败则返回默认值
func getEnvFloat32(key string, defaultValue float32) float32 {
	if value := os.Getenv(key); value != "" {
		if parsed, err := strconv.ParseFloat(value, 32); err == nil {
			return float32(parsed)
		}
	}
	return defaultValue
}

// getEnvBool 获取环境变量并转换为 bool，如果不存在或转换失败则返回默认值
func getEnvBool(key string, defaultValue bool) bool {
	if value := os.Getenv(key); value != "" {
		if parsed, err := strconv.ParseBool(value); err == nil {
			return parsed
		}
	}
	return defaultValue
}

// LoadConfig 从环境变量加载配置
func LoadConfig() (*Config, error) {
	config := &Config{
		Server: ServerConfig{
			Port: getEnv("SERVER_PORT", "8081"),
		},
		Backend: BackendConfig{
			URL:      getEnv("BACKEND_URL", "ws://localhost:8080"),
			CallType: getEnv("BACKEND_CALL_TYPE", "webrtc"),
		},
		Java: JavaConfig{
			BaseURL:       getEnv("JAVA_BASE_URL", "http://localhost:8121/api"),
			InternalToken: getEnv("JAVA_INTERNAL_TOKEN", ""),
		},
		Audio: AudioConfig{
			Codec: getEnv("AUDIO_CODEC", "g722"),
		},
		ASR: ASRConfig{
			Provider:   getEnv("ASR_PROVIDER", "tencent"),
			Language:   getEnv("ASR_LANGUAGE", "zh-CN"),
			SampleRate: getEnvUint32("ASR_SAMPLE_RATE", 16000),
			AppID:      getEnv("ASR_APP_ID", ""),
			SecretID:   getEnv("ASR_SECRET_ID", ""),
			SecretKey:  getEnv("ASR_SECRET_KEY", ""),
			Endpoint:   getEnv("ASR_ENDPOINT", "asr.tencentcloudapi.com"),
			ModelType:  getEnv("ASR_MODEL_TYPE", "16k_zh"),
		},
		TTS: TTSConfig{
			Provider:        getEnv("TTS_PROVIDER", "tencent"),
			SampleRate:      getEnvInt32("TTS_SAMPLE_RATE", 16000),
			Speaker:         getEnv("TTS_SPEAKER", "601008"),
			Speed:           getEnvFloat32("TTS_SPEED", 1.0),
			Volume:          getEnvInt32("TTS_VOLUME", 10),
			EmotionCategory: getEnv("TTS_EMOTION", "jieshuo"),
			AppID:           getEnv("TTS_APP_ID", ""),
			SecretID:        getEnv("TTS_SECRET_ID", ""),
			SecretKey:       getEnv("TTS_SECRET_KEY", ""),
			Codec:           getEnv("TTS_CODEC", "pcm"),
			Endpoint:        getEnv("TTS_ENDPOINT", "tts.tencentcloudapi.com"),
		},
		LLM: LLMConfig{
			APIKey:       getEnv("LLM_API_KEY", ""),
			Model:        getEnv("LLM_MODEL", "qwen-turbo"),
			URL:          getEnv("LLM_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
			SystemPrompt: getEnv("LLM_SYSTEM_PROMPT", "You are a helpful assistant. Provide concise responses. Use 'hangup' tool when the conversation is complete."),
		},
		VAD: VADConfig{
			Model:     getEnv("VAD_MODEL", "silero"),
			Endpoint:  getEnv("VAD_ENDPOINT", ""),
			SecretKey: getEnv("VAD_SECRET_KEY", ""),
		},
		Call: CallConfig{
			BreakOnVAD: getEnvBool("CALL_BREAK_ON_VAD", false),
			WithSIP:    getEnvBool("CALL_WITH_SIP", false),
			Record:     getEnvBool("CALL_RECORD", false),
			Caller:     getEnv("CALL_CALLER", ""),
			Callee:     getEnv("CALL_CALLEE", ""),
		},
		WebHook: WebHookConfig{
			Addr:   getEnv("WEBHOOK_ADDR", ""),
			Prefix: getEnv("WEBHOOK_PREFIX", "/webhook"),
		},
		EOU: EOUConfig{
			Type:     getEnv("EOU_TYPE", ""),
			Endpoint: getEnv("EOU_ENDPOINT", ""),
		},
		Database: DatabaseConfig{
			DSN: getEnv("DATABASE_DSN", "{}:{}@tcp({}:3306)/VoicePBX?charset=utf8mb4&parseTime=True&loc=Local"),
		},
		BigModel: BigModelConfig{
			SearchApiUrl:   getEnv("BIG_MODEL_SEARCH_API_URL", "https://open.bigmodel.cn/api/paas/v4/assistant"),
			SearchApiKey:   getEnv("BIG_MODEL_SEARCH_API_KEY", ""),
			SearchApiModel: getEnv("BIG_MODEL_SEARCH_API_MODEL", "glm-4v-flash"),
		},
	}

	// 设置 SiliconFlow 配置
	config.LLM.SiliconFlow.APIKey = getEnv("LLM_SILICONFLOW_API_KEY", "")
	config.LLM.SiliconFlow.URL = getEnv("LLM_SILICONFLOW_URL", "https://api.siliconflow.cn/v1/chat/completions")
	config.LLM.SiliconFlow.Model = getEnv("LLM_SILICONFLOW_MODEL", "Qwen/Qwen2.5-7B-Instruct")
	config.LLM.SiliconFlow.SystemPrompt = getEnv("LLM_SILICONFLOW_SYSTEM_PROMPT", "You are a helpful assistant. Provide concise responses. Use 'hangup' tool when the conversation is complete. 如果我说使用联网搜索那你就使用这个search online这个工具，如果我说生成图片那你就使用generate image这个工具.please use Chinese to replay!! 铭记使用中文回答！！")
	config.LLM.SiliconFlow.InterviewModel = getEnv("LLM_SILICONFLOW_INTERVIEW_MODEL", config.LLM.SiliconFlow.Model)
	config.LLM.SiliconFlow.InterviewSystemPrompt = getEnv("LLM_SILICONFLOW_INTERVIEW_SYSTEM_PROMPT", "你是一个严谨的中文面试官。你不调用任何工具。你的目标是逐步提问并评估候选人。每次只问一个问题，保持问题清晰简短。")
	config.LLM.SiliconFlow.InterviewPrompt = getEnv("LLM_SILICONFLOW_INTERVIEW_PROMPT", "")

	return config, nil
}
