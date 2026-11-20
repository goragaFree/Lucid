#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SaturationConfig {
    float Saturation;
    float Contrast;
};

// Яркость по Rec. 709: при Saturation = 0 картинка становится честно чёрно-белой
const vec3 Luma = vec3(0.2126, 0.7152, 0.0722);

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float luma = dot(color.rgb, Luma);
    vec3 result = mix(vec3(luma), color.rgb, Saturation);
    // Контраст вокруг серой середины: >1 — глубже тени и ярче света
    result = (result - 0.5) * Contrast + 0.5;
    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
