package com.take2.launcher;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

public class SystemBarApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                View content = activity.findViewById(android.R.id.content);
                if (content == null) return;
                content.setOnApplyWindowInsetsListener((view, insets) -> {
                    int left = insets.getSystemWindowInsetLeft();
                    int top = insets.getSystemWindowInsetTop();
                    int right = insets.getSystemWindowInsetRight();
                    int bottom = insets.getSystemWindowInsetBottom();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        DisplayCutout cutout = insets.getDisplayCutout();
                        if (cutout != null) {
                            left = Math.max(left, cutout.getSafeInsetLeft());
                            top = Math.max(top, cutout.getSafeInsetTop());
                            right = Math.max(right, cutout.getSafeInsetRight());
                            bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                        }
                    }
                    view.setPadding(left, top, right, bottom);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return WindowInsets.CONSUMED;
                    return insets.consumeSystemWindowInsets();
                });
                content.requestApplyInsets();
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }
}
