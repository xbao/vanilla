package ch.blinkenlights.android.vanilla;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

public class VanillaApp extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		LeakCanary.install(this);
	}
}
