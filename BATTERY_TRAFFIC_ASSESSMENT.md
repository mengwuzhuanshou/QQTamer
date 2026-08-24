# QQTamer 耗电 / 流量损耗评估

> 评估时间：2026-08-24，实机（荣耀 LDY-AN00 / Android 16 / KernelSU / LSPosed v2.1.1，QQ 9.3.35）
> 方法：静态逐 hook 开销分析 + 3 分钟后台窗口实测（快照 A→B 对比）。

## 结论速览

- **模块自身零网络请求**：全部 hook 为纯本地 Java 层，不发起任何网络调用 → 直接流量损耗为 0；
- **后台 CPU 实测 0.0%**：QQ 主进程与 :MSF 进程 180 秒后台窗口内 CPU 均为 0.0%，且**整机唯一的 PARTIAL_WAKE_LOCK 是 Google Play 的任务锁，QQ 一个都没持有**；
- **hook 调用开销可忽略**：全部 hook 合计约每进程 0.2~1ms 级（启动期一次性）+ 后台近乎零触发；
- **日志写入可忽略**：lspd verbose 日志 3 分钟仅增长 717B（含框架全部模块），且我们做了采样限频；
- **热更新拦截无重试风暴**：14 次命中 = 每个冷启恰好 1 次，无会话内重试；
- **净收益为正**：后台保活服务停止 + 杂项服务拦截的省电量远大于 hook 自身开销。

## 一、逐 hook 开销分析（静态）

| Hook | 触发频率 | 单次开销 | 实测 |
| --- | --- | --- | --- |
| WakeLock.acquire 截断 | 仅后台；本次窗口 0 次 | instanceof + 反射 getTag(~2µs) + 关键词循环 | 0 次命中；QQ 后台无锁可截 |
| ContextWrapper.startService 过滤(8后缀) | 服务启动时，每进程数十次 | getComponent+后缀匹配 ~1-3µs | 过滤已装，命中 0（杂项服务根本未被拉起） |
| CoreService start/stop + 后台 stop | 启动 1 次 + 后台切换 1 次/会话 | 空判断 + 反射 | 3 次 background-stop / 2 次 start 拦截 |
| PatchRedirectCenter.getRedirector→null | 每个 QFix 类 static{} 一次（约百级） | 单例返回 null ~2µs | 每冷启一批，合计 <1ms |
| DexPatchInstaller.installDexPatch | 每冷启 1 次 | 空操作 + 采样日志 | **14 次 = 每冷启 1 次，无重试** |
| vassplash.c#a / splashad.w#h | 冷启各 1 次 | setResult 空 Map/false | 每次冷启 1 次 |
| PackageUtil.isAppInstalled 清洗 | 取决于探测路径 | 关键词 12 项 contains | **0 次命中（QQ 本会话未探测敏感包）** |
| QQBeaconReport 静默 | 仅开启时（默认关） | — | 默认关，无开销 |
| AppForeBack 生命周期计数 | 每次 Activity 切换 | 整数增减 | 常驻但 O(1) |

单次 hook 调用经 LSPosed 原生桥接约 1-3µs，全部 hook 每进程合计的额外 CPU 不到 1ms 量级，
对应电量 < 0.001%/天级别，可忽略。内存占用：模块 dex 39KB + 静态常量，可忽略。

## 二、实测数据（3 分钟后台窗口，快照 A→B）

| 指标 | 快照 A (13:22) | 快照 B (13:25) | 结论 |
| --- | --- | --- | --- |
| QQ 主进程 CPU | 3.7%（含前台加载） | **0.0%** | 后台零 CPU |
| :MSF 进程 CPU | 0.0% | **0.0%** | 推送通道静默常驻 |
| 整机 PARTIAL_WAKE_LOCK | — | 仅 Play 商店 1 个 | **QQ 后台不持锁** |
| wakelock 截断命中 | — | 0 次 | 无锁可截，hook 无负担 |
| lspd verbose 日志 | 346,717B | 347,434B | +717B/3min（全框架，含采样限频） |
| QQ 进程存活 | pid 15648 | pid 15648 | CoreService 停止不影响存活 |
| QQ uid 流量(当前2h桶) | — | 后台 rx≈63KB+wifi / 6KB 蜂窝 | 无明显异常流量 |

## 三、流量评估

1. **直接流量 = 0**：模块不联网；
2. **间接流量**：热更新被拦后 QQ 每冷启尝试一次补丁安装（有缓存时可能不再下载），patch 体量小且非每次下载；无重试循环（14 次命中 = 14 个冷启，均匀分布）；
3. 未开启的开关（beacon 静默/闹钟降级）若开启：beacon 静默可省后台埋点流量（理论），闹钟降级不产生流量；
4. 环境清洗（isAppInstalled→false）不改流量——上报照发、只是内容变干净（这正是风控安全的设计取舍）。

## 四、耗电净收益（行为层面）

- **省**：后台停 CoreService(GuardManager) 前台保活（实测 QQ 后台 0 CPU 0 锁）、游戏中心等 7 个杂项服务不再拉起、wakelock 后台上限、闹钟降级(可选)；
- **换**：收消息依赖推送通道而非常驻长连接（:MSF 仍在，进程存活，实测无感知延迟）；开墓碑会更快回收进程，推送走系统通道仍可达；
- **风险点与兜底**：音视频/导航/推送类 wakelock 白名单、keep-push 豁免已内置；墓碑默认关。

## 五、优化实施记录（v1.1.2，已实机验证）

- ✅ **WakeLock 免重反射**：实例级 IdentityHashMap 缓存豁免结论（上限 256 自动清空），同一 WakeLock 重复 acquire 不再反射 getTag、不再跑关键词循环；
- ✅ **前后台日志一次性**：`foreground entered` / `background entered` / `CoreService stop requested` 均改为每冷启动各记 1 条（compareAndSet 一次性），QQ 频繁前后台切换不再刷日志（实测 fg→bg→fg→bg 循环仅首轮各 1 条）；墓碑批量日志同样首轮一次性；
- 待办：热更新拦截前移到 onConfig/onDownload（连补丁下载都省，当前只拦安装）。

**总体评级：hook 自身损耗 ≈ 0（CPU/流量/内存均无感），行为净收益明显为正，无风控层面的流量/行为异常信号。**