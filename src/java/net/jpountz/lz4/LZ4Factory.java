package net.jpountz.lz4;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Arrays;

/**
 * Entry point for the LZ4 API.
 * <p>
 * This class has 3 instances<ul>
 * <li>a {@link #nativeInstance() native} instance which is a JNI binding to
 * the original LZ4 implementation.</li>
 * <li>a {@link #safeInstance() safe Java} instance which is a pure Java port
 * of the original C library,</li>
 * <li>an {@link #unsafeInstance() unsafe Java} instance which is a Java port
 * using the unofficial {@link sun.misc.Unsafe} API.</li>
 * </ul>
 * <p>
 * Not all instances may work on your computer, as a consequence it is advised
 * to use the {@link #fastestInstance()} method to pull a {@link LZ4Factory}
 * instance.
 * <p>
 * All methods from this class are very costly, so you should get an instance
 * once, and then reuse it whenever possible. This is typically done by storing
 * a {@link LZ4Factory} instance in a static field.
 */
public final class LZ4Factory {

  private static LZ4Factory instance(String impl) {
    try {
      return new LZ4Factory(impl);
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  decompressors that are native bindings to the original C API.<p>
   *  Although this instance is likely to be slightly faster on large inputs,
   *  beware that the JNI overhead might make it much slower than its
   *  Java counterparts on small inputs. */
  public static LZ4Factory nativeInstance() {
    return instance("JNI");
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  decompressors that are written with Java's official API. */
  public static LZ4Factory safeInstance() {
    return instance("JavaSafe");
  }

  /** Return a {@link LZ4Factory} instance that returns compressors and
   *  decompressors that may use {@link sun.misc.Unsafe} to speed up compression
   *  and decompression. */
  public static LZ4Factory unsafeInstance() {
    return instance("JavaUnsafe");
  }

  /**
   * Return the fastest available {@link LZ4Factory} instance. This method
   * will first try to load the native instance, then the unsafe java one and
   * finally the safe java one if the JVM doesn't have {@link sun.misc.Unsafe}
   * support.
   */
  public static LZ4Factory fastestInstance() {
    try {
      return nativeInstance();
    } catch (Throwable t1) {
      try {
        return unsafeInstance();
      } catch (Throwable t2) {
        return safeInstance();
      }
    }
  }

  private final String impl;
  private final LZ4Compressor fastCompressor;
  private final LZ4Compressor highCompressor;
  private final LZ4Decompressor decompressor;
  private final LZ4UnknownSizeDecompressor unknownSizeDecompressor;

  private LZ4Factory(String impl) throws ClassNotFoundException {
    this.impl = impl;
    final Class<?> compressorEnum = Class.forName("net.jpountz.lz4.LZ4" + impl + "Compressor");
    if (!LZ4Compressor.class.isAssignableFrom(compressorEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    LZ4Compressor[] compressors = ((Class<? extends LZ4Compressor>) compressorEnum).getEnumConstants();
    if (compressors.length != 2) {
      throw new AssertionError();
    }
    fastCompressor = compressors[0];
    highCompressor = compressors[1];

    final Class<?> decompressorEnum = Class.forName("net.jpountz.lz4.LZ4" + impl + "Decompressor");
    if (!LZ4Decompressor.class.isAssignableFrom(decompressorEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    LZ4Decompressor[] decompressors = ((Class<? extends LZ4Decompressor>) decompressorEnum).getEnumConstants();
    if (decompressors.length != 1) {
      throw new AssertionError();
    }
    decompressor = decompressors[0];

    final Class<?> unknownSizeDecompressorEnum = Class.forName("net.jpountz.lz4.LZ4" + impl + "UnknownSizeDecompressor");
    if (!LZ4UnknownSizeDecompressor.class.isAssignableFrom(unknownSizeDecompressorEnum)) {
      throw new AssertionError();
    }
    @SuppressWarnings("unchecked")
    LZ4UnknownSizeDecompressor[] unknownSizeDecompressors = ((Class<? extends LZ4UnknownSizeDecompressor>) unknownSizeDecompressorEnum).getEnumConstants();
    if (decompressors.length != 1) {
      throw new AssertionError();
    }
    unknownSizeDecompressor = unknownSizeDecompressors[0];

    // quickly test that everything works as expected
    final byte[] original = new byte[] {'a','b','c','d',' ',' ',' ',' ',' ',' ','a','b','c','d','e','f','g','h','i','j'};
    for (LZ4Compressor compressor : Arrays.asList(fastCompressor, highCompressor)) {
      final int maxCompressedLength = compressor.maxCompressedLength(original.length);
      final byte[] compressed = new byte[maxCompressedLength];
      final int compressedLength = compressor.compress(original, 0, original.length, compressed, 0, maxCompressedLength);
      final byte[] restored = new byte[original.length];
      decompressor.decompress(compressed, 0, restored, 0, original.length);
      if (!Arrays.equals(original, restored)) {
        throw new AssertionError();
      }
      Arrays.fill(restored, (byte) 0);
      final int decompressedLength = unknownSizeDecompressor.decompress(compressed, 0, compressedLength, restored, 0);
      if (decompressedLength != original.length || !Arrays.equals(original, restored)) {
        throw new AssertionError();
      }
    }

  }

  /** Return a {@link LZ4Compressor} that uses a fast-scan method to compress
   *  data. */
  public LZ4Compressor fastCompressor() {
    return fastCompressor;
  }

  /** Return a {@link LZ4Compressor} that uses more CPU and memory to compress
   *  data in order to improve the compression ratio. */
  public LZ4Compressor highCompressor() {
    return highCompressor;
  }

  /** Return a {@link LZ4Decompressor} instance. */
  public LZ4Decompressor decompressor() {
    return decompressor;
  }

  /** Return a {@link LZ4UnknownSizeDecompressor} instance. */
  public LZ4UnknownSizeDecompressor unknwonSizeDecompressor() {
    return unknownSizeDecompressor;
  }

  /** Prints the fastest instance. */
  public static void main(String[] args) {
    System.out.println("Fastest instance is " + fastestInstance());
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + ":" + impl;
  }

}
