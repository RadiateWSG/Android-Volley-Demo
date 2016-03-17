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
