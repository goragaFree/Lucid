#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in float cornerRadius;
flat in float ringThickness;
out vec4 fragColor;

void main() {
    float radius = min(cornerRadius, min(halfSize.x, halfSize.y));
    vec2 delta = abs(localPos) - (halfSize - vec2(radius));
    float distance = length(max(delta, vec2(0.0))) + min(max(delta.x, delta.y), 0.0) - radius;
    float aa = max(length(vec2(dFdx(distance), dFdy(distance))), 0.0001);
    float coverage = clamp(0.5 - distance / aa, 0.0, 1.0);
    // Выборка не выходит за 8x8 область лица даже на расширенном AA-кваде.
    vec2 cell = clamp((localPos + halfSize) / (2.0 * halfSize), vec2(0.0001), vec2(0.9999));
    vec4 face = texture(Sampler0, (vec2(8.0, 8.0) + cell * 8.0) / 64.0);
    vec4 hat = texture(Sampler0, (vec2(40.0, 8.0) + cell * 8.0) / 64.0);
    float alpha = face.a + hat.a * (1.0 - face.a);
    vec3 rgb = (hat.rgb * hat.a + face.rgb * face.a * (1.0 - hat.a)) / max(alpha, 0.0001);
    vec4 color = vec4(rgb, alpha) * vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.0) discard;
    fragColor = color;
}
