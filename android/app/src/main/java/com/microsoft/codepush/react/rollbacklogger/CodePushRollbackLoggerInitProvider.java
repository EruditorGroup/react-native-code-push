package com.microsoft.codepush.react.rollbacklogger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

public final class CodePushRollbackLoggerInitProvider extends ContentProvider {

    private final long appStartedMls = new Date().getTime();

    @Override
    public boolean onCreate() {
        CodePushRollbackLogger logger = CodePushRollbackLogger.init(getContext());

        try {
            getContext().startService(new Intent(getContext(), ForceCloseAppAwareService.class));
        } catch (Exception e) {
            /*
            https://developer.android.com/guide/components/services
            https://developer.android.com/about/versions/oreo/background
            Starts from API level 26(Android 8), the system not allowed to start background service while app on background.
            Example of exception:
            java.lang.IllegalStateException: Not allowed to start service Intent
            */
            e.printStackTrace();
        }

        boolean isInBackground = BackgroundDetector.isInBackground(getContext());
        String appStartedEvent = isInBackground ? "App started (background)" : "App started";
        logger.log(new CodePushRollbackLogger.Event(appStartedEvent, appStartedMls));

        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
