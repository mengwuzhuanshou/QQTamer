package com.tamer.qq.hooks;

import java.util.HashMap;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * 开屏广告拦截（QQ 9.x vas-splash 管线）：
 * - com.tencent.mobileqq.vassplash.common#c.a(String,Set) 是缓存广告读取的唯一出口，
 *   返回空 Map = 冷启动无预取广告可放；
 * - com.tencent.mobileqq.splashad#w.h(SplashActivity,QQAppInterface) 是广告跳转闸门
 *   （SplashActivity.dealFromSplashAD 调用），置 false = 广告后的拉起/跳转全部短路。
 * 已知边界：GDT(gdtad) 自身的实时拉新链路未在 v1 拦截（见 README 已知限制）。
 */
public final class SplashAdBlocker {

    public static void hook(final ClassLoader cl, final com.tamer.qq.QQConfig cfg) {
        final boolean on = cfg.get(com.tamer.qq.QQConfig.KEY_SPLASH_AD, true);
        if (!on) return;

        // 缓存读取 -> 空 Map（保持返回类型一致，调用方按无缓存处理）
        HookUtil.tryHookFlex(cl, "com.tencent.mobileqq.vassplash.common.c", "a", 2,
                new HookUtil.FlexCallback() {
                    @Override public void fire(MethodHookParam param) {
                        param.setResult(new HashMap<Object, Object>());
                    }
                });

        // 广告后跳转闸门 -> false（dealFromSplashAD 返回 false = 走正常主页路径）
        HookUtil.tryHookFlex(cl, "com.tencent.mobileqq.splashad.w", "h", 2,
                new HookUtil.FlexCallback() {
                    @Override public void fire(MethodHookParam param) {
                        param.setResult(Boolean.FALSE);
                    }
                });
    }

    private SplashAdBlocker() {}
}