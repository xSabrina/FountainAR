#version 300 es

precision mediump float;

uniform sampler2D u_Texture;

in vec2 v_TexCoord;

layout(location = 0) out vec4 o_FragColor;

void main() {
    vec2 texCoord = vec2(v_TexCoord.x, 1.0 - v_TexCoord.y);
    o_FragColor = vec4(texture(u_Texture, texCoord).rgb, 0.7);

    return;
}
