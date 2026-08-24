package com.tamer.qq;

import com.tamer.qq.hooks.AppForeBack;
import com.tamer.qq.hooks.HookUtil;
import com.tamer.qq.hooks.PowerSaver;
import com.tamer.qq.hooks.SplashAdBlocker;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * QQTamer —— 手机 QQ(com.tencent.mobileqq) 净化/省电/去开屏广告 LSPosed 模块。
 * 设计原则（风控优先）：
 *  1) 只做 Java 层钩子，不碰 native 库、不碰 MSF 协议与登录链路（参考手表QQ魔改的改动面）；
 *  2) 省电类钩子仅后台生效，前台体验零改动；音视频场景白名单；
 *  3) 全部开关独立、默认保守，任一 hook 失败不影响其它功能与宿主运行。
 */
public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        if (!QQConfig.TARGET_PKG.equals(lpp.packageName)) {
            return;
        }
        // 仅主进程生效：:tool/:miniapp/:qzone 等子进程一律跳过，减少面
        if (lpp.processName != null && !lpp.processName.equals(QQConfig.TARGET_PKG)) {
            HookUtil.log("skip non-main process: " + lpp.processName);
            return;
        }
        HookUtil.log("handleLoadPackage pkg=" + lpp.packageName + " process=" + lpp.processName);
        XSharedPreferences xsp = new XSharedPreferences(QQConfig.MODULE_PKG, QQConfig.PREFS_NAME);
        final QQConfig cfg = QQConfig.loadForHook(xsp);
        if (cfg.get(QQConfig.KEY_DEBUG_ALIVE, false)) writeAliveMarker();
        try {
            java.io.File pf = xsp.getFile();
            HookUtil.log("prefs=" + (pf == null ? "null" : pf.getPath())
                    + " canRead=" + (pf != null && pf.canRead()));
        } catch (Throwable ignored) {}
        HookUtil.log("confSrc=" + QQConfig.sConfSource + " effective: master=" + cfg.get(QQConfig.KEY_MASTER, true)
                + " keepalive=" + cfg.get(QQConfig.KEY_KEEPALIVE, true)
                + " bgSvc=" + cfg.get(QQConfig.KEY_BG_SERVICES, true)
                + " wlCap=" + cfg.get(QQConfig.KEY_WAKELOCK_CAP, true)
                + " alarm=" + cfg.get(QQConfig.KEY_ALARM_RELAX, false)
                + " tombstone=" + cfg.get(QQConfig.KEY_TOMBSTONE, false)
                + " keepPush=" + cfg.get(QQConfig.KEY_TOMBSTONE_KEEP_PUSH, true)
                + " splashAd=" + cfg.get(QQConfig.KEY_SPLASH_AD, true)
                + " alive=" + cfg.get(QQConfig.KEY_DEBUG_ALIVE, false));

        if (!cfg.get(QQConfig.KEY_MASTER, true)) {
            HookUtil.log("module disabled by master switch");
            return;
        }

        final ClassLoader cl = lpp.classLoader;
        // 前后台跟踪最先装（其余钩子的“仅后台”判定依赖它）
        safe("AppForeBack", new Thunk() { public void run() { AppForeBack.install(cl); } });
        safe("SplashAdBlocker", new Thunk() { public void run() { SplashAdBlocker.hook(cl, cfg); } });
        safe("PowerSaver", new Thunk() { public void run() { PowerSaver.hook(cl, cfg); } });
    }

    interface Thunk { void run() throws Throwable; }

    private static void safe(String name, Thunk t) {
        try {
            t.run();
        } catch (Throwable tr) {
            HookUtil.log(name + " init failed: " + tr);
        }
    }

    /** 在目标应用 files 目录写存活标记（64KB 封顶），便于无 logcat 时肉眼确认 */
    private static void writeAliveMarker() {
        try {
            java.io.File dir = new java.io.File("/data/data/" + QQConfig.TARGET_PKG + "/files");
            java.io.File f = new java.io.File(dir, "qq_tamer_alive.txt");
            if (f.length() > 64 * 1024) {
                f.delete();
            }
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write("loaded at " + new java.util.Date() + " pid=" + android.os.Process.myPid() + "\n");
            w.close();
        } catch (Throwable t) {
            android.util.Log.i(HookUtil.TAG, "marker write failed: " + t);
        }
    }
}