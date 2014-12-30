/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.content.Context;
import android.net.Uri;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.ResponseBody;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** A {@link Downloader} which uses OkHttp to download images. */
public class OkHttpDownloader implements Downloader {
  private static OkHttpClient defaultOkHttpClient() {
    OkHttpClient client = new OkHttpClient();
    client.setConnectTimeout(Utils.DEFAULT_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    client.setReadTimeout(Utils.DEFAULT_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    return client;
  }

  private final OkHttpClient client;

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   */
  public OkHttpDownloader(final Context context) {
    this(Utils.createDefaultCacheDir(context));
  }

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into the specified
   * directory.
   *
   * @param cacheDir The directory in which the cache should be stored
   */
  public OkHttpDownloader(final File cacheDir) {
    this(cacheDir, Utils.calculateDiskCacheSize(cacheDir));
  }

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into your application
   * cache directory.
   *
   * @param maxSize The size limit for the cache.
   */
  public OkHttpDownloader(final Context context, final long maxSize) {
    this(Utils.createDefaultCacheDir(context), maxSize);
  }

  /**
   * Create new downloader that uses OkHttp. This will install an image cache into the specified
   * directory.
   *
   * @param cacheDir The directory in which the cache should be stored
   * @param maxSize The size limit for the cache.
   */
  public OkHttpDownloader(final File cacheDir, final long maxSize) {
    this(defaultOkHttpClient());
    try {
      client.setCache(new com.squareup.okhttp.Cache(cacheDir, maxSize));
    } catch (IOException ignored) {
    }
  }

  /**
   * Create a new downloader that uses the specified OkHttp instance. A response cache will not be
   * automatically configured.
   */
  public OkHttpDownloader(OkHttpClient client) {
    this.client = client;
  }

  protected final OkHttpClient getClient() {
    return client;
  }

  @Override public Response load(Uri uri, boolean localCacheOnly) throws IOException {
    com.squareup.okhttp.Request.Builder requestBuilder =
        new com.squareup.okhttp.Request.Builder().url(uri.toString());

    if (localCacheOnly) {
      requestBuilder.addHeader("Cache-Control", "only-if-cached,max-age=" + Integer.MAX_VALUE);
    }

    com.squareup.okhttp.Response response = client.newCall(requestBuilder.build()).execute();
    int responseCode = response.code();
    if (responseCode >= 300) {
      throw new ResponseException(responseCode + " " + response.message(), localCacheOnly,
          responseCode);
    }

    boolean fromCache = response.cacheResponse() != null;

    ResponseBody responseBody = response.body();
    return new Response(responseBody.byteStream(), fromCache, responseBody.contentLength());
  }

  @Override public void shutdown() {
    com.squareup.okhttp.Cache cache = client.getCache();
    if (cache != null) {
      try {
        cache.close();
      } catch (IOException ignored) {
      }
    }
  }
}
