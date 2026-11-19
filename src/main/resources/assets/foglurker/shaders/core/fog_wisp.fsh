#version 150

// A wisp of fog. Unlike Minecraft's particles there is no hard cut-off at low alpha: the faint edges stay soft.
uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.004) {
        discard;
    }
    fragColor = color;
}
