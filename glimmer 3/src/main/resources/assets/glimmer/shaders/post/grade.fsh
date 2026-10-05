#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform GradeConfig {
    float Saturation;
    float Contrast;
    float Tint;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, Saturation);
    c = (c - 0.5) * Contrast + 0.5;
    c *= mix(vec3(1.0), vec3(0.94, 0.99, 1.06), Tint);
    fragColor = vec4(max(c, vec3(0.0)), 1.0);
}
