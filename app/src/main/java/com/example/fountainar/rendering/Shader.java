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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.GLException;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * A GPU shader representing the state of its associated uniforms, and some additional draw state.
 */
public class Shader implements Closeable {

    private static final String TAG = Shader.class.getSimpleName();
    private final Map<Integer, Uniform> UNIFORMS = new HashMap<>();
    private final Map<String, Integer> UNIFORM_LOCATIONS = new HashMap<>();
    private final Map<Integer, String> UNIFORM_NAMES = new HashMap<>();

    int vertexShaderId = 0;
    int fragmentShaderId = 0;
    private int programId;
    private int maxTextureUnit = 0;
    private boolean depthTest = true;
    private boolean depthWrite = true;
    private boolean blendingEnabled = false;
    private BlendFactor sourceRgbBlend = BlendFactor.ONE;
    private BlendFactor destRgbBlend = BlendFactor.ZERO;
    private BlendFactor sourceAlphaBlend = BlendFactor.ONE;
    private BlendFactor destAlphaBlend = BlendFactor.ZERO;

    /**
     * Constructs a {@link Shader} given the shader code.
     *
     * @param defines A map of shader precompiler symbols to be defined with the given names and
     *                values
     */
    public Shader(String vertexShaderCode, String fragmentShaderCode, Map<String, String> defines) {
        String definesCode = createShaderDefinesCode(defines);

        try {
            vertexShaderId =
                    createShader(
                            GLES30.GL_VERTEX_SHADER, insertShaderDefinesCode(vertexShaderCode,
                                    definesCode));
            fragmentShaderId =
                    createShader(
                            GLES30.GL_FRAGMENT_SHADER, insertShaderDefinesCode(fragmentShaderCode,
                                    definesCode));

            programId = GLES30.glCreateProgram();
            GLError.maybeThrowGLException("Shader program creation failed", "glCreateProgram");
            GLES30.glAttachShader(programId, vertexShaderId);
            GLError.maybeThrowGLException("Failed to attach vertex shader", "glAttachShader");
            GLES30.glAttachShader(programId, fragmentShaderId);
            GLError.maybeThrowGLException("Failed to attach fragment shader", "glAttachShader");
            GLES30.glLinkProgram(programId);
            GLError.maybeThrowGLException("Failed to link shader program", "glLinkProgram");
            final int[] linkStatus = new int[1];
            GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0);

            if (linkStatus[0] == GLES30.GL_FALSE) {
                String infoLog = GLES30.glGetProgramInfoLog(programId);
                GLError.maybeLogGLError(
                        Log.WARN, TAG, "Failed to retrieve shader program info log",
                        "glGetProgramInfoLog");
                throw new GLException(0, "Shader link failed: " + infoLog);
            }
        } catch (Throwable t) {
            close();
            throw t;
        } finally {
            if (vertexShaderId != 0) {
                GLES30.glDeleteShader(vertexShaderId);
                GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free vertex shader",
                        "glDeleteShader");
            }

