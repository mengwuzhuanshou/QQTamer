Release v1.1.5（versionCode 7）

⚠ AI-generated module / 本模块由 AI 生成，代码未经人工长期审计，请自行评估风险。

## 更新内容 / What's new

- **移除热更新守卫**（hotfix_disable_redirect / hotfix_block_dexpatch）：实机确认会触发风控，价值太低，
  连同 QFix 重定向失效、补丁安装拦截与 Tinker 服务过滤一并删除。
  **Removed the hot-update guard** (QFix redirect neutralization, dex-patch install
  blocking and Tinker service filter): confirmed on-device to trigger risk control —
  too low value for the risk, gone entirely.
- **移除伪装回报**（report_clean_env / report_quiet_beacon_bg / report_clean_beacon_root）：
  环境探测清洗、后台静默埋点（未实机测试）、isRooted 清洗全部删除——不做任何"返回假数据"式欺骗。
  **Removed disguised reporting** (env-probe cleaning, background beacon quieting —
  never field-tested, isRooted cleaning): no more fake-return deception of any kind.
- 设置页同步移除「伪装回报」「热更新守卫」两个分组；模块功能收敛为 **省电 + 去开屏广告**。
  Settings UI updated; the module now focuses on **power saving + splash-ad removal**.
- 顺带收编 v1.1.4 未提交改动：confSrc 配置来源日志、debug_alive_marker 存活标记开关、
  conf 文件 BOM 兼容与 su 同步结果日志。
  Also folds in the uncommitted v1.1.4 changes: conf-source logging, liveness-marker
  switch, BOM-tolerant conf parsing and su-sync result logging.

## 环境要求 / Requirements

- 已 root + Zygisk + LSPosed / rooted device with Zygisk + LSPosed
- QQ 9.3.x（实测 9.3.35 / versionCode 15560）

安装后请在 LSPosed 勾选作用域「QQ(com.tencent.mobileqq)」并强制停止 QQ 后重开。
After install, select the QQ scope in LSPosed, then force-stop and reopen QQ.

---

Release v1.1.3（versionCode 5）

⚠ AI-generated module / 本模块由 AI 生成，代码未经人工长期审计，请自行评估风险。

## 更新内容 / What's new

- **更换签名密钥**：旧签名密钥密码曾泄露，已废弃并换新钥重新签名；
  与 1.1.2 及更早版本签名不兼容，安装前请先卸载旧版本。
  **Re-signed with a fresh key** (the previous key's password was leaked and is
  now retired). The new signature is incompatible with older builds — uninstall
  the old module before installing this one.
- 墓碑模式新增「保留消息推送保活」开关（默认开）：MSF/推送服务/推送唤醒锁豁免，收消息不受影响
  Tombstone mode now keeps message-push alive (on by default): MSF, push services and
  push wake-locks are exempt from background trimming.
- WakeLock 豁免判定加实例级缓存，省去重复反射 / per-instance wakelock-exempt cache.
- 前后台转换日志改为每冷启动各记一次，QQ 频繁切换不再刷日志 / foreground/background
  transition logs are now one-shot per cold start.
- 构建引擎统一到工作区共享构件 common/，仓库不含任何本地路径与密钥
  Build engine consolidated into shared common/; no local paths or keys in the repo.

## 环境要求 / Requirements

- 已 root + Zygisk + LSPosed / rooted device with Zygisk + LSPosed
- QQ 9.3.x（实测 9.3.35 / versionCode 15560）

安装后请在 LSPosed 勾选作用域「QQ(com.tencent.mobileqq)」并强制停止 QQ 后重开。
After install, select the QQ scope in LSPosed, then force-stop and reopen QQ.