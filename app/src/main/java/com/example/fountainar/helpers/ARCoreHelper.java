package com.example.fountainar.helpers;

import android.app.Activity;
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

/**
 * Helper for ARCore functionality, including ARCore installation check, session setup,
 * and session configuration.
 */
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
     * Checks if ARCore is supported and up to date on the device.
     *
     * @return true if ARCore is installed and up to date, false otherwise.
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
                    ArCoreApk.InstallStatus installStatus =
                            ArCoreApk.getInstance().
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
                return false;

            case UNKNOWN_CHECKING:

            case UNKNOWN_ERROR:

            case UNKNOWN_TIMED_OUT:

        }
        return false;
    }

    /**
     * Sets up the ARCore session by creating a new session, configuring it, and resuming it.
     * Displays error messages if any exceptions occur during the setup process.
     */
    public void setupSession() {
        Exception exception = null;
        String message = null;

        if (session == null) {
            createNewSession();
            ARActivity.geospatialHelper.setSession(session);
        } else {
            ARActivity.geospatialHelper.getLastLocation();
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
            ARActivity.snackbarHelper.showError(activity, message);
            Log.e(TAG, String.valueOf(R.string.session_create_or_config_error), exception);
        }
    }

    /**
     * Creates a new ARCore session if one doesn't exist. Handles different scenarios such as
     * requesting ARCore installation, checking camera and location permissions, and handling
     * exceptions related to ARCore installation and device compatibility.
     */
    private void createNewSession() {
        Exception exception = null;
        String message = null;

        try {
            switch (ArCoreApk.getInstance().requestInstall(activity,
                    !installRequested)) {
                case INSTALL_REQUESTED:
                    installRequested = true;
                    return;
                case INSTALLED:
                    break;
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
            ARActivity.snackbarHelper.showError(activity, message);
            Log.e(TAG, "Error creating session", exception);
        }
    }

    /**
     * Configures the ARCore session with featured settings, if GeospatialMode is supported.
     * Sets the GeospatialHelper state and configures the session with the desired settings.
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
        config.setDepthMode(Config.DepthMode.AUTOMATIC);
        depthSettings.setUseDepthForOcclusion(true);
        session.configure(config);
    }

    public Session updatedSession() {
        return session;
    }

}