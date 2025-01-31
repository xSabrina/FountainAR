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

import android.opengl.GLES30;
import android.util.Log;

import java.io.Closeable;

/**
 * A framebuffer associated with a texture.
 */
public class Framebuffer implements Closeable {
    private static final String TAG = Framebuffer.class.getSimpleName();
    private final int[] FRAME_BUFFER_ID = {0};
    private final Texture COLOR_TEXTURE;
    private final Texture DEPTH_TEXTURE;

    private int width = -1;
    private int height = -1;

    /**
     * Constructs a {@link Framebuffer} which renders internally to a texture.
     *
     * <p>In order to render to the {@link Framebuffer}, use {@link CustomRender#draw(Mesh, Shader,
     * Framebuffer)}.
     */
    public Framebuffer(int width, int height) {
        try {
            COLOR_TEXTURE =
                    new Texture(
                            Texture.Target.TEXTURE_2D,
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            false);
            DEPTH_TEXTURE =
                    new Texture(
                            Texture.Target.TEXTURE_2D,
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            false);

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, DEPTH_TEXTURE.getTextureId());
            GLError.maybeThrowGLException("Failed to bind depth texture", "glBindTexture");

            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE,
                    GLES30.GL_NONE);
            GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri");

            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_NEAREST);
            GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri");

            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_NEAREST);
            GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri");

            resize(width, height);

            GLES30.glGenFramebuffers(1, FRAME_BUFFER_ID, 0);
            GLError.maybeThrowGLException("Framebuffer creation failed", "glGenFramebuffers");

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, FRAME_BUFFER_ID[0]);
            GLError.maybeThrowGLException("Failed to bind framebuffer", "glBindFramebuffer");

            GLES30.glFramebufferTexture2D(
                    GLES30.GL_FRAMEBUFFER,
                    GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D,
                    COLOR_TEXTURE.getTextureId(),
                    0);
            GLError.maybeThrowGLException(
                    "Failed to bind color texture to framebuffer", "glFramebufferTexture2D");

            GLES30.glFramebufferTexture2D(
                    GLES30.GL_FRAMEBUFFER,
                    GLES30.GL_DEPTH_ATTACHMENT,
                    GLES30.GL_TEXTURE_2D,
                    DEPTH_TEXTURE.getTextureId(),
                    0);
            GLError.maybeThrowGLException(
                    "Failed to bind depth texture to framebuffer", "glFramebufferTexture2D");

            int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Framebuffer construction not complete: code "
                        + status);
            }
        } catch (Throwable t) {
            close();
            throw t;
        }
    }

    /**
     * Deletes the framebuffer if valid and closes the associated color and depth textures to avoid
     * memory leaks.
     */
    @Override
    public void close() {
        if (FRAME_BUFFER_ID[0] != 0) {
            GLES30.glDeleteFramebuffers(1, FRAME_BUFFER_ID, 0);
            GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free framebuffer",
                    "glDeleteFramebuffers");
            FRAME_BUFFER_ID[0] = 0;
        }

        COLOR_TEXTURE.close();
        DEPTH_TEXTURE.close();
    }

    /**
     * Resizes the framebuffer to the given dimensions.
     */
    public void resize(int width, int height) {
        if (this.width == width && this.height == height) {
            return;
        }

        this.width = width;
        this.height = height;

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, COLOR_TEXTURE.getTextureId());
        GLError.maybeThrowGLException("Failed to bind color texture", "glBindTexture");

        GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA,
                width,
                height,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null);
        GLError.maybeThrowGLException("Failed to specify color texture format", "glTexImage2D");

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, DEPTH_TEXTURE.getTextureId());
        GLError.maybeThrowGLException("Failed to bind depth texture", "glBindTexture");

        GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_DEPTH_COMPONENT32F,
                width,
                height,
                0,
                GLES30.GL_DEPTH_COMPONENT,
                GLES30.GL_FLOAT,
                null);
        GLError.maybeThrowGLException("Failed to specify depth texture format", "glTexImage2D");
    }

    /**
     * Returns the color texture associated with this framebuffer.
     */
    public Texture getColorTexture() {
        return COLOR_TEXTURE;
    }

    /**
     * Returns the depth texture associated with this framebuffer.
     */
    public Texture getDepthTexture() {
        return DEPTH_TEXTURE;
    }

    /**
     * Returns the width of the framebuffer.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of the framebuffer.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the id of the framebuffer.
     */
    /* package-private */
    int getFramebufferId() {
        return FRAME_BUFFER_ID[0];
    }
}