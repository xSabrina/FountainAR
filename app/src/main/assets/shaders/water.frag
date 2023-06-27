#version 320 es

precision mediump float;

in vec4 o_Position;

const float refractionIndex = 1.5;
const float reflectivity = 0.8;
const float fresnelPower = 5.0;

vec3 calculateRefraction(vec3 incidentRay, vec3 normal, float refractionIndex) {
    return refract(incidentRay, normal, refractionIndex);
}

vec3 calculateReflection(vec3 incidentRay, vec3 normal) {
    return reflect(incidentRay, normal);
}

out vec4 o_FragColor;

void main() {
    vec3 incidentRay = normalize(o_Position.xyz);

    vec3 normal = vec3(0.0, 0.0, 1.0);
    vec3 refractedRay = calculateRefraction(incidentRay, normal, refractionIndex);
    vec3 reflectedRay = calculateReflection(incidentRay, normal);

    float fresnel = reflectivity + (1.0 - reflectivity) * pow(1.0 - dot(-incidentRay, refractedRay), fresnelPower);

    vec4 refractedColor = vec4(0.5, 0.5, 1.0, 1.0);
    vec4 reflectedColor = vec4(1.0, 1.0, 1.0, 1.0);

    vec4 finalColor = mix(refractedColor, reflectedColor, fresnel);
    finalColor.a = 1.0 - fresnel;

    o_FragColor = finalColor;
}