#version 300 es
precision mediump float;

uniform mat4 u_ModelViewProjection;
uniform mat3 u_NormalView;

in vec4 a_Position;
in vec3 a_Normal;

out vec3 v_Position;
out vec3 v_Normal;

void main() {
  gl_Position = u_ModelViewProjection * a_Position;
  v_Position = a_Position.xyz;
  v_Normal = normalize(u_NormalView * a_Normal);
}