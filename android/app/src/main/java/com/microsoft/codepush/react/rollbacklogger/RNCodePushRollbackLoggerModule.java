package com.microsoft.codepush.react.rollbacklogger;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class RNCodePushRollbackLoggerModule extends ReactContextBaseJavaModule {
    @NonNull
    @Override
    public String getName() {
        return "RNCodePushRollbackLogger";
    }

    @ReactMethod
    public void log(String value, double mls) {
        CodePushRollbackLogger.getInstance().log(new CodePushRollbackLogger.Event(value, (long) mls));
    }
}
