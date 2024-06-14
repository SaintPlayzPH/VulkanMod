#version 450
layout (constant_id = 0) const bool USE_FOG = true;
#include "fog.glsl"

layout(binding = 1) uniform UBO{
    vec4 ColorModulator;
    vec4 FogColor;
    float FogStart;
    float FogEnd;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in float vertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    fragColor = USE_FOG ? linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor) : color;
}


