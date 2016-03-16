/*
 * Created by Storm Zhang, Feb 11, 2014.
 */

package com.spirit.talk.androidvolleydemo;

import android.app.Application;

import com.spirit.talk.androidvolleydemo.data.RequestManager;


public class VolleyApp extends Application {
	@Override
    public void onCreate() {
        super.onCreate();
        init();
    }


    private void init() {
        RequestManager.init(this);
    }
}
