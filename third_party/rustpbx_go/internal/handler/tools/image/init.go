package image

import "pbx_back_end/internal/handler/tools"

func init() {
	tools.Register("generateImage", GenerateImageDefinition(), HandleGenerateImage)
	tools.Register("transformText", TransformTextDefinition(), HandleTransformText)
}
