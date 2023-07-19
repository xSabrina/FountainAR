/*
 * Copyright 2020 Google LLC
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
package com.example.fountainar.rendering;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the Occlusion option setting and shared preferences.
 */
public class DepthSettings {
    public static final String SHARED_PREFERENCES_ID = "SHARED_PREFERENCES_OCCLUSION_OPTIONS";
    public static final String SHARED_PREFERENCES_SHOW_DEPTH_ENABLE_DIALOG_OOBE =
            "show_depth_enable_dialog_oobe";
    public static final String SHARED_PREFERENCES_USE_DEPTH_FOR_OCCLUSION =
            "use_depth_for_occlusion";
    public boolean depthColorVisualizationEnabled = false;
    public boolean useDepthForOcclusion = false;
    private SharedPreferences sharedPreferences;

    /**
     * Initializes the current settings based on when the app was last used.
     */
    public void onCreate(Context context) {
        sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_ID,
                Context.MODE_PRIVATE);
        useDepthForOcclusion =
                sharedPreferences.getBoolean(SHARED_PREFERENCES_USE_DEPTH_FOR_OCCLUSION,
                        false);
    }

    /**
     * Retrieves whether depth-based occlusion is enabled.
     */
    public boolean useDepthForOcclusion() {
        return useDepthForOcclusion;
    }

    public void setUseDepthForOcclusion(boolean enable) {
        if (enable == useDepthForOcclusion) {
            return;
        }

        useDepthForOcclusion = enable;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SHARED_PREFERENCES_USE_DEPTH_FOR_OCCLUSION, useDepthForOcclusion);
        editor.apply();
    }

    /**
     * Retrieves whether to render the depth map visualization instead of the camera feed.
     */
    public boolean depthColorVisualizationEnabled() {
        return depthColorVisualizationEnabled;
    }

    public void setDepthColorVisualizationEnabled(boolean depthColorVisualizationEnabled) {
        this.depthColorVisualizationEnabled = depthColorVisualizationEnabled;
    }

    /**
     * Determines if the initial prompt to use depth-based occlusion should be shown.
     */
    public boolean shouldShowDepthEnableDialog() {
        boolean showDialog =
                sharedPreferences.getBoolean(SHARED_PREFERENCES_SHOW_DEPTH_ENABLE_DIALOG_OOBE,
                        true);

        if (showDialog) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(SHARED_PREFERENCES_SHOW_DEPTH_ENABLE_DIALOG_OOBE, false);
            editor.apply();
        }

        return showDialog;
    }
}
