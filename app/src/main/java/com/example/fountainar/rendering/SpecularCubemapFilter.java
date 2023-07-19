/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.fountainar.rendering;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.media.Image;
import android.opengl.GLES30;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ar.core.ImageFormat;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Filters a provided cubemap into a cubemap lookup texture which is a function of the direction of
 * a reflected ray of light and material roughness, i.e. the LD term of the specular IBL
 * calculation.
 *
 * <p>See <a href="https://google.github.io/filament/Filament.md.html#lighting/imagebasedlights">...</a>
 * for a more detailed explanation.
 */
public class SpecularCubemapFilter implements Closeable {
    private static final String TAG = SpecularCubemapFilter.class.getSimpleName();
    private static final int COMPONENTS_PER_VERTEX = 2;
    private static final int NUMBER_OF_VERTICES = 4;
    private static final int FLOAT_SIZE = 4;
    private static final int COORDINATES_BUFFER_SIZE =
            COMPONENTS_PER_VERTEX * NUMBER_OF_VERTICES * FLOAT_SIZE;
    private static final FloatBuffer COORDINATES_BUFFER =
            ByteBuffer.allocateDirect(COORDINATES_BUFFER_SIZE).order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
    private static final int NUMBER_OF_CUBE_FACES = 6;

    private static final String[] ATTACHMENT_LOCATION_DEFINES = {
            "PX_LOCATION", "NX_LOCATION", "PY_LOCATION", "NY_LOCATION", "PZ_LOCATION",
            "NZ_LOCATION",
    };

    private static final int[] ATTACHMENT_ENUMS = {
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_COLOR_ATTACHMENT1,
            GLES30.GL_COLOR_ATTACHMENT2,
            GLES30.GL_COLOR_ATTACHMENT3,
            GLES30.GL_COLOR_ATTACHMENT4,
            GLES30.GL_COLOR_ATTACHMENT5,
    };

    private static final float PI_F = (float) Math.PI;

    static {
        COORDINATES_BUFFER.put(new float[]{-1f, -1f, +1f, -1f, -1f, +1f, +1f, +1f});
    }

    private final int RESOLUTION;
    private final int NUMBER_OF_IMPORTANCE_SAMPLES;
    private final int NUMBER_OF_MIPMAP_LEVELS;
    private final Texture RADIANCE_CUBEMAP;
    private final Texture LD_CUBEMAP;
    private final Shader[] SHADERS;
    private final Mesh MESH;
    private final int[][] FRAMEBUFFERS;

    /**
     * Constructs a {@link SpecularCubemapFilter}.
     *
     * <p>The provided resolution refers to both the width and height of the input resolution and
     * the resolution of the highest mipmap level of the filtered cubemap texture.
     *
     * <p>Ideally, the cubemap would need to be filtered by computing a function of every sample
     * over the hemisphere for every texel. Since this is not practical to compute, a limited,
     * discrete number of importance samples are selected instead. A larger number of importance
     * samples will generally provide more accurate results, but in the case of ARCore, the cubemap
     * estimations are already very low resolution, and higher values provide rapidly diminishing
     * returns.
     */
    public SpecularCubemapFilter(CustomRender render, int resolution, int numberOfImportanceSamples)
            throws IOException {
        this.RESOLUTION = resolution;
        this.NUMBER_OF_IMPORTANCE_SAMPLES = numberOfImportanceSamples;
        this.NUMBER_OF_MIPMAP_LEVELS = log2(resolution) + 1;

        RADIANCE_CUBEMAP = new Texture(Texture.Target.TEXTURE_CUBE_MAP,
                Texture.WrapMode.CLAMP_TO_EDGE);
        LD_CUBEMAP = new Texture(Texture.Target.TEXTURE_CUBE_MAP,
                Texture.WrapMode.CLAMP_TO_EDGE);

        ChunkIterable chunks = new ChunkIterable(getMaxColorAttachments());
        initializeLdCubemap();
        SHADERS = createShaders(render, chunks);
        FRAMEBUFFERS = createFramebuffers(chunks);
        try (VertexBuffer coordinatesBuffer = new VertexBuffer(COMPONENTS_PER_VERTEX,
                COORDINATES_BUFFER)) {
            MESH = new Mesh(Mesh.PrimitiveMode.TRIANGLE_STRIP, null,
                    new VertexBuffer[]{coordinatesBuffer});
        }
    }

