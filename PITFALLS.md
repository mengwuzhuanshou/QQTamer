# 踩坑记录 / Pitfalls（QQTamer 会话）

> 承接 HonorMarketTamer/PITFALLS.md 的格式。以下全部为本会话真实踩过或沉淀。
> 构建管线统一走工作区共享构件 `common/`（builder.py + build-stub，见其 README）。

## 1. jadx 在沙箱/受限环境下启动即挂：skylot 目录 AccessDenied

jadx 1.5+ 启动要建 %APPDATA% 下的 skylot 目录，工作区外写入被沙箱拒绝，报 JadxCommonFiles$DirsLoader AccessDeniedException。解法：进程内重定向三件套 APPDATA / LOCALAPPDATA / USERPROFILE 到工作区目录再跑。另外：**jadx 正常结束也常报 exit code 1**（finished with errors, count: N 是部分类反编译失败），别当致命错误。

## 2. 前会话留下的 dex_scan.py 静默失效

同一份逻辑手抄重写就能出结果，原脚本却永远只打印标题行、exit 0、无 traceback（疑似文件本身有不可见字符损坏）。教训：**拿到静默无输出的脚本，先原样重写再调试**；扫描器每个 dex 打一行计数并 flush，才能区分「没命中」和「没跑」。

## 3. 40 个 dex 找锚点的正确姿势（本次沉淀）

1. 直接解析 dex 二进制抽字符串表：header 偏移 56/60 = string_ids_size/off，64/68 = type_ids，96/100 = class_defs；uleb128 读长度后跟 MUTF-8 至 0x00；
2. 按 class_def → type_id → descriptor 建 descriptor 到 dex 的索引（JSON），之后关键词秒查某类定义在哪个 dex；
3. 字符串命中不等于类在该 dex（引用串也会出现），**必须用索引核对定义位置**再决定反编译谁；
4. 大 dex 用 jadx 整体反编译（10MB 约 3 分钟）比反复 --single-class 省事，输出当资料库 grep。

## 4. 别信方法名猜语义：c.m/c.n 差点被当成缓存写入钩掉

vassplash/common 的 c.m(nr2.a)/c.n(nr2.a) 从签名看极像缓存广告 bean，反编译后实际是 C2S/beacon 上报函数；真正的缓存读取是 c.a(String,Set)。教训：**钩子锚点必须读过方法体**，否则要么无效要么误伤。

## 5. 编译桩接口方法必须与运行时逐一对应

Application.ActivityLifecycleCallbacks 桩若只声明部分方法，匿名实现类能编过，但 ART 加载时因未实现全部抽象方法直接被拒（注册回调即炸）。桩要把 onCreated/Started/Resumed/Paused/Stopped/SaveInstanceState/Destroyed 七个全声明。同类问题：XSharedPreferences 桩缺 contains() 编译期就报；XposedHelpers 缺 callStaticMethod 同理——桩按需补齐即可（现在统一补在共享构件 build-stub/，全局生效）。

## 6. PowerShell 内嵌 Python/JS 的转义修罗场

- JS 双引号串里写 PS 反引号转义序列会被 JS 当字面量塞进命令导致解析爆炸；
- python -c 多行用分号拼接 if/for 必炸缩进；
解法固化：**复杂脚本一律落盘成文件再执行**，不在命令行里拼。README 曾因长字符串拼接语法错误失败一次，改为逐行数组 join 后一次通过。

## 7. QFix 重定向中心是热更的单点咽喉（且构造性安全）

每个补丁类的范式都是 IPatchRedirector r = $redirector_; 然后 r == null 或 !r.hasPatch(id) 就走原逻辑 —— 把 PatchRedirectCenter.getRedirector(int) 钩成返回 null 即可让全部热修补丁退化基线，无需逐类去补。前提是宿主确实全员空安全：抽查 5 处以上再上。副作用：QQBeaconReport 上报的 param_patch_version 恒为初始值（对本模块反而是伪装加分项）。

## 8. 检测对抗的最小面原则

QQ 9.3.35 对 Xposed 的检测其实只有一处被实证：崩溃诊断里 PackageUtil.isAppInstalled(ctx, de.robv.android.xposed.installer) 的结果拼进上报串。清洗这一个入口即可让报告变干净。**不要**去钩 PackageManager 全家桶（面大、易碎、易被统计出异常）。

## 9. 构建流水线复用清单（统一走共享构件）

新模块工程 = 拷 `module_conf.py` 模板 + `tools/build_module.py` 薄入口（内容固定），图标放 `tools/icon/ic_launcher.png`；编译桩/axml_writer/arsc_builder/builder.py 统一在共享构件 `common/`（MB_TOOLS 指向），工程不再自带副本。引擎一次构建约 30 秒。产物自检：zip 条目、dex 类名、invokedynamic 应为 0、arsc 对齐。

## 10. LSPosed 下用宿主 classloader 解析框架类必挂：类名被改写

XposedHelpers.findClass("android.app.ContextWrapper", 宿主CL) 在 LSPosed v2 环境里把框架类名改写为 android$app$ContextWrapper 再查找 → ClassNotFoundError（模块 API 被改名注入的机制）。Hook 框架类一律**直接引用 .class**（如 android.content.ContextWrapper.class，编译桩补一个即可），不要 findClass 按名字找。本模块两处服务过滤器（PowerSaver/HotfixGuard）就是这么修复的，修复前静默失败、修复后立即 armed。

## 11. LSPosed v2.1.1 配置库与 SELinux 写入

- 配置 = /data/adb/lspd/config/modules_config.db（SQLite，三表：modules / modules_state / scope），改模块启用与作用域 = 插三行 + PRAGMA wal_checkpoint(TRUNCATE)；改完 force-stop+重启目标进程即生效（daemon 按进程启动读配置）；
- **SELinux**：Enforcing 下 ksu 上下文对 system_file 现有文件 open-write 被拒（cp/mv 覆盖 Permission denied），但可建新文件、可 unlink（-shm/-wal 能 rm）。兜底：setenforce 0 → 写入 → setenforce 1；
- 拉二进制文件用 adb exec-out 经 cmd /c 重定向，**不要用 PowerShell >**（会把二进制当文本编码破坏文件）；
- 注意：外部直接替换 daemon 正在使用的 db（删 -wal/-shm 再 cp）有风险——曾观察到 daemon 后续把配置整体搬走/重建，导致模块启用状态丢失。改配置优先用 LSPosed 管理器 UI；必须直接改 db 时先停 lspd daemon 或整机重启后再替换。

## 12. 设备验证纪律（继承工作区通用交接）

- adb 绝对路径 + 设备序列号见工作区交接文档；所有 adb 命令带 -s 序列号；
- su -c 一条命令一个进程，禁 && 链与管道；grep 单 token 模式；
- XposedBridge.log 只进 lspd verbose 日志（通配符路径），android.util.Log 进 logcat，双通道都在用；
- 标准验证循环：install -r → kill -9 目标进程 → monkey 启动 → 等 12~15s → grep 日志；
- LSPosed 注入偶发丢失（daemon binder ENOSPC）：冷启后无新会话日志就再杀再启一次；
- 日志里的 !!! FAILED BINDER TRANSACTION !!!（JavaBinder）是 daemon ENOSPC 老毛病，与模块无关。
