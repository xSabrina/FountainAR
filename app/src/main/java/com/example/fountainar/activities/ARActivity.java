package com.example.fountainar.activities;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import com.example.fountainar.helpers.SnackbarHelper;
import com.example.fountainar.rendering.CustomRender;
import com.example.fountainar.rendering.SceneRenderer;
import com.google.ar.core.Anchor;
import com.google.ar.core.Earth;
import com.google.ar.core.Session;

import org.checkerframework.checker.units.qual.A;

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
    private GLSurfaceView surfaceView;
    private SceneRenderer sceneRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences(ALLOW_GEOSPATIAL_ACCESS_KEY, Context.MODE_PRIVATE);
        setContentView(R.layout.activity_ar);
        arCoreHelper = new ARCoreHelper(this);
        geospatialHelper = new GeospatialHelper(this);
        displayRotationHelper = new DisplayRotationHelper(this);
        BackPressedHandler.setupBackPressedCallback(this);
    }

    /**
     * Checks if the system supports the required technology (OpenGL ES 3.0 and ARCore).
     * If the system meets the requirements, returns true. Otherwise, displays a toast message
     * and finishes the activity.
     *
     * @return true if the system supports the required technology, false otherwise.
     */
    private boolean systemSupportsNeededTechnology() {
        String openGlVersion = ((ActivityManager)
                Objects.requireNonNull(this.getSystemService(Context.ACTIVITY_SERVICE)))
                .getDeviceConfigurationInfo().getGlEsVersion();

        if (Double.parseDouble(openGlVersion) >= 3.0 &&
                arCoreHelper.isARCoreSupportedAndUpToDate()) {
            return true;
        } else {
            Toast.makeText(this, R.string.opengl_version_required,
                            Toast.LENGTH_SHORT)
                    .show();
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

    @Override
    public void onSurfaceCreated(CustomRender render) {
        sceneRenderer.setupScene(render);
    }

    @Override
    public void onSurfaceChanged(CustomRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        sceneRenderer.resizeFramebuffer(width, height);
    }

    /**
     * Callback when a new frame needs to be drawn. Draws the scene, updates the session if needed,
     * and updates the geospatial state if Earth is available.
     *
     * @param render The CustomRender instance.
     */
    @Override
    public void onDrawFrame(CustomRender render) {
        if (session == null) {
            return;
        }

        sceneRenderer.drawScene(session, render, anchor);
        displayRotationHelper.updateSessionIfNeeded(session);
        Earth earth = session.getEarth();

        if (earth != null) {
            geospatialHelper.updateGeospatialState(earth);
        }
    }

    /**
     * Called when the activity is resumed from the paused state.
     * Initializes the AR session if the user has granted geospatial access,
     * otherwise shows the privacy notice dialog.
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (sharedPreferences.getBoolean(ALLOW_GEOSPATIAL_ACCESS_KEY, false)) {
            if (CameraPermissionHelper.hasNoCameraPermission(this)) {
                CameraPermissionHelper.requestCameraPermission(this);
            } else {
                if (systemSupportsNeededTechnology()) {
                    setupARSession();
                }
            }
        } else {
            showPrivacyNoticeDialog();

            if (systemSupportsNeededTechnology()) {
                setupARSession();
            }

            surfaceView.onResume();
            displayRotationHelper.onResume();
        }


    }

    private void setupARSession() {
        surfaceView = findViewById(R.id.surface_view);
        new CustomRender(surfaceView, this, getAssets());
        sceneRenderer = new SceneRenderer(this);
        arCoreHelper.setupSession();
        session = arCoreHelper.updatedSession();
    }

    private void showPrivacyNoticeDialog() {
        DialogFragment dialog = PrivacyNoticeDialogFragment.createDialog();
        dialog.show(getSupportFragmentManager(), PrivacyNoticeDialogFragment.class.getName());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CameraPermissionHelper.CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupARSession();
            } else {
                Toast.makeText(this, R.string.cam_permission_needed, Toast.LENGTH_LONG)
                        .show();
                CameraPermissionHelper.launchPermissionSettings(this);
            }
        }
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
