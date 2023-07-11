package com.example.fountainar.helpers;

import android.app.Activity;
import android.widget.TextView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

/**
 * Helper for displaying Snackbar messages with dismiss functionality.
 * This class provides methods to show different types of Snackbars and define their dismiss
 * behavior. Only one Snackbar can be displayed at a time.
 */
public final class SnackbarHelper {
    private static final int BACKGROUND_COLOR = 0xbf323232;
    private final int MAX_LINES = 2;

    private Snackbar snackbar;
    public boolean isDismissed = false;

    public SnackbarHelper() {
    }

    /**
     * Displays a Snackbar message with a dismiss button.
     *
     * @param activity The activity where the Snackbar should be displayed.
     * @param message  The message to be shown in the Snackbar.
     */
    public void showMessageWithDismiss(Activity activity, String message) {
        show(activity, message, SnackbarHelper.DismissBehavior.SHOW);
    }

    /**
     * Displays a Snackbar error message and finishes the activity on dismissal.
     *
     * @param activity     The activity where the Snackbar should be displayed.
     * @param errorMessage The error message to be shown in the Snackbar.
     */
    public void showError(Activity activity, String errorMessage) {
        show(activity, errorMessage, SnackbarHelper.DismissBehavior.FINISH);
    }

    /**
     * Displays a Snackbar with the specified message and dismiss behavior.
     *
     * @param activity        The activity where the Snackbar should be displayed.
     * @param message         The message to be shown in the Snackbar.
     * @param dismissBehavior The dismiss behavior of the Snackbar.
     */
    private void show(Activity activity, String message,
                      SnackbarHelper.DismissBehavior dismissBehavior) {
        activity.runOnUiThread(() -> {
            snackbar = Snackbar.make(activity.findViewById(android.R.id.content), message,
                    Snackbar.LENGTH_INDEFINITE);
            snackbar.getView().setBackgroundColor(BACKGROUND_COLOR);
            if (dismissBehavior != SnackbarHelper.DismissBehavior.HIDE) {
                snackbar.setAction("Okay", v -> snackbar.dismiss());
                if (dismissBehavior == SnackbarHelper.DismissBehavior.FINISH) {
                    snackbar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        @Override
                        public void onDismissed(Snackbar transientBottomBar, int event) {
                            super.onDismissed(transientBottomBar, event);
                            isDismissed = true;
                        }
                    });
                }
            }
            ((TextView) snackbar.getView().findViewById(
                    com.google.android.material.R.id.snackbar_text)).setMaxLines(MAX_LINES);
            snackbar.show();
        });
    }


    /**
     * Enumerates the possible dismiss behaviors for the Snackbar.
     * HIDE: Snackbar will be hidden without any action.
     * SHOW: Snackbar will display a dismiss button.
     * FINISH: Snackbar will display a dismiss button and finish the activity on dismissal.
     */
    private enum DismissBehavior {
        HIDE,
        SHOW,
        FINISH
    }
}