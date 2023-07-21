package com.example.fountainar.activities;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.example.fountainar.R;
import com.example.fountainar.fragments.PrivacyNoticeDialogFragment;
import com.example.fountainar.fragments.VpsAvailabilityNoticeDialogFragment;
import com.example.fountainar.handlers.BackPressedHandler;
import com.example.fountainar.helpers.ARCoreHelper;
import com.example.fountainar.helpers.CameraPermissionHelper;
import com.example.fountainar.helpers.DisplayRotationHelper;
import com.example.fountainar.helpers.FullScreenHelper;
import com.example.fountainar.helpers.GeospatialHelper;
import com.example.fountainar.helpers.LocationPermissionHelper;
import com.example.fountainar.helpers.QuizHelper;
import com.example.fountainar.helpers.SnackbarHelper;
import com.example.fountainar.rendering.CustomRender;
import com.example.fountainar.rendering.SceneRenderer;
import com.google.ar.core.Anchor;
import com.google.ar.core.Earth;
import com.google.ar.core.Session;

import java.util.Objects;

/**
 * Activity to manage the AR experience by setting up components, rendering the scene, and handling
 * permissions. Handles the creation of the AR session, updates the geospatial state, and manages
 * the lifecycle.
 */
public class ARActivity extends AppCompatActivity implements
        CustomRender.Renderer,
        VpsAvailabilityNoticeDialogFragment.NoticeDialogListener,
        PrivacyNoticeDialogFragment.NoticeDialogListener {
    private static final String ALLOW_GEOSPATIAL_ACCESS_KEY = "ALLOW_GEOSPATIAL_ACCESS";

    public static SnackbarHelper snackbarHelper = new SnackbarHelper();
    @SuppressLint("StaticFieldLeak")
    public static GeospatialHelper geospatialHelper;
    public static Anchor anchor;
    public static Session session;
    private SharedPreferences sharedPreferences;
    private DisplayRotationHelper displayRotationHelper;
    private ARCoreHelper arCoreHelper;
    private QuizHelper quizHelper;
    private GLSurfaceView surfaceView;
    private SceneRenderer sceneRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences(ALLOW_GEOSPATIAL_ACCESS_KEY, Context.MODE_PRIVATE);
        setContentView(R.layout.activity_ar);
        arCoreHelper = new ARCoreHelper(this);
        quizHelper = new QuizHelper(this);
        geospatialHelper = new GeospatialHelper(this);
        displayRotationHelper = new DisplayRotationHelper(this);
        BackPressedHandler.setupBackPressedCallback(this);
        surfaceView = findViewById(R.id.surface_view);
        sceneRenderer = new SceneRenderer(this);
        new CustomRender(surfaceView, this, getAssets());

        runOnUiThread(() -> {
            Toast.makeText(ARActivity.this, R.string.models_loading,
                    Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onDrawFrame(CustomRender render) {
        if (session == null) {
            return;
        }

        setupSession();
        sceneRenderer.drawScene(session, render, anchor);

        if(anchor != null) {
            quizHelper.setupQuiz();
        }

        displayRotationHelper.updateSessionIfNeeded(session);
        Earth earth = session.getEarth();

        if (earth != null) {
            geospatialHelper.updateGeospatialState(earth);
        }
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        if (!sharedPreferences.edit().putBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY, true).commit()) {
            throw new AssertionError(
                    "The user preference could not be saved in SharedPreferences");
        }

        setupSession();
    }

    /**
     * Sets up the AR session by checking and requesting camera and location permissions.
     * If both permissions are granted and the system supports the required technology
     * (OpenGL ES 3.0 and ARCore), it sets up the session elements.
     */
    private void setupSession() {
        if (CameraPermissionHelper.hasNoCameraPermission(this)
                && !CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
        } else if (LocationPermissionHelper.hasNoFineLocationPermission(this)
                && !LocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
            LocationPermissionHelper.requestFineLocationPermission(this);
        } else {
            if (systemSupportsNeededTechnology()) {
                setupSessionElements();
            }
        }
    }

    /**
     * Checks if the system supports the required technology (OpenGL ES 3.0 and ARCore).
     * If the system does not meet the requirements, it displays a toast message and finishes the
     * activity.
     *
     * @return true if the system supports the required technology.
     */
    private boolean systemSupportsNeededTechnology() {
        String openGlVersion = ((ActivityManager)
                Objects.requireNonNull(this.getSystemService(Context.ACTIVITY_SERVICE)))
                .getDeviceConfigurationInfo().getGlEsVersion();

        if (Double.parseDouble(openGlVersion) >= 3.0 &&
                arCoreHelper.isARCoreSupportedAndUpToDate()) {
            return true;
        } else {
            Toast.makeText(this, R.string.opengl_version_required, Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
    }

    /**
     * Sets up the necessary elements for the AR session by also
     * using {@link ARCoreHelper#setupSession()}.
     */
    private void setupSessionElements() {
        if (session == null) {
            arCoreHelper.setupSession();
            session = arCoreHelper.updatedSession();
        }
    }

    @Override
    public void onDialogContinueClick(DialogFragment dialog) {
        dialog.dismiss();
    }

    @Override
    public void onSurfaceCreated(CustomRender render) {
        if (render != null) {
            sceneRenderer.setupScene(render);
        }
    }

    @Override
    public void onSurfaceChanged(CustomRender render, int width, int height) {
        if (render != null) {
            displayRotationHelper.onSurfaceChanged(width, height);
            sceneRenderer.resizeFramebuffer(width, height);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CameraPermissionHelper.CAMERA_PERMISSION_CODE) {
            if (CameraPermissionHelper.hasNoCameraPermission(this) ||
                    CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                Toast.makeText(this, R.string.cam_permission_needed,
                        Toast.LENGTH_LONG).show();
                CameraPermissionHelper.launchPermissionSettings(this);
            }
        } else if (requestCode == LocationPermissionHelper.LOCATION_PERMISSION_CODE) {
            if (LocationPermissionHelper.hasNoFineLocationPermission(this) ||
                    LocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                Toast.makeText(this, R.string.loc_permission_needed,
                        Toast.LENGTH_LONG).show();
                LocationPermissionHelper.launchPermissionSettings(this);
            } else {
                setupSessionElements();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!sharedPreferences.getBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY, false)) {
            showPrivacyNoticeDialog();
        } else {
            if (!CameraPermissionHelper.hasNoCameraPermission(this)
                    && !LocationPermissionHelper.hasNoFineLocationPermission(this)
                    && systemSupportsNeededTechnology()) {
                setupSessionElements();
            } else {
                if (CameraPermissionHelper.hasNoCameraPermission(this)
                        && !CameraPermissionHelper
                        .shouldShowRequestPermissionRationale(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                }
                if (LocationPermissionHelper.hasNoFineLocationPermission(this)
                        && !LocationPermissionHelper
                        .shouldShowRequestPermissionRationale(this)) {
                    LocationPermissionHelper.requestFineLocationPermission(this);
                }
            }
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    /**
     * Displays the privacy notice dialog, which prompts the user to acknowledge the need for
     * using visual data.
     */
    private void showPrivacyNoticeDialog() {
        DialogFragment dialog = PrivacyNoticeDialogFragment.createDialog();
        dialog.show(getSupportFragmentManager(), PrivacyNoticeDialogFragment.class.getName());
    }

    @Override
    public void onPause() {
        super.onPause();

        if (session != null) {
            sceneRenderer.pauseSoundPool();
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

        sceneRenderer.releaseSoundPool();
        super.onDestroy();
    }
}
