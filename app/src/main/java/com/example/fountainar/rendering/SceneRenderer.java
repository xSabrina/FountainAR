package com.example.fountainar.rendering;

import android.app.Activity;
import android.opengl.Matrix;
import android.util.Log;

import com.example.fountainar.R;
import com.example.fountainar.activities.ArView;
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
    private static final ArrayList<Mesh> virtualWaterJetMeshes = new ArrayList<>(5);
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

    public SceneRenderer(Activity activity) {
        this.activity = activity;
        trackingStateHelper = new TrackingStateHelper(activity);

        if (DemographicQuestionnaire.probNum % 2 == 0) {
            isSubjectGroupWithAnimation = true;
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
                    "models/texture_fountain.png",
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.SRGB);

            Texture virtualFountainTexture =
                    Texture.createFromAsset(
                            render,
                            "models/texture_fountain.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.LINEAR);

            virtualFountainMesh = Mesh.createFromAsset(render, "models/fountain.obj");

            if (isSubjectGroupWithAnimation) {
                Texture virtualWaterTexture =
                        Texture.createFromAsset(
                                render,
                                "models/texture_water.png",
                                Texture.WrapMode.CLAMP_TO_EDGE,
                                Texture.ColorFormat.SRGB);

                Texture virtualWaterJetTexture =
                        Texture.createFromAsset(
                                render,
                                "models/texture_water_jet.png",
                                Texture.WrapMode.CLAMP_TO_EDGE,
                                Texture.ColorFormat.SRGB);

                virtualWaterSurfaceMesh = Mesh.createFromAsset(render,
                        "models/water_surface.obj");

                for (int i = 30; i < 40; i++) {
                    virtualWaterJetMeshes.add(Mesh.createFromAsset(render,
                            "models/animation/fountain_animation" + i + ".obj"));
                }

                virtualWaterJetShader = Shader.createFromAssets(
                                render,
                                "shaders/water.vert",
                                "shaders/water.frag",
                                null)
                        .setTexture("u_Texture", virtualWaterJetTexture)
                        .setDepthWrite(false);

                virtualWaterShader = Shader.createFromAssets(
                                render,
                                "shaders/water.vert",
                                "shaders/water.frag",
                                null)
                        .setTexture("u_Texture", virtualWaterTexture)
                        .setDepthWrite(false);
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
            ArView.snackbarHelper.showError(activity,
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
            ArView.snackbarHelper.showError(activity, String.valueOf(R.string.cam_not_available));
            return;
        }

        Camera camera = frame.getCamera();

        backgroundRenderer.updateDisplayGeometry(frame);
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        backgroundRenderer.drawBackground(render);

        if (camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        drawVirtualObjects(camera, render, anchor);
    }

    /**
     * Gets projection and camera matrices, visualize planes and virtual object and
     * compose virtual scene with background.
     */
    public void drawVirtualObjects(Camera camera, CustomRender render, Anchor anchor) {
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
            }
        }
    }

    public void resizeFramebuffer(int width, int height) {
        virtualSceneFramebuffer.resize(width, height);
    }
}
