package com.example.fountainar.rendering;

import android.app.Activity;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;
import android.widget.Toast;

import com.example.fountainar.R;
import com.example.fountainar.activities.ARActivity;
import com.example.fountainar.activities.DemographicQuestionnaire;
import com.example.fountainar.helpers.SoundPoolHelper;
import com.example.fountainar.helpers.TrackingStateHelper;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Renderer responsible for rendering the virtual scene on the AR display. It handles setting up the
 * scene, updating and drawing the virtual objects, and managing the rendering process.
 */
public class SceneRenderer {

    private static final String TAG = SceneRenderer.class.getSimpleName();
    private static final ArrayList<Mesh> VIRTUAL_WATER_JET_MESHES = new ArrayList<>(5);
    private static final float[] MODEL_MATRIX = new float[16];
    private static final float[] VIEW_MATRIX = new float[16];
    private static final float[] PROJECTION_MATRIX = new float[16];
    private static final float[] MODEL_VIEW_MATRIX = new float[16];
    private static final float[] MODEL_VIEW_PROJECTION_MATRIX = new float[16];
    private static final float[] SPHERICAL_HARMONIC_FACTORS = {
            0.282095f,
            -0.325735f,
            0.325735f,
            -0.325735f,
            0.273137f,
            -0.273137f,
            0.078848f,
            -0.273137f,
            0.136569f,
    };
    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;
    private static final float Z_NEAR = 1.3f;
    private static final float Z_FAR = 500f;
    private final static int WATER_JETS_START = 170;
    private final static int WATER_JETS_END = 175;
    private static boolean isSubjectGroupWithAnimation = false;
    private static BackgroundRenderer backgroundRenderer;
    private static Mesh virtualFountainMesh;
    private static Mesh virtualWaterSurfaceMesh;
    private static Shader virtualFountainShader;
    private static Shader virtualWaterShader;
    private static Shader virtualWaterSurfaceShader;
    private static int meshCounter = 0;
    private final float[] SPHERICAL_HARMONIC_COEFFICIENTS = new float[9 * 3];
    private final float[] VIEW_INVERSE_MATRIX = new float[16];
    private final float[] WORLD_LIGHT_DIRECTION = {0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] VIEW_LIGHT_DIRECTION = new float[4];
    private final Activity ACTIVITY;
    private final TrackingStateHelper TRACKING_STATE_HELPER;
    public Framebuffer virtualSceneFramebuffer;
    private Frame frame;
    private boolean hasSetTextureNames = false;
    private SpecularCubemapFilter cubemapFilter;
    private SoundPoolHelper soundPoolHelper;
    private boolean loaded = false;
    private Texture dfgTexture;

    public SceneRenderer(Activity activity) {
        this.ACTIVITY = activity;
        TRACKING_STATE_HELPER = new TrackingStateHelper(activity);

        if (DemographicQuestionnaire.probNum % 2 == 0) {
            isSubjectGroupWithAnimation = true;
            soundPoolHelper = new SoundPoolHelper(activity);
        }
    }

