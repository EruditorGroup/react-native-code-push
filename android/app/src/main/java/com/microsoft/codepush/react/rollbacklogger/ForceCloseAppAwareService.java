package com.microsoft.codepush.react.rollbacklogger;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public final class ForceCloseAppAwareService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        CodePushRollbackLogger.getInstance().onForceCloseApp();
        this.stopSelf();
    }
}
