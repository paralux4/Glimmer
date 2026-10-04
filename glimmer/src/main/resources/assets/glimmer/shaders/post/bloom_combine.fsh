#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;

layout(std140) uniform BloomConfig {
    float Threshold;
    float Softness;
    float Strength;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 scene = texture(SceneSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    fragColor = vec4(scene + bloom * Strength, 1.0);
}
