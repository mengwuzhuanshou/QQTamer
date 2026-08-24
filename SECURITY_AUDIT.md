# QQTamer 安全审计：QQ 检测面 / 上报组成 / 模块 hook 问题

> 审计日期：2026-08-24。触发：连续被 QQ 踢下线（其中一次弹了「安全风险提示」）。
> 结论先行：模块 hook 全是本地行为、不碰协议；**配置管道 bug 已实锤并修复（v1.1.4）**；
> 踢号最可能的根因是**环境检测**（KernelSU root + Zygisk/LSPosed 注入痕迹），由 QQ 自身安全栈完成，模块无法仅靠自身消除。

## 1. QQ 的检测面清单（不止崩溃报告那一个）

| 检测层 | 组件 | 内容 | 我们能清洗吗 |
| --- | --- | --- | --- |
| Java 上报 | Beacon SDK（com.tencent.beacon） | 每次 beacon 事件都带公共参数 isRooted(1/0) + qimei + imei/androidId/mac/wifi/机型等（BeaconPubParams 全字段见第 2 节） | 可钩 e.l().m() 清洗 isRooted（v1.1.4 新增开关，默认关） |
| Java 采集 | gathererga（com.tencent.gathererga） | isRooted 文件检查：/system/app/Superuser.apk、/sbin/su、/system/bin/su 等 10 个经典 su 路径（InfoID 316） | 仅经典路径，KSU 不含这些路径，KSU 上大概率已返回 false，价值低 |
| 崩溃诊断 | com.tencent.qqperf.monitor.crash.c | isXposedInstalled + 登录场景/UIN + QzoneConfig + 线程栈（栈里会出现 LSPosed/Xposed 帧） | 已清洗 isXposedInstalled 字段；线程栈清洗不了（真实调用链） |
| Native 安全引擎 | QSec / qqprotect（com.tencent.qqprotect.qsec.*、libQSec.so、libKcsdk、libnativefilescan.so） | 自有加密通道（qsecprotocol/SecCipher/QSecCloudQuery）+ O3Report/QSecRptController 上报 + native 文件/进程/注入检测；内容对我们完全不透明 | 无法从 Java 层清洗；native 检测（maps 扫 lspd 注入、su 探测等）看不到也改不了 |
| 踢号通道 | MSF 推送 RequestPushForceOffline 到 MainService.popupNotification | KickedType 仅 4 种（多开/手机端/改密/低版本）；安全风险踢号走 securityKickedType/CODE_SSO_KICKED 独立通道，文案服务端下发 | 不适用 |

结论：Java 层能清洗的只有 beacon isRooted + 崩溃 isXposedInstalled；真正决定「安全风险」踢号的是 QSec native 层对 root/注入环境的检测，与模块无关（LSPosed 框架本身的 zygisk 注入痕迹一直在，NTQQBattery/qqmax 时代也一样被踢+弹警告）。

## 2. 上报内容是怎么组成的（回报组成）

### 2.1 Beacon 公共参数（每次事件必带，BeaconPubParams）

appVersion / boundleId / sdkId / sdkVersion / beaconId / appFirstInstallTime / appLastUpdatedTime / platform / dtMf / osVersion / hardwareOs / brand / model / language / resolution / dpi / isRooted(1/0) / fingerprint / qimei / mac / wifiMac / wifiSsid / cid / networkType / modelApn / imei / dtImei2 / dtMeid / imsi / androidId

### 2.2 QQBeaconReport 事件参数（realReport 组成）

appKey(0S200MNJT807V3GE) + eventCode + user_uin + param_is_gray_version + param_patch_version（IRFix.getLoadVersion()，热更被拦后恒为基线值）+ isSucceed + EventType(REALTIME/IMMEDIATE_MSF) + 业务 params

### 2.3 崩溃诊断串（crash.c#c() 拼装）

super.c（EUP 基础崩溃信息+线程栈）+ isXposedInstalled 布尔 + 登录场景/账号 + QzoneConfig 若干 + 上次崩溃文本(Base64) + RDMEtraMsg + Native 线程信息

### 2.4 QSec（qqprotect）上报：黑盒
## 3. 模块 hook 审计（找出的问题）

| 序号 | 问题 | 影响 | 修复（v1.1.4） |
| --- | --- | --- | --- |
| 1 | 配置管道 bug（实锤）：设置页写入的开关从未被 QQ 进程读到——LSPosed v2 把模块数据重定向，canRead=false；唯一生效路径是 /data/local/tmp 的 conf，而设置页的 su 同步一直失败，导致每次 QQ 启动都是默认配置 | 你拨的热更开关全部无效，A/B 全部失真（以为关了实际一直开着） | conf 解析去 BOM、日志加 confSrc= 来源、设置页 su 同步记录 rc、写 UTF-8 无 BOM |
| 2 | 存活标记文件写在 QQ 的 files 目录（qq_tamer_alive.txt） | 给 QQ 的 nativefilescan 留了一个多余指纹 | 改为 debug_alive_marker 开关，默认不写 |
| 3 | env-clean 只洗崩溃报告的 isXposedInstalled，没洗 beacon 的 isRooted（高频上报里的 root 信号） | root 信号每次 beacon 事件都在报 | 新增 report_clean_beacon_root 钩 beacon e.l().m() 返回 false（默认关，实验性） |
| 4 | 热更守卫（getRedirector 置 null + 拦 installDexPatch）是唯一有服务端可见指纹的钩子：param_patch_version 恒基线、补丁永不应用 | 若服务端按补丁合规判客户端可能触发踢号（未证实） | 保持开关化；A/B 观察中（当前 conf 已置 hotfix=false） |
| 5 | 其余钩子（服务拦截/WakeLock/开屏/生命周期）全部本地、零网络发送 | 无服务端信号 | 无需改 |

## 4. 建议路线

1. A/B 继续（当前：模块休眠 + QQ 正常运行）。若休眠期仍被踢，模块完全排除，根因是环境检测；
2. 环境检测的解法不在模块层：装 Shamiko + LSPosed denylist（QQ 加入 denylist）隐藏 root 与注入痕迹，这是唯一能对抗 QSec native 检测的手段；
3. 重新启用模块时装 v1.1.4（配置管道已修，拨开关终于会生效），可按需开 report_clean_beacon_root 清洗 isRooted 上报；
4. 若确认热更守卫与踢号相关（A/B 后），把守卫改成只记录不拦截，或默认关。

## 5. 证据与日志速查

- 设备全部 QQ 会话（14:34/14:48/15:42/17:17/17:25/17:27）effective 恒为默认值（qfix=true 等）→ 配置管道从未生效；
- 17:28 起 push 的 /data/local/tmp/qq_tamer.conf 生效：effective 显示 qfix=false dexPatch=false（首次真实生效）；
- prefs（apexdata qq_tamer_config.xml）曾只含 power_tombstone_keep_push=false + 热更两键 false，均未被进程读到；
- 两次踢号（带警告/不带警告）都发生在热更守卫开启状态。

自有加密通道（SecCipher/qsecprotocol），O3CollectDataApi/O3ReportApi/QSecRptControllerImpl 构成独立于 beacon 的采集上报体系，内容未在 dex 明文中暴露。
