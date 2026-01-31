package search

import "pbx_back_end/internal/handler/tools"

func init() {
	tools.Register("searchOnline", Definition(), Handle)
}
