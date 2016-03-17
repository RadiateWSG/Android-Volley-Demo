package com.spirit.talk.androidvolleydemo.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.spirit.talk.androidvolleydemo.data.RequestManager;

public class BaseActivity extends Activity {
	protected Activity activity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		activity = this;
	}

	@Override
	public void onStop() {
		super.onStop();
		RequestManager.cancelAll(this);
	}

	protected void executeRequest(Request<?> request) {
		RequestManager.addRequest(request, this);
	}

	protected Response.ErrorListener errorListener() {
		return new Response.ErrorListener() {
			@Override
			public void onErrorResponse(VolleyError error) {
				Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
			}
		};
	}
}
