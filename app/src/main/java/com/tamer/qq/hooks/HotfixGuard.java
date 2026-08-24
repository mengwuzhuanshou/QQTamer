package com.tamer.qq.hooks;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * 热更新守卫：
 * 1) QFix 重定向中心失效 —— PatchRedirectCenter.getRedirector(int) 一律返回 null。
    QQ 全部补丁类都遵循 "if (r == null || !r.hasPatch(id)) 走原逻辑" 的空安全范式
    （已抽查 QFixApplicationImpl/CoreService/splashad.w/QQBeaconReport 等），
    因此返回 null 是构造性安全的：所有热修补丁退化为基线代码。
    附带效果：QQBeaconReport 上报的 param_patch_version 恒为初始值。
 * 2) DexPatchInstaller.installDexPatch(Context,boolean) 直接吞掉（RFix dex 补丁入口）。
 * 3) Tinker 补丁前台服务 TinkerPatchForeService 任何状态下都不允许启动。
 */
public final class HotfixGuard {

    private static final String[] TINKER_SERVICE_SUFFIXES = { "TinkerPatchForeService" };

    public static void hook(final ClassLoader cl, final com.tamer.qq.QQConfig cfg) {
        final boolean redirect = cfg.get(com.tamer.qq.QQConfig.KEY_QFIX_REDIRECT, true);
        final boolean dexpatch = cfg.get(com.tamer.qq.QQConfig.KEY_DEX_PATCH, true);

        if (redirect) {
            HookUtil.tryHookFlex(cl, "com.tencent.mobileqq.qfix.redirect.PatchRedirectCenter",
                    "getRedirector", 1, new HookUtil.FlexCallback() {
                        @Override public void fire(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });
        }

        if (dexpatch) {
            HookUtil.tryHookFlex(cl, "com.tencent.hotpatch.DexPatchInstaller", "installDexPatch", 2,
                    new HookUtil.FlexCallback() {
                        @Override public void fire(MethodHookParam param) {
                            HookUtil.log("blocked DexPatchInstaller.installDexPatch");
                            param.setResult(null);
                        }
                    });
            // Tinker 服务无条件拦截（与前后台无关）
            installTinkerServiceFilter(cl);
        }
    }

    private static void installTinkerServiceFilter(ClassLoader cl) {
        try {
            Class<?> cw = android.content.ContextWrapper.class;
            de.robv.android.xposed.XC_MethodHook filter = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object arg0 = param.args != null && param.args.length > 0 ? param.args[0] : null;
                        if (!(arg0 instanceof android.content.Intent)) return;
                        android.content.ComponentName cn = ((android.content.Intent) arg0).getComponent();
                        if (cn == null) return;
                        String name = cn.getClassName();
                        if (name == null) return;
                        for (String suf : TINKER_SERVICE_SUFFIXES) {
                            if (name.endsWith(suf)) {
                                HookUtil.log("blocked tinker service start");
                                param.setResult(null);
                                return;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            de.robv.android.xposed.XposedBridge.hookAllMethods(cw, "startService", filter);
            HookUtil.log("tinker service filter installed");
        } catch (Throwable t) {
            HookUtil.log("installTinkerServiceFilter failed: " + t);
        }
    }

    private HotfixGuard() {}
}