    /**
     * Sets up the virtual scene by initializing the background renderer, frame buffer,
     * cubemap filter, textures, meshes, and shaders.
     *
     * @param render The custom render object.
     */
    public void setupScene(CustomRender render) {
        try {
            if (!loaded) {
                ACTIVITY.runOnUiThread(() -> Toast.makeText(ACTIVITY,
                        R.string.models_loading, Toast.LENGTH_LONG).show());
            }

            backgroundRenderer = new BackgroundRenderer();
            backgroundRenderer.setUseDepthVisualization(render, false);
            backgroundRenderer.setUseOcclusion(render, true);
            virtualSceneFramebuffer = new Framebuffer(1, 1);
            setupLightingElements(render);
            setupFountainObject(render);

            if (isSubjectGroupWithAnimation) {
                setupWaterObjects(render);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            ARActivity.snackbarHelper.showError(ACTIVITY,
                    String.valueOf(R.string.read_asset_failed) + e);
        }
    }

    /**
     * Sets up the lighting elements for the virtual scene.
     *
     * @param render The CustomRender object for rendering the scene.
     * @throws RuntimeException if setup fails or there are I/O exceptions.
     * @throws IOException      if there is an error reading the "models/dfg.raw" file.
     */
    private void setupLightingElements(CustomRender render) throws IOException {
        try {
            cubemapFilter =
                    new SpecularCubemapFilter(
                            render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);
            dfgTexture =
                    new Texture(
                            Texture.Target.TEXTURE_2D,
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            false);

            final int dfgResolution = 64;
            final int dfgChannels = 2;
            final int halfFloatSize = 2;

            ByteBuffer buffer = ByteBuffer.allocateDirect(dfgResolution * dfgResolution
                    * dfgChannels * halfFloatSize);

            try (InputStream is = ACTIVITY.getAssets().open("models/dfg.raw")) {
                int bytesRead = is.read(buffer.array());
                if (bytesRead != buffer.capacity()) {
                    throw new IOException("Failed to read the entire DFG texture");
                }
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
            GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture");
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D,
                    0,
                    GLES30.GL_RG16F,
                    dfgResolution,
                    dfgResolution,
                    0,
                    GLES30.GL_RG,
                    GLES30.GL_HALF_FLOAT,
                    buffer);
            GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets up the virtual fountain object by creating the necessary textures, meshes, and shaders.
     * It initializes the cubemap filter for specular reflections, loads the fountain textures,
     * and creates the shader program with the appropriate textures and cubemap.
     *
     * @param render The custom render object.
     */
    private void setupFountainObject(CustomRender render) {
        try {
            Texture virtualFountainAlbedoTexture = Texture.createFromAsset(
                    render, "models/fountain_albedo.png",
                    Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.SRGB);

            Texture virtualFountainPbrTexture = Texture.createFromAsset(
                    render, "models/fountain_pbr.png",
                    Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.LINEAR);

            virtualFountainMesh = Mesh.createFromAsset(render, "models/fountain.obj");


            virtualFountainShader = Shader.createFromAssets(render,
                            "shaders/environmental_hdr.vert",
                            "shaders/environmental_hdr.frag",
                            new HashMap<String, String>() {
                                {
                                    put(
                                            "NUMBER_OF_MIPMAP_LEVELS",
                                            Integer.toString(
                                                    cubemapFilter.getNumberOfMipmapLevels()));
                                }
                            })
                    .setTexture("u_AlbedoTexture", virtualFountainAlbedoTexture)
                    .setTexture("u_RoughnessMetallicAmbientOcclusionTexture",
                            virtualFountainPbrTexture)
                    .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                    .setTexture("u_DfgTexture", dfgTexture);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets up the water objects by creating the necessary shaders and meshes.
     * If the subject group has animation, it creates the shaders for water and water surface,
     * loads the water surface mesh, and loads the water jet meshes for animation.
     *
     * @param render The custom render object.
     */
    private void setupWaterObjects(CustomRender render) {
        try {
            if (isSubjectGroupWithAnimation) {
                virtualWaterShader = Shader.createFromAssets(
                        render, "shaders/water.vert",
                        "shaders/water.frag", null);

                virtualWaterSurfaceShader = Shader.createFromAssets(
                                render, "shaders/water_surface.vert",
                                "shaders/water_surface.frag",
                                new HashMap<String, String>() {
                                    {
                                        put(
                                                "NUMBER_OF_MIPMAP_LEVELS",
                                                Integer.toString(
                                                        cubemapFilter.getNumberOfMipmapLevels()));
                                    }
                                })
                        .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                        .setTexture("u_DfgTexture", dfgTexture);
                virtualWaterSurfaceMesh = Mesh.createFromAsset(render,
                        "models/water_surface.obj");

                for (int i = WATER_JETS_START; i < WATER_JETS_END; i++) {
                    VIRTUAL_WATER_JET_MESHES.add(Mesh.createFromAsset(render,
                            "models/animation/water_jets" + i + ".obj"));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Draws the virtual scene on the AR display by updating the frame, rendering the background,
     * and drawing the virtual objects.
     *
     * @param session The AR session.
     * @param render  The custom render object used for rendering.
     * @param anchor  The anchor point for the virtual objects.
     */
    public void drawScene(Session session, CustomRender render, Anchor anchor) {
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(new int[]{backgroundRenderer.getCameraColorTexture()
                    .getTextureId()});
            hasSetTextureNames = true;
        }

        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available during onDrawFrame", e);
            ARActivity.snackbarHelper.showError(ACTIVITY,
                    String.valueOf(R.string.cam_not_available));
            return;
        }

        Camera camera = frame.getCamera();
        backgroundRenderer.updateDisplayGeometry(frame);
        TRACKING_STATE_HELPER.updateKeepScreenOnFlag(camera.getTrackingState());
        backgroundRenderer.drawBackground(render);

        if (camera.getTrackingState() == TrackingState.TRACKING) {
            try {
                drawVirtualObjects(camera, render, anchor);
                loaded = true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Draws the virtual objects in the scene by setting up the projection and camera matrices,
     * and rendering the virtual fountain and water objects.
     *
     * @param camera The AR camera.
     * @param render The custom render object.
     * @param anchor The anchor point for the virtual objects.
     * @throws IOException If there is an error while drawing the objects.
     */
    private void drawVirtualObjects(Camera camera, CustomRender render, Anchor anchor)
            throws IOException {
        virtualSceneFramebuffer.bind();
        camera.getProjectionMatrix(PROJECTION_MATRIX, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(VIEW_MATRIX, 0);
        LightEstimate lightEstimate = frame.getLightEstimate();

        updateLightEstimation(virtualFountainShader, lightEstimate);

        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);

        if (anchor != null) {
            anchor.getPose().toMatrix(MODEL_MATRIX, 0);
            float[] rotationMatrix = new float[16];
            Matrix.setRotateM(rotationMatrix, 0, 180, 0.0f, 1.0f, 0.0f);
            float[] rotationModelMatrix = new float[16];
            Matrix.multiplyMM(rotationModelMatrix, 0, MODEL_MATRIX, 0,
                    rotationMatrix, 0);
            Matrix.multiplyMM(MODEL_VIEW_MATRIX, 0, VIEW_MATRIX, 0,
                    rotationModelMatrix, 0);
            Matrix.multiplyMM(MODEL_VIEW_PROJECTION_MATRIX, 0, PROJECTION_MATRIX,
                    0, MODEL_VIEW_MATRIX, 0);

            virtualFountainShader.setMat4("u_ModelViewProjection",
                    MODEL_VIEW_PROJECTION_MATRIX);
            virtualFountainShader.setVec3("u_LightIntensity",
                    lightEstimate.getEnvironmentalHdrMainLightIntensity());
            virtualFountainShader.setVec3("u_ViewLightDirection",
                    lightEstimate.getEnvironmentalHdrMainLightDirection());

            render.draw(virtualFountainMesh, virtualFountainShader, virtualSceneFramebuffer);
            backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);

            if (isSubjectGroupWithAnimation) {
                updateLightEstimation(virtualWaterSurfaceShader, lightEstimate);
                setupWater(camera, render);
            }
        }
    }

    /**
     * Sets up the rendering and sound for the water related objects.
     *
     * @param camera The AR camera.
     * @param render The custom render object.
     */
    private void setupWater(Camera camera, CustomRender render) {
        meshCounter = (meshCounter + 1) % VIRTUAL_WATER_JET_MESHES.size();
        LightEstimate lightEstimate = frame.getLightEstimate();

        setShaderUniforms(camera, virtualWaterShader);
        virtualWaterSurfaceShader.setMat4("u_ModelViewProjection",
                MODEL_VIEW_PROJECTION_MATRIX);
        virtualWaterSurfaceShader.setVec3("u_LightIntensity",
                lightEstimate.getEnvironmentalHdrMainLightIntensity());
        virtualWaterSurfaceShader.setVec3("u_ViewLightDirection",
                lightEstimate.getEnvironmentalHdrMainLightDirection());

        render.draw(VIRTUAL_WATER_JET_MESHES.get(meshCounter), virtualWaterShader,
                virtualSceneFramebuffer);
        render.draw(virtualWaterSurfaceMesh, virtualWaterSurfaceShader, virtualSceneFramebuffer);
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);

        soundPoolHelper.play();

    }

    /**
     * Updates light estimation for the shader based on the current frame's light estimation.
     */
    private void updateLightEstimation(Shader shader, LightEstimate lightEstimate) {
        if (lightEstimate.getState() != LightEstimate.State.VALID) {
            shader.setBool("u_LightEstimateIsValid", false);
            return;
        }

        shader.setBool("u_LightEstimateIsValid", true);
        Matrix.invertM(VIEW_INVERSE_MATRIX, 0, VIEW_MATRIX, 0);
        shader.setMat4("u_ViewInverse", VIEW_INVERSE_MATRIX);

        updateMainLight(shader,
                lightEstimate.getEnvironmentalHdrMainLightDirection(),
                lightEstimate.getEnvironmentalHdrMainLightIntensity());
        updateSphericalHarmonicsCoefficients(shader,
                lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
        cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap());
    }

    /**
     * Updates main light direction and intensity.
     */
    private void updateMainLight(Shader shader, float[] direction, float[] intensity) {
        WORLD_LIGHT_DIRECTION[0] = direction[0];
        WORLD_LIGHT_DIRECTION[1] = direction[1];
        WORLD_LIGHT_DIRECTION[2] = direction[2];
        Matrix.multiplyMV(VIEW_LIGHT_DIRECTION, 0, SceneRenderer.VIEW_MATRIX,
                0, WORLD_LIGHT_DIRECTION, 0);

        shader.setVec4("u_ViewLightDirection", VIEW_LIGHT_DIRECTION);
        shader.setVec3("u_LightIntensity", intensity);
    }

    /**
     * Updates spherical harmonics coefficients.
     */
    private void updateSphericalHarmonicsCoefficients(Shader shader, float[] coefficients) {
        if (coefficients.length != 9 * 3) {
            throw new IllegalArgumentException("The given coefficients array must be of " +
                    "length 27 (3 components per 9 coefficients");
        }

        for (int i = 0; i < 9 * 3; ++i) {
            SPHERICAL_HARMONIC_COEFFICIENTS[i]
                    = coefficients[i] * SPHERICAL_HARMONIC_FACTORS[i / 3];
        }

        shader.setVec3Array(
                "u_SphericalHarmonicsCoefficients", SPHERICAL_HARMONIC_COEFFICIENTS);
    }

    /**
     * Sets the shader uniforms for a given shader to control various rendering parameters.
     * The uniforms set include the normal matrix, model-view-projection matrix,
     * light direction, and camera position.
     *
     * @param camera The AR camera used for rendering.
     * @param shader The shader to set the uniforms for.
     */
    private void setShaderUniforms(Camera camera, Shader shader) {
        float[] normalMatrix = {
                MODEL_VIEW_MATRIX[0], MODEL_VIEW_MATRIX[1], MODEL_VIEW_MATRIX[2],
                MODEL_VIEW_MATRIX[4], MODEL_VIEW_MATRIX[5], MODEL_VIEW_MATRIX[6],
                MODEL_VIEW_MATRIX[8], MODEL_VIEW_MATRIX[9], MODEL_VIEW_MATRIX[10]
        };

        shader.setMat3("u_NormalView", normalMatrix);
        shader.setMat4("u_ModelViewProjection", MODEL_VIEW_PROJECTION_MATRIX);
        shader.setVec3("u_LightDirection",
                frame.getLightEstimate().getEnvironmentalHdrMainLightDirection());
        shader.setVec3("u_CameraPosition", camera.getPose().getTranslation());
    }

    /**
     * Resizes the framebuffer to the specified width and height.
     *
     * @param width  The new width of the framebuffer.
     * @param height The new height of the framebuffer.
     */
    public void resizeFramebuffer(int width, int height) {
        if (virtualSceneFramebuffer == null) {
            return;
        }

        virtualSceneFramebuffer.resize(width, height);
    }


    /**
     * Pauses the soundPool if it is initialized.
     */
    public void pauseSoundPool() {
        if (soundPoolHelper != null) {
            soundPoolHelper.pause();
        }
    }

    /**
     * Releases the soundPool if it is initialized.
     */
    public void releaseSoundPool() {
        if (soundPoolHelper != null) {
            soundPoolHelper.release();
        }
    }
}
