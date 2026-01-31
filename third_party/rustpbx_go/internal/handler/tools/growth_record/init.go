package growth_record

import "pbx_back_end/internal/handler/tools"

func init() {
	tools.Register("addGrowthRecord", DefinitionAddGrowthRecord(), Handle)
	tools.Register("addTodayGrowthRecord", DefinitionAddToday(), HandleAddToday)
}
