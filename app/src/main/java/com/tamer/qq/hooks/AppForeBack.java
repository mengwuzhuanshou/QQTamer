package com.tamer.qq.hooks;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 前台/后台跟踪：通过 Application 生命周期回调统计 started Activity 数。
 * 所有“仅后台生效”的省电钩子都以此为准；前台状态绝不干预。
 * 说明：匿名回调按编译桩签名声明（运行时同名同签名，注册即生效）。
 */
public final class AppForeBack {
    private static final AtomicBoolean sInstalled = new AtomicBoolean(false);
    private static final AtomicBoolean sFgLogged = new AtomicBoolean(false);
    private static final AtomicBoolean sBgLogged = new AtomicBoolean(false);
    private static final AtomicInteger sStarted = new AtomicInteger(0);
    private static volatile boolean sForeground = true;
    private static final CopyOnWriteArrayList<Runnable> sBgEnter = new CopyOnWriteArrayList<Runnable>();
    private static final CopyOnWriteArrayList<Runnable> sFgEnter = new CopyOnWriteArrayList<Runnable>();

    public static boolean isForeground() { return sForeground; }

    public static void addOnBackgroundEnter(Runnable r) { if (r != null) sBgEnter.add(r); }
    public static void addOnForegroundEnter(Runnable r) { if (r != null) sFgEnter.add(r); }

    private static void enterBackground() {
        sForeground = false;
        if (sBgLogged.compareAndSet(false, true)) {
            HookUtil.log("background entered (once per cold start)");
        }
        for (Runnable r : sBgEnter) {
            try { r.run(); } catch (Throwable t) { HookUtil.log("bg-enter task failed: " + t); }
        }
    }

    private static void enterForeground() {
        sForeground = true;
        if (sFgLogged.compareAndSet(false, true)) {
            HookUtil.log("foreground entered (once per cold start)");
        }
        for (Runnable r : sFgEnter) {
            try { r.run(); } catch (Throwable t) { HookUtil.log("fg-enter task failed: " + t); }
        }
    }

    /** hook Application.onCreate 注册生命周期回调（主进程内只装一次） */
    public static void install(final ClassLoader cl) {
        if (!sInstalled.compareAndSet(false, true)) return;
        try {
            Class<?> appCls = XposedHelpers.findClass("android.app.Application", cl);
            XC_MethodHook after = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object app = param.thisObject;
                        if (app == null) return;
                        Application.ActivityLifecycleCallbacks cb = new Application.ActivityLifecycleCallbacks() {
                            @Override public void onActivityCreated(Activity a, Bundle b) { }
                            @Override public void onActivityStarted(Activity a) {
                                int n = sStarted.incrementAndGet();
                                if (n == 1 && !sForeground) enterForeground();
                            }
                            @Override public void onActivityResumed(Activity a) { }
                            @Override public void onActivityPaused(Activity a) { }
                            @Override public void onActivityStopped(Activity a) {
                                int n = sStarted.decrementAndGet();
                                if (n <= 0 && sForeground) enterBackground();
                            }
                            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                            @Override public void onActivityDestroyed(Activity a) { }
                        };
                        XposedHelpers.callMethod(app, "registerActivityLifecycleCallbacks", cb);
                        HookUtil.log("lifecycle callbacks registered on " + app.getClass().getName());
                    } catch (Throwable t) {
                        HookUtil.log("register lifecycle callbacks failed: " + t);
                    }
                }
            };
            XposedBridge.hookAllMethods(appCls, "onCreate", after);
            HookUtil.log("hookedAll Application#onCreate");
        } catch (Throwable t) {
            HookUtil.log("AppForeBack install failed: " + t);
        }
    }

    private AppForeBack() {}
}