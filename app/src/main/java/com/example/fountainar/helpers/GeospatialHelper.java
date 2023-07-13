package com.example.fountainar.helpers;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.example.fountainar.activities.ARActivity;
import com.example.fountainar.fragments.VpsAvailabilityNoticeDialogFragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.ar.core.Anchor;
import com.google.ar.core.Earth;
import com.google.ar.core.Frame;
import com.google.ar.core.GeospatialPose;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.VpsAvailability;
import com.google.ar.core.VpsAvailabilityFuture;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * Helper for geospatial operations and ARCore functionality.
 */
public class GeospatialHelper {

    private static final String TAG = GeospatialHelper.class.getSimpleName();
    private static final double LATITUDE = 49.000457247612424;
    private static final double LONGITUDE = 12.092906819740683;
    private static final double LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS = 10;
    private static final double LOCALIZING_ORIENTATION_YAW_ACCURACY_THRESHOLD_DEGREES = 15;
    private static final double LOCALIZED_HORIZONTAL_ACCURACY_HYSTERESIS_METERS = 10;
    private static final double LOCALIZED_ORIENTATION_YAW_ACCURACY_HYSTERESIS_DEGREES = 10;
    private static final int LOCALIZING_TIMEOUT_SECONDS = 180;
    public static long localizingStartTimestamp;
    public static State state = State.UNINITIALIZED;
    private final Activity ACTIVITY;
    private final QuizHelper QUIZHELPER;
    private final FusedLocationProviderClient FUSED_LOCATION_CLIENT;
    private Session session;

    public GeospatialHelper(Activity activity) {
        this.ACTIVITY = activity;
        FUSED_LOCATION_CLIENT = LocationServices.getFusedLocationProviderClient(activity);
        QUIZHELPER = new QuizHelper(activity);
    }

    public void setSession(Session session) {
        this.session = session;
    }

    /**
     * Retrieves the last known device location using the fused location provider.
     * It then checks the availability of VPS (Visual Positioning System) based on the location.
     */
    public void getLastLocation() {
        try {
            FUSED_LOCATION_CLIENT
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

    /**
     * Checks the availability of VPS (Visual Positioning System) based on the given latitude and longitude.
     *
     * @param latitude  The latitude coordinate.
     * @param longitude The longitude coordinate.
     */
    public void checkVpsAvailability(double latitude, double longitude) {
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
                ACTIVITY.getMainExecutor()
        );
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

    /**
     * Shows a dialog indicating that VPS (Visual Positioning System) is not available.
     * This dialog is displayed when VPS availability check fails.
     */
    private void showVpsNotAvailabilityNoticeDialog() {
        DialogFragment dialog = VpsAvailabilityNoticeDialogFragment.createDialog();
        FragmentActivity fragmentActivity = (FragmentActivity) ACTIVITY;
        dialog.show(fragmentActivity.getSupportFragmentManager(),
                VpsAvailabilityNoticeDialogFragment.class.getName());
    }

    /**
     * Updates the geospatial state of the application based on the current state of the Earth
     * object. It changes the behavior of the application depending on the current state.
     *
     * @param earth The Earth object containing information about geospatial tracking.
     */
    public void updateGeospatialState(Earth earth) {
        if (earth.getEarthState() != Earth.EarthState.ENABLED) {
            state = State.EARTH_STATE_ERROR;
            Log.e(TAG, earth.getEarthState().toString());
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

        if (geospatialPose.getHorizontalAccuracy() <=
                LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS
                && geospatialPose.getOrientationYawAccuracy()
                <= LOCALIZING_ORIENTATION_YAW_ACCURACY_THRESHOLD_DEGREES) {
            state = State.LOCALIZED;

            if (ARActivity.anchor == null) {
                placeAnchor(earth);
            }

            return;
        }

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
     * Creates a terrain anchor at given world coordinates using earth object.
     */
    private void placeAnchor(Earth earth) {
        if (earth.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        GeospatialPose geospatialPose = earth.getCameraGeospatialPose();
        Frame frame;

        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            throw new RuntimeException(e);
        }

        List<Plane> detectedPlanes = frame.getUpdatedTrackables(Plane.class)
                .stream()
                .filter(plane -> plane.getTrackingState() == TrackingState.TRACKING)
                .collect(Collectors.toList());

        if (!detectedPlanes.isEmpty()) {
            Plane plane = detectedPlanes.get(0);
            Pose planePose = plane.getCenterPose();
            Anchor planeAnchor = plane.createAnchor(planePose);

            double latitude = geospatialPose.getLatitude();
            double longitude = geospatialPose.getLongitude();

            float[] quaternion = geospatialPose.getEastUpSouthQuaternion();
            earth.resolveAnchorOnTerrainAsync(
                    latitude,
                    longitude,
                    0.0f,
                    quaternion[0],
                    quaternion[1],
                    quaternion[2],
                    quaternion[3],
                    (earthAnchor, state) -> {
                        if (state == Anchor.TerrainAnchorState.SUCCESS) {
                            Pose combinedPose = combinePoses(planeAnchor.getPose(), earthAnchor.getPose());
                            ARActivity.anchor = session.createAnchor(combinedPose);
                        }
                    });

            QUIZHELPER.setupQuiz();
        } else {
            placeAnchor(earth);
        }
    }

    /**
     * Combines two poses by applying the transformation of the second pose to the first pose.
     */
    private Pose combinePoses(Pose pose1, Pose pose2) {
        float[] combinedTranslation = new float[3];
        float[] combinedRotationQuaternion = new float[4];

        // Combine translations
        float[] translation1 = pose1.getTranslation();
        float[] translation2 = pose2.getTranslation();
        for (int i = 0; i < 3; i++) {
            combinedTranslation[i] = translation1[i] + translation2[i];
        }

        // Combine rotations
        float[] rotationQuaternion1 = pose1.getRotationQuaternion();
        float[] rotationQuaternion2 = pose2.getRotationQuaternion();
        combinedRotationQuaternion[0] = rotationQuaternion1[0] + rotationQuaternion2[0];
        combinedRotationQuaternion[1] = rotationQuaternion1[1] + rotationQuaternion2[1];
        combinedRotationQuaternion[2] = rotationQuaternion1[2] + rotationQuaternion2[2];
        combinedRotationQuaternion[3] = rotationQuaternion1[3] + rotationQuaternion2[3];

        return new Pose(combinedTranslation, combinedRotationQuaternion);
    }


    public enum State {
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
}
