package com.example.fountainar.rendering;

import android.app.Activity;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

import com.example.fountainar.R;
import com.example.fountainar.activities.ARActivity;
import com.example.fountainar.activities.DemographicQuestionnaire;
import com.example.fountainar.helpers.SoundPoolHelper;
import com.example.fountainar.helpers.TrackingStateHelper;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * Renderer responsible for rendering the virtual scene on the AR display. It handles setting up the
 * scene, updating and drawing the virtual objects, and managing the rendering process.
 */
public class SceneRenderer {

    private static final String TAG = SceneRenderer.class.getSimpleName();
    private static final ArrayList<Mesh> VIRTUAL_WATER_JET_MESHES = new ArrayList<>(30);
    private static final float[] MODEL_MATRIX = new float[16];
    private static final float[] VIEW_MATRIX = new float[16];
    private static final float[] PROJECTION_MATRIX = new float[16];
    private static final float[] MODEL_VIEW_MATRIX = new float[16];
    private static final float[] MODEL_VIEW_PROJECTION_MATRIX = new float[16];
    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 1000f;
    private final static int waterJetsStart = 160;
    private final static int waterJetsEnd = 165;
    private static boolean isSubjectGroupWithAnimation = false;
    private static BackgroundRenderer backgroundRenderer;
    private static Mesh virtualFountainMesh;
    private static Mesh virtualWaterSurfaceMesh;
    private static Shader virtualFountainShader;
    private static Shader virtualWaterShader;
    private static int meshCounter = 0;
    private final Activity activity;
    private final TrackingStateHelper trackingStateHelper;
    public Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;
    private SpecularCubemapFilter cubemapFilter;
    private SoundPoolHelper soundPoolHelper;

    public SceneRenderer(Activity activity) {
        this.activity = activity;
        trackingStateHelper = new TrackingStateHelper(activity);

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
            backgroundRenderer = new BackgroundRenderer();
            virtualSceneFramebuffer = new Framebuffer(1, 1);

            cubemapFilter =
                    new SpecularCubemapFilter(
                            render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);

            Texture dfgTexture = new Texture(
                    Texture.Target.TEXTURE_2D,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    false);

            Texture virtualFountainTexture =
                    Texture.createFromAsset(
                            render,
                            "models/Fountain_Baked.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.LINEAR);

            Texture virtualFountainAlbedoTexture = Texture.createFromAsset(
                    render,
                    "models/Fountain_Albedo.png",
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.SRGB);

            virtualFountainMesh = Mesh.createFromAsset(render, "models/Fountain.obj");

            virtualFountainShader =
                    Shader.createFromAssets(
                                    render,
                                    "shaders/environmental_hdr.vert",
                                    "shaders/environmental_hdr.frag",
                                    new HashMap<String, String>() {
                                        {
                                            put(
                                                    "NUMBER_OF_MIPMAP_LEVELS",
                                                    Integer.toString(
                                                            cubemapFilter.getNumberOfMipmapLevels())
                                            );
                                        }
                                    })
                            .setTexture("u_AlbedoTexture", virtualFountainAlbedoTexture)
                            .setTexture("u_RoughnessMetallicAmbientOcclusionTexture",
                                    virtualFountainTexture)
                            .setTexture("u_Cubemap",
                                    cubemapFilter.getFilteredCubemapTexture())
                            .setTexture("u_DfgTexture", dfgTexture);

            if (isSubjectGroupWithAnimation) {
                virtualWaterShader = Shader.createFromAssets(
                        render, "shaders/water.vert",
                        "shaders/water.frag", null);

                virtualWaterSurfaceMesh = Mesh.createFromAsset(render,
                        "models/Water_Surface.obj");

                for (int i = waterJetsStart; i < waterJetsEnd; i++) {
                    VIRTUAL_WATER_JET_MESHES.add(Mesh.createFromAsset(render,
                            "models/animation/Fountain_Animated" + i + ".obj"));
                }
            }

            backgroundRenderer.setUseDepthVisualization(render, false);
            backgroundRenderer.setUseOcclusion(render, true);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            ARActivity.snackbarHelper.showError(activity,
                    String.valueOf(R.string.read_asset_failed) + e);
        }
    }

    /**
     * Draws the virtual scene by updating the frame, rendering the background, and drawing
     * the virtual objects.
     *
     * @param session The AR session.
     * @param render  The custom render object.
     * @param anchor  The anchor point for the virtual objects.
     */
    public void drawScene(Session session, CustomRender render, Anchor anchor) {
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        Frame frame;

        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available during onDrawFrame", e);
            ARActivity.snackbarHelper.showError(activity,
                    String.valueOf(R.string.cam_not_available));
            return;
        }

        Camera camera = frame.getCamera();
        backgroundRenderer.updateDisplayGeometry(frame);
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());
        backgroundRenderer.drawBackground(render);

        if (camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        try {
            drawVirtualObjects(camera, render, anchor);
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        camera.getProjectionMatrix(PROJECTION_MATRIX, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(VIEW_MATRIX, 0);
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
            Matrix.multiplyMM(MODEL_VIEW_PROJECTION_MATRIX, 0, PROJECTION_MATRIX, 0,
                    MODEL_VIEW_MATRIX, 0);
            virtualFountainShader.setMat4("u_ModelViewProjection",
                    MODEL_VIEW_PROJECTION_MATRIX);
            render.draw(virtualFountainMesh, virtualFountainShader,
                    virtualSceneFramebuffer);
            backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);

            if (isSubjectGroupWithAnimation) {
                meshCounter = (meshCounter + 1) % VIRTUAL_WATER_JET_MESHES.size();
                float[] normalMatrix = new float[]{
                        MODEL_VIEW_MATRIX[0], MODEL_VIEW_MATRIX[1], MODEL_VIEW_MATRIX[2],
                        MODEL_VIEW_MATRIX[4], MODEL_VIEW_MATRIX[5], MODEL_VIEW_MATRIX[6],
                        MODEL_VIEW_MATRIX[8], MODEL_VIEW_MATRIX[9], MODEL_VIEW_MATRIX[10]
                };
                virtualWaterShader.setMat3("u_NormalView", normalMatrix);
                virtualWaterShader.setMat4("u_ModelViewProjection",
                        MODEL_VIEW_PROJECTION_MATRIX);

                float[] lightDirection = {0.0f, 1.0f, 0.0f}; // Example: light coming from above
                virtualWaterShader.setVec3("u_LightDirection", lightDirection);
                // Set the camera position uniform
                float[] cameraPosition = camera.getPose().getTranslation();
                virtualWaterShader.setVec3("u_CameraPosition", cameraPosition);

                render.draw(virtualWaterSurfaceMesh, virtualWaterShader,
                        virtualSceneFramebuffer);
                render.draw(VIRTUAL_WATER_JET_MESHES.get(meshCounter), virtualWaterShader,
                        virtualSceneFramebuffer);
                soundPoolHelper.play();
            }
            backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
        }
    }

    /**
     * Resizes the framebuffer to the specified width and height.
     *
     * @param width  The new width of the framebuffer.
     * @param height The new height of the framebuffer.
     */
    public void resizeFramebuffer(int width, int height) {
        virtualSceneFramebuffer.resize(width, height);
    }

    /**
     * Pauses the sound pool if it is initialized.
     */
    public void pauseSoundPool() {
        if (soundPoolHelper != null) {
            soundPoolHelper.pause();
        }
    }

    /**
     * Releases the sound pool if it is initialized.
     */
    public void releaseSoundPool() {
        if (soundPoolHelper != null) {
            soundPoolHelper.release();
        }
    }
}
