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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/** A GPU-side texture. */
public class Texture implements Closeable {
  private static final String TAG = Texture.class.getSimpleName();
  private final int[] TEXTURE_ID = {0};
  private final Target TARGET;

  /**
   * Describes the way the texture's edges are rendered.
   *
   * @see <a href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glTexParameter.xhtml">GL_TEXTURE_WRAP_S</a>.
   */
  public enum WrapMode {
    CLAMP_TO_EDGE(GLES30.GL_CLAMP_TO_EDGE),
    MIRRORED_REPEAT(GLES30.GL_MIRRORED_REPEAT),
    REPEAT(GLES30.GL_REPEAT);

    final int GL_ES_ENUM;
    WrapMode(int gl_es_enum) {
      this.GL_ES_ENUM = gl_es_enum;
    }
  }

  /**
   * Describes the target this texture is bound to.
   *
   * @see <a href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glBindTexture.xhtml">glBindTexture</a>.
   */
  public enum Target {
    TEXTURE_2D(GLES30.GL_TEXTURE_2D),
    TEXTURE_EXTERNAL_OES(GLES11Ext.GL_TEXTURE_EXTERNAL_OES),
    TEXTURE_CUBE_MAP(GLES30.GL_TEXTURE_CUBE_MAP);

    final int glesEnum;

    Target(int glesEnum) {
      this.glesEnum = glesEnum;
    }
  }

  /**
   * Describes the color format of the texture.
   *
   * @see <a href="https://www.khronos.org/registry/OpenGL-Refpages/es3.0/html/glTexImage2D.xhtml">glTexImage2d</a>.
   */
  public enum ColorFormat {
    LINEAR(GLES30.GL_RGBA8),
    SRGB(GLES30.GL_SRGB8_ALPHA8);

    final int glesEnum;

    ColorFormat(int glesEnum) {
      this.glesEnum = glesEnum;
    }
  }

  /**
   * Constructs an empty {@link Texture}.
   *
   * <p>Since {@link Texture}s created in this way are not populated with data, this method is
   * mostly only useful for creating {@link Target#TEXTURE_EXTERNAL_OES} textures. See {@link
   * #createFromAsset} if you want a texture with data.
   */
  public Texture(Target target, WrapMode wrapMode) {
    this(target, wrapMode, true);
  }

  public Texture(Target target, WrapMode wrapMode, boolean useMipmaps) {
    this.TARGET = target;
    GLES30.glGenTextures(1, TEXTURE_ID, 0);
    GLError.maybeThrowGLException("Texture creation failed", "glGenTextures");
    int minFilter = useMipmaps ? GLES30.GL_LINEAR_MIPMAP_LINEAR : GLES30.GL_LINEAR;

    try {
      GLES30.glBindTexture(target.glesEnum, TEXTURE_ID[0]);
      GLError.maybeThrowGLException("Failed to bind texture", "glBindTexture");
      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_MIN_FILTER, minFilter);
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri");
      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri");

      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_WRAP_S, wrapMode.GL_ES_ENUM);
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri");
      GLES30.glTexParameteri(target.glesEnum, GLES30.GL_TEXTURE_WRAP_T, wrapMode.GL_ES_ENUM);
      GLError.maybeThrowGLException("Failed to set texture parameter", "glTexParameteri");
    } catch (Throwable t) {
      close();
      throw t;
    }
  }

  /** Creates a texture from the given asset file name. */
  public static Texture createFromAsset(
          CustomRender render, String assetFileName, WrapMode wrapMode, ColorFormat colorFormat)
      throws IOException {
    Texture texture = new Texture(Target.TEXTURE_2D, wrapMode);
    Bitmap bitmap = null;

    try {
      bitmap = convertBitmapToConfig(
              BitmapFactory.decodeStream(render.getAssets().open(assetFileName)));
      ByteBuffer buffer = ByteBuffer.allocateDirect(bitmap.getByteCount());
      bitmap.copyPixelsToBuffer(buffer);
      buffer.rewind();

      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.getTextureId());
      GLError.maybeThrowGLException("Failed to bind texture", "glBindTexture");

      GLES30.glTexImage2D(
          GLES30.GL_TEXTURE_2D,
          0,
          colorFormat.glesEnum,
          bitmap.getWidth(),
          bitmap.getHeight(),
          0,
          GLES30.GL_RGBA,
          GLES30.GL_UNSIGNED_BYTE,
          buffer);

      GLError.maybeThrowGLException("Failed to populate texture data", "glTexImage2D");
      GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
      GLError.maybeThrowGLException("Failed to generate mipmaps", "glGenerateMipmap");
    } catch (Throwable t) {
      texture.close();
      throw t;
    } finally {
      if (bitmap != null) {
        bitmap.recycle();
      }
    }

    return texture;
  }

  @Override
  public void close() {
    if (TEXTURE_ID[0] != 0) {
      GLES30.glDeleteTextures(1, TEXTURE_ID, 0);
      GLError.maybeLogGLError(Log.WARN, TAG, "Failed to free texture",
              "glDeleteTextures");
      TEXTURE_ID[0] = 0;
    }
  }

  /** Retrieves the native texture ID. */
  public int getTextureId() {
    return TEXTURE_ID[0];
  }

  public Target getTarget() {
    return TARGET;
  }

  private static Bitmap convertBitmapToConfig(Bitmap bitmap) {
    if (bitmap.getConfig() == Bitmap.Config.ARGB_8888) {
      return bitmap;
    }

    Bitmap result = bitmap.copy(Bitmap.Config.ARGB_8888, false);
    bitmap.recycle();

    return result;
  }
}
