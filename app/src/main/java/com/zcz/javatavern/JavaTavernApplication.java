package com.zcz.javatavern;

import android.app.Application;

import com.zcz.javatavern.performance.StartupTracer;

public final class JavaTavernApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        StartupTracer.markApplicationCreated();
    }
}
