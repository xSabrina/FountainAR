package com.example.fountainar.handlers;

import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fountainar.R;

public class BackPressedHandler {

    private static final String TAG = BackPressedHandler.class.getSimpleName();

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

    private static void showBackDialog(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.no_backing);
        builder.setMessage(R.string.study_not_backable);
        builder.setPositiveButton("OK", (dialog, which) -> {
        });
        builder.create().show();
    }
}
