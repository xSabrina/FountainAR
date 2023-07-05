package com.example.fountainar.helpers;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.example.fountainar.R;
import com.example.fountainar.activities.ARActivity;
import com.example.fountainar.fragments.VpsAvailabilityNoticeDialogFragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.ar.core.Anchor;
import com.google.ar.core.Earth;
import com.google.ar.core.GeospatialPose;
import com.google.ar.core.ResolveAnchorOnTerrainFuture;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.VpsAvailability;
import com.google.ar.core.VpsAvailabilityFuture;
import com.google.ar.core.exceptions.ResourceExhaustedException;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.TimeUnit;

public class GeospatialHelper {

    private static final String TAG = GeospatialHelper.class.getSimpleName();

    private static final double latitude = 49.00050454115168;
    private static final double longitude = 12.093612000040514;
    private static final double LOCALIZING_HORIZONTAL_ACCURACY_THRESHOLD_METERS = 10;
    private static final double LOCALIZING_ORIENTATION_YAW_ACCURACY_THRESHOLD_DEGREES = 15;
    private static final double LOCALIZED_HORIZONTAL_ACCURACY_HYSTERESIS_METERS = 10;
    private static final double LOCALIZED_ORIENTATION_YAW_ACCURACY_HYSTERESIS_DEGREES = 10;
    private static final int LOCALIZING_TIMEOUT_SECONDS = 180;
    public static long localizingStartTimestamp;
    public static State state = State.UNINITIALIZED;
    private final FusedLocationProviderClient fusedLocationClient;
    private final QuizHelper quizHelper;
    private final Activity activity;
    private Session session;

    public GeospatialHelper(Activity activity) {
        this.activity = activity;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
        quizHelper = new QuizHelper(activity);
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public void getLastLocation() {
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
                activity.getMainExecutor()
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

    private void showVpsNotAvailabilityNoticeDialog() {
        DialogFragment dialog = VpsAvailabilityNoticeDialogFragment.createDialog();
        FragmentActivity fragmentActivity = (FragmentActivity) activity;
        dialog.show(fragmentActivity.getSupportFragmentManager(),
                VpsAvailabilityNoticeDialogFragment.class.getName());
    }

    /**
     * Change behavior depending on the current {@link State} of the application.
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
     * Handle the updating for {State.PRE_TRACKING}. In this state, wait for {@link Earth} to
     * have {TrackingState.TRACKING}. If it hasn't been enabled by now, then we've encountered
     * an unrecoverable {State.EARTH_STATE_ERROR}.
     */
    private void updatePreTrackingState(Earth earth) {
        if (earth.getTrackingState() == TrackingState.TRACKING) {
            state = State.LOCALIZING;
        }
    }

    /**
     * Handle the updating for {@link State#LOCALIZING}. In this state, wait for the horizontal and
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
                quizHelper.setupQuiz();
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
     * Handle the updating for {@link State#LOCALIZED}. In this state, check the accuracy for
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
     * Place an anchor when geospatial accuracy is high enough.
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
     * Create a terrain anchor at given world coordinates using earth object.
     */
    private void createTerrainAnchor(Earth earth, GeospatialPose geospatialPose) {
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
                (anchor, state) -> {
                    if (state == Anchor.TerrainAnchorState.SUCCESS) {
                        ARActivity.anchor = anchor;
                    } else {
                        ARActivity.snackbarHelper.showMessageWithDismiss(activity,
                                activity.getString(R.string.error_create_anchor));
                        Log.e(TAG, "Exception creating terrain anchor");
                    }
                });
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
