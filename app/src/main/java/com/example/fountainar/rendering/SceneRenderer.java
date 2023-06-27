package com.example.fountainar.rendering;

import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.opengl.Matrix;
import android.util.Log;

import com.example.fountainar.R;
import com.example.fountainar.activities.ARActivity;
import com.example.fountainar.activities.DemographicQuestionnaire;
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

public class SceneRenderer {

    private static final String TAG = SceneRenderer.class.getSimpleName();
    private static final ArrayList<Mesh> virtualWaterJetMeshes = new ArrayList<>(30);
    private static final float[] modelMatrix = new float[16];
    private static final float[] viewMatrix = new float[16];
    private static final float[] projectionMatrix = new float[16];
    private static final float[] modelViewMatrix = new float[16];
    private static final float[] modelViewProjectionMatrix = new float[16];
    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 1000f;
    private static boolean isSubjectGroupWithAnimation = false;
    private static BackgroundRenderer backgroundRenderer;
    private static Mesh virtualFountainMesh;
    private static Mesh virtualWaterSurfaceMesh;
    private static Shader virtualFountainShader;
    private static Shader virtualWaterJetShader;
    private static Shader virtualWaterShader;
    private static int meshCounter = 0;
    private final Activity activity;
    private final TrackingStateHelper trackingStateHelper;
    public Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;
    private SpecularCubemapFilter cubemapFilter;
    private final static int waterjetsStart = 160;
    private final static int waterjetsEnd = 170;
    private SoundPool soundPool;
    private int soundId;
    private boolean soundPoolPlaying = false;

    public SceneRenderer(Activity activity) {
        this.activity = activity;
        trackingStateHelper = new TrackingStateHelper(activity);

        if (DemographicQuestionnaire.probNum % 2 == 0) {
            isSubjectGroupWithAnimation = true;
            setupSoundPool();
        }
    }

    public void setupScene(CustomRender render) {
        try {
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, 1, 1);

            cubemapFilter =
                    new SpecularCubemapFilter(
                            render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);

            Texture dfgTexture = new Texture(
                    render,
                    Texture.Target.TEXTURE_2D,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    false);

            Texture virtualFountainAlbedoTexture = Texture.createFromAsset(
                    render,
                    "models/Fountain_Albedo.png",
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.SRGB);

            Texture virtualFountainTexture =
                    Texture.createFromAsset(
                            render,
                            "models/Fountain_Baked.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.LINEAR);

            virtualFountainMesh = Mesh.createFromAsset(render, "models/Fountain.obj");

            if (isSubjectGroupWithAnimation) {
                Texture virtualWaterTexture =
                        Texture.createFromAsset(
                                render,
                                "models/Water_Baked.png",
                                Texture.WrapMode.CLAMP_TO_EDGE,
                                Texture.ColorFormat.SRGB);

                Texture virtualWaterJetTexture =
                        Texture.createFromAsset(
                                render,
                                "models/Water_Baked.png",
                                Texture.WrapMode.CLAMP_TO_EDGE,
                                Texture.ColorFormat.SRGB);

                virtualWaterSurfaceMesh = Mesh.createFromAsset(render,
                        "models/Water_Surface.obj");

                for (int i = waterjetsStart; i < waterjetsEnd; i++) {
                    virtualWaterJetMeshes.add(Mesh.createFromAsset(render,
                            "models/animation/Fountain_Animated" + i + ".obj"));
                }

                virtualWaterJetShader = Shader.createFromAssets(
                                render,
                                "shaders/water.vert",
                                "shaders/water.frag",
                                null)
                        .setTexture("u_Texture", virtualWaterJetTexture);

                virtualWaterShader = Shader.createFromAssets(
                                render,
                                "shaders/water.vert",
                                "shaders/water.frag",
                                null)
                        .setTexture("u_Texture", virtualWaterTexture);
            }

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

            backgroundRenderer.setUseDepthVisualization(render, false);
            backgroundRenderer.setUseOcclusion(render, false);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            ARActivity.snackbarHelper.showError(activity,
                    String.valueOf(R.string.read_asset_failed) + e);
        }
    }

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
     * Gets projection and camera matrices, visualize planes and virtual object and
     * compose virtual scene with background.
     */
    private void drawVirtualObjects(Camera camera, CustomRender render, Anchor anchor)
            throws IOException {
        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(viewMatrix, 0);
        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);

        if (anchor != null) {
            anchor.getPose().toMatrix(modelMatrix, 0);
            float[] rotationMatrix = new float[16];
            Matrix.setRotateM(rotationMatrix, 0, 180, 0.0f, 1.0f, 0.0f);
            float[] rotationModelMatrix = new float[16];
            Matrix.multiplyMM(rotationModelMatrix, 0, modelMatrix, 0,
                    rotationMatrix, 0);
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0,
                    rotationModelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0,
                    modelViewMatrix, 0);

            virtualFountainShader.setMat4("u_ModelViewProjection",
                    modelViewProjectionMatrix);
            render.draw(virtualFountainMesh, virtualFountainShader,
                    virtualSceneFramebuffer);

            backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);

            if (isSubjectGroupWithAnimation) {
                Mesh virtualWaterJetMesh = virtualWaterJetMeshes.get(meshCounter);
                meshCounter++;

                if (meshCounter == virtualWaterJetMeshes.size()) {
                    meshCounter = 0;
                }

                virtualWaterShader.setMat4("u_ModelViewProjection",
                        modelViewProjectionMatrix);
                virtualWaterJetShader.setMat4("u_ModelViewProjection",
                        modelViewProjectionMatrix);

                render.draw(virtualWaterSurfaceMesh, virtualWaterShader, virtualSceneFramebuffer);
                render.draw(virtualWaterJetMesh, virtualWaterJetShader, virtualSceneFramebuffer);

                backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
                if (!soundPoolPlaying) {
                    soundPoolPlaying = true;
                    soundPool.play(soundId, 0.8f, 0.8f, 1, -1,
                            1.0f);
                }

            }
        }
    }

    private void setupSoundPool() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        soundPool = new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .build();

        soundId = soundPool.load(activity, R.raw.fountain_animation_sound, 1);
    }

    public void pauseSoundPool(){
        soundPool.pause(soundId);
    }

    public void releaseSoundPool(){
        soundPool.stop(soundId);
        soundPool.release();
        soundPool = null;
    }

    public void resizeFramebuffer(int width, int height) {
        virtualSceneFramebuffer.resize(width, height);
    }
}