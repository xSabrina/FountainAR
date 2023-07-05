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

import android.annotation.SuppressLint;
import android.opengl.GLES30;
import android.opengl.GLException;
import android.opengl.GLU;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Module for handling OpenGL errors.
 */
public class GLError {

    private GLError() {
    }

    /**
     * Throws a {@link GLException} if a GL error occurred.
     *
     * @param reason The reason for the GL error.
     * @param api    The name of the GL API function.
     * @throws GLException If a GL error occurred.
     */
    public static void maybeThrowGLException(String reason, String api) {
        List<Integer> errorCodes = getGlErrors();

        if (errorCodes != null) {
            throw new GLException(errorCodes.get(0), formatErrorMessage(reason, api, errorCodes));
        }
    }

    /**
     * Logs a message with the given logcat priority if a GL error occurred.
     *
     * @param priority The logcat priority level.
     * @param tag      The tag to identify the log message.
     * @param reason   The reason for the GL error.
     * @param api      The name of the GL API function.
     */
    public static void maybeLogGLError(int priority, String tag, String reason, String api) {
        List<Integer> errorCodes = getGlErrors();

        if (errorCodes != null) {
            Log.println(priority, tag, formatErrorMessage(reason, api, errorCodes));
        }
    }

    /**
     * Formats the error message with the GL error codes.
     *
     * @param reason     The reason for the GL error.
     * @param api        The name of the GL API function.
     * @param errorCodes The list of GL error codes.
     * @return The formatted error message.
     */
    @SuppressLint("DefaultLocale")
    private static String formatErrorMessage(String reason, String api, List<Integer> errorCodes) {
        StringBuilder builder = new StringBuilder(String.format("%s: %s: ", reason, api));
        Iterator<Integer> iterator = errorCodes.iterator();

        while (iterator.hasNext()) {
            int errorCode = iterator.next();
            builder.append(String.format("%s (%d)", GLU.gluErrorString(errorCode), errorCode));

            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }

        return builder.toString();
    }

    /**
     * Retrieves the GL error codes.
     *
     * @return The list of GL error codes, or null if no error occurred.
     */
    private static List<Integer> getGlErrors() {
        int errorCode = GLES30.glGetError();

        if (errorCode == GLES30.GL_NO_ERROR) {
            return null;
        }

        List<Integer> errorCodes = new ArrayList<>();
        errorCodes.add(errorCode);

        while (true) {
            errorCode = GLES30.glGetError();

            if (errorCode == GLES30.GL_NO_ERROR) {
                break;
            }

            errorCodes.add(errorCode);
        }

        return errorCodes;
    }
}
