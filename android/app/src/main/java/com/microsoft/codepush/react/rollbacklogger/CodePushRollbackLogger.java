package com.microsoft.codepush.react.rollbacklogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CodePushRollbackLogger {
    private static final String LOG_TAG = "CodePushRollbackLogger";
    private static final String EVENTS_KEY = "EVENTS_KEY";
    private static final String ROLLBACK_REASON_KEY = "ROLLBACK_REASON_KEY";
    private static final String EVENT_JS_STARTED = "JS started";

    private static CodePushRollbackLogger instance;

    static CodePushRollbackLogger init(@NonNull Context appContext) {
        if (instance != null) {
            throw new IllegalStateException("init() should be called once");
        }
        instance = new CodePushRollbackLogger(appContext);
        return instance;
    }

    public static CodePushRollbackLogger getInstance() {
        if (instance == null) {
            throw new IllegalStateException("init() should be called first");
        }
        return instance;
    }

    private final SharedPreferences storage;
    private final SimpleDateFormat dateFormatter;
    private final Gson gson = new Gson();

    private ArrayList<Event> events = new ArrayList<>();
    private boolean isJSStarted = false;
    private boolean isBundleWaitNotifying = false;
    private boolean wasJSExceptionWhileBundleWaitNotifying = false;

    @Nullable
    private CodePushRollbackReporter rollbackReporter;
    @Nullable
    private CodePushRollbackExceptionReporter exceptionReporter;

    private CodePushRollbackLogger(@NonNull Context appContext) {
        storage = appContext.getSharedPreferences("CODE_PUSH_LOGGER_KEY", Context.MODE_PRIVATE);
        dateFormatter = new SimpleDateFormat("dd/MMM/yyyy HH:mm:ss:sss z");
        try {
            final String eventsStr = storage.getString(EVENTS_KEY, null);
            if (eventsStr != null) {
                events = gson.fromJson(eventsStr, new TypeToken<ArrayList<Event>>() {
                }.getType());
            }
        } catch (Throwable e) {
            onCatch(e);
        }
    }

    public void setRollbackReporter(@NonNull CodePushRollbackReporter rollbackReporter) {
        this.rollbackReporter = rollbackReporter;
    }

    public void setExceptionReporter(@NonNull CodePushRollbackExceptionReporter exceptionReporter) {
        this.exceptionReporter = exceptionReporter;
    }

    public void log(@NonNull String value) {
        log(new Event(value));
    }

    public void log(@NonNull Throwable value) {
        log(new Event(stackTraceToString(value)));
    }

    public void log(@NonNull Event event) {
        Log.d(LOG_TAG, event.toString());
        try {
            if (event.value.contains(EVENT_JS_STARTED)) isJSStarted = true;
            events.add(event);
            storage.edit().putString(EVENTS_KEY, gson.toJson(events)).apply();
        } catch (Throwable e) {
            onCatch(e);
        }
    }

    public void onWaitingNotifyAppReady() {
        isBundleWaitNotifying = true;
        log("onWaitingNotifyAppReady: Bundle running first time");
    }

    public void onNotifyAppReady() {
        Log.d(LOG_TAG, "onNotifyAppReady");
        clearState();
    }

    public void onJSError(@NonNull Throwable value) {
        onJSExceptionInternal(value, CodePushRollbackReason.JS_ERROR, "onJSError");
    }

    public void onJSUnhPromiseRejection(@NonNull Throwable value) {
        onJSExceptionInternal(value, CodePushRollbackReason.JS_UNH_PROMISE_REJECTION, "onJSUnhPromiseRejection");
    }

    private void onJSExceptionInternal(@NonNull Throwable value, @NonNull CodePushRollbackReason reason, @NonNull String logPrefix) {
        try {
            if (!isBundleWaitNotifying) {
                Log.d(LOG_TAG, logPrefix + ": isBundleWaitNotifying=false -> skip");
                return;
            }
            wasJSExceptionWhileBundleWaitNotifying = true;
            storage.edit().putString(ROLLBACK_REASON_KEY, reason.name()).commit();
            log(logPrefix + ": " + stackTraceToString(value));
            Log.d(LOG_TAG, logPrefix + ": " + "CodePushRollbackReason=" + reason);
        } catch (Throwable e) {
            onCatch(e);
        }
    }

    public void onNativeCrash(@NonNull Throwable value) {
        try {
            if (!isBundleWaitNotifying) {
                Log.d(LOG_TAG, "onNativeCrash: isBundleWaitNotifying=false -> skip");
                return;
            }
            storage.edit().putString(ROLLBACK_REASON_KEY, CodePushRollbackReason.NATIVE_CRASH.name()).commit();
            log("onNativeCrash: " + stackTraceToString(value));
            Log.d(LOG_TAG, "onNativeCrash: CodePushRollbackReason=NATIVE_CRASH");
        } catch (Throwable e) {
            onCatch(e);
        }
    }

    public void onForceCloseApp() {
        try {
            if (!isBundleWaitNotifying) {
                Log.d(LOG_TAG, "onForceCloseApp: isBundleWaitNotifying=false -> skip");
                clearState();
                return;
            }
            if (wasJSExceptionWhileBundleWaitNotifying) {
                Log.d(LOG_TAG, "onForceCloseApp: wasJSExceptionWhileBundleWaitNotifying=true -> skip");
                return;
            }
            final CodePushRollbackReason reason = isJSStarted
                    ? CodePushRollbackReason.FORCE_QUIT_SUPPOSE_SLOW_JS
                    : CodePushRollbackReason.FORCE_QUIT_SUPPOSE_SLOW_NATIVE;
            storage.edit().putString(ROLLBACK_REASON_KEY, reason.name()).commit();
            log("onForceCloseApp: supposedCodePushRollbackReason=" + reason);
        } catch (Throwable e) {
            onCatch(e);
        }
    }

    public void onRollback() {
        Log.d(LOG_TAG, "onRollback");
        try {
            events.add(new Event("onRollback"));
            final List<Event> tmpEvents = (ArrayList<Event>) events.clone();

            final String reasonStr = storage.getString(ROLLBACK_REASON_KEY, null);
            CodePushRollbackReason reasonEnum;
            try {
                reasonEnum = CodePushRollbackReason.valueOf(reasonStr);
            } catch (Exception e) {
                reasonEnum = CodePushRollbackReason.UNKNOWN;
            }
            final Exception reason;
            switch (reasonEnum) {
                case JS_ERROR:
                    reason = new CodePushRollbackByJSError();
                    break;
                case JS_UNH_PROMISE_REJECTION:
                    reason = new CodePushRollbackByJSUnhPromiseRejection();
                    break;
                case NATIVE_CRASH:
                    reason = new CodePushRollbackByNativeCrash();
                    break;
                case FORCE_QUIT_SUPPOSE_SLOW_JS:
                    reason = new CodePushRollbackByForceQuitSupposeSlowJS();
                    break;
                case FORCE_QUIT_SUPPOSE_SLOW_NATIVE:
                    reason = new CodePushRollbackByForceQuitSupposeSlowNative();
                    break;
                default:
                    reason = new CodePushRollbackByUnknown();
            }
            clearState();

            new Thread(() -> {
                try {
                    final List<String> formattedEvents = new ArrayList<>(tmpEvents.size());
                    for (Event event : tmpEvents) {
                        formattedEvents.add(dateFormatter.format(event.mls) + ":" + event.value);
                    }
                    Log.d(LOG_TAG, "onRollback: reason=" + reasonStr + ", events=" + tmpEvents);

                    if (rollbackReporter != null) {
                        rollbackReporter.report(formattedEvents, reason);
                    } else {
                        Log.d(LOG_TAG, "Skipping onRollback reporting: rollbackReporter is null");
                    }
                } catch (Throwable e) {
                    onCatch(e);
                }
            }).start();
        } catch (Throwable e) {
            onCatch(e);
        }
    }

    private void clearState() {
        Log.d(LOG_TAG, "clearState");
        try {
            isJSStarted = false;
            isBundleWaitNotifying = false;
            wasJSExceptionWhileBundleWaitNotifying = false;
            events.clear();

            final SharedPreferences.Editor editor = storage.edit();
            editor.remove(EVENTS_KEY);
            editor.remove(ROLLBACK_REASON_KEY);
            editor.apply();
        } catch (Throwable e) {
            onCatch(e);
        }
    }

    private void onCatch(@NonNull Throwable e) {
        Log.e(LOG_TAG, e.getMessage(), e);
        if (exceptionReporter != null) exceptionReporter.report(e);
    }

    private String stackTraceToString(@NonNull Throwable value) {
        try {
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            value.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Throwable e) {
            onCatch(e);
            return value.getMessage();
        }
    }

    public static class Event {
        public final String value;
        public final long mls;

        public Event(@NonNull String value) {
            this(value, new Date().getTime());
        }

        public Event(@NonNull String value, @NonNull Long mls) {
            this.value = value;
            this.mls = mls;
        }

        @Override
        public String toString() {
            return "Event{" +
                    "value='" + value + '\'' +
                    ", mls=" + mls +
                    '}';
        }
    }

    public enum CodePushRollbackReason {
        UNKNOWN,
        JS_ERROR,
        JS_UNH_PROMISE_REJECTION,
        NATIVE_CRASH,
        FORCE_QUIT_SUPPOSE_SLOW_JS,
        FORCE_QUIT_SUPPOSE_SLOW_NATIVE,
    }

    public interface CodePushRollbackReporter {
        void report(@NonNull List<String> events, @NonNull Throwable reason);
    }

    public interface CodePushRollbackExceptionReporter {
        void report(@NonNull Throwable exception);
    }

    public static class CodePushRollbackByUnknown extends Exception {
    }

    public static class CodePushRollbackByJSError extends Exception {
    }

    public static class CodePushRollbackByJSUnhPromiseRejection extends Exception {
    }

    public static class CodePushRollbackByNativeCrash extends Exception {
    }

    public static class CodePushRollbackByForceQuitSupposeSlowJS extends Exception {
    }

    public static class CodePushRollbackByForceQuitSupposeSlowNative extends Exception {
    }
}
