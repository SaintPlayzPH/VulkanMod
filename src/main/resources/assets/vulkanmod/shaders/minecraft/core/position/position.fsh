#version 450
layout (constant_id = 0) const bool USE_SKY_FOG = true;
vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    if (vertexDistance <= fogStart) {
        return inColor;
    }

    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

layout(binding = 1) uniform UBO{
    vec4 ColorModulator;
    vec4 FogColor;
    float FogStart;
    float FogEnd;
};

layout(location = 0) in float vertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = USE_SKY_FOG ? linear_fog(ColorModulator, vertexDistance, FogStart, FogEnd, FogColor) : ColorModulator;
}
