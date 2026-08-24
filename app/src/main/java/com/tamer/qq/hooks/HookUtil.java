package com.tamer.qq.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 容错 Hook 工具：精确签名 + 弹性签名（按参数个数过滤）两种模式 */
public final class HookUtil {

    public static final String TAG = "QQTamer";

    /** 弹性回调：由调用方在回调内自行做类型判断 */
    public interface FlexCallback {
        void fire(MethodHookParam param) throws Throwable;
    }

    public static void log(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    /** 按类名+方法名+精确参数类型 hook */
    public static void tryHook(ClassLoader cl, String className, String methodName,
                               Object[] paramTypes, XC_MethodHook callback) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            Object[] args = new Object[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, args, 0, paramTypes.length);
            args[paramTypes.length] = callback;
            XposedHelpers.findAndHookMethod(clazz, methodName, args);
            log("hooked " + className + "#" + methodName);
        } catch (Throwable t) {
            log("hook FAILED " + className + "#" + methodName + " : " + t);
        }
    }

    /**
     * 弹性 hook：该类同名方法的全部重载，仅参数个数匹配时执行回调。
     * 不依赖参数类型名，规避混淆签名漂移。
     */
    public static void tryHookFlex(final ClassLoader cl, final String className, final String methodName,
                                   final int argc, final FlexCallback cb) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            XC_MethodHook wrapper = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args != null && param.args.length == argc) {
                            cb.fire(param);
                        }
                    } catch (Throwable t) {
                        log("flex callback error " + className + "#" + methodName + ": " + t);
                    }
                }
            };
            XposedBridge.hookAllMethods(clazz, methodName, wrapper);
            log("hookedAll(" + methodName + ", argc=" + argc + ") on " + className);
        } catch (Throwable t) {
            log("hookAll FAILED " + className + "#" + methodName + " : " + t);
        }
    }

    /** hook 一个类的全部同名重载方法 */
    public static void tryHookAll(ClassLoader cl, String className, String methodName,
                                  XC_MethodHook callback) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            XposedBridge.hookAllMethods(clazz, methodName, callback);
            log("hookedAll " + className + "#" + methodName);
        } catch (Throwable t) {
            log("hookAll FAILED " + className + "#" + methodName + " : " + t);
        }
    }

    /** before-hook 中把方法替换为固定返回值 */
    public static XC_MethodHook returnValue(final Object value) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(value);
            }
        };
    }

    /** 高频路径采样日志：n <= N 或 n % M == 0 才打 */
    public static boolean sample(java.util.concurrent.atomic.AtomicInteger counter, int firstN, int everyM) {
        int n = counter.incrementAndGet();
        return n <= firstN || n % everyM == 0;
    }

    private HookUtil() {}
}