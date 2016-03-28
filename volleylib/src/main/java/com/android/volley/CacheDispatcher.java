/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.volley;

import android.os.Process;

import java.util.concurrent.BlockingQueue;

/**
 * Provides a thread for performing cache triage on a queue of requests.
 *
 * Requests added to the specified cache queue are resolved from cache.
 * Any deliverable response is posted back to the caller via a
 * {@link ResponseDelivery}.  Cache misses and responses that require
 * refresh are enqueued on the specified network queue for processing
 * by a {@link NetworkDispatcher}.
 */
public class CacheDispatcher extends Thread {

    private static final boolean DEBUG = VolleyLog.DEBUG;

    /** The queue of requests coming in for triage. */
    private final BlockingQueue<Request<?>> mCacheQueue;

    /** The queue of requests going out to the network. */
    private final BlockingQueue<Request<?>> mNetworkQueue;

    /** The cache to read from. */
    private final Cache mCache;

    /** For posting responses. */
    private final ResponseDelivery mDelivery;

    /** Used for telling us to die. */
    private volatile boolean mQuit = false;

    /**
     * Creates a new cache triage dispatcher thread.  You must call {@link #start()}
     * in order to begin processing.
     *
     * @param cacheQueue Queue of incoming requests for triage
     * @param networkQueue Queue to post requests that require network to
     * @param cache Cache interface to use for resolution
     * @param delivery Delivery interface to use for posting responses
     */
    /**
     * @mark
     * @see RequestQueue#start() 在RequestQueue中的start()方法中被调用
     * 持有以下引用：
     * @param cacheQueue 缓存的请求队列
     * @param networkQueue 网络请求队列
     * @param cache 缓存的请求结果
     * @param delivery 请求结果的消息分发器
     */
    public CacheDispatcher(
            BlockingQueue<Request<?>> cacheQueue, BlockingQueue<Request<?>> networkQueue,
            Cache cache, ResponseDelivery delivery) {
        mCacheQueue = cacheQueue;
        mNetworkQueue = networkQueue;
        mCache = cache;
        mDelivery = delivery;
    }

    /**
     * Forces this dispatcher to quit immediately.  If any requests are still in
     * the queue, they are not guaranteed to be processed.
     */
    public void quit() {
        mQuit = true;
        interrupt();
    }

    @Override
    public void run() {
        if (DEBUG) VolleyLog.v("start new dispatcher");
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        // Make a blocking call to initialize the cache.
        mCache.initialize();

        while (true) {
            try {
                //@mark 从缓存队列中取出一个请求，如果缓存队列为空则进行阻塞。
                // Get a request from the cache triage queue, blocking until
                // at least one is available.
                final Request<?> request = mCacheQueue.take();
                request.addMarker("cache-queue-take");

                //@mark 如果该请求已经取消了，则从请求队列中删除，并继续取下一条请求。
                // If the request has been canceled, don't bother dispatching it.
                if (request.isCanceled()) {
                    request.finish("cache-discard-canceled");
                    continue;
                }

                //@mark 检查改请求结果是否在缓存结果中，
                // Attempt to retrieve this item from cache.
                Cache.Entry entry = mCache.get(request.getCacheKey());
                if (entry == null) {
                    request.addMarker("cache-miss");
                    //@mark 如果不在缓存结果中，则加入到网络请求队列中；
                    // Cache miss; send off to the network dispatcher.
                    mNetworkQueue.put(request);
                    continue;
                }

                //@mark 如果缓存结果已过期，则加入到网络请求队列中。
                // If it is completely expired, just send it to the network.
                if (entry.isExpired()) {
                    request.addMarker("cache-hit-expired");
                    request.setCacheEntry(entry);
                    mNetworkQueue.put(request);
                    continue;
                }

                //@mark 缓存命中，并调用parseNetworkResponse()方法解析为我们需要的Response。
                // We have a cache hit; parse its data for delivery back to the request.
                request.addMarker("cache-hit");
                Response<?> response = request.parseNetworkResponse(
                        new NetworkResponse(entry.data, entry.responseHeaders));
                request.addMarker("cache-hit-parsed");

                //@mark 验证缓存的新鲜度
                if (!entry.refreshNeeded()) {
                    //@mark 缓存命中，且不需要刷新，则直接传递响应的结果。
                    // Completely unexpired cache hit. Just deliver the response.
                    mDelivery.postResponse(request, response);
                } else {
                    // Soft-expired cache hit. We can deliver the cached response,
                    // but we need to also send the request to the network for
                    // refreshing.
                    request.addMarker("cache-hit-refresh-needed");
                    request.setCacheEntry(entry);

                    // Mark the response as intermediate.
                    response.intermediate = true;

                    //@mark 需要刷新的情况：传递一个intermediate Response，并带上Runnable参数，
                    //该Runnable做的操作是把该请求放入网络请求队列中。
                    // Post the intermediate response back to the user and have
                    // the delivery then forward the request along to the network.
                    mDelivery.postResponse(request, response, new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mNetworkQueue.put(request);
                            } catch (InterruptedException e) {
                                // Not much we can do about this.
                            }
                        }
                    });
                }

            } catch (InterruptedException e) {
                // We may have been interrupted because it was time to quit.
                //@mark 此处提供了退出循环的方法。
                if (mQuit) {
                    return;
                }
                continue;
            }
        }
    }
}
