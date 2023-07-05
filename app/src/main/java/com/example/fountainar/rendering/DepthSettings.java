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
 * Settings for the occlusion option and shared preferences.
 */
public class DepthSettings {
    public static final String SHARED_PREFERENCES_ID = "SHARED_PREFERENCES_OCCLUSION_OPTIONS";
    public static final String SHARED_PREFERENCES_USE_DEPTH_FOR_OCCLUSION =
            "use_depth_for_occlusion";
    private boolean useDepthForOcclusion = false;
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

    public void setUseDepthForOcclusion(boolean enable) {
        if (enable == useDepthForOcclusion) {
            return;
        }

        useDepthForOcclusion = enable;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SHARED_PREFERENCES_USE_DEPTH_FOR_OCCLUSION, useDepthForOcclusion);
        editor.apply();
    }
}
