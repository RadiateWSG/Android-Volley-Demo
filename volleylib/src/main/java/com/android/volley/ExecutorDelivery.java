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

import android.os.Handler;

import java.util.concurrent.Executor;

/**
 * Delivers responses and errors.
 * 请求结果传输接口具体实现类。
 * 在Handler对应线程中（RequestQueue中默认实现是主线程）传输缓存调度线程或者网络调度线程中产生的请求结果或请求错误，
 * 会在请求成功的情况下调用 Request.deliverResponse(…) 函数，
 * 失败时调用 Request.deliverError(…) 函数。
 */
public class ExecutorDelivery implements ResponseDelivery {
    /** Used for posting responses, typically to the main thread. */
    private final Executor mResponsePoster;

    /**
     * Creates a new response delivery interface.
     * @param handler {@link Handler} to post responses on
     */
    /**@mark_key
     * 该方法在RequestQueue类中被调用：new ExecutorDelivery(new Handler(Looper.getMainLooper()))
     * @see RequestQueue#RequestQueue(Cache, Network, int)
     * 可以看出mResponsePoster被初始化为向主线程发送消息的Executor。
     */
    public ExecutorDelivery(final Handler handler) {
        // Make an Executor that just wraps the handler.
        mResponsePoster = new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    /**
     * Creates a new response delivery interface, mockable version
     * for testing.
     * @param executor For running delivery tasks
     */
    public ExecutorDelivery(Executor executor) {
        mResponsePoster = executor;
    }

    @Override
    public void postResponse(Request<?> request, Response<?> response) {
        postResponse(request, response, null);
    }

    @Override
    public void postResponse(Request<?> request, Response<?> response, Runnable runnable) {
        request.markDelivered();
        request.addMarker("post-response");
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, runnable));
    }

    @Override
    public void postError(Request<?> request, VolleyError error) {
        request.addMarker("post-error");
        Response<?> response = Response.error(error);
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, null));
    }

    /**
     * A Runnable used for delivering network responses to a listener on the
     * main thread.
     */
    @SuppressWarnings("rawtypes")
    private class ResponseDeliveryRunnable implements Runnable {
        private final Request mRequest;
        private final Response mResponse;
        private final Runnable mRunnable;

        public ResponseDeliveryRunnable(Request request, Response response, Runnable runnable) {
            mRequest = request;
            mResponse = response;
            mRunnable = runnable;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            // If this request has canceled, finish it and don't deliver.
            if (mRequest.isCanceled()) {
                mRequest.finish("canceled-at-delivery");
                return;
            }

            // Deliver a normal response or error, depending.
            if (mResponse.isSuccess()) {
                mRequest.deliverResponse(mResponse.result);
            } else {
                mRequest.deliverError(mResponse.error);
            }

            // If this is an intermediate response, add a marker, otherwise we're done
            // and the request can be finished.
            if (mResponse.intermediate) {
                mRequest.addMarker("intermediate-response");
            } else {
                mRequest.finish("done");
            }

            // If we have been provided a post-delivery runnable, run it.
            if (mRunnable != null) {
                mRunnable.run();
            }
       }
    }
}
