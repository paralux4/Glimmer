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
    float l = max(c.r, max(c.g, c.b));
    float k = smoothstep(Threshold, Threshold + Softness, l);
    fragColor = vec4(c * k, 1.0);
}
