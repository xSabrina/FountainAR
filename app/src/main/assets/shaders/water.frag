#version 300 es

in vec3 v_Normal;
in vec3 v_Position;
in vec2 v_TexCoord;

out vec4 o_FragColor;

uniform vec3 u_LightDirection;
uniform vec3 u_CameraPosition;

const float shininess = 64.0;
const vec3 waterColor = vec3(1.0, 1.0, 1.0);
const float waterTransparency = 0.8;

void main() {
    vec3 normal = normalize(v_Normal);
    vec3 lightDirection = normalize(u_LightDirection);
    vec3 viewDirection = normalize(u_CameraPosition - v_Position);
    vec3 halfVector = normalize(lightDirection + viewDirection);

    vec3 ambientColor = vec3(0.2, 0.2, 0.2);
    vec3 ambient = ambientColor * waterColor;

    float diffuse = max(dot(normal, lightDirection), 0.0);
    vec3 diffuseColor = vec3(1.0, 1.0, 1.0);
    vec3 diffuseLight = diffuseColor * waterColor * diffuse;

    float specular = pow(max(dot(normal, halfVector), 0.0), shininess);
    vec3 specularColor = vec3(1.0, 1.0, 1.0);
    vec3 specularLight = specularColor * specular;

    vec3 finalColor = ambient + diffuseLight + specularLight;

    finalColor = pow(finalColor, vec3(0.4545));

    o_FragColor = vec4(finalColor, waterTransparency);
}