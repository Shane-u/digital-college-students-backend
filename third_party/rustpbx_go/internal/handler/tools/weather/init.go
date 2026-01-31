package weather

import "pbx_back_end/internal/handler/tools"

func init() {
	tools.Register("queryWeather", Definition(), Handle)
}
