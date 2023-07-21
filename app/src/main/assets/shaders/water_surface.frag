#version 300 es

precision mediump float;

const int kNumberOfRoughnessLevels = NUMBER_OF_MIPMAP_LEVELS;

uniform vec3 u_LightIntensity;
uniform vec3 u_ViewLightDirection;
uniform vec3 u_SphericalHarmonicsCoefficients[9];
uniform samplerCube u_Cubemap;
uniform sampler2D u_DfgTexture;
uniform mat4 u_ViewInverse;
uniform bool u_LightEstimateIsValid;

struct MaterialParameters {
    vec3 diffuse;
    float perceptualRoughness;
    float roughness;
    float metallic;
    float ambientOcclusion;

    vec3 f0;
    vec2 dfg;
    vec3 energyCompensation;
};

struct ShadingParameters {
    float normalDotView;
    float normalDotHalfway;
    float normalDotLight;
    float viewDotHalfway;
    float oneMinusNormalDotHalfwaySquared;

    vec3 worldNormalDirection;
    vec3 worldReflectDirection;
};

in vec3 v_ViewPosition;
in vec3 v_ViewNormal;
in vec2 v_TexCoord;

layout(location = 0) out vec4 o_FragColor;

const float kPi = 3.14159265359;

vec3 Pbr_CalculateMainLightRadiance(const ShadingParameters shading,
const MaterialParameters material,
const vec3 mainLightIntensity) {
    vec3 diffuseTerm = material.diffuse / kPi;

    return diffuseTerm * mainLightIntensity * shading.normalDotLight;
}

vec3 Pbr_CalculateDiffuseEnvironmentalRadiance(const vec3 normal,
const vec3 coefficients[9]) {
    vec3 radiance = coefficients[0] + coefficients[1] * (normal.y) +
    coefficients[2] * (normal.z) + coefficients[3] * (normal.x) +
    coefficients[4] * (normal.y * normal.x) +
    coefficients[5] * (normal.y * normal.z) +
    coefficients[6] * (3.0 * normal.z * normal.z - 1.0) +
    coefficients[7] * (normal.z * normal.x) +
    coefficients[8] * (normal.x * normal.x - normal.y * normal.y);

    return max(radiance, 0.0);
}

vec3 Pbr_CalculateSpecularEnvironmentalRadiance(
const ShadingParameters shading, const MaterialParameters material,
const samplerCube cubemap) {
    float specularAO = clamp(pow(shading.normalDotView + material.ambientOcclusion,
        exp2(-16.0 * material.roughness - 1.0)) - 1.0 + material.ambientOcclusion, 0.0, 1.0);
    float lod = material.perceptualRoughness * float(kNumberOfRoughnessLevels - 1);
    vec3 LD = textureLod(cubemap, shading.worldReflectDirection, lod).rgb;
    vec3 E = mix(material.dfg.xxx, material.dfg.yyy, material.f0);

    return E * LD * specularAO * material.energyCompensation;
}

vec3 Pbr_CalculateEnvironmentalRadiance(
const ShadingParameters shading, const MaterialParameters material,
const vec3 sphericalHarmonicsCoefficients[9], const samplerCube cubemap) {
    vec3 diffuseTerm = Pbr_CalculateDiffuseEnvironmentalRadiance(shading.worldNormalDirection,
        sphericalHarmonicsCoefficients) * material.diffuse * material.ambientOcclusion;
    vec3 specularTerm = Pbr_CalculateSpecularEnvironmentalRadiance(shading, material, cubemap);

    return diffuseTerm + specularTerm;
}

void Pbr_CreateShadingParameters(const in vec3 viewNormal,
const in vec3 viewPosition,
const in vec4 viewLightDirection,
const in mat4 viewInverse,
out ShadingParameters shading) {
    vec3 normalDirection = normalize(viewNormal);
    vec3 viewDirection = -normalize(viewPosition);
    vec3 lightDirection = normalize(viewLightDirection.xyz);
    vec3 halfwayDirection = normalize(viewDirection + lightDirection);
    shading.normalDotView = max(dot(normalDirection, viewDirection), 1e-4);
    shading.normalDotHalfway = clamp(dot(normalDirection, halfwayDirection), 0.0, 1.0);
    shading.normalDotLight = clamp(dot(normalDirection, lightDirection), 0.0, 1.0);
    shading.viewDotHalfway = clamp(dot(viewDirection, halfwayDirection), 0.0, 1.0);

    vec3 NxH = cross(normalDirection, halfwayDirection);
    shading.oneMinusNormalDotHalfwaySquared = dot(NxH, NxH);

    shading.worldNormalDirection = (viewInverse * vec4(normalDirection, 0.0)).xyz;
    vec3 reflectDirection = reflect(-viewDirection, normalDirection);
    shading.worldReflectDirection = (viewInverse * vec4(reflectDirection, 0.0)).xyz;
}

void Pbr_CreateMaterialParameters(const in vec2 texCoord,
const in ShadingParameters shading,
out MaterialParameters material) {
    vec3 albedo = vec3(0.0, 0.3, 0.6);
    float perceptualRoughness = 0.0;
    float metallic = 0.0;
    float ambientOcclusion = 1.0;

    const float kMinPerceptualRoughness = 0.089;
    material.perceptualRoughness = max(perceptualRoughness, kMinPerceptualRoughness);
    material.roughness = material.perceptualRoughness * material.perceptualRoughness;
    material.metallic = metallic;
    material.ambientOcclusion = ambientOcclusion;

    material.diffuse = albedo * (1.0 - material.metallic);
    material.f0 = mix(vec3(0.04), albedo, material.metallic);
    material.dfg = textureLod(u_DfgTexture, vec2(shading.normalDotView,
        material.perceptualRoughness), 0.0).xy;
    material.energyCompensation = 1.0 + material.f0 * (1.0 / material.dfg.y - 1.0);
}

vec3 LinearToSrgb(const vec3 color) {
    vec3 kGamma = vec3(1.0 / 2.2);

    return clamp(pow(color, kGamma), 0.0, 1.0);
}

void main() {
    vec2 texCoord = vec2(v_TexCoord.x, 1.0 - v_TexCoord.y);

    if (u_LightEstimateIsValid) {
        vec3 waterColor = vec3(0.0, 0.3, 0.6);
        float waterAlpha = 0.5;
        float waterRoughness = 0.01;

        ShadingParameters shading;
        vec4 mainLightDirectionWithAlpha = vec4(u_ViewLightDirection, 1.0);
        Pbr_CreateShadingParameters(v_ViewNormal, v_ViewPosition, mainLightDirectionWithAlpha,
            u_ViewInverse, shading);

        MaterialParameters material;
        Pbr_CreateMaterialParameters(texCoord, shading, material);

        vec3 mainLightRadiance = Pbr_CalculateMainLightRadiance(shading, material,
            u_LightIntensity);
        vec3 environmentalRadiance = Pbr_CalculateEnvironmentalRadiance(shading, material,
            u_SphericalHarmonicsCoefficients, u_Cubemap);
        vec3 radiance = mainLightRadiance + environmentalRadiance;

        o_FragColor = vec4(LinearToSrgb(radiance), waterAlpha);
    } else {
        o_FragColor = vec4(0.6, 0.8, 1.0, 1.0);
    }
}
