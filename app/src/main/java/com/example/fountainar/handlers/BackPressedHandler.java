package com.example.fountainar.handlers;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fountainar.R;

/**
 * A handler for back button press events and displaying a confirmation dialog.
 */
public class BackPressedHandler {

    private static final String TAG = BackPressedHandler.class.getSimpleName();

    /**
     * Sets up a callback for the back button press event.
     *
     * @param activity The AppCompatActivity where the callback is set up.
     */
    public static void setupBackPressedCallback(AppCompatActivity activity) {
        OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.d(TAG, "onBackPressedCallback: handleOnBackPressed");
                showBackDialog(activity);
            }
        };

        activity.getOnBackPressedDispatcher().addCallback(activity, onBackPressedCallback);
    }

    /**
     * Displays a dialog when the back button is pressed.
     *
     * @param activity The Activity where the dialog is displayed.
     */
    private static void showBackDialog(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.no_backing);
        builder.setMessage(R.string.study_not_backable);
        builder.setPositiveButton("OK", (dialog, which) -> {
        });
        builder.create().show();
    }
}
