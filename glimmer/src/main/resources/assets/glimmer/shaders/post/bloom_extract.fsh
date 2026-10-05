#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform BloomConfig {
    float Threshold;
    float Softness;
    float Strength;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float sat = (mx - mn) / max(mx, 0.0001);
    float bright = smoothstep(Threshold, Threshold + Softness, mx);
    float neon = smoothstep(0.45, 0.75, sat);   // vivid effect colors
    float hot = smoothstep(0.93, 1.0, mn);      // white-hot cores
    fragColor = vec4(c * bright * max(neon, hot), 1.0);
}
