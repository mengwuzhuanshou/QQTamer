package com.tamer.qq.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.tamer.qq.QQConfig;

/** 模块设置界面（纯代码 UI，无资源依赖）。三副本配置同步策略。 */
public class SettingsActivity extends Activity {

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float den = getResources().getDisplayMetrics().density;
        final int dp = Math.max(1, Math.round(den));

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setBackgroundColor(Color.parseColor("#FAFAFA"));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp * 20, dp * 24, dp * 20, dp * 40);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("QQ 净化助手 (QQTamer)");
        title.setTextColor(Color.parseColor("#111111"));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        addSpace(dp * 4);

        TextView sub = new TextView(this);
        sub.setText("目标应用：com.tencent.mobileqq（手机 QQ 9.x）\n"
                + "省电类钩子只在后台生效；开屏广告拦截让冷启动直达主页。\n"
                + "修改任意开关后，请在 LSPosed 中强制停止 QQ 后重新打开。");
        sub.setTextColor(Color.parseColor("#666666"));
        sub.setTextSize(13);
        root.addView(sub);
        addSpace(dp * 16);

        addSwitch(QQConfig.KEY_MASTER, "模块总开关", "Master switch", "关闭后模块完全休眠，等同未启用");

        section("省电", "Power saving (background-only)");
        addSwitch(QQConfig.KEY_KEEPALIVE, "拦截后台保活服务", "Block background keep-alive",
                "后台期间拦截并停止 CoreService(GuardManager) 前台保活；前台不受影响");
        addSwitch(QQConfig.KEY_BG_SERVICES, "拦截后台杂项服务", "Block misc background services",
                "游戏中心(Wadl)/云游戏/小世界发布/彩签/ilink 保活等服务的后台启动");
        addSwitch(QQConfig.KEY_WAKELOCK_CAP, "截断后台 WakeLock", "Cap background wakelocks",
                "后台期间无限期锁改为 60 秒、超长超时截断；音视频/导航场景自动放行");
        addSwitch(QQConfig.KEY_ALARM_RELAX, "降级后台精确闹钟", "Relax background alarms",
                "默认关闭；开启后后台 setExact* 退化为非精确，省电但可能延迟提醒");
        addSwitch(QQConfig.KEY_TOMBSTONE, "墓碑模式(激进)", "Tombstone mode (aggressive)",
                "默认关闭；进后台立刻停掉保活与杂项服务组，推送可能变慢，出问题先关它");
        addSwitch(QQConfig.KEY_TOMBSTONE_KEEP_PUSH, "墓碑保留消息推送保活", "Keep push alive in tombstone",
                "默认开启；墓碑/后台拦截时豁免 MSF、推送服务与推送唤醒锁，收消息不受影响");

        section("开屏广告", "Splash ads");
        addSwitch(QQConfig.KEY_SPLASH_AD, "拦截开屏广告", "Block splash ads",
                "掐断 vas-splash 缓存投喂与广告后跳转闸门；冷启动直达主页");

        addSpace(dp * 20);
        TextView foot = new TextView(this);
        foot.setText("⚠ 本模块由 AI 生成，请自行评估风险。/ AI-generated; use at your own discretion.\n\n"
                + "风控提示：模块不碰登录/MSF 协议与 native 库，只做 Java 层后台行为收敛；\n"
                + "请勿同时叠加多个同类模块。\n\n"
                + "配置文件：/data/data/" + QQConfig.MODULE_PKG + "/shared_prefs/" + QQConfig.PREFS_NAME + ".xml\n"
                + "若开关不生效：1) LSPosed 中启用本模块并勾选作用域\"QQ\"；2) 强制停止并重开 QQ。\n"
                + "If switches don't work: enable module in LSPosed, select QQ scope, force-stop QQ.");
        foot.setTextColor(Color.parseColor("#999999"));
        foot.setTextSize(12);
        root.addView(foot);

        syncConfigFile();
    }

    private void section(String text, String textEn) {
        float den = getResources().getDisplayMetrics().density;
        int dp = Math.max(1, Math.round(den));
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#1E88E5"));
        tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp * 8, 0, 0);
        root.addView(tv);
        TextView tve = new TextView(this);
        tve.setText(textEn);
        tve.setTextColor(Color.parseColor("#7FA6D9"));
        tve.setTextSize(11);
        tve.setPadding(0, 0, 0, dp * 8);
        root.addView(tve);
    }

    private void addSwitch(final String key, String title, String titleEn, String desc) {
        float den = getResources().getDisplayMetrics().density;
        final int dp = Math.max(1, Math.round(den));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp * 10, 0, dp * 10);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextColor(Color.parseColor("#222222"));
        t1.setTextSize(16);
        textCol.addView(t1);

        TextView ten = new TextView(this);
        ten.setText(titleEn);
        ten.setTextColor(Color.parseColor("#AAAAAA"));
        ten.setTextSize(11);
        textCol.addView(ten);

        TextView t2 = new TextView(this);
        t2.setText(desc);
        t2.setTextColor(Color.parseColor("#888888"));
        t2.setTextSize(12);
        textCol.addView(t2);

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(textCol, textLp);

        Switch sw = new Switch(this);
        boolean def = QQConfig.defaultValueOf(key);
        sw.setChecked(getSp().getBoolean(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getSp().edit().putBoolean(key, isChecked).apply();
                makeWorldReadable();
                syncConfigFile();
                Toast.makeText(SettingsActivity.this,
                        "已保存，强制停止 QQ 重开后生效", Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(sw, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addSpace(int px) {
        View v = new View(this);
        root.addView(v, new ViewGroup.LayoutParams(1, px));
    }

    private android.content.SharedPreferences getSp() {
        return getSharedPreferences(QQConfig.PREFS_NAME, MODE_PRIVATE);
    }

    /** 三副本配置同步：自身 files / apexdata prefs 同级 / root 写 /data/local/tmp */
    private void syncConfigFile() {
        StringBuilder sb = new StringBuilder();
        for (String key : QQConfig.ALL_KEYS) {
            sb.append(key).append('=')
              .append(getSp().getBoolean(key, QQConfig.defaultValueOf(key)))
              .append('\n');
        }
        byte[] data;
        try {
            data = sb.toString().getBytes("UTF-8");
        } catch (Throwable e) { return; }

        try {
            writeConf(new java.io.File(getFilesDir(), QQConfig.CONF_NAME), data);
        } catch (Throwable ignored) {}

        try {
            de.robv.android.xposed.XSharedPreferences xsp = new de.robv.android.xposed.XSharedPreferences(
                    QQConfig.MODULE_PKG, QQConfig.PREFS_NAME);
            java.io.File xf = xsp.getFile();
            if (xf != null && xf.getParentFile() != null) {
                writeConf(new java.io.File(xf.getParentFile(), QQConfig.CONF_NAME), data);
            }
        } catch (Throwable ignored) {}

        try {
            java.io.File tmp = new java.io.File(getFilesDir(), "qq_tamer_global.tmp");
            writeConf(tmp, data);
            int rc = -1;
            String[] suCmds = {
                    "cat '" + tmp.getAbsolutePath() + "' > /data/local/tmp/" + QQConfig.CONF_NAME
                            + " && chmod 644 /data/local/tmp/" + QQConfig.CONF_NAME,
            };
            try {
                ProcessBuilder pb = new ProcessBuilder("su", "-c", suCmds[0]);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                rc = p.waitFor();
            } catch (Throwable t) {
                android.util.Log.e("QQTamer", "su conf sync ex: " + t);
            }
            android.util.Log.i("QQTamer", "global conf sync rc=" + rc
                    + " (0 = ok; 其它值/异常 = root 未授权或 su 不可用，开关可能不生效)");
            tmp.delete();
        } catch (Throwable t) {
            android.util.Log.e(HookUtilTag(), "global conf sync ex", t);
        }
    }

    private static String HookUtilTag() { return "QQTamer"; }

    private static void writeConf(java.io.File f, byte[] data) {
        try {
            java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(f), "UTF-8");
            w.write(new String(data, "UTF-8"));
            w.flush();
            w.close();
            android.system.Os.chmod(f.getAbsolutePath(), 0644);
        } catch (Throwable ignored) {
        }
    }

    private void makeWorldReadable() {
        try {
            android.content.pm.ApplicationInfo ai = getApplicationInfo();
            if (ai != null && ai.dataDir != null) {
                java.io.File prefsDir = new java.io.File(ai.dataDir, "shared_prefs");
                java.io.File f = new java.io.File(prefsDir, QQConfig.PREFS_NAME + ".xml");
                if (f.exists()) {
                    android.system.Os.chmod(ai.dataDir, 0751);
                    android.system.Os.chmod(prefsDir.getAbsolutePath(), 0755);
                    android.system.Os.chmod(f.getAbsolutePath(), 0644);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}