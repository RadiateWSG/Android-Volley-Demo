
package com.spirit.talk.androidvolleydemo.model;

public class ActivityInfo {
	public String title;
	public Class<?> name;

	public ActivityInfo(String title, Class<?> name) {
		this.title = title;
		this.name = name;
	}

	public String toString() {
		return title;
	}
}
