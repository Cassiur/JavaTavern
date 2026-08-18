package com.zcz.javatavern.performance;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

public final class StartupTracer {
    private static final String LOG_TAG = "JavaTavernStartup";
    private static long applicationCreatedAt;

    private StartupTracer() {
    }

    public static void markApplicationCreated() {
        applicationCreatedAt = SystemClock.elapsedRealtime();
    }

    public static void trackFirstDraw(Activity activity, View rootView) {
        rootView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                rootView.getViewTreeObserver().removeOnPreDrawListener(this);
                long elapsed = SystemClock.elapsedRealtime() - applicationCreatedAt;
                Log.i(LOG_TAG, "first_draw_ms=" + elapsed);
                activity.reportFullyDrawn();
                return true;
            }
        });
    }
}
