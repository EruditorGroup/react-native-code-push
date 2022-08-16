package com.microsoft.codepush.react.rollbacklogger;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    UncaughtExceptionHandler(@NonNull Context context, @Nullable Thread.UncaughtExceptionHandler defaultHandler) {
        this.context = context;
        this.defaultHandler = defaultHandler;
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        // We are now safely being called after Crashlytics does its own thing.
        // Whoever is the last handler on Thread.getDefaultUncaughtExceptionHandler() will execute first on uncaught exceptions.
        // Firebase Crashlytics will handle its own behavior first before calling ours in its own 'finally' block.
        // You can choose to propagate upwards (it will kill the app by default) or do your own thing and propagate if needed.
        try {
            CodePushRollbackLogger.getInstance().onNativeCrash(e);
            Thread.sleep(1000L);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (defaultHandler != null) defaultHandler.uncaughtException(t, e);
        }
    }
}
