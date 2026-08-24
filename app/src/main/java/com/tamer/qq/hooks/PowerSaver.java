package com.tamer.qq.hooks;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedHelpers;

/**
 * 省电组：保活服务拦截 / 杂项后台服务拦截 / WakeLock 截断 / 精确闹钟降级 / 墓碑模式。
 * 参考实现：NTQQBattery(墓碑模式+服务拦截)、TSBattery(后台唤醒抑制)。
 * 原则：只在后台生效、不碰 MSF 心跳与登录链路、音视频场景白名单。
 */
public final class PowerSaver {

    private static final long WAKELOCK_CAP_MS = 60_000L;

    private static final String CORE_SERVICE = "com.tencent.mobileqq.app.CoreService";
    private static final String[] BG_SERVICE_SUFFIXES = {
            "WadlProxyService",           // 游戏中心常驻代理
            "WadlJsBridgeService",
            "WadlNotificationService",
            "YunGameService",             // 云游戏
            "WinkPublishService",         // 小世界发布
            "ColorNoteSmallScreenService",// 彩签小屏
            "Ilink2Service",              // luggage ilink 保活
            "Ilink2KeepAliveService",
    };
    private static final String[] TOMBSTONE_STOP_SUFFIXES = {
            "WadlProxyService", "WadlJsBridgeService", "WadlNotificationService",
            "YunGameService", "WinkPublishService", "ColorNoteSmallScreenService",
            "Ilink2KeepAliveService",
    };
    /** 音视频/导航等前台媒体场景白名单：这些 wakelock 不截断 */
    private static final String[] WL_EXEMPT_KEYWORDS = {
            "audio", "video", "music", "play", "player", "nav", "gps", "call",
    };
    /** 消息推送相关保活：墓碑/后台拦截时豁免的服务与唤醒锁关键词（MSF/推送/通知/信令） */
    private static final String[] PUSH_EXEMPT_KEYWORDS = {
            "msf", "push", "notify", "qpush", "wup", "signal", "msg",
    };
    private static volatile boolean sKeepPush = true;

