package com.example.fountainar.helpers;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;

import com.example.fountainar.R;
import com.example.fountainar.activities.ARActivity;
import com.example.fountainar.rendering.DepthSettings;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.FineLocationPermissionNotGrantedException;
import com.google.ar.core.exceptions.GooglePlayServicesLocationLibraryNotLinkedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.core.exceptions.UnsupportedConfigurationException;

public class ARCoreHelper {

    private static final String TAG = ARCoreHelper.class.getSimpleName();
    private final Activity activity;
    private final DepthSettings depthSettings = new DepthSettings();
    public Session session = ARActivity.session;
    private boolean installRequested = false;

    public ARCoreHelper(Activity activity) {
        this.activity = activity;
        depthSettings.onCreate(activity);
    }

    /**
     * @return ARCore is installed and up to date.
     */
    public boolean isARCoreSupportedAndUpToDate() {
        ArCoreApk.Availability availability =
                ArCoreApk.getInstance().checkAvailability(activity);

        switch (availability) {
            case SUPPORTED_INSTALLED:
                return true;

            case SUPPORTED_APK_TOO_OLD:
            case SUPPORTED_NOT_INSTALLED:
                try {
                    ArCoreApk.InstallStatus installStatus = ArCoreApk.getInstance().
                            requestInstall(activity, true);
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
            case UNKNOWN_CHECKING:
            case UNKNOWN_ERROR:
            case UNKNOWN_TIMED_OUT:
                return false;
        }

        return false;
    }

    /**
     * Creates an ARCore session.
     */
    public void setupSession() {
        try {
            if (session == null) {
                createNewSession();
                ARActivity.geospatialHelper.setSession(session);
            } else {
                ARActivity.geospatialHelper.getLastLocation();
            }

            configureSession();
            session.resume();
        } catch (Exception e) {
            String message;

            if (e instanceof CameraNotAvailableException) {
                message = String.valueOf(R.string.cam_not_available);
            } else if (e instanceof GooglePlayServicesLocationLibraryNotLinkedException) {
                message = String.valueOf(R.string.proguard_error);
            } else if (e instanceof FineLocationPermissionNotGrantedException) {
                message = String.valueOf(R.string.no_permission_fine_location);
            } else if (e instanceof UnsupportedConfigurationException) {
                message = String.valueOf(R.string.geospatial_not_supported);
            } else if (e instanceof SecurityException) {
                message = String.valueOf(R.string.cam_or_network_error);
            } else {
                message = String.valueOf(R.string.session_create_or_config_error);
            }

            session = null;
            ARActivity.snackbarHelper.showError(activity, message);
            Log.e(TAG, message, e);
        }
    }

    private void createNewSession() {
        try {
            if (ArCoreApk.getInstance().requestInstall(activity, !installRequested)
                    == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true;
                return;
            }

            if (CameraPermissionHelper.hasNoCameraPermission(activity)) {
                CameraPermissionHelper.requestCameraPermission(activity);
                return;
            }

            if (LocationPermissionHelper.hasNoFineLocationPermission(activity)) {
                LocationPermissionHelper.requestFineLocationPermission(activity);
                return;
            }

            session = new Session(activity);
        } catch (Exception e) {
            String message;

            if (e instanceof UnavailableArcoreNotInstalledException
                    || e instanceof UnavailableUserDeclinedInstallationException) {
                message = String.valueOf(R.string.install_arcore);
            } else if (e instanceof UnavailableApkTooOldException) {
                message = String.valueOf(R.string.update_arcore);
            } else if (e instanceof UnavailableSdkTooOldException) {
                message = String.valueOf(R.string.update_app);
            } else if (e instanceof UnavailableDeviceNotCompatibleException) {
                message = String.valueOf(R.string.ar_not_supported);
            } else {
                message = String.valueOf(R.string.ar_session_failed);
            }

            ARActivity.snackbarHelper.showError(activity, message);
            Log.e(TAG, "Error creating session", e);
        }
    }

    /**
     * Configures session with featured settings, if GeospatialMode is supported.
     */
    private void configureSession() {
        if (!session.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
            GeospatialHelper.state = GeospatialHelper.State.UNSUPPORTED;
            return;
        }

        GeospatialHelper.state = GeospatialHelper.State.PRE_TRACKING;
        GeospatialHelper.localizingStartTimestamp = System.currentTimeMillis();

        Config config = session.getConfig();
        config.setGeospatialMode(Config.GeospatialMode.ENABLED);
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        depthSettings.setUseDepthForOcclusion(false);
        depthSettings.setDepthColorVisualizationEnabled(false);
        session.configure(config);
    }

    public Session updatedSession() {
        return session;
    }

}
