#version 320 es

uniform mat4 uMVPMatrix;

in vec4 a_Position;

out vec4 o_Position;

void main() {
  gl_Position = uMVPMatrix * a_Position;
  o_Position = gl_Position;
}