    private static final ThreadLocal<Boolean> sInWakeLock = new ThreadLocal<Boolean>();
    /** WakeLock 实例 -> 是否豁免（IdentityHashMap 缓存 getTag 反射与关键词循环；上限 256 自动清空） */
    private static final java.util.Map<Object, Boolean> sWlExemptCache =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<Object, Boolean>());
    private static final AtomicInteger sWlCapCount = new AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicBoolean sBgStopLogged =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean sTombstoneLogged =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final AtomicInteger sSvcBlockCount = new AtomicInteger(0);
    private static volatile boolean sCoreKilledThisBg = false;

    public static void hook(final ClassLoader cl, final com.tamer.qq.QQConfig cfg) {

        final boolean keepAlive = cfg.get(com.tamer.qq.QQConfig.KEY_KEEPALIVE, true);
        final boolean bgSvc = cfg.get(com.tamer.qq.QQConfig.KEY_BG_SERVICES, true);
        final boolean wlCap = cfg.get(com.tamer.qq.QQConfig.KEY_WAKELOCK_CAP, true);
        final boolean alarmRelax = cfg.get(com.tamer.qq.QQConfig.KEY_ALARM_RELAX, false);
        final boolean tombstone = cfg.get(com.tamer.qq.QQConfig.KEY_TOMBSTONE, false);
        sKeepPush = cfg.get(com.tamer.qq.QQConfig.KEY_TOMBSTONE_KEEP_PUSH, true);

        if (keepAlive || tombstone) {
            // startCoreService(boolean)：后台期间不允许拉起保活前台服务
            HookUtil.tryHookFlex(cl, CORE_SERVICE, "startCoreService", 1, new HookUtil.FlexCallback() {
                @Override public void fire(MethodHookParam param) {
                    if (!com.tamer.qq.hooks.AppForeBack.isForeground()) {
                        if (HookUtil.sample(sSvcBlockCount, 5, 50)) {
                            HookUtil.log("blocked CoreService.startCoreService (background) #"
                                    + sSvcBlockCount.get());
                        }
                        param.setResult(null);
                    }
                }
            });
            // startTempService()：API<25 的临时内核服务，同样只在后台拦
            HookUtil.tryHookFlex(cl, CORE_SERVICE, "startTempService", 0, new HookUtil.FlexCallback() {
                @Override public void fire(MethodHookParam param) {
                    if (!com.tamer.qq.hooks.AppForeBack.isForeground()) {
                        param.setResult(null);
                    }
                }
            });
        }

        if (keepAlive || bgSvc) {
            // ContextWrapper.startService：按组件类名后缀过滤杂项服务（仅后台）
            installStartServiceFilter(cl, BG_SERVICE_SUFFIXES, bgSvc);
        }

        if (wlCap) {
            installWakelockCap(cl);
        }

        if (alarmRelax) {
            installAlarmRelax(cl);
        }

        if (tombstone || keepAlive) {
            com.tamer.qq.hooks.AppForeBack.addOnBackgroundEnter(new Runnable() {
                @Override public void run() {
                    if (sCoreKilledThisBg) return;
                    sCoreKilledThisBg = true;
                    if (keepAlive) stopCoreServiceQuietly(cl);
                    if (tombstone) stopSuffixServicesQuietly(cl);
                }
            });
            com.tamer.qq.hooks.AppForeBack.addOnForegroundEnter(new Runnable() {
                @Override public void run() { sCoreKilledThisBg = false; }
            });
        }
    }

    private static void installStartServiceFilter(ClassLoader cl,
            final String[] suffixes, final boolean onlyBackground) {
        try {
            // 直接引用框架类，避免 LSPosed 对模块内 findClass 框架类名的改写（android$app$ContextWrapper）
            Class<?> cw = android.content.ContextWrapper.class;
            de.robv.android.xposed.XC_MethodHook filter = new de.robv.android.xposed.XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (onlyBackground && com.tamer.qq.hooks.AppForeBack.isForeground()) return;
                        Object arg0 = param.args != null && param.args.length > 0 ? param.args[0] : null;
                        if (!(arg0 instanceof android.content.Intent)) return;
                        android.content.ComponentName cn = ((android.content.Intent) arg0).getComponent();
                        if (cn == null) return;
                        String name = cn.getClassName();
                        if (name == null) return;
                        // 消息推送保活豁免：墓碑/后台拦截不碰 MSF 与推送链路
                        if (sKeepPush && isPushName(name)) {
                            return;
                        }
                        for (String suf : suffixes) {
                            if (name.endsWith(suf)) {
                                if (HookUtil.sample(sSvcBlockCount, 5, 50)) {
                                    HookUtil.log("blocked startService " + name + " #" + sSvcBlockCount.get());
                                }
                                param.setResult(null);
                                return;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            de.robv.android.xposed.XposedBridge.hookAllMethods(cw, "startService", filter);
            HookUtil.log("startService filter installed (" + suffixes.length + " suffixes, bg=" + onlyBackground + ")");
        } catch (Throwable t) {
            HookUtil.log("installStartServiceFilter failed: " + t);
        }
    }

    private static void installWakelockCap(final ClassLoader cl) {
        HookUtil.tryHookAll(cl, "android.os.PowerManager$WakeLock", "acquire",
                new de.robv.android.xposed.XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (com.tamer.qq.hooks.AppForeBack.isForeground()) return;
                            Boolean guard = sInWakeLock.get();
                            if (guard != null && guard.booleanValue()) return;
                            String tag = null;
                            Boolean cachedExempt = sWlExemptCache.get(param.thisObject);
                            if (cachedExempt == null) {
                                try {
                                    Object t = XposedHelpers.callMethod(param.thisObject, "getTag");
                                    tag = t instanceof String ? (String) t : null;
                                } catch (Throwable ignored) {}
                                boolean exempt = isExempt(tag);
                                if (sWlExemptCache.size() > 256) {
                                    sWlExemptCache.clear();
                                }
                                sWlExemptCache.put(param.thisObject, Boolean.valueOf(exempt));
                                cachedExempt = Boolean.valueOf(exempt);
                            }
                            if (cachedExempt.booleanValue()) return;
                            int argc = param.args == null ? 0 : param.args.length;
                            if (argc == 0) {
                                // 无限期持有 -> 改为有限期 CAP
                                sInWakeLock.set(Boolean.TRUE);
                                try {
                                    XposedHelpers.callMethod(param.thisObject, "acquire",
                                            Long.valueOf(WAKELOCK_CAP_MS));
                                } finally {
                                    sInWakeLock.set(null);
                                }
                                if (HookUtil.sample(sWlCapCount, 10, 100)) {
                                    HookUtil.log("wakelock capped(indefinite->" + WAKELOCK_CAP_MS + "ms) tag="
                                            + tag + " #" + sWlCapCount.get());
                                }
                                param.setResult(null);
                            } else if (argc == 1 && param.args[0] instanceof Long) {
                                long timeout = ((Long) param.args[0]).longValue();
                                if (timeout > WAKELOCK_CAP_MS) {
                                    param.args[0] = Long.valueOf(WAKELOCK_CAP_MS);
                                    if (HookUtil.sample(sWlCapCount, 10, 100)) {
                                        HookUtil.log("wakelock capped(" + timeout + "->" + WAKELOCK_CAP_MS
                                                + "ms) tag=" + tag + " #" + sWlCapCount.get());
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
    }

    private static boolean isExempt(String tag) {
        if (tag == null) return false;
        String lower = tag.toLowerCase();
        for (String k : WL_EXEMPT_KEYWORDS) {
            if (lower.contains(k)) return true;
        }
        if (sKeepPush) {
            for (String k : PUSH_EXEMPT_KEYWORDS) {
                if (lower.contains(k)) return true;
            }
        }
        return false;
    }

    /** 类名是否命中消息推送保活关键词 */
    private static boolean isPushName(String className) {
        if (className == null) return false;
        String lower = className.toLowerCase();
        for (String k : PUSH_EXEMPT_KEYWORDS) {
            if (lower.contains(k)) return true;
        }
        return false;
    }

    private static void installAlarmRelax(final ClassLoader cl) {
        String[][] pairs = {
                {"setExact", "set"},
                {"setExactAndAllowWhileIdle", "setAndAllowWhileIdle"},
                {"setWindow", "setWindow"}, // setWindow 本身非精确，不动
        };
        for (int i = 0; i < pairs.length; i++) {
            final String from = pairs[i][0];
            final String to = pairs[i][1];
            if (from.equals(to)) continue;
            HookUtil.tryHookAll(cl, "android.app.AlarmManager", from,
                    new de.robv.android.xposed.XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (com.tamer.qq.hooks.AppForeBack.isForeground()) return;
                                Object[] args = param.args;
                                if (args == null || args.length < 3) return;
                                param.setResult(null);
                                XposedHelpers.callMethod(param.thisObject, to, args[0], args[1], args[2]);
                                HookUtil.log("alarm relaxed " + from + " -> " + to);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        }
    }

    private static void stopCoreServiceQuietly(ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass(CORE_SERVICE, cl);
            XposedHelpers.callStaticMethod(c, "stopCoreService");
            if (sBgStopLogged.compareAndSet(false, true)) {
                HookUtil.log("background: CoreService stop requested (once per cold start)");
            }
        } catch (Throwable t) {
            HookUtil.log("stop CoreService failed: " + t);
        }
    }

    private static void stopSuffixServicesQuietly(ClassLoader cl) {
        try {
            Object ctx = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("com.tencent.qphone.base.util.BaseApplication", cl),
                    "getContext");
            if (ctx == null) return;
            boolean first = sTombstoneLogged.compareAndSet(false, true);
            for (String suf : TOMBSTONE_STOP_SUFFIXES) {
                try {
                    String clsName = findServiceImplSuffix(cl, suf);
                    if (clsName == null) continue;
                    if (sKeepPush && isPushName(clsName)) {
                        if (first) HookUtil.log("tombstone: keep push-alive " + clsName);
                        continue;
                    }
                    Class<?> svc = XposedHelpers.findClass(clsName, cl);
                    android.content.Intent it = new android.content.Intent((android.content.Context) ctx, svc);
                    XposedHelpers.callMethod(ctx, "stopService", it);
                    if (first) HookUtil.log("tombstone: stopped " + clsName);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            HookUtil.log("tombstone stops failed: " + t);
        }
    }

    private static String findServiceImplSuffix(ClassLoader cl, String suffix) {
        String[] candidates = {
                "com.tencent.gamecenter.wadl.api.impl.WadlProxyService",
                "com.tencent.gamecenter.wadl.biz.service.WadlJsBridgeService",
                "com.tencent.gamecenter.wadl.notification.WadlNotificationService",
                "com.tencent.mobileqq.gamecenter.yungame.YunGameService",
                "com.tencent.mobileqq.winkpublish.service.WinkPublishService",
                "com.tencent.mobileqq.colornote.smallscreen.ColorNoteSmallScreenService",
                "com.tencent.luggage.login.ilink2service.Ilink2KeepAliveService",
        };
        for (String c : candidates) {
            if (c.endsWith(suffix)) return c;
        }
        return null;
    }

    private PowerSaver() {}
}