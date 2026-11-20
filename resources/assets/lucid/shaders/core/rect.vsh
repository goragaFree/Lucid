#version 330

// Копия объявлений из ванильного gui.vsh.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;   // позиция внутри фигуры, в пикселях от её центра
in ivec2 UV1;  // половина ширины/высоты * 16
in ivec2 UV2;  // x: радиус * 16, y: толщина обводки * 16 (0 = заливка)

out vec4 vertexColor;
out vec2 localPos;
flat out vec2 halfSize;
flat out float cornerRadius;
flat out float ringThickness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;

    // Матрица к localPos не применяется: форма считается в своей системе координат,
    // иначе поворот исказил бы скругление.
    localPos = UV0;

    halfSize = vec2(UV1) / 16.0;
    cornerRadius = float(UV2.x) / 16.0;
    ringThickness = float(UV2.y) / 16.0;
}
