package com.example.fountainar.helpers;

import android.app.Activity;
import android.widget.TextView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

public final class SnackbarHelper {
    private Snackbar snackbar;
    private static final int BACKGROUND_COLOR = 0xbf323232;
    public boolean isDismissed = false;

    public SnackbarHelper() {
    }

    public void showMessageWithDismiss(Activity activity, String message) {
        show(activity, message, SnackbarHelper.DismissBehavior.SHOW);
    }

    public void showError(Activity activity, String errorMessage) {
        show(activity, errorMessage, SnackbarHelper.DismissBehavior.FINISH);
    }

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
                    com.google.android.material.R.id.snackbar_text)).setMaxLines(maxLines);
            snackbar.show();
        });
    }

    private enum DismissBehavior {
        HIDE,
        SHOW,
        FINISH
    }

    private final int maxLines = 2;
}