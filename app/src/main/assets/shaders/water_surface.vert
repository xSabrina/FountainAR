#version 300 es

precision mediump float;

uniform mat4 u_ModelViewProjection;
uniform mat3 u_NormalView;

in vec4 a_Position;
in vec3 a_Normal;
in vec2 a_TexCoord;

out vec3 v_Position;
out vec3 v_Normal;
out vec2 v_TexCoord;

void main() {
  gl_Position = u_ModelViewProjection * a_Position;
  v_Position = a_Position.xyz;
  v_Normal = normalize(u_NormalView * a_Normal);
  v_TexCoord = a_TexCoord;
}
