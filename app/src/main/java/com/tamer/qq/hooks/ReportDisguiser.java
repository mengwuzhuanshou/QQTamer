package com.tamer.qq.hooks;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * 伪装回报组：
 * 1) 环境探测清洗 —— PackageUtil.isAppInstalled 对 xposed/magisk/su 等关键词一律回报未安装。
    实测锚点：com.tencent.qqperf.monitor.crash.c 会把 isXposedInstalled 结果写进崩溃诊断上报
    （"isXposedInstalled: " + recordXposedInfo），清洗后该报告呈现为纯净环境。
 * 2) 后台 beacon 静默（默认关）—— hook QQBeaconReport.realReport，后台丢弃埋点。
 */
public final class ReportDisguiser {

    private static final String[] SENSITIVE_PKG_KEYWORDS = {
            "xposed", "lsposed", "edxposed", "de.robv", "magisk",
            "superuser", "supersu", "chainfire", "busybox", "substrate",
            "taichi", "virtualxposed", "riru", "zygisk",
    };

    private static final AtomicInteger sCleanCount = new AtomicInteger(0);
    private static final AtomicInteger sQuietCount = new AtomicInteger(0);

    public static void hook(final ClassLoader cl, final com.tamer.qq.QQConfig cfg) {
        final boolean envClean = cfg.get(com.tamer.qq.QQConfig.KEY_ENV_CLEAN, true);
        final boolean quiet = cfg.get(com.tamer.qq.QQConfig.KEY_BEACON_QUIET, false);

        if (envClean) {
            HookUtil.tryHookFlex(cl, "com.tencent.mobileqq.utils.PackageUtil", "isAppInstalled", 2,
                    new HookUtil.FlexCallback() {
                        @Override public void fire(MethodHookParam param) {
                            try {
                                Object a1 = param.args != null && param.args.length > 1 ? param.args[1] : null;
                                if (!(a1 instanceof String)) return;
                                String pkg = ((String) a1).toLowerCase();
                                for (String k : SENSITIVE_PKG_KEYWORDS) {
                                    if (pkg.contains(k)) {
                                        if (HookUtil.sample(sCleanCount, 5, 50)) {
                                            HookUtil.log("env-clean: report \"" + a1
                                                    + "\" as not-installed #" + sCleanCount.get());
                                        }
                                        param.setResult(Boolean.FALSE);
                                        return;
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }

        if (quiet) {
            HookUtil.tryHookAll(cl, "com.tencent.mobileqq.statistics.QQBeaconReport", "realReport",
                    new de.robv.android.xposed.XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!com.tamer.qq.hooks.AppForeBack.isForeground()) {
                                    if (HookUtil.sample(sQuietCount, 5, 100)) {
                                        Object code = param.args != null && param.args.length > 2
                                                ? param.args[2] : "?";
                                        HookUtil.log("beacon quiet(background): drop event=" + code
                                                + " #" + sQuietCount.get());
                                    }
                                    param.setResult(null);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }
    }

    private ReportDisguiser() {}
}