//light.glsl
//#pragma once
const float MINECRAFT_LIGHT_POWER = 0.6;
const float MINECRAFT_AMBIENT_LIGHT = 0.4;

vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {
    return vec4(color.rgb * min(1.0, (max(0.0, dot(normalize(lightDir0), normal)) + max(0.0, dot(normalize(lightDir1), normal))) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT), color.a);
}

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texelFetch(lightMap, ivec2(uv >> 4), 0);
}

vec4 sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texelFetch(lightMap, ivec2(uv >> 4), 0);
}

vec4 sample_lightmap2(sampler2D lightMap, uint uv) {
    return texelFetch(lightMap, ivec2(uv >> 12, (uv >> 4) & 0xF), 0);
}
