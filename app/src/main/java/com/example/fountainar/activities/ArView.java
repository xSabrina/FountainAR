package com.example.fountainar.activities;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.example.fountainar.R;
import com.example.fountainar.fragments.PrivacyNoticeDialogFragment;
import com.example.fountainar.fragments.VpsAvailabilityNoticeDialogFragment;
import com.example.fountainar.helpers.ARCoreHelper;
import com.example.fountainar.helpers.CameraPermissionHelper;
import com.example.fountainar.helpers.DisplayRotationHelper;
import com.example.fountainar.helpers.FullScreenHelper;
import com.example.fountainar.helpers.GeospatialHelper;
import com.example.fountainar.helpers.LocationPermissionHelper;
import com.example.fountainar.helpers.SnackbarHelper;
import com.example.fountainar.helpers.TrackingStateHelper;
import com.example.fountainar.rendering.BackgroundRenderer;
import com.example.fountainar.rendering.CustomRender;
import com.example.fountainar.rendering.DepthSettings;
import com.example.fountainar.rendering.Framebuffer;
import com.example.fountainar.rendering.Mesh;
import com.example.fountainar.rendering.Shader;
import com.example.fountainar.rendering.SpecularCubemapFilter;
import com.example.fountainar.rendering.Texture;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Earth;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class ArView extends AppCompatActivity implements
        CustomRender.Renderer,
        VpsAvailabilityNoticeDialogFragment.NoticeDialogListener,
        PrivacyNoticeDialogFragment.NoticeDialogListener {

    private static final String TAG = ArView.class.getSimpleName();
    private static final String ALLOW_GEOSPATIAL_ACCESS_KEY = "ALLOW_GEOSPATIAL_ACCESS";
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 1000f;
    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;
    public static SnackbarHelper snackbarHelper = new SnackbarHelper();
    public static GeospatialHelper geospatialHelper;
    public static Anchor anchor;
    public static Session session;

    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private final DepthSettings depthSettings = new DepthSettings();
    private final ArrayList<Mesh> virtualWaterJetMeshes = new ArrayList<>(5);
    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    private SharedPreferences sharedPreferences;
    private DisplayRotationHelper displayRotationHelper;
    private boolean isSubjectGroupWithAnimation = false;

    //ARCORE
    private ARCoreHelper arCoreHelper;
    private GLSurfaceView surfaceView;
    private BackgroundRenderer backgroundRenderer;
    private SpecularCubemapFilter cubemapFilter;

    //MODEL-RELATED
    private Mesh virtualFountainMesh;
    private Mesh virtualWaterSurfaceMesh;
    private Shader virtualFountainShader;
    private Shader virtualWaterJetShader;
    private Shader virtualWaterShader;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;
    private int meshCounter = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences(ALLOW_GEOSPATIAL_ACCESS_KEY, Context.MODE_PRIVATE);
        setContentView(R.layout.activity_ar_view);

        depthSettings.onCreate(this);
        arCoreHelper = new ARCoreHelper(this);
        geospatialHelper = new GeospatialHelper(this);
        displayRotationHelper = new DisplayRotationHelper(this);

        if (DemographicQuestionnaire.probNum % 2 == 0) {
            isSubjectGroupWithAnimation = true;
        }

        if (systemSupportsNeededTechnology()) {
            surfaceView = findViewById(R.id.surface_view);
            new CustomRender(surfaceView, this, getAssets());
        }
    }

    /**
     * @return OPENGL and ARCore are installed and up to date.
     */
    private boolean systemSupportsNeededTechnology() {
        String openGlVersion = ((ActivityManager) Objects.requireNonNull(this
                .getSystemService(Context.ACTIVITY_SERVICE))).getDeviceConfigurationInfo().
                getGlEsVersion();

        if (Double.parseDouble(openGlVersion) >= 3.0 &&
                arCoreHelper.isARCoreSupportedAndUpToDate()) {
            return true;
        } else {
            Toast.makeText(this, R.string.opengl_version_required, Toast.LENGTH_SHORT)
                    .show();
            this.finish();
            return false;
        }

    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        if (!sharedPreferences.edit().putBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY, true).commit()) {
            throw new AssertionError(
                    "The user preference could not be saved in SharedPreferences");
        }

        arCoreHelper.setupSession();
        session = arCoreHelper.updatedSession();
    }

    @Override
    public void onDialogContinueClick(DialogFragment dialog) {
        dialog.dismiss();
    }

    /**
     * Prepares the rendering objects, involving reading shaders and 3D model files.
     */
    @Override
    public void onSurfaceCreated(CustomRender render) {

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

                for (int i = 30; i < 60; i++) {
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
            snackbarHelper.showError(this,
                    String.valueOf(R.string.read_asset_failed) + e);
        }
    }

    @Override
    public void onSurfaceChanged(CustomRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }

    @Override
    public void onDrawFrame(CustomRender render) {
        if (session == null) {
            return;
        }

        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        displayRotationHelper.updateSessionIfNeeded(session);
        Frame frame;

        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available during onDrawFrame", e);
            snackbarHelper.showError(this, String.valueOf(R.string.cam_not_available));
            return;
        }

        Camera camera = frame.getCamera();

        backgroundRenderer.updateDisplayGeometry(frame);
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        Earth earth = session.getEarth();
        if (earth != null) {
            geospatialHelper.updateGeospatialState(earth);
        }

        backgroundRenderer.drawBackground(render);

        if (camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        drawVirtualObjects(camera, render);
    }

    /**
     * Gets projection and camera matrices, visualize planes and virtual object and
     * compose virtual scene with background.
     */
    private void drawVirtualObjects(Camera camera, CustomRender render) {
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

    @Override
    protected void onResume() {
        super.onResume();

        if (sharedPreferences.getBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY, false)) {
            arCoreHelper.setupSession();
            session = arCoreHelper.updatedSession();
        } else {
            showPrivacyNoticeDialog();
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    /**
     * Shows privacy dialog fragment.
     */
    private void showPrivacyNoticeDialog() {
        DialogFragment dialog = PrivacyNoticeDialogFragment.createDialog();
        dialog.show(getSupportFragmentManager(), PrivacyNoticeDialogFragment.class.getName());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (CameraPermissionHelper.hasNoCameraPermission(this)) {

            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                Toast.makeText(this, R.string.cam_permission_needed, Toast.LENGTH_LONG)
                        .show();
                CameraPermissionHelper.launchPermissionSettings(this);
            }

            finish();
        }

        if (LocationPermissionHelper.hasFineLocationPermissionsResponseInResult(permissions)
                && LocationPermissionHelper.hasNoFineLocationPermission(this)) {
            Toast.makeText(this, R.string.fine_loc_needed, Toast.LENGTH_LONG).show();

            if (!LocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                LocationPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }

        super.onDestroy();
    }
}

