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

import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A CustomRender context.
 */
public class CustomRender {
    private final AssetManager ASSET_MANAGER;

    private int viewportWidth = 1;
    private int viewportHeight = 1;

    /**
     * Constructs a CustomRender object and instantiates GLSurfaceView parameters.
     *
     * @param glSurfaceView Android GLSurfaceView
     * @param renderer      Renderer implementation to receive callbacks
     * @param assetManager  AssetManager for loading Android resources
     */
    public CustomRender(GLSurfaceView glSurfaceView, Renderer renderer, AssetManager assetManager) {
        this.ASSET_MANAGER = assetManager;
        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setEGLContextClientVersion(3);
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16,
                0);

        glSurfaceView.setRenderer(
                new GLSurfaceView.Renderer() {
                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                        GLES30.glEnable(GLES30.GL_BLEND);
                        GLError.maybeThrowGLException("Failed to enable blending", "glEnable");
                        renderer.onSurfaceCreated(CustomRender.this);
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int w, int h) {
                        viewportWidth = w;
                        viewportHeight = h;
                        renderer.onSurfaceChanged(CustomRender.this, w, h);
                    }

                    @Override
                    public void onDrawFrame(GL10 gl) {
                        clear(null, 0f, 0f, 0f, 1f);
                        renderer.onDrawFrame(CustomRender.this);
                    }
                });

        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glSurfaceView.setWillNotDraw(false);
    }

    /**
     * Draws a {@link Mesh} with the specified {@link Shader}.
     */
    public void draw(Mesh mesh, Shader shader) {
        draw(mesh, shader, null);
    }

    /**
     * Draws a {@link Mesh} with the specified {@link Shader} to the given {@link Framebuffer}.
     *
     * <p>The {@code framebuffer} argument may be null, in which case the default framebuffer is used.
     */
    public void draw(Mesh mesh, Shader shader, Framebuffer framebuffer) {
        useFramebuffer(framebuffer);
        shader.lowLevelUse();
        mesh.lowLevelDraw();
    }

    /**
     * Clears the given framebuffer.
     *
     * <p>The {@code framebuffer} argument may be null, in which case the default framebuffer is
     * cleared.
     */
    public void clear(Framebuffer framebuffer, float r, float g, float b, float a) {
        useFramebuffer(framebuffer);
        GLES30.glClearColor(r, g, b, a);
        GLError.maybeThrowGLException("Failed to set clear color", "glClearColor");
        GLES30.glDepthMask(true);
        GLError.maybeThrowGLException("Failed to set depth write mask", "glDepthMask");
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLError.maybeThrowGLException("Failed to clear framebuffer", "glClear");
    }

    /**
     * Returns the AssetManager associated with this object.
     *
     * @return The AssetManager instance.
     */
    AssetManager getAssets() {
        return ASSET_MANAGER;
    }

    /**
     * Sets the current framebuffer and viewport dimensions for rendering.
     *
     * @param framebuffer The Framebuffer to use for rendering. Pass null to use the default
     *                    framebuffer.
     */
    private void useFramebuffer(Framebuffer framebuffer) {
        int framebufferId;
        int viewportWidth;
        int viewportHeight;

        if (framebuffer == null) {
            framebufferId = 0;
            viewportWidth = this.viewportWidth;
            viewportHeight = this.viewportHeight;
        } else {
            framebufferId = framebuffer.getFramebufferId();
            viewportWidth = framebuffer.getWidth();
            viewportHeight = framebuffer.getHeight();
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId);
        GLError.maybeThrowGLException("Failed to bind framebuffer", "glBindFramebuffer");
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight);
        GLError.maybeThrowGLException("Failed to set viewport dimensions", "glViewport");
    }

    /**
     * Interface to be implemented for rendering callbacks.
     */
    public interface Renderer {
        /**
         * Called by {@link CustomRender} when the GL render surface is created.
         *
         * <p>See {@link GLSurfaceView.Renderer#onSurfaceCreated}.
         */
        void onSurfaceCreated(CustomRender render);

        /**
         * Called by {@link CustomRender} when the GL render surface dimensions are changed.
         *
         * <p>See {@link GLSurfaceView.Renderer#onSurfaceChanged}.
         */
        void onSurfaceChanged(CustomRender render, int width, int height);

        /**
         * Called by {@link CustomRender} when a GL frame is to be rendered.
         *
         * <p>See {@link GLSurfaceView.Renderer#onDrawFrame}.
         */
        void onDrawFrame(CustomRender render);
    }
}
