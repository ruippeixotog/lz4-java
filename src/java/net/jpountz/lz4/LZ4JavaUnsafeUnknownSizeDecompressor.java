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

import static net.jpountz.lz4.LZ4UnsafeUtils.readShortLittleEndian;
import static net.jpountz.lz4.LZ4UnsafeUtils.safeArraycopy;
import static net.jpountz.lz4.LZ4UnsafeUtils.wildArraycopy;
import static net.jpountz.lz4.LZ4UnsafeUtils.wildIncrementalCopy;
import static net.jpountz.lz4.LZ4Utils.COPY_LENGTH;
import static net.jpountz.lz4.LZ4Utils.MIN_MATCH;
import static net.jpountz.lz4.LZ4Utils.ML_BITS;
import static net.jpountz.lz4.LZ4Utils.ML_MASK;
import static net.jpountz.lz4.LZ4Utils.RUN_MASK;
import static net.jpountz.lz4.LZ4Utils.naiveIncrementalCopy;
import static net.jpountz.util.UnsafeUtils.readByte;
import static net.jpountz.util.Utils.checkRange;


/**
 * Very fast decompressor written in pure Java with the unofficial
 * sun.misc.Unsafe API.
 */
enum LZ4JavaUnsafeUnknownSizeDecompressor implements LZ4UnknownSizeDecompressor {

  INSTANCE {

    @Override
    public int decompress(byte[] src, int srcOff, int srcLen,
        byte[] dest, int destOff) {
      checkRange(src, srcOff, srcLen);
      checkRange(dest, destOff);

      final int srcEnd = srcOff + srcLen;
      final int destEnd = dest.length;

      int sOff = srcOff;
      int dOff = destOff;

      while (sOff < srcEnd) {
        final int token = readByte(src, sOff++) & 0xFF;

        // literals
        int literalLen = token >>> ML_BITS;
        if (literalLen == RUN_MASK) {
            byte len;
            while ((len = readByte(src, sOff++)) == (byte) 0xFF) {
              literalLen += 0xFF;
            }
            literalLen += len & 0xFF;
        }

        final int literalCopyEnd = dOff + literalLen;
        if (literalCopyEnd > destEnd - COPY_LENGTH || sOff + literalLen > srcEnd - COPY_LENGTH) {
          if (literalCopyEnd > destEnd || sOff + literalLen > srcEnd) {
            throw new LZ4Exception("Malformed input at " + sOff);
          } else {
            safeArraycopy(src, sOff, dest, dOff, literalLen);
            sOff += literalLen;
            dOff += literalLen;
            if (sOff < srcEnd) {
              throw new LZ4Exception("Malformed input at " + sOff);
            }
            break; // EOF
          }
        }

        wildArraycopy(src, sOff, dest, dOff, literalLen);
        sOff += literalLen;
        dOff = literalCopyEnd;

        // matchs
        final int matchDec = readShortLittleEndian(src, sOff);
        sOff += 2;
        int matchOff = dOff - matchDec;

        if (matchOff < destOff) {
          throw new LZ4Exception("Malformed input at " + sOff);
        }

        int matchLen = token & ML_MASK;
        if (matchLen == ML_MASK) {
          byte len;
          while ((len = readByte(src, sOff++)) == (byte) 0xFF) {
            matchLen += 0xFF;
          }
          matchLen += len & 0xFF;
        }
        matchLen += MIN_MATCH;

        final int matchCopyEnd = dOff + matchLen;

        if (matchCopyEnd > dest.length - COPY_LENGTH) {
          if (matchCopyEnd > dest.length) {
            throw new LZ4Exception("Malformed input at " + sOff);
          }
          naiveIncrementalCopy(dest, matchOff, dOff, matchLen);
        } else {
          wildIncrementalCopy(dest, matchOff, dOff, matchCopyEnd);
        }
        dOff = matchCopyEnd;
      }

      return dOff - destOff;
    }

  };

  @Override
  public String toString() {
    return getDeclaringClass().getSimpleName();
  }

}
