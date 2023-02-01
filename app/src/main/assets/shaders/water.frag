#version 300 es

precision mediump float;

in vec2 textureCoords;
in vec4 clipSpace;

in vec3 toCameraVector;

in vec3 fromLightVector;

uniform sampler2D reflectionTexture;
uniform sampler2D refractionTexture;
uniform sampler2D dudvMap;

uniform sampler2D normalMap;

uniform sampler2D depthMap;

uniform float moveFactor;

uniform vec3 lightColour;

const float waveStrength = 0.02;

const float shineDamper = 20.0;
const float reflectivity = 0.6;

layout(location = 0) out vec4 o_FragColor;

void main(void) {
    vec2 ndc = (clipSpace.xy / clipSpace.w) / 2.0 + 0.5;
    vec2 refractTexCoords = vec2(ndc.x, ndc.y);
    vec2 reflectTexCoords = vec2(ndc.x, -ndc.y);

    // Sample depthMap texture and refractTexCoords.
    float near = 0.1;
    float far = 1000.0;
    float depth = texture(depthMap, refractTexCoords).r;
    float floorDistance = 2.0 * near * far / (far + near - (2.0 * depth - 1.0) * (far - near)); // Calculate the distance from camera to floor.

    depth = gl_FragCoord.z;
    float waterDistance = 2.0 * near * far / (far + near - (2.0 * depth - 1.0) * (far - near)); // Calculate the distance from camera to water.
    float waterDepth = floorDistance - waterDistance; // Calculate the distance from water to floor.

    // Sample dudvMap and textureCoords to get the distortions.
    // vec2 distortion1 = (texture(dudvMap, vec2(textureCoords.x + moveFactor, textureCoords.y)).rg * 2.0 - 1.0) * waveStrength;
    // vec2 distortion2 = (texture(dudvMap, vec2(-textureCoords.x + moveFactor, textureCoords.y + moveFactor)).rg * 2.0 - 1.0) * waveStrength;
    // vec2 totalDistortion = distortion1 + distortion2;
    vec2 distortedTexCoords = texture(dudvMap, vec2(textureCoords.x + moveFactor, textureCoords.y)).rg * 0.1;
    distortedTexCoords = textureCoords + vec2(distortedTexCoords.x, distortedTexCoords.y + moveFactor);
    vec2 totalDistortion = (texture(dudvMap, distortedTexCoords).rg * 2.0 - 1.0) * waveStrength * clamp(waterDepth/20.0, 0.0, 1.0);

    // Clamp the refractTexCoords to prevent glitch at the bottom of the screen.
    refractTexCoords += totalDistortion;
    refractTexCoords = clamp(refractTexCoords, 0.001, 0.999);

    // Clamp the reflectTexCoords to prevent glitch at the bottom of the screen.
    reflectTexCoords += totalDistortion;
    reflectTexCoords.x = clamp(reflectTexCoords.x, 0.001, 0.999);
    reflectTexCoords.y = clamp(reflectTexCoords.y, -0.999, -0.001);

    // Sample the calculated reflection and refraction distortion values to the textures samples to get the resulting colors.
    vec4 reflectColour = texture(reflectionTexture, reflectTexCoords);
    vec4 refractColour = texture(refractionTexture, refractTexCoords);

    // Sample normal map texture.
    vec4 normalMapColour = texture(normalMap, distortedTexCoords);
    vec3 normal = vec3(normalMapColour.r * 2.0 - 1.0, normalMapColour.b * 3.0, normalMapColour.g * 2.0 - 1.0);
    normal = normalize(normal);

    // Calculate the how reflective the water surface is based on the view angle of the camera.
    vec3 viewVector = normalize(toCameraVector);
    float refractiveFactor = dot(viewVector, normal);
    refractiveFactor = pow(refractiveFactor, 10.0);

    // Calculate the specular lighting.
    vec3 reflectedLight = reflect(normalize(fromLightVector), normal);
    float specular = max(dot(reflectedLight, viewVector), 0.0);
    specular = pow(specular, shineDamper);
    vec3 specularHighlights = lightColour * specular * reflectivity * clamp(waterDepth/5.0, 0.0, 1.0);

    o_FragColor = mix(reflectColour, refractColour, refractiveFactor);
    o_FragColor = mix(o_FragColor, vec4(0.0, 0.3, 0.5, 1.0), 0.2) + vec4(specularHighlights, 0.0); // Add a blue tint to the color of the water.
    // o_FragColor.a = clamp(waterDepth/5.0, 0.0, 1.0);
}