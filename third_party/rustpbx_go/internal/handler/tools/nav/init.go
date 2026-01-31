package nav

import "pbx_back_end/internal/handler/tools"

func init() {
	tools.Register("switchNavTab", Definition(), Handle)
}