    private static int getMaxColorAttachments() {
        int[] result = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_MAX_COLOR_ATTACHMENTS, result, 0);
        GLError.maybeThrowGLException("Failed to get max color attachments", "glGetIntegerv");
        return result[0];
    }

    private static int log2(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }

        return Integer.SIZE - Integer.numberOfLeadingZeros(value - 1);
    }


    private static float log4(float value) {
        return (float) (Math.log(value) / Math.log(4.0));
    }

    private static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    private static float sin(float value) {
        return (float) Math.sin(value);
    }

    private static float cos(float value) {
        return (float) Math.cos(value);
    }

    private static float[] hammersley(int i, float iN) {
        long bits = ((long) i << 16) | (i >>> 16);
        bits = ((bits & 0x55555555L) << 1) | ((bits & 0xAAAAAAAAL) >>> 1);
        bits = ((bits & 0x33333333L) << 2) | ((bits & 0xCCCCCCCCL) >>> 2);
        bits = ((bits & 0x0F0F0F0FL) << 4) | ((bits & 0xF0F0F0F0L) >>> 4);
        bits = ((bits & 0x00FF00FFL) << 8) | ((bits & 0xFF00FF00L) >>> 8);

        float tof = 0.5f / 0x80000000L;

        return new float[]{i * iN, bits * tof};
    }


    private static float[] hemisphereImportanceSampleDggx(float[] u, float a) {
        float phi = 2.0f * PI_F * u[0];
        float cosTheta2 = (1f - u[1]) / (1f + (a + 1f) * ((a - 1f) * u[1]));
        float cosTheta = sqrt(cosTheta2);
        float sinTheta = sqrt(1f - cosTheta2);

        return new float[]{sinTheta * cos(phi), sinTheta * sin(phi), cosTheta};
    }

    private static float distributionGgx(float noh, float a) {
        float f = (a - 1f) * ((a + 1f) * (noh * noh)) + 1f;

        return (a * a) / (PI_F * f * f);
    }

    @Override
    public void close() {
        closeFrameBuffers();
        closeTexture(RADIANCE_CUBEMAP);
        closeTexture(LD_CUBEMAP);
        closeShaders();
    }

    private void closeFrameBuffers() {
        if (FRAMEBUFFERS != null) {
            for (int[] framebufferChunks : FRAMEBUFFERS) {
                GLES30.glDeleteFramebuffers(framebufferChunks.length, framebufferChunks, 0);
                GLError.maybeLogGLError(
                        Log.WARN, TAG, "Failed to free framebuffers",
                        "glDeleteFramebuffers"
                );
            }
        }
    }

    private void closeTexture(Texture texture) {
        if (texture != null) {
            texture.close();
        }
    }

    private void closeShaders() {
        if (SHADERS != null) {
            for (Shader shader : SHADERS) {
                shader.close();
            }
        }
    }


    /**
     * Updates and filters the provided cubemap textures from ARCore.
     *
     * <p>This method should be called every frame with the result of {link
     * com.google.ar.core.LightEstimate.acquireEnvironmentalHdrCubeMap()} to update the filtered
     * cubemap texture, accessible via {link getFilteredCubemapTexture()}.
     *
     * <p>The given {@link Image}s will be closed by this method, even if an exception occurs.
     */
    public void update(Image[] images) {
        try {
            bindRadianceCubemapTexture();
            validateImages(images);
            populateCubemapFaces(images);
            generateCubemapMipmaps();
            renderCubemapLevels();
        } finally {
            closeImages(images);
        }
    }

    private void bindRadianceCubemapTexture() {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, RADIANCE_CUBEMAP.getTextureId());
        GLError.maybeThrowGLException("Failed to bind radiance cubemap texture", "glBindTexture");
    }

    private void validateImages(Image[] images) {
        if (images.length != NUMBER_OF_CUBE_FACES) {
            throw new IllegalArgumentException(
                    "Number of images differs from the number of sides of a cube.");
        }

        for (int i = 0; i < NUMBER_OF_CUBE_FACES; ++i) {
            Image image = images[i];

            if (image.getFormat() != ImageFormat.RGBA_FP16) {
                throw new IllegalArgumentException(
                        "Unexpected image format for cubemap: " + image.getFormat());
            }

            int faceResolution = image.getHeight();

            if (faceResolution != RESOLUTION || faceResolution != image.getWidth()) {
                throw new IllegalArgumentException(
                        "Cubemap face is not square or resolution does not match expected value.");
            }
        }
    }

    private void populateCubemapFaces(Image[] images) {
        for (int i = 0; i < NUMBER_OF_CUBE_FACES; ++i) {
            Image image = images[i];
            ByteBuffer imageBuffer = image.getPlanes()[0].getBuffer();

            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                    0,
                    GLES30.GL_RGBA16F,
                    RESOLUTION,
                    RESOLUTION,
                    0,
                    GLES30.GL_RGBA,
                    GLES30.GL_HALF_FLOAT,
                    imageBuffer
            );

            GLError.maybeThrowGLException("Failed to populate cubemap face", "glTexImage2D");
        }
    }

    private void generateCubemapMipmaps() {
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_CUBE_MAP);
        GLError.maybeThrowGLException("Failed to generate cubemap mipmaps", "glGenerateMipmap");
    }

    private void renderCubemapLevels() {
        for (int level = 0; level < NUMBER_OF_MIPMAP_LEVELS; ++level) {
            int mipmapResolution = RESOLUTION >> level;
            GLES30.glViewport(0, 0, mipmapResolution, mipmapResolution);
            GLError.maybeThrowGLException("Failed to set viewport dimensions", "glViewport");

            for (int chunkIndex = 0; chunkIndex < SHADERS.length; ++chunkIndex) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, FRAMEBUFFERS[level][chunkIndex]);
                GLError.maybeThrowGLException("Failed to bind cubemap framebuffer",
                        "glBindFramebuffer");
                SHADERS[chunkIndex].setInt("u_RoughnessLevel", level);
                SHADERS[chunkIndex].lowLevelUse();
                MESH.lowLevelDraw();
            }
        }
    }

    private void closeImages(Image[] images) {
        for (Image image : images) {
            image.close();
        }
    }


    /**
     * Returns the number of mipmap levels in the filtered cubemap texture.
     */
    public int getNumberOfMipmapLevels() {
        return NUMBER_OF_MIPMAP_LEVELS;
    }

    /**
     * Returns the filtered cubemap texture whose contents are updated with each call to {@link
     * #update(Image[])}.
     */
    public Texture getFilteredCubemapTexture() {
        return LD_CUBEMAP;
    }

    public Texture getRadianceCubemapTexture() {
        return RADIANCE_CUBEMAP;
    }

    private void initializeLdCubemap() {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, LD_CUBEMAP.getTextureId());
        GLError.maybeThrowGLException("Could not bind LD cubemap texture", "glBindTexture");

        for (int level = 0; level < NUMBER_OF_MIPMAP_LEVELS; ++level) {
            int mipmapResolution = RESOLUTION >> level;

            for (int face = 0; face < NUMBER_OF_CUBE_FACES; ++face) {
                GLES30.glTexImage2D(
                        GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face,
                        level,
                        GLES30.GL_RGB16F,
                        mipmapResolution,
                        mipmapResolution,
                        0,
                        GLES30.GL_RGB,
                        GLES30.GL_HALF_FLOAT,
                        null
                );
                GLError.maybeThrowGLException("Could not initialize LD cubemap mipmap",
                        "glTexImage2D");
            }
        }
    }

    private Shader[] createShaders(CustomRender render, ChunkIterable chunks) throws IOException {
        ImportanceSampleCacheEntry[][] importanceSampleCaches = generateImportanceSampleCaches();

        HashMap<String, String> commonDefines = new HashMap<>();
        commonDefines.put("NUMBER_OF_IMPORTANCE_SAMPLES",
                Integer.toString(NUMBER_OF_IMPORTANCE_SAMPLES));
        commonDefines.put("NUMBER_OF_MIPMAP_LEVELS", Integer.toString(NUMBER_OF_MIPMAP_LEVELS));

        Shader[] shaders = new Shader[chunks.numberOfChunks];

        for (Chunk chunk : chunks) {
            HashMap<String, String> defines = new HashMap<>(commonDefines);
            for (int location = 0; location < chunk.chunkSize; ++location) {
                defines.put(ATTACHMENT_LOCATION_DEFINES[chunk.firstFaceIndex + location],
                        Integer.toString(location));
            }

            shaders[chunk.chunkIndex] = Shader.createFromAssets(render,
                            "shaders/cubemap_filter.vert",
                            "shaders/cubemap_filter.frag", defines)
                    .setTexture("u_Cubemap", RADIANCE_CUBEMAP)
                    .setDepthTest(false)
                    .setDepthWrite(false);
        }

        for (Shader shader : shaders) {
            for (int i = 0; i < importanceSampleCaches.length; ++i) {
                ImportanceSampleCacheEntry[] cache = importanceSampleCaches[i];
                String cacheName = "u_ImportanceSampleCaches[" + i + "]";
                shader.setInt(cacheName + ".number_of_entries", cache.length);
                for (int j = 0; j < cache.length; ++j) {
                    ImportanceSampleCacheEntry entry = cache[j];
                    String entryName = cacheName + ".entries[" + j + "]";
                    shader.setVec3(entryName + ".direction", entry.direction)
                            .setFloat(entryName + ".contribution", entry.contribution)
                            .setFloat(entryName + ".level", entry.level);
                }
            }
        }

        return shaders;
    }

    /**
     * Creates and configures the framebuffers for rendering the LD cubemap mipmaps.
     *
     * @param chunks The iterable containing the chunks for which framebuffers need to be created.
     * @return An array of framebuffers, organized by mipmap level.
     */
    private int[][] createFramebuffers(ChunkIterable chunks) {
        int[][] framebuffers = new int[NUMBER_OF_MIPMAP_LEVELS][];

        for (int level = 0; level < NUMBER_OF_MIPMAP_LEVELS; ++level) {
            int[] framebufferChunks = new int[chunks.numberOfChunks];
            GLES30.glGenFramebuffers(framebufferChunks.length, framebufferChunks, 0);
            GLError.maybeThrowGLException("Could not create cubemap framebuffers",
                    "glGenFramebuffers");

            for (Chunk chunk : chunks) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,
                        framebufferChunks[chunk.chunkIndex]);
                GLError.maybeThrowGLException("Could not bind framebuffer", "glBindFramebuffer");
                GLES30.glDrawBuffers(chunk.chunkSize, ATTACHMENT_ENUMS, 0);
                GLError.maybeThrowGLException("Could not bind draw buffers", "glDrawBuffers");

                for (int attachment = 0; attachment < chunk.chunkSize; ++attachment) {
                    GLES30.glFramebufferTexture2D(
                            GLES30.GL_FRAMEBUFFER,
                            GLES30.GL_COLOR_ATTACHMENT0 + attachment,
                            GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + chunk.firstFaceIndex +
                                    attachment,
                            LD_CUBEMAP.getTextureId(),
                            level);
                    GLError.maybeThrowGLException(
                            "Could not attach LD cubemap mipmap to framebuffer",
                            "glFramebufferTexture");
                }
            }
            framebuffers[level] = framebufferChunks;
        }

        return framebuffers;
    }

    /**
     * Generates a cache of importance sampling terms in tangent space, indexed by {@code
     * [roughnessLevel-1][sampleIndex]}.
     */
    private ImportanceSampleCacheEntry[][] generateImportanceSampleCaches() {
        ImportanceSampleCacheEntry[][] result = new ImportanceSampleCacheEntry[
                NUMBER_OF_MIPMAP_LEVELS - 1][];

        for (int i = 0; i < NUMBER_OF_MIPMAP_LEVELS - 1; ++i) {
            int mipmapLevel = i + 1;
            float perceptualRoughness = mipmapLevel / (float) (NUMBER_OF_MIPMAP_LEVELS - 1);
            float roughness = perceptualRoughness * perceptualRoughness;
            int resolution = this.RESOLUTION >> mipmapLevel;
            float log4omegaP = log4((4.0f * PI_F) / (6 * resolution * resolution));
            float inverseNumberOfSamples = 1f / NUMBER_OF_IMPORTANCE_SAMPLES;
            ArrayList<ImportanceSampleCacheEntry> cache =
                    new ArrayList<>(NUMBER_OF_IMPORTANCE_SAMPLES);
            float weight = 0f;

            for (int sampleIndex = 0; sampleIndex < NUMBER_OF_IMPORTANCE_SAMPLES; ++sampleIndex) {
                float[] u = hammersley(sampleIndex, inverseNumberOfSamples);
                float[] h = hemisphereImportanceSampleDggx(u, roughness);
                float noh = h[2];
                float noh2 = noh * noh;
                float nol = 2f * noh2 - 1f;

                if (nol > 0) {
                    ImportanceSampleCacheEntry entry = new ImportanceSampleCacheEntry();
                    entry.direction = new float[]{2f * noh * h[0], 2 * noh * h[1], nol};
                    float pdf = distributionGgx(noh, roughness) / 4f;
                    float log4omegaS = log4(1f / (NUMBER_OF_IMPORTANCE_SAMPLES * pdf));
                    float log4K = 1f;
                    float l = log4omegaS - log4omegaP + log4K;
                    entry.level = min(max(l, 0f), (float) (NUMBER_OF_MIPMAP_LEVELS - 1));
                    entry.contribution = nol;
                    cache.add(entry);
                    weight += nol;
                }
            }

            float weightInverse = 1f / weight;
            for (ImportanceSampleCacheEntry entry : cache) {
                entry.contribution *= weightInverse;
            }

            result[i] = cache.toArray(new ImportanceSampleCacheEntry[0]);
        }

        return result;
    }

    private static class Chunk {
        public final int chunkIndex;
        public final int chunkSize;
        public final int firstFaceIndex;

        public Chunk(int chunkIndex, int maxChunkSize) {
            this.chunkIndex = chunkIndex;
            this.firstFaceIndex = chunkIndex * maxChunkSize;
            this.chunkSize = min(maxChunkSize, NUMBER_OF_CUBE_FACES - this.firstFaceIndex);
        }
    }

    private static class ChunkIterable implements Iterable<Chunk> {
        public final int maxChunkSize;
        public final int numberOfChunks;

        public ChunkIterable(int maxNumberOfColorAttachments) {
            this.maxChunkSize = min(maxNumberOfColorAttachments, NUMBER_OF_CUBE_FACES);
            int numberOfChunks = NUMBER_OF_CUBE_FACES / this.maxChunkSize;

            if (NUMBER_OF_CUBE_FACES % this.maxChunkSize != 0) {
                numberOfChunks++;
            }

            this.numberOfChunks = numberOfChunks;
        }

        @NonNull
        @Override
        public Iterator<Chunk> iterator() {
            return new Iterator<Chunk>() {
                private Chunk chunk = new Chunk(0, maxChunkSize);

                @Override
                public boolean hasNext() {
                    return chunk.chunkIndex < numberOfChunks;
                }

                @Override
                public Chunk next() {
                    Chunk result = this.chunk;
                    this.chunk = new Chunk(result.chunkIndex + 1, maxChunkSize);

                    return result;
                }
            };
        }
    }

    private static class ImportanceSampleCacheEntry {
        public float[] direction;
        public float contribution;
        public float level;
    }
}
