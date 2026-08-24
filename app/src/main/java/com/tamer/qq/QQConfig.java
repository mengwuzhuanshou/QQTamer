package com.tamer.qq;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * QQTamer 开关配置：与设置界面(SettingsActivity)共用同一份 SharedPreferences。
 * Hook 侧按 conf 文件(/data/local/tmp 副本优先) -> XSharedPreferences 兜底读取。
 */
public final class QQConfig {
    public static final String MODULE_PKG = "com.tamer.qq";
    public static final String PREFS_NAME = "qq_tamer_config";
    public static final String TARGET_PKG = "com.tencent.mobileqq";
    public static final String CONF_NAME = "qq_tamer.conf";

    // ===== 总开关 =====
    public static final String KEY_MASTER = "master_enabled";

    // ===== 省电 =====
    /** 后台时拦截并停止 CoreService(QQ 的前台保活服务，GuardManager) */
    public static final String KEY_KEEPALIVE = "power_block_core_keepalive";
    /** 后台时拦截游戏中心/闪屏/彩签等杂项 Service 启动 */
    public static final String KEY_BG_SERVICES = "power_block_bg_services";
    /** 后台期间 WakeLock 无限期获取改为有限期、超长超时截断(音视频白名单) */
    public static final String KEY_WAKELOCK_CAP = "power_cap_wakelocks";
    /** 后台期间 setExact* 精确闹钟降级为非精确(默认关) */
    public static final String KEY_ALARM_RELAX = "power_relax_alarms";
    /** 激进墓碑模式：进后台立刻停 CoreService + 杂项服务组(默认关) */
    public static final String KEY_TOMBSTONE = "power_tombstone_mode";
    /** 墓碑/后台拦截时保留消息推送相关保活（MSF/推送服务/推送唤醒锁，默认开） */
    public static final String KEY_TOMBSTONE_KEEP_PUSH = "power_tombstone_keep_push";

    // ===== 伪装回报 =====
    /** 环境探测报告清洗：xposed/magisk/su 等包名探测一律回报“未安装” */
    public static final String KEY_ENV_CLEAN = "report_clean_env";
    /** 后台静默 beacon 埋点(理论可被服务端统计发现，默认关) */
    public static final String KEY_BEACON_QUIET = "report_quiet_beacon_bg";

    // ===== 开屏广告 =====
    public static final String KEY_SPLASH_AD = "ad_block_splash";

    // ===== 热更新守卫 =====
    /** QFix 补丁重定向中心失效：所有热修补丁不再生效(null 安全) */
    public static final String KEY_QFIX_REDIRECT = "hotfix_disable_redirect";
    /** 拦截 DexPatchInstaller 安装 + Tinker 补丁服务 */
    public static final String KEY_DEX_PATCH = "hotfix_block_dexpatch";

    public static final String[] ALL_KEYS = {
        KEY_MASTER,
        KEY_KEEPALIVE, KEY_BG_SERVICES, KEY_WAKELOCK_CAP, KEY_ALARM_RELAX, KEY_TOMBSTONE,
        KEY_TOMBSTONE_KEEP_PUSH,
        KEY_ENV_CLEAN, KEY_BEACON_QUIET,
        KEY_SPLASH_AD,
        KEY_QFIX_REDIRECT, KEY_DEX_PATCH,
    };

    private final android.content.SharedPreferences sp;

    public QQConfig(android.content.SharedPreferences sp) { this.sp = sp; }

    public boolean get(String key, boolean def) {
        try { return sp.getBoolean(key, def); } catch (Throwable t) { return def; }
    }

    /** 默认值表：与 SettingsActivity 保持一致 */
    public static boolean defaultValueOf(String key) {
        if (KEY_MASTER.equals(key)) return true;
        if (KEY_KEEPALIVE.equals(key)) return true;
        if (KEY_BG_SERVICES.equals(key)) return true;
        if (KEY_WAKELOCK_CAP.equals(key)) return true;
        if (KEY_ALARM_RELAX.equals(key)) return false;
        if (KEY_TOMBSTONE.equals(key)) return false;
        if (KEY_TOMBSTONE_KEEP_PUSH.equals(key)) return true;
        if (KEY_ENV_CLEAN.equals(key)) return true;
        if (KEY_BEACON_QUIET.equals(key)) return false;
        if (KEY_SPLASH_AD.equals(key)) return true;
        if (KEY_QFIX_REDIRECT.equals(key)) return true;
        if (KEY_DEX_PATCH.equals(key)) return true;
        return false;
    }

