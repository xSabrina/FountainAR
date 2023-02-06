package com.example.fountainar.activities;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.fragment.app.DialogFragment;

import com.example.fountainar.R;
import com.example.fountainar.fragments.PrivacyNoticeDialogFragment;
import com.example.fountainar.fragments.VpsAvailabilityNoticeDialogFragment;
import com.example.fountainar.helpers.CameraPermissionHelper;
import com.example.fountainar.helpers.DisplayRotationHelper;
import com.example.fountainar.helpers.FullScreenHelper;
import com.example.fountainar.helpers.LocationPermissionHelper;
import com.example.fountainar.helpers.SnackbarHelper;
import com.example.fountainar.helpers.TrackingStateHelper;
import com.example.fountainar.rendering.BackgroundRenderer;
import com.example.fountainar.rendering.DepthSettings;
import com.example.fountainar.rendering.Framebuffer;
import com.example.fountainar.rendering.Mesh;
import com.example.fountainar.rendering.SampleRender;
import com.example.fountainar.rendering.Shader;
import com.example.fountainar.rendering.SpecularCubemapFilter;
import com.example.fountainar.rendering.Texture;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Earth;
import com.google.ar.core.Frame;
import com.google.ar.core.GeospatialPose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.VpsAvailability;
import com.google.ar.core.VpsAvailabilityFuture;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.FineLocationPermissionNotGrantedException;
import com.google.ar.core.exceptions.GooglePlayServicesLocationLibraryNotLinkedException;
import com.google.ar.core.exceptions.ResourceExhaustedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.core.exceptions.UnsupportedConfigurationException;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ArView extends AppCompatActivity implements
        SampleRender.Renderer,
        VpsAvailabilityNoticeDialogFragment.NoticeDialogListener,
        PrivacyNoticeDialogFragment.NoticeDialogListener {

    //COMMON
    private static final String TAG = ArView.class.getSimpleName();
    private final SnackbarHelper snackbarHelper = new SnackbarHelper();
    private SharedPreferences sharedPreferences;
    private DisplayRotationHelper displayRotationHelper;

    //GEOSPATIAL API
    private static final String ALLOW_GEOSPATIAL_ACCESS_KEY = "ALLOW_GEOSPATIAL_ACCESS";
    private Anchor anchor = null;
    private FusedLocationProviderClient fusedLocationClient;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 1000f;
    private static final double LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS = 10;
    private static final double LOCALIZING_ORIENTATION_YAW_ACCURACY_THRESHOLD_DEGREES = 15;
    private static final double LOCALIZED_HORIZONTAL_ACCURACY_HYSTERESIS_METERS = 10;
    private static final double LOCALIZED_ORIENTATION_YAW_ACCURACY_HYSTERESIS_DEGREES = 10;

    private long localizingStartTimestamp;
    private static final int LOCALIZING_TIMEOUT_SECONDS = 180;

    enum State {
        /**
         * The Geospatial API has not yet been initialized.
         */
        UNINITIALIZED,
        /**
         * The Geospatial API is not supported.
         */
        UNSUPPORTED,
        /**
         * The Geospatial API has encountered an unrecoverable error.
         */
        EARTH_STATE_ERROR,
        /**
         * The Session has started, but {@link Earth} isn't {TrackingState.TRACKING} yet.
         */
        PRE_TRACKING,
        /**
         * {@link Earth} is {TrackingState.TRACKING}, but the desired positioning confidence
         * hasn't been reached yet.
         */
        LOCALIZING,
        /**
         * The desired positioning confidence wasn't reached in time.
         */
        LOCALIZING_FAILED,
        /**
         * {@link Earth} is {TrackingState.TRACKING} and the desired positioning confidence has
         * been reached.
         */
        LOCALIZED
    }
    private State state = State.UNINITIALIZED;

    //ARCORE
    private boolean installRequested = false;
    private Session session;
    private GLSurfaceView surfaceView;
    private BackgroundRenderer backgroundRenderer;

    //MODEL-RELATED
    private Mesh virtualFountainMesh;
    private Shader virtualFountainShader;
    private Mesh virtualWaterSurfaceMesh;
    private final ArrayList<Mesh> virtualWaterJetMeshes = new ArrayList<>(5);
    private Shader virtualWaterJetShader;
    private Shader virtualWaterShader;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;
    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    private int meshCounter = 0;
    private boolean isSubjectGroupWithAnimation = false;
    private SpecularCubemapFilter cubemapFilter;
    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;
    private final DepthSettings depthSettings = new DepthSettings();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences(ALLOW_GEOSPATIAL_ACCESS_KEY, Context.MODE_PRIVATE);
        setContentView(R.layout.activity_ar_view);
        depthSettings.onCreate(this);

        if(DemographicQuestionnaire.probNum % 2 == 0){
            isSubjectGroupWithAnimation = true;
        }

        if (systemSupportsNeededTechnology()) {
            surfaceView = findViewById(R.id.surface_view);
            displayRotationHelper = new DisplayRotationHelper(this);
            new SampleRender(surfaceView, this, getAssets());
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

            setupQuiz();
        }
    }

    /**
     * @return OPENGL and ARCore are installed and up to date.
     */
    private boolean systemSupportsNeededTechnology() {
        String openGlVersion = ((ActivityManager) Objects.requireNonNull(this
                .getSystemService(Context.ACTIVITY_SERVICE))).getDeviceConfigurationInfo().
                getGlEsVersion();

        if (Double.parseDouble(openGlVersion) >= 3.0 && isARCoreSupportedAndUpToDate()) {
            return true;
        } else {
            Toast.makeText(this, R.string.opengl_version_required, Toast.LENGTH_SHORT)
                    .show();
            this.finish();
            return false;
        }

    }

    /**
     * @return ARCore is installed and up to date.
     */
    private boolean isARCoreSupportedAndUpToDate() {
        ArCoreApk.Availability availability =
                ArCoreApk.getInstance().checkAvailability(this);

        switch (availability) {
            case SUPPORTED_INSTALLED:
                return true;

            case SUPPORTED_APK_TOO_OLD:

            case SUPPORTED_NOT_INSTALLED:
                try {
                    ArCoreApk.InstallStatus installStatus =
                            ArCoreApk.getInstance().
                                    requestInstall(this, true);
                    switch (installStatus) {
                        case INSTALL_REQUESTED:
                            Log.i(TAG, "ARCore installation requested");
                            return false;
                        case INSTALLED:
                            return true;
                    }
                } catch (UnavailableException e) {
                    Log.e(TAG, "ARCore not installed", e);
                }
                return false;

            case UNSUPPORTED_DEVICE_NOT_CAPABLE:
                return false;

            case UNKNOWN_CHECKING:

            case UNKNOWN_ERROR:

            case UNKNOWN_TIMED_OUT:

        }
        return false;
    }

    /**
     * Sets up the quiz related to provided AR content.
     */
    private void setupQuiz() {
        Button startButton = findViewById(R.id.ar_button_start);
        Button task1Button = findViewById(R.id.ar_button_task1);
        Button task2Button = findViewById(R.id.ar_button_task2);
        Button task3Button = findViewById(R.id.ar_button_task3);

        RelativeLayout instructionsLayout = findViewById(R.id.ar_layout_instructions);
        RelativeLayout task1Layout = findViewById(R.id.ar_layout_task1);
        RelativeLayout task2Layout = findViewById(R.id.ar_layout_task2);
        RelativeLayout task3Layout = findViewById(R.id.ar_layout_task3);

        startButton.setOnClickListener(view -> {
            instructionsLayout.setVisibility(View.GONE);
            task1Layout.setVisibility(View.VISIBLE);
        });

        task1Button.setOnClickListener(view -> {
            RadioGroup radioGroup = findViewById(R.id.ar_rg_task1);
            continueQuiz(radioGroup, task1Layout, task2Layout);
        });

        task2Button.setOnClickListener(view -> {
            RadioGroup radioGroup = findViewById(R.id.ar_rg_task2);
            continueQuiz(radioGroup, task2Layout, task3Layout);
        });

        task3Button.setOnClickListener(view -> {
            RadioGroup radioGroup = findViewById(R.id.ar_rg_task3);
            if (radioGroup.getCheckedRadioButtonId() == -1) {
                Toast.makeText(getApplicationContext(), R.string.ar_missing_answer,
                        Toast.LENGTH_SHORT).show();
            } else {
                Intent intentMain = new Intent(getApplicationContext(),
                        TAQuestionnaire.class);
                startActivity(intentMain);
            }
        });
    }

    /**
     * Continues quiz if current question is answered yet.
     *
     * @param rg            RadioGroup of multiple choice options
     * @param currentLayout Layout of the current quiz page.
     * @param nextLayout    Layout of the next quiz page.
     */
    private void continueQuiz(RadioGroup rg, RelativeLayout currentLayout,
                              RelativeLayout nextLayout) {

        if (rg.getCheckedRadioButtonId() == -1) {
            Toast.makeText(getApplicationContext(), R.string.ar_missing_answer, Toast.LENGTH_SHORT)
                    .show();
        } else {
            currentLayout.setVisibility(View.GONE);
            nextLayout.setVisibility(View.VISIBLE);
        }

    }

    /**
     * Creates ARCore session.
     */
    private void createSession() {
        Exception exception = null;
        String message = null;

        if (session == null) {
            try {
                switch (ArCoreApk.getInstance().requestInstall(this,
                        !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                if (CameraPermissionHelper.hasNoCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                }
                if (LocationPermissionHelper.hasNoFineLocationPermission(this)) {
                    LocationPermissionHelper.requestFineLocationPermission(this);
                }

                session = new Session(this);
            } catch (UnavailableArcoreNotInstalledException
                     | UnavailableUserDeclinedInstallationException e) {
                message = String.valueOf(R.string.install_arcore);
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = String.valueOf(R.string.update_arcore);
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = String.valueOf(R.string.update_app);
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = String.valueOf(R.string.ar_not_supported);
                exception = e;
            } catch (Exception e) {
                message = String.valueOf(R.string.ar_session_failed);
                exception = e;
            }

            if (message != null) {
                snackbarHelper.showError(this, message);
                Log.e(TAG, "Error creating session", exception);
                return;
            }

        } else {
            getLastLocation();
        }

        try {
            configureSession();
            session.resume();
        } catch (CameraNotAvailableException e) {
            message = String.valueOf(R.string.cam_not_available);
            exception = e;
        } catch (GooglePlayServicesLocationLibraryNotLinkedException e) {
            message = String.valueOf(R.string.proguard_error);
            exception = e;
        } catch (FineLocationPermissionNotGrantedException e) {
            message = String.valueOf(R.string.no_permission_fine_location);
            exception = e;
        } catch (UnsupportedConfigurationException e) {
            message = String.valueOf(R.string.geospatial_not_supported);
            exception = e;
        } catch (SecurityException e) {
            message = String.valueOf(R.string.cam_or_network_error);
            exception = e;
        }

        if (message != null) {
            session = null;
            snackbarHelper.showError(this, message);
            Log.e(TAG, String.valueOf(R.string.session_create_or_config_error), exception);
        }
    }

    private void getLastLocation() {
        try {
            fusedLocationClient
                    .getLastLocation()
                    .addOnSuccessListener(
                            location -> {
                                double latitude = 0;
                                double longitude = 0;
                                if (location != null) {
                                    latitude = location.getLatitude();
                                    longitude = location.getLongitude();
                                } else {
                                    Log.e(TAG, "Last location is null");
                                }
                                checkVpsAvailability(latitude, longitude);
                            });
        } catch (SecurityException e) {
            Log.e(TAG, "No location permissions granted by the user");
        }
    }

    private void checkVpsAvailability(double latitude, double longitude) {
        ListenableFuture<VpsAvailability> availabilityFuture =
                checkVpsAvailabilityFuture(latitude, longitude);
        Futures.addCallback(
                availabilityFuture,
                new FutureCallback<VpsAvailability>() {
                    @Override
                    public void onSuccess(VpsAvailability result) {
                        if (result != VpsAvailability.AVAILABLE) {
                            showVpsNotAvailabilityNoticeDialog();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(TAG, "Error checking VPS availability", t);
                    }
                },
                getMainExecutor());
    }

    private void showVpsNotAvailabilityNoticeDialog() {
        DialogFragment dialog = VpsAvailabilityNoticeDialogFragment.createDialog();
        dialog.show(getSupportFragmentManager(),
                VpsAvailabilityNoticeDialogFragment.class.getName());
    }

    /**
     * Configures session with featured settings, if GeospatialMode is supported.
     */
    private void configureSession() {
        if (!session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
            state = State.UNSUPPORTED;
            return;
        }

        Config config = session.getConfig();
        config.setGeospatialMode(Config.GeospatialMode.ENABLED);
        config.setDepthMode(Config.DepthMode.AUTOMATIC);
        depthSettings.setUseDepthForOcclusion(false);
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        session.configure(config);

        state = State.PRE_TRACKING;
        localizingStartTimestamp = System.currentTimeMillis();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (CameraPermissionHelper.hasNoCameraPermission(this)) {
            Toast.makeText(this, R.string.cam_permission_needed, Toast.LENGTH_LONG).show();

            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
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

        createSession();
    }

    @Override
    public void onDialogContinueClick(DialogFragment dialog) {
        dialog.dismiss();
    }

    /**
     * Prepares the rendering objects, involving reading shaders and 3D model files.
     */
    @Override
    public void onSurfaceCreated(SampleRender render) {

        try {
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render,1, 1);

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

            if (isSubjectGroupWithAnimation){

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

                for (int i = 50; i < 110; i++){
                    virtualWaterJetMeshes.add(Mesh.createFromAsset(render,
                            "models/animation/fountain_animated" + i + ".obj"));
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
                                                    Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                                        }
                                    })
                            .setTexture("u_AlbedoTexture", virtualFountainAlbedoTexture)
                            .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualFountainTexture)
                            .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
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
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }

    @Override
    public void onDrawFrame(SampleRender render) {
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
            updateGeospatialState(earth);
        }

        backgroundRenderer.drawBackground(render);

        if (camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        drawVirtualObjects(camera, render);
    }

    /**
     * Changes behavior depending on the current {@link State} of the application.
     */
    private void updateGeospatialState(Earth earth) {
        if (earth.getEarthState() != Earth.EarthState.ENABLED) {
            state = State.EARTH_STATE_ERROR;
            Log.e(TAG, "Earth state error");
            return;
        }

        if (earth.getTrackingState() != TrackingState.TRACKING) {
            state = State.PRE_TRACKING;
            return;
        }

        if (state == State.PRE_TRACKING) {
            updatePreTrackingState(earth);
        } else if (state == State.LOCALIZING) {
            updateLocalizingState(earth);
        } else if (state == State.LOCALIZED) {
            updateLocalizedState(earth);
        }
    }

    /**
     * Handles the updating for {State.PRE_TRACKING}. In this state, wait for {@link Earth} to
     * have {TrackingState.TRACKING}. If it hasn't been enabled by now, then we've encountered
     * an unrecoverable {State.EARTH_STATE_ERROR}.
     */
    private void updatePreTrackingState(Earth earth) {
        if (earth.getTrackingState() == TrackingState.TRACKING) {
            state = State.LOCALIZING;
        }
    }

    /**
     * Handles the updating for {@link State#LOCALIZING}. In this state, wait for the horizontal and
     * orientation threshold to improve until it reaches your threshold.
     *
     * <p> If it takes too long for the threshold to be reached, this could mean that GPS data isn't
     * accurate enough, or that the user is in an area that can't be localized with StreetView.
     */
    private void updateLocalizingState(Earth earth) {
        GeospatialPose geospatialPose = earth.getCameraGeospatialPose();

       // if (geospatialPose.getHorizontalAccuracy() <=
        //        LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS
       //         && geospatialPose.getOrientationYawAccuracy()
      //          <= LOCALIZING_ORIENTATION_YAW_ACCURACY_THRESHOLD_DEGREES) {
           state = State.LOCALIZED;

            if (anchor == null) {
                placeAnchor(earth);
                Log.i(TAG, "Anchor placed");
            }

        //    return;
       // }

        if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() -
                localizingStartTimestamp)
                > LOCALIZING_TIMEOUT_SECONDS) {
            state = State.LOCALIZING_FAILED;
        }
    }

    /**
     * Handles the updating for {@link State#LOCALIZED}. In this state, check the accuracy for
     * degradation and return to {@link State#LOCALIZING} if the position accuracies have dropped
     * too low.
     */
    private void updateLocalizedState(Earth earth) {
        GeospatialPose geospatialPose = earth.getCameraGeospatialPose();

        if (geospatialPose.getHorizontalAccuracy()
                > LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS
                + LOCALIZED_HORIZONTAL_ACCURACY_HYSTERESIS_METERS
                || geospatialPose.getOrientationYawAccuracy()
                > LOCALIZING_ORIENTATION_YAW_ACCURACY_THRESHOLD_DEGREES
                + LOCALIZED_ORIENTATION_YAW_ACCURACY_HYSTERESIS_DEGREES) {
            state = State.LOCALIZING;
            localizingStartTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Places an anchor when geospatial accuracy is high enough.
     */
    private void placeAnchor(Earth earth) {
        if (earth.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        GeospatialPose geospatialPose = earth.getCameraGeospatialPose();
        createTerrainAnchor(earth, geospatialPose);
        Log.i(TAG, "Anchor was placed");
    }

    /**
     * Creates a terrain anchor at given world coordinates using earth object.
     */
    private void createTerrainAnchor(Earth earth, GeospatialPose geospatialPose) {
       // double latitude = 49.00915685423378;

       // double longitude = 12.093960015990586;
        double latitude = geospatialPose.getLatitude();

        double longitude = geospatialPose.getLongitude();
        float[] quaternion = geospatialPose.getEastUpSouthQuaternion();

        try {
            anchor = earth.resolveAnchorOnTerrain(
                    latitude,
                    longitude,
                    0.0f,
                    quaternion[0],
                    quaternion[1],
                    quaternion[2],
                    quaternion[3]);
        } catch (ResourceExhaustedException e) {
            snackbarHelper.showMessageWithDismiss(this,
                    String.valueOf(R.string.error_create_anchor));
            Log.e(TAG, "Exception creating terrain anchor");
        }
    }

    /**
     * Gets projection and camera matrices, visualize planes and virtual object and
     * compose virtual scene with background.
     */
    private void drawVirtualObjects(Camera camera, SampleRender render) {
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

            if(isSubjectGroupWithAnimation) {
                Mesh virtualWaterJetMesh = virtualWaterJetMeshes.get(meshCounter);
                meshCounter++;
                if(meshCounter == virtualWaterJetMeshes.size()){
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


    /**
     * Wrapper for session checking vps availability.
     */
    private ListenableFuture<VpsAvailability> checkVpsAvailabilityFuture(double latitude,
                                                                         double longitude) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    final VpsAvailabilityFuture future =
                            session.checkVpsAvailabilityAsync(
                                    latitude, longitude, completer::set);
                    completer.addCancellationListener(future::cancel, Runnable::run);
                    return "checkVpsAvailabilityFuture";
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sharedPreferences.getBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY,false)) {
            createSession();
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

