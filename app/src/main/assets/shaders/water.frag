#version 300 es

precision mediump float;

in vec3 v_Normal;
in vec3 v_Position;
in vec2 v_TexCoord;

out vec4 o_FragColor;

uniform vec3 u_LightDirection;
uniform vec3 u_CameraPosition;

const float shininess = 128.0; // Increased shininess for a more pronounced effect
const vec3 waterColor = vec3(0.8, 0.8, 1.0); // Slightly bluish water color
const float waterTransparency = 0.85; // Slightly more transparent

void main() {
    vec3 normal = normalize(v_Normal);
    vec3 lightDirection = normalize(u_LightDirection);
    vec3 viewDirection = normalize(u_CameraPosition - v_Position);
    vec3 halfVector = normalize(lightDirection + viewDirection);

    vec3 ambientColor = vec3(0.2, 0.2, 0.2);
    float ambientIntensity = dot(ambientColor, waterColor);
    vec3 ambient = ambientIntensity * waterColor;

    float diffuse = max(dot(normal, lightDirection), 0.0);
    vec3 diffuseColor = vec3(0.8, 0.8, 0.8); // Slightly darker diffuse color
    vec3 diffuseLight = diffuseColor * waterColor * diffuse;

    float specular = pow(max(dot(normal, halfVector), 0.0), shininess);
    vec3 specularColor = vec3(1.0, 1.0, 1.0);
    vec3 specularLight = specularColor * specular;

    vec3 finalColor = ambient + diffuseLight + specularLight;
    finalColor = pow(finalColor, vec3(0.4545));

    o_FragColor = vec4(finalColor, waterTransparency);
}