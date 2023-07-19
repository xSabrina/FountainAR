#version 300 es
precision mediump float;

in vec3 v_Normal;
in vec3 v_Position;
in vec2 v_TexCoord;

out vec4 o_FragColor;

uniform vec3 u_LightDirection;
uniform vec3 u_CameraPosition;
uniform sampler2D u_ReflectionTexture;

const float shininess = 64.0;
const vec3 waterColor = vec3(0.2, 0.5, 1.0);
const float waterTransparency = 0.5;
const float distortionStrength = 0.03;

float random(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}



void main() {
    vec2 distortionOffset = vec2(
    random(displacedPosition.xy * u_Time) - 0.5,
    random(displacedPosition.yz * u_Time) - 0.5
    ) * distortionStrength;

    vec3 distortedPosition = vec3(
    displacedPosition.x + distortionOffset.x,
    displacedPosition.y + distortionOffset.y,
    displacedPosition.z
    );

    vec3 normal = normalize(v_Normal);
    vec3 lightDirection = normalize(u_LightDirection);
    vec3 viewDirection = normalize(u_CameraPosition - distortedPosition);
    vec3 halfVector = normalize(lightDirection + viewDirection);

    vec3 ambientColor = vec3(0.2, 0.2, 0.2);
    float ambientIntensity = dot(ambientColor, waterColor);
    vec3 ambient = ambientIntensity * waterColor;

    float diffuse = max(dot(normal, lightDirection), 0.0);
    vec3 diffuseColor = vec3(1.0, 1.0, 1.0);
    vec3 diffuseLight = diffuseColor * waterColor * diffuse;

    float specular = pow(max(dot(normal, halfVector), 0.0), shininess);
    vec3 specularColor = vec3(1.0, 1.0, 1.0);
    vec3 specularLight = specularColor * specular;

    vec3 finalColor = ambient + diffuseLight + specularLight;
    finalColor = pow(finalColor, vec3(0.4545));

    // Reflection
    vec2 texCoord = vec2(0.5, 0.5) + distortedPosition.xy / 2.0;
    vec4 reflectionColor = texture(u_ReflectionTexture, texCoord);

    // Blend reflection with original color
    finalColor = mix(finalColor, reflectionColor.rgb, waterTransparency);

    o_FragColor = vec4(finalColor, waterTransparency);
}
