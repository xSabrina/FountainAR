#version 300 es
precision mediump float;

in vec3 v_Normal;
in vec3 v_Position;

out vec4 FragColor;

const float refractionIndex = 1.33;
const float reflectivity = 0.8;
const float fresnelPower = 5.0;

float calculateFresnel(vec3 incidentRay, vec3 normal) {
    return pow(1.0 - dot(incidentRay, normal), fresnelPower);
}

vec3 calculateAttenuation(float distance) {
    float attenuation = 0.15 / distance;
    return vec3(1.0, 1.0, 1.0) - vec3(attenuation);
}

void main() {
    vec3 incidentRay = normalize(v_Normal);
    vec3 refractedRay = refract(incidentRay, v_Normal, refractionIndex);
    vec3 reflectedRay = reflect(incidentRay, v_Normal);

    float fresnel = calculateFresnel(-incidentRay, refractedRay);

    vec4 refractedColor = vec4(0.7, 0.85, 1.0, 0.7);
    vec4 reflectedColor = vec4(1.0, 1.0, 1.0, 0.7);
    vec4 finalColor = mix(refractedColor, reflectedColor, fresnel);

    float distance = length(v_Position);
    vec3 attenuation = calculateAttenuation(distance);
    finalColor.rgb *= attenuation;
    finalColor.a = 1.0 - fresnel;

    FragColor = finalColor;
}