/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.fountainar.helpers;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;

/**
 * Helper to ask for camera permission.
 */
public final class StoragePermissionHelper {

    private static final int READ_PERMISSION_CODE = 0;
    private static final int WRITE_PERMISSION_CODE = 1;
    private static final String READ_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE;
    private static final String WRITE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    /**
     * Check, if we have the necessary reading permissions for this app.
     */
    public static boolean hasReadPermission(Activity activity) {
        return ActivityCompat.checkSelfPermission(activity, READ_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check, if we have the necessary writing permissions for this app.
     */
    public static boolean hasWritePermission(Activity activity) {
        return ActivityCompat.checkSelfPermission(activity, WRITE_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Ask for reading storage permission.
     */
    public static void requestReadPermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity, new String[]{READ_PERMISSION}, READ_PERMISSION_CODE);
    }

    /**
     * Ask for writing storage permission.
     */
    public static void requestWritePermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity, new String[]{WRITE_PERMISSION}, WRITE_PERMISSION_CODE);
    }

    /**
     * Check, if we need to show the rationale for this permission.
     */
    public static boolean shouldShowRequestReadPermissionRationale(Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, READ_PERMISSION);
    }

    /**
     * Launch application settings to grant permission.
     */
    public static void launchReadPermissionSettings(Activity activity) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }

    /**
     * Check, if we need to show the rationale for this permission.
     */
    public static boolean shouldShowRequestWritePermissionRationale(Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, WRITE_PERMISSION);
    }

    /**
     * Launch application settings to grant permission.
     */
    public static void launchWritePermissionSettings(Activity activity) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }

}
