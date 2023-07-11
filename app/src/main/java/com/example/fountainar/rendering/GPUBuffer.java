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

import android.opengl.GLES30;
import android.util.Log;

import java.nio.Buffer;

/**
 * A Buffer for the GPU usage.
 */
class GPUBuffer {
    private static final String TAG = GPUBuffer.class.getSimpleName();
    public static final int INT_SIZE = 4;
    public static final int FLOAT_SIZE = 4;
    private final int TARGET;
    private final int NUMBER_OF_BYTES_ARRAY;
    private final int[] BUFFER_ID = {0};

    private int size;
    private int capacity;

    public GPUBuffer(int target, int numberOfBytesPerEntry, Buffer entries) {
        if (entries != null) {
            if (!entries.isDirect()) {
                throw new IllegalArgumentException(
                        "If non-null, entries buffer must be a direct buffer");
            }
            if (entries.limit() == 0) {
                entries = null;
            }
        }

        this.TARGET = target;
        this.NUMBER_OF_BYTES_ARRAY = numberOfBytesPerEntry;

        if (entries == null) {
            this.size = 0;
            this.capacity = 0;
        } else {
            this.size = entries.limit();
            this.capacity = entries.limit();
        }

        try {
            GLES30.glBindVertexArray(0);
            GLError.maybeThrowGLException("Failed to unbind vertex array", "glBindVertexArray");

            GLES30.glGenBuffers(1, BUFFER_ID, 0);
            GLError.maybeThrowGLException("Failed to generate buffers", "glGenBuffers");

            GLES30.glBindBuffer(target, BUFFER_ID[0]);
            GLError.maybeThrowGLException("Failed to bind buffer object", "glBindBuffer");

            if (entries != null) {
                entries.rewind();
                GLES30.glBufferData(
                        target, entries.limit() * numberOfBytesPerEntry, entries,
                        GLES30.GL_DYNAMIC_DRAW);
            }

            GLError.maybeThrowGLException("Failed to populate buffer object", "glBufferData");
        } catch (Throwable t) {
            free();
            throw t;
        }
    }

    /**
     * Sets the buffer entries.
     *
     * @param entries The buffer entries to set.
     */
    public void set(Buffer entries) {
        if (entries == null || entries.limit() == 0) {
            size = 0;
            return;
        }

        if (!entries.isDirect()) {
            throw new IllegalArgumentException(
                    "If non-null, entries buffer must be a direct buffer");
        }

        GLES30.glBindBuffer(TARGET, BUFFER_ID[0]);
        GLError.maybeThrowGLException("Failed to bind vertex buffer object", "glBindBuffer");

        entries.rewind();

        if (entries.limit() <= capacity) {
            GLES30.glBufferSubData(TARGET, 0, entries.limit() * NUMBER_OF_BYTES_ARRAY,
                    entries);
            GLError.maybeThrowGLException("Failed to populate vertex buffer object",
                    "glBufferSubData");
            size = entries.limit();
        } else {
            GLES30.glBufferData(
                    TARGET, entries.limit() * NUMBER_OF_BYTES_ARRAY, entries,
                    GLES30.GL_DYNAMIC_DRAW);
            GLError.maybeThrowGLException("Failed to populate vertex buffer object",
                    "glBufferData");
            size = entries.limit();
            capacity = entries.limit();
        }
    }

    /**
     * Frees the buffer object.
     */
    public void free() {
        if (BUFFER_ID[0] != 0) {
            GLES30.glDeleteBuffers(1, BUFFER_ID, 0);
            GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free buffer object",
                    "glDeleteBuffers");
            BUFFER_ID[0] = 0;
        }
    }

    /**
     * Retrieves the buffer object ID.
     *
     * @return The buffer object ID.
     */
    public int getBufferId() {
        return BUFFER_ID[0];
    }

    /**
     * Retrieves the size of the buffer.
     *
     * @return The size of the buffer.
     */
    public int getSize() {
        return size;
    }
}
