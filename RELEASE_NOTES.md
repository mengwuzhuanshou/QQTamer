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