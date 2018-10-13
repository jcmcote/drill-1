/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.io.compress.zstd;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.Compressor;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDirectBufferCompressingStream;
import com.github.luben.zstd.util.Native;

/**
 * A {@link Compressor} based on the snappy compression algorithm.
 * http://code.google.com/p/snappy/
 *
 * jccote !!!DID NOT TEST THIS CLASS, JUST RENAMED SNAPPY FOR ZSTD!!!
 */
public class ZstdCompressor implements Compressor {
  private static final Log LOG = LogFactory.getLog(ZstdCompressor.class.getName());

  private int directBufferSize;
  private ByteBuffer compressedDirectBuf = null;
  private int uncompressedDirectBufLen;
  private ByteBuffer uncompressedDirectBuf = null;
  private byte[] userBuf = null;
  private int userBufOff = 0, userBufLen = 0;
  private boolean finish, finished;

  private long bytesRead = 0L;
  private long bytesWritten = 0L;

  static {
    if (!Native.isLoaded()) {
      try {
        // initIDs();
        Native.load();
      } catch (Throwable t) {
        LOG.error("failed to load ZstdCompressor", t);
      }
    }
  }

  public static boolean isNativeCodeLoaded() {
    return Native.isLoaded();
  }

  /**
   * Creates a new compressor.
   *
   * @param directBufferSize
   *          size of the direct buffer to be used.
   */
  public ZstdCompressor(int directBufferSize) {
    this.directBufferSize = directBufferSize;
    uncompressedDirectBuf = ByteBuffer.allocateDirect(directBufferSize);
    compressedDirectBuf = ByteBuffer.allocateDirect(directBufferSize);
    compressedDirectBuf.position(directBufferSize);
  }

  /**
   * Creates a new compressor with the default buffer size.
   */
  public ZstdCompressor() {
    this(ZstdDirectBufferCompressingStream.recommendedOutputBufferSize());
  }

  /**
   * Sets input data for compression. This should be called whenever
   * #needsInput() returns <code>true</code> indicating that more input data is
   * required.
   *
   * @param b
   *          Input data
   * @param off
   *          Start offset
   * @param len
   *          Length
   */
  @Override
  public void setInput(byte[] b, int off, int len) {
    if (b == null) {
      throw new NullPointerException();
    }
    if (off < 0 || len < 0 || off > b.length - len) {
      throw new ArrayIndexOutOfBoundsException();
    }
    finished = false;

    if (len > uncompressedDirectBuf.remaining()) {
      // save data; now !needsInput
      this.userBuf = b;
      this.userBufOff = off;
      this.userBufLen = len;
    } else {
      ((ByteBuffer) uncompressedDirectBuf).put(b, off, len);
      uncompressedDirectBufLen = uncompressedDirectBuf.position();
    }

    bytesRead += len;
  }

  /**
   * If a write would exceed the capacity of the direct buffers, it is set aside
   * to be loaded by this function while the compressed data are consumed.
   */
  void setInputFromSavedData() {
    if (0 >= userBufLen) {
      return;
    }
    finished = false;

    uncompressedDirectBufLen = Math.min(userBufLen, directBufferSize);
    ((ByteBuffer) uncompressedDirectBuf).put(userBuf, userBufOff, uncompressedDirectBufLen);

    // Note how much data is being fed to snappy
    userBufOff += uncompressedDirectBufLen;
    userBufLen -= uncompressedDirectBufLen;
  }

  /**
   * Does nothing.
   */
  @Override
  public void setDictionary(byte[] b, int off, int len) {
    // do nothing
  }

  /**
   * Returns true if the input data buffer is empty and #setInput() should be
   * called to provide more input.
   *
   * @return <code>true</code> if the input data buffer is empty and #setInput()
   *         should be called in order to provide more input.
   */
  @Override
  public boolean needsInput() {
    return !(compressedDirectBuf.remaining() > 0 || uncompressedDirectBuf.remaining() == 0 || userBufLen > 0);
  }

  /**
   * When called, indicates that compression should end with the current
   * contents of the input buffer.
   */
  @Override
  public void finish() {
    finish = true;
  }

  /**
   * Returns true if the end of the compressed data output stream has been
   * reached.
   *
   * @return <code>true</code> if the end of the compressed data output stream
   *         has been reached.
   */
  @Override
  public boolean finished() {
    // Check if all uncompressed data has been consumed
    return (finish && finished && compressedDirectBuf.remaining() == 0);
  }

  /**
   * Fills specified buffer with compressed data. Returns actual number of bytes
   * of compressed data. A return value of 0 indicates that needsInput() should
   * be called in order to determine if more input data is required.
   *
   * @param b
   *          Buffer for the compressed data
   * @param off
   *          Start offset of the data
   * @param len
   *          Size of the buffer
   * @return The actual number of bytes of compressed data.
   */
  @Override
  public int compress(byte[] b, int off, int len) throws IOException {
    if (b == null) {
      throw new NullPointerException();
    }
    if (off < 0 || len < 0 || off > b.length - len) {
      throw new ArrayIndexOutOfBoundsException();
    }

    // Check if there is compressed data
    int n = compressedDirectBuf.remaining();
    if (n > 0) {
      n = Math.min(n, len);
      ((ByteBuffer) compressedDirectBuf).get(b, off, n);
      bytesWritten += n;
      return n;
    }

    // Re-initialize the snappy's output direct-buffer
    compressedDirectBuf.clear();
    compressedDirectBuf.limit(0);
    if (0 == uncompressedDirectBuf.position()) {
      // No compressed data, so we should have !needsInput or !finished
      setInputFromSavedData();
      if (0 == uncompressedDirectBuf.position()) {
        // Called without data; write nothing
        finished = true;
        return 0;
      }
    }

    // Compress data
    n = compressBytesDirect();
    compressedDirectBuf.limit(n);
    uncompressedDirectBuf.clear(); // snappy consumes all buffer input

    // Set 'finished' if snapy has consumed all user-data
    if (0 == userBufLen) {
      finished = true;
    }

    // Get atmost 'len' bytes
    n = Math.min(n, len);
    bytesWritten += n;
    ((ByteBuffer) compressedDirectBuf).get(b, off, n);

    return n;
  }

  /**
   * Resets compressor so that a new set of input data can be processed.
   */
  @Override
  public void reset() {
    finish = false;
    finished = false;
    uncompressedDirectBuf.clear();
    uncompressedDirectBufLen = 0;
    compressedDirectBuf.clear();
    compressedDirectBuf.limit(0);
    userBufOff = userBufLen = 0;
    bytesRead = bytesWritten = 0L;
  }

  /**
   * Prepare the compressor to be used in a new stream with settings defined in
   * the given Configuration
   *
   * @param conf
   *          Configuration from which new setting are fetched
   */
  @Override
  public void reinit(Configuration conf) {
    reset();
  }

  /**
   * Return number of bytes given to this compressor since last reset.
   */
  @Override
  public long getBytesRead() {
    return bytesRead;
  }

  /**
   * Return number of bytes consumed by callers of compress since last reset.
   */
  @Override
  public long getBytesWritten() {
    return bytesWritten;
  }

  /**
   * Closes the compressor and discards any unprocessed input.
   */
  @Override
  public void end() {
  }

  private int compressBytesDirect() {
    int level = 1;
    long ret = Zstd.compressDirectByteBuffer(compressedDirectBuf, 0, directBufferSize, uncompressedDirectBuf, 0,
        uncompressedDirectBuf.remaining(), level);
    return (int) ret;
  }

  // public native static String getLibraryName();
}
