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

import android.media.Image;
import android.opengl.GLES30;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;

/**
 * Renderer for the AR camera background and composing the scene foreground. The camera
 * background can be rendered as either camera image data or camera depth data. The virtual scene
 * can be composited with or without depth occlusion.
 */
public class BackgroundRenderer {

    private static final int COORDS_BUFFER_SIZE = 2 * 4 * 4;
    private static final FloatBuffer NDC_QUAD_COORDS_BUFFER =
            ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final FloatBuffer VIRTUAL_SCENE_TEX_COORDS_BUFFER =
            ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();

    static {
        NDC_QUAD_COORDS_BUFFER.put(
                new float[]{
                        -1f, -1f, +1f, -1f, -1f, +1f, +1f, +1f,
                });
        VIRTUAL_SCENE_TEX_COORDS_BUFFER.put(
                new float[]{
                        0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f,
                });
    }

    private final FloatBuffer CAMERA_TEX_COORDS = ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final Mesh MESH;
    private final VertexBuffer CAMERA_TEX_COORDS_VERT_BUFFER;
    private final Texture CAMERA_DEPTH_TEXTURE;
    private final Texture CAMERA_COLOR_TEXTURE;

    private Shader backgroundShader;
    private Shader occlusionShader;
    private boolean useDepthVisualization;
    private boolean useOcclusion;
    private float aspectRatio;
    private Texture reflectionTexture;

    /**
     * Allocates and initializes OpenGL resources needed by the background renderer. Must be called
     * during a {@link CustomRender.Renderer} callback, typically in {link
     * CustomRender.Renderer#onSurfaceCreated()}.
     */
    public BackgroundRenderer() {
        CAMERA_COLOR_TEXTURE = new Texture(Texture.Target.TEXTURE_EXTERNAL_OES,
                Texture.WrapMode.CLAMP_TO_EDGE, false);
        CAMERA_DEPTH_TEXTURE = new Texture(Texture.Target.TEXTURE_2D,
                Texture.WrapMode.CLAMP_TO_EDGE, false);
        VertexBuffer screenCoordsVertexBuffer = new VertexBuffer(2,
                NDC_QUAD_COORDS_BUFFER);
        CAMERA_TEX_COORDS_VERT_BUFFER = new VertexBuffer(2, null);
        VertexBuffer virtualSceneTexCoordsVertexBuffer = new VertexBuffer(2,
                VIRTUAL_SCENE_TEX_COORDS_BUFFER);
        VertexBuffer[] vertexBuffers = {
                screenCoordsVertexBuffer,
                CAMERA_TEX_COORDS_VERT_BUFFER,
                virtualSceneTexCoordsVertexBuffer};
        MESH = new Mesh(Mesh.PrimitiveMode.TRIANGLE_STRIP, null, vertexBuffers);
    }

    /**
     * Sets whether the background camera image should be replaced with a depth visualization
     * instead. This reloads the corresponding shader code, and must be called on the GL thread.
     */
    public void setUseDepthVisualization(CustomRender render, boolean useDepthVisualization)
            throws IOException {
        if (backgroundShader != null) {
            if (this.useDepthVisualization == useDepthVisualization) {
                return;
            }

            backgroundShader.close();
            backgroundShader = null;
            this.useDepthVisualization = useDepthVisualization;
        }

        if (useDepthVisualization) {
            Texture depthColorPaletteTexture = Texture.createFromAsset(render,
                    "models/depth_color_palette.png", Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.LINEAR);

            backgroundShader = Shader.createFromAssets(render,
                            "shaders/background_show_depth_color_visualization.vert",
                            "shaders/background_show_depth_color_visualization.frag",
                            null)
                    .setTexture("u_CameraDepthTexture", CAMERA_DEPTH_TEXTURE)
                    .setTexture("u_ColorMap", depthColorPaletteTexture)
                    .setDepthTest(false)
                    .setDepthWrite(false);
        } else {
            backgroundShader = Shader.createFromAssets(render,
                            "shaders/background_show_camera.vert",
                            "shaders/background_show_camera.frag",
                            null)
                    .setTexture("u_CameraColorTexture", CAMERA_COLOR_TEXTURE)
                    .setDepthTest(false)
                    .setDepthWrite(false);
        }
    }

    /**
     * Sets whether to use depth for occlusion. This reloads the shader code with new {@code
     * #define}s, and must be called on the GL thread.
     */
    public void setUseOcclusion(CustomRender render, boolean useOcclusion) throws IOException {
        if (occlusionShader != null) {
            if (this.useOcclusion == useOcclusion) {
                return;
            }
            occlusionShader.close();
            occlusionShader = null;
            this.useOcclusion = useOcclusion;
        }
        HashMap<String, String> defines = new HashMap<>();
        defines.put("USE_OCCLUSION", useOcclusion ? "1" : "0");
        occlusionShader =
                Shader.createFromAssets(render, "shaders/occlusion.vert", "shaders/occlusion.frag", defines)
                        .setDepthTest(false)
                        .setDepthWrite(false)
                        .setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA);
        if (useOcclusion) {
            occlusionShader
                    .setTexture("u_CameraDepthTexture", CAMERA_DEPTH_TEXTURE)
                    .setFloat("u_DepthAspectRatio", aspectRatio);
        }
    }

    /**
     * Updates the display geometry. This must be called every frame before calling either of
     * BackgroundRenderer's draw methods.
     *
     * @param frame The current {@code Frame} as returned by {link Session#update()}.
     */
    public void updateDisplayGeometry(Frame frame) {
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    NDC_QUAD_COORDS_BUFFER,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    CAMERA_TEX_COORDS);
            CAMERA_TEX_COORDS_VERT_BUFFER.set(CAMERA_TEX_COORDS);
        }
    }

    /**
     * Draws the AR background image. The image will be drawn such that virtual content rendered
     * with the matrices provided by {@link com.google.ar.core.Camera#getViewMatrix(float[], int)}
     * and {@link com.google.ar.core.Camera#getProjectionMatrix(float[], int, float, float)} will
     * accurately followstatic physical objects.
     */
    public void drawBackground(CustomRender render) {
        render.draw(MESH, backgroundShader);
    }

    /**
     * Draws the virtual scene. Any objects rendered in the given {@link Framebuffer} will be drawn
     * given the previously specified {link OcclusionMode}.
     *
     * <p>Virtual content should be rendered using the matrices provided by {@link
     * com.google.ar.core.Camera#getViewMatrix(float[], int)} and {@link
     * com.google.ar.core.Camera#getProjectionMatrix(float[], int, float, float)}.
     */
    public void drawVirtualScene(
            CustomRender render, Framebuffer virtualSceneFramebuffer, float zNear, float zFar) {
        reflectionTexture = virtualSceneFramebuffer.getColorTexture();
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, reflectionTexture.getTextureId());
        occlusionShader.setTexture("u_VirtualSceneColorTexture", reflectionTexture);

        if (useOcclusion) {
            occlusionShader
                    .setTexture("u_VirtualSceneDepthTexture",
                            virtualSceneFramebuffer.getDepthTexture())
                    .setFloat("u_ZNear", zNear)
                    .setFloat("u_ZFar", zFar);
        }

        render.draw(MESH, occlusionShader);
    }

    /**
     * Returns the camera color texture generated by this object.
     */
    public Texture getCameraColorTexture() {
        return CAMERA_COLOR_TEXTURE;
    }

}