    /** Hook 侧加载：conf 文件最高优先级，其次 XSharedPreferences */
    public static QQConfig loadForHook(de.robv.android.xposed.XSharedPreferences xsp) {
        Map<String, Boolean> m = readConfFile(xsp);
        if (m != null) {
            return new QQConfig(new MapBackedPrefs(m));
        }
        return new QQConfig(new XspBackedPrefs(xsp));
    }

    private static Map<String, Boolean> readConfFile(
            de.robv.android.xposed.XSharedPreferences xsp) {
        java.util.List<String> candidates = new java.util.ArrayList<String>();
        candidates.add("/data/local/tmp/" + CONF_NAME);
        candidates.add("/data/user/0/" + MODULE_PKG + "/files/" + CONF_NAME);
        try {
            File xf = xsp.getFile();
            if (xf != null && xf.getParentFile() != null) {
                candidates.add(new File(xf.getParentFile(), CONF_NAME).getAbsolutePath());
            }
        } catch (Throwable ignored) {}
        for (String p : candidates) {
            try {
                File f = new File(p);
                if (!f.isFile() || !f.canRead()) continue;
                Map<String, Boolean> m = new HashMap<String, Boolean>();
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    int i = line.indexOf('=');
                    if (i <= 0) continue;
                    m.put(line.substring(0, i).trim(),
                          "true".equals(line.substring(i + 1).trim()));
                }
                br.close();
                if (!m.isEmpty()) return m;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** 内存映射版 SharedPreferences 适配器 */
    private static final class MapBackedPrefs implements android.content.SharedPreferences {
        private final Map<String, Boolean> map;
        MapBackedPrefs(Map<String, Boolean> map) { this.map = map; }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Boolean v = map.get(key);
            return v == null ? defValue : v.booleanValue();
        }
        @Override public int getInt(String key, int defValue) { return defValue; }
        @Override public String getString(String key, String defValue) { return defValue; }
        @Override public android.content.SharedPreferences.Editor edit() { throw new UnsupportedOperationException(); }
        @Override public Map<String, ?> getAll() { throw new UnsupportedOperationException(); }
        @Override public long getLong(String key, long defValue) { return defValue; }
        @Override public float getFloat(String key, float defValue) { return defValue; }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return defValues; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public void registerOnSharedPreferenceChangeListener(
                android.content.SharedPreferences.OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(
                android.content.SharedPreferences.OnSharedPreferenceChangeListener l) { }
    }

    /** XSharedPreferences 兜底适配器 */
    private static final class XspBackedPrefs implements android.content.SharedPreferences {
        private final de.robv.android.xposed.XSharedPreferences xsp;
        XspBackedPrefs(de.robv.android.xposed.XSharedPreferences xsp) { this.xsp = xsp; }
        @Override public boolean getBoolean(String key, boolean defValue) {
            try { return xsp.getBoolean(key, defValue); } catch (Throwable t) { return defValue; }
        }
        @Override public int getInt(String key, int defValue) { return defValue; }
        @Override public String getString(String key, String defValue) { return defValue; }
        @Override public android.content.SharedPreferences.Editor edit() { throw new UnsupportedOperationException(); }
        @Override public Map<String, ?> getAll() { throw new UnsupportedOperationException(); }
        @Override public long getLong(String key, long defValue) { return defValue; }
        @Override public float getFloat(String key, float defValue) { return defValue; }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return defValues; }
        @Override public boolean contains(String key) {
            try { return xsp.contains(key); } catch (Throwable t) { return false; }
        }
        @Override public void registerOnSharedPreferenceChangeListener(
                android.content.SharedPreferences.OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(
                android.content.SharedPreferences.OnSharedPreferenceChangeListener l) { }
    }
}