            if (fragmentShaderId != 0) {
                GLES30.glDeleteShader(fragmentShaderId);
                GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free fragment shader",
                        "glDeleteShader");
            }
        }
    }

    /**
     * Creates a {@link Shader} from the given asset file names.
     *
     * <p>The file contents are interpreted as UTF-8 text.
     *
     * @param defines A map of shader precompiler symbols to be defined with the given names and
     *                values
     */
    public static Shader createFromAssets(
            CustomRender render,
            String vertexShaderFileName,
            String fragmentShaderFileName,
            Map<String, String> defines)
            throws IOException {
        AssetManager assets = render.getAssets();

        return new Shader(inputStreamToString(assets.open(vertexShaderFileName)),
                inputStreamToString(assets.open(fragmentShaderFileName)),
                defines);
    }

    /**
     * Creates a shader of the specified type and compiles it with the provided shader code.
     *
     * @param type The type of the shader, such as GLES30.GL_VERTEX_SHADER or GLES30.GL_FRAGMENT_SHADER.
     * @param code The shader code to be compiled.
     * @return The ID of the compiled shader.
     * @throws GLException If the shader compilation fails.
     */
    private static int createShader(int type, String code) {
        int shaderId = GLES30.glCreateShader(type);
        GLError.maybeThrowGLException("Shader creation failed", "glCreateShader");
        GLES30.glShaderSource(shaderId, code);
        GLError.maybeThrowGLException("Shader source failed", "glShaderSource");
        GLES30.glCompileShader(shaderId);
        GLError.maybeThrowGLException("Shader compilation failed", "glCompileShader");

        final int[] compileStatus = new int[1];
        GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0);

        if (compileStatus[0] == GLES30.GL_FALSE) {
            String infoLog = GLES30.glGetShaderInfoLog(shaderId);
            GLError.maybeLogGLError(
                    Log.WARN, TAG, "Failed to retrieve shader info log",
                    "glGetShaderInfoLog");
            GLES30.glDeleteShader(shaderId);
            GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free shader",
                    "glDeleteShader");
            throw new GLException(0, "Shader compilation failed: " + infoLog);
        }

        return shaderId;
    }

    /**
     * Creates a string containing shader precompiler defines based on the given map of defines.
     *
     * @param defines A map of shader precompiler symbols to be defined with their names and values.
     * @return The string representation of the shader precompiler defines.
     */
    private static String createShaderDefinesCode(Map<String, String> defines) {
        if (defines == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, String> entry : defines.entrySet()) {
            builder.append("#define ").append(entry.getKey()).append(" ").append(entry.getValue())
                    .append("\n");
        }

        return builder.toString();
    }

    /**
     * Inserts the shader precompiler defines code into the source code of a shader.
     *
     * @param sourceCode   The original source code of the shader.
     * @param definesCode  The code containing the shader precompiler defines.
     * @return The modified shader source code with the precompiler defines inserted.
     */
    private static String insertShaderDefinesCode(String sourceCode, String definesCode) {
        String result =
                sourceCode.replaceAll(
                        "(?m)^(\\s*#\\s*version\\s+.*)$", "$1\n"
                                + Matcher.quoteReplacement(definesCode));

        if (result.equals(sourceCode)) {
            return definesCode + sourceCode;
        }

        return result;
    }

    /**
     * Reads the contents of an input stream and converts it into a string.
     *
     * @param stream The input stream to be converted.
     * @return The string representation of the input stream contents.
     * @throws IOException If an error occurs while reading the input stream.
     */
    private static String inputStreamToString(InputStream stream) throws IOException {
        InputStreamReader reader = new InputStreamReader(stream, UTF_8.name());
        char[] buffer = new char[1024 * 4];
        StringBuilder builder = new StringBuilder();
        int amount;

        while ((amount = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, amount);
        }

        reader.close();

        return builder.toString();
    }

    @Override
    public void close() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId);
            programId = 0;
        }
    }

    /**
     * Sets depth test state.
     *
     * @see <a href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glEnable.xhtml">glEnable(GL_DEPTH_TEST)</a>.
     */
    public Shader setDepthTest(boolean depthTest) {
        this.depthTest = depthTest;
        return this;
    }

    /**
     * Sets depth write state.
     *
     * @see <a href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glDepthMask.xhtml">glDepthMask</a>.
     */
    public Shader setDepthWrite(boolean depthWrite) {
        this.depthWrite = depthWrite;
        return this;
    }

    /**
     * Sets blending function.
     *
     * @see <a href="https://www.khronos.org/registry/OpenGL-Refpages/gl4/html/glBlendFunc.xhtml">glBlendFunc</a>
     */
    public Shader setBlend(BlendFactor sourceBlend, BlendFactor destBlend) {
        this.sourceRgbBlend = sourceBlend;
        this.destRgbBlend = destBlend;
        this.sourceAlphaBlend = sourceBlend;
        this.destAlphaBlend = destBlend;
        return this;
    }

    /**
     * Sets blending functions separately for RGB and alpha channels.
     *
     * @see <a href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glBlendFuncSeparate.xhtml">glBlendFunc</a>
     */
    public Shader setBlend(
            BlendFactor sourceRgbBlend,
            BlendFactor destRgbBlend,
            BlendFactor sourceAlphaBlend,
            BlendFactor destAlphaBlend) {
        this.sourceRgbBlend = sourceRgbBlend;
        this.destRgbBlend = destRgbBlend;
        this.sourceAlphaBlend = sourceAlphaBlend;
        this.destAlphaBlend = destAlphaBlend;
        return this;
    }

    /**
     * Sets a texture uniform.
     */
    public Shader setTexture(String name, Texture texture) {
        int location = getUniformLocation(name);
        Uniform uniform = UNIFORMS.get(location);
        int textureUnit;

        if (!(uniform instanceof UniformTexture)) {
            textureUnit = maxTextureUnit++;
        } else {
            UniformTexture uniformTexture = (UniformTexture) uniform;
            textureUnit = uniformTexture.getTextureUnit();
        }

        UNIFORMS.put(location, new UniformTexture(textureUnit, texture));
        return this;
    }

    /**
     * Sets a {@code bool} uniform.
     */
    public Shader setBool(String name, boolean v0) {
        int[] values = {v0 ? 1 : 0};
        UNIFORMS.put(getUniformLocation(name), new UniformInt(values));

        return this;
    }

    /**
     * Sets an {@code int} uniform.
     */
    public void setInt(String name, int v0) {
        int[] values = {v0};
        UNIFORMS.put(getUniformLocation(name), new UniformInt(values));

    }

    /**
     * Sets a {@code float} uniform.
     */
    public Shader setFloat(String name, float v0) {
        float[] values = {v0};
        UNIFORMS.put(getUniformLocation(name), new Uniform1f(values));

        return this;
    }

    /**
     * Sets a {@code vec2} uniform.
     */
    public Shader setVec2(String name, float[] values) {
        if (values.length != 2) {
            throw new IllegalArgumentException("Value array length must be 2");
        }

        UNIFORMS.put(getUniformLocation(name), new Uniform2f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code vec3} uniform.
     */
    public Shader setVec3(String name, float[] values) {
        if (values.length != 3) {
            throw new IllegalArgumentException("Value array length must be 3");
        }

        UNIFORMS.put(getUniformLocation(name), new Uniform3f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code vec4} uniform.
     */
    public Shader setVec4(String name, float[] values) {
        if (values.length != 4) {
            throw new IllegalArgumentException("Value array length must be 4");
        }

        UNIFORMS.put(getUniformLocation(name), new Uniform4f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code mat2} uniform.
     */
    public Shader setMat2(String name, float[] values) {
        if (values.length != 4) {
            throw new IllegalArgumentException("Value array length must be 4 (2x2)");
        }

        UNIFORMS.put(getUniformLocation(name), new UniformMatrix2f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code mat3} uniform.
     */
    public void setMat3(String name, float[] values) {
        if (values.length != 9) {
            throw new IllegalArgumentException("Value array length must be 9 (3x3)");
        }

        UNIFORMS.put(getUniformLocation(name), new UniformMatrix3f(values.clone()));

    }

    /**
     * Sets a {@code mat4} uniform.
     */
    public void setMat4(String name, float[] values) {
        if (values.length != 16) {
            throw new IllegalArgumentException("Value array length must be 16 (4x4)");
        }

        UNIFORMS.put(getUniformLocation(name), new UniformMatrix4f(values.clone()));

    }

    /**
     * Sets a {@code bool} array uniform.
     */
    public Shader setBoolArray(String name, boolean[] values) {
        int[] intValues = new int[values.length];

        for (int i = 0; i < values.length; ++i) {
            intValues[i] = values[i] ? 1 : 0;
        }

        UNIFORMS.put(getUniformLocation(name), new UniformInt(intValues));

        return this;
    }

    /**
     * Sets an {@code int} array uniform.
     */
    public Shader setIntArray(String name, int[] values) {
        UNIFORMS.put(getUniformLocation(name), new UniformInt(values.clone()));

        return this;
    }

    /**
     * Sets a {@code float} array uniform.
     */
    public Shader setFloatArray(String name, float[] values) {
        UNIFORMS.put(getUniformLocation(name), new Uniform1f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code vec2} array uniform.
     */
    public Shader setVec2Array(String name, float[] values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Value array length must be divisible by 2");
        }

        UNIFORMS.put(getUniformLocation(name), new Uniform2f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code vec3} array uniform.
     */
    public Shader setVec3Array(String name, float[] values) {
        if (values.length % 3 != 0) {
            throw new IllegalArgumentException("Value array length must be divisible by 3");
        }

        UNIFORMS.put(getUniformLocation(name), new Uniform3f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code vec4} array uniform.
     */
    public Shader setVec4Array(String name, float[] values) {
        if (values.length % 4 != 0) {
            throw new IllegalArgumentException("Value array length must be divisible by 4");
        }

        UNIFORMS.put(getUniformLocation(name), new Uniform4f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code mat2} array uniform.
     */
    public Shader setMat2Array(String name, float[] values) {
        if (values.length % 4 != 0) {
            throw new IllegalArgumentException("Value array length must be divisible by 4 (2x2)");
        }

        UNIFORMS.put(getUniformLocation(name), new UniformMatrix2f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code mat3} array uniform.
     */
    public Shader setMat3Array(String name, float[] values) {
        if (values.length % 9 != 0) {
            throw new IllegalArgumentException("Values array length must be divisible by 9 (3x3)");
        }

        UNIFORMS.put(getUniformLocation(name), new UniformMatrix3f(values.clone()));

        return this;
    }

    /**
     * Sets a {@code mat4} uniform.
     */
    public Shader setMat4Array(String name, float[] values) {
        if (values.length % 16 != 0) {
            throw new IllegalArgumentException("Value array length must be divisible by 16 (4x4)");
        }

        UNIFORMS.put(getUniformLocation(name), new UniformMatrix4f(values.clone()));

        return this;
    }

    /**
     * Activates the shader. Don't call this directly unless you are doing low level OpenGL code;
     * instead, prefer {@link CustomRender#draw}.
     */
    public void lowLevelUse() {
        if (programId == 0) {
            throw new IllegalStateException("Attempted to use freed shader");
        }

        GLES30.glUseProgram(programId);
        GLError.maybeThrowGLException("Failed to use shader program", "glUseProgram");

        GLES30.glBlendFuncSeparate(
                sourceRgbBlend.glesEnum,
                destRgbBlend.glesEnum,
                sourceAlphaBlend.glesEnum,
                destAlphaBlend.glesEnum);
        GLError.maybeThrowGLException("Failed to set blend mode", "glBlendFuncSeparate");

        GLES30.glDepthMask(depthWrite);
        GLError.maybeThrowGLException("Failed to set depth write mask", "glDepthMask");

        if (depthTest) {
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            GLError.maybeThrowGLException("Failed to enable depth test", "glEnable");
        } else {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            GLError.maybeThrowGLException("Failed to disable depth test", "glDisable");
        }

        try {
            ArrayList<Integer> obsoleteEntries = new ArrayList<>(UNIFORMS.size());

            for (Map.Entry<Integer, Uniform> entry : UNIFORMS.entrySet()) {
                try {
                    entry.getValue().use(entry.getKey());

                    if (!(entry.getValue() instanceof UniformTexture)) {
                        obsoleteEntries.add(entry.getKey());
                    }
                } catch (GLException e) {
                    String name = UNIFORM_NAMES.get(entry.getKey());
                    throw new IllegalArgumentException("Error setting uniform `" + name + "'", e);
                }
            }
            obsoleteEntries.forEach(UNIFORMS.keySet()::remove);
        } finally {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLError.maybeLogGLError(Log.WARN, TAG, "Failed to set active texture",
                    "glActiveTexture");
        }
    }

    private int getUniformLocation(String name) {
        Integer locationObject = UNIFORM_LOCATIONS.get(name);

        if (locationObject != null) {
            return locationObject;
        }

        int location = GLES30.glGetUniformLocation(programId, name);
        GLError.maybeThrowGLException("Failed to find uniform", "glGetUniformLocation");

        if (location == -1) {
            throw new IllegalArgumentException("Shader uniform does not exist: " + name);
        }

        UNIFORM_LOCATIONS.put(name, location);
        UNIFORM_NAMES.put(location, name);

        return location;
    }

    public int getProgramId() {
        return programId;
    }

    public int getFragShader() {
        return fragmentShaderId;
    }

    public int getVertShader() {
        return vertexShaderId;
    }

    /**
     * A factor to be used in a blend function.
     *
     * @see <a href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glBlendFunc.xhtml">glBlendFunc</a>
     */
    public enum BlendFactor {
        ZERO(GLES30.GL_ZERO),
        ONE(GLES30.GL_ONE),
        SRC_COLOR(GLES30.GL_SRC_COLOR),
        ONE_MINUS_SRC_COLOR(GLES30.GL_ONE_MINUS_SRC_COLOR),
        DST_COLOR(GLES30.GL_DST_COLOR),
        ONE_MINUS_DST_COLOR(GLES30.GL_ONE_MINUS_DST_COLOR),
        SRC_ALPHA(GLES30.GL_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(GLES30.GL_ONE_MINUS_SRC_ALPHA),
        DST_ALPHA(GLES30.GL_DST_ALPHA),
        ONE_MINUS_DST_ALPHA(GLES30.GL_ONE_MINUS_DST_ALPHA),
        CONSTANT_COLOR(GLES30.GL_CONSTANT_COLOR),
        ONE_MINUS_CONSTANT_COLOR(GLES30.GL_ONE_MINUS_CONSTANT_COLOR),
        CONSTANT_ALPHA(GLES30.GL_CONSTANT_ALPHA),
        ONE_MINUS_CONSTANT_ALPHA(GLES30.GL_ONE_MINUS_CONSTANT_ALPHA);

        final int glesEnum;
        BlendFactor(int glesEnum) {
            this.glesEnum = glesEnum;
        }
    }

    private interface Uniform {
        void use(int location);
    }

    private static class UniformTexture implements Uniform {
        private final int textureUnit;
        private final Texture texture;

        public UniformTexture(int textureUnit, Texture texture) {
            this.textureUnit = textureUnit;
            this.texture = texture;
        }

        public int getTextureUnit() {
            return textureUnit;
        }

        @Override
        public void use(int location) {
            if (texture.getTextureId() == 0) {
                throw new IllegalStateException("Tried to draw with freed texture");
            }

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + textureUnit);
            GLError.maybeThrowGLException("Failed to set active texture", "glActiveTexture");
            GLES30.glBindTexture(texture.getTarget().glesEnum, texture.getTextureId());
            GLError.maybeThrowGLException("Failed to bind texture", "glBindTexture");
            GLES30.glUniform1i(location, textureUnit);
            GLError.maybeThrowGLException("Failed to set shader texture uniform", "glUniform1i");
        }
    }

    private static class UniformInt implements Uniform {
        private final int[] values;
        public UniformInt(int[] values) {
            this.values = values;
        }

        @Override
        public void use(int location) {
            GLES30.glUniform1iv(location, values.length, values, 0);
            GLError.maybeThrowGLException("Failed to set shader uniform 1i", "glUniform1iv");
        }
    }

    private static class Uniform1f implements Uniform {
        private final float[] values;
        public Uniform1f(float[] values) {
            this.values = values;
        }

        @Override
        public void use(int location) {
            GLES30.glUniform1fv(location, values.length, values, 0);
            GLError.maybeThrowGLException("Failed to set shader uniform 1f", "glUniform1fv");
        }
    }

    private static class Uniform2f implements Uniform {
        private final float[] values;
        public Uniform2f(float[] values) {
            this.values = values;
        }

        @Override
        public void use(int location) {
            GLES30.glUniform2fv(location, values.length / 2, values, 0);
            GLError.maybeThrowGLException("Failed to set shader uniform 2f", "glUniform2fv");
        }
    }

    private static class Uniform3f implements Uniform {
        private final float[] values;
        public Uniform3f(float[] values) {
            this.values = values;
        }

        @Override
        public void use(int location) {
            GLES30.glUniform3fv(location, values.length / 3, values, 0);
            GLError.maybeThrowGLException("Failed to set shader uniform 3f", "glUniform3fv");
        }
    }

    private static class Uniform4f implements Uniform {
        private final float[] values;
        public Uniform4f(float[] values) {
            this.values = values;
        }

        @Override
        public void use(int location) {
            GLES30.glUniform4fv(location, values.length / 4, values, 0);
            GLError.maybeThrowGLException("Failed to set shader uniform 4f", "glUniform4fv");
        }
    }

    private static class UniformMatrix2f implements Uniform {
        private final float[] values;
        public UniformMatrix2f(float[] values) {
            this.values = values;
        }

        @Override
        public void use(int location) {
            GLES30.glUniformMatrix2fv(location, values.length / 4, false, values,
                    0);
            GLError.maybeThrowGLException("Failed to set shader uniform matrix 2f",
                    "glUniformMatrix2fv");
        }
    }

    private static class UniformMatrix3f implements Uniform {
        private final float[] values;
        public UniformMatrix3f(float[] values) {
            this.values = values;
        }

        @Override
        public void use(int location) {
            GLES30.glUniformMatrix3fv(location, values.length / 9, false, values,
                    0);
            GLError.maybeThrowGLException("Failed to set shader uniform matrix 3f",
                    "glUniformMatrix3fv");
        }
    }

    private static class UniformMatrix4f implements Uniform {
        private final float[] values;
        public UniformMatrix4f(float[] values) {
            this.values = values;
        }

        @Override
        public void use(int location) {
            GLES30.glUniformMatrix4fv(location, values.length / 16, false, values,
                    0);
            GLError.maybeThrowGLException("Failed to set shader uniform matrix 4f",
                    "glUniformMatrix4fv");
        }
    }
}