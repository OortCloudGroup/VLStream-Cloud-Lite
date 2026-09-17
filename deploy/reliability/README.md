# 无人值守部署模板

这些文件用于**新的、经过隔离验证的部署**，没有在现场服务器安装。现有现场已经有同名服务和定制恢复脚本，不能直接覆盖。

## 范围

- `vlstream-stack.service`：系统启动后按 Compose 依赖启动；失败 30 秒后重试；正常关机有序停止。不构建或拉取新镜像。
- `recovery.py`：五个服务的持续健康检查；连续三次不健康后定向重启，操作之间至少冷却 300 秒。Redis 容器重启后安排媒体及后端重建运行状态。设备/上级离线不触发整栈重启。
- `vlstream-recovery.timer`：开机后 30 秒首次执行、每轮结束 60 秒后再次执行，避免一次性巡检。
- 台账先写临时文件、fsync 后原子替换；操作前记录冷却时间。损坏的台账保留副本并重新观察；损坏前尚未完成的 Redis 恢复动作无法凭空恢复，需要人工核对。

模板只接受当前仓库 `mysql / redis / mqtt / zlmediakit / wvp-backend` 五服务拓扑；现场的第六个前端服务、外部基础设施和额外 Compose override 必须先适配并验证。脚本检测到拓扑不匹配会退出，不遍历或操作其他项目。

## 安装前核对

1. 将包含后端修复的固定版本部署到隔离环境，执行 Flyway 迁移。首次访问工作台读取旧 Redis 布局，确认已进入 `sys_work_layout`；新保存始终写 MySQL，旧缓存不覆盖新记录。
2. 暂停位置上报，等待旧版本 Redis 位置队列排空，再切换新版本。残留队列需要定向迁移，不能直接删除 Redis 数据卷。
3. 保留原 MySQL/Redis 数据卷及配置，确认备份可恢复。当前 `compose.yaml` 仍保留 Redis AOF；该模板**不自动切换 tmpfs 或清空 Redis**。除布局和国标位置外，还需审计其他模块的 Redis 队列和状态，再决定纯缓存切换。
4. 核对 `/usr/bin/docker`、`/usr/bin/python3`、项目路径、五个服务健康检查、存储挂载与 Docker 开机启动设置。Python 要求 3.8+，Linux 提供 `fcntl` 文件锁。
5. 验证媒体服务健康检查能代表所需 API 可用性。容器 HTTP 健康不能替代媒体业务探测；当前模板没有现场 API 的“连续检测不到媒体服务”专项探针。
6. 每日数据库/配置/补丁包备份、保留 7 份、备份恢复演练由现场备份系统设置。本目录不包含现场备份脚本；原始资料只给了路径与验收结论，没有提供脚本内容。

## 只读预检

在目标项目根目录运行（不重启、不写台账）：

```sh
python3 deploy/reliability/recovery.py --project-directory /opt/vlstream/app --state /var/lib/vlstream-recovery/state.json
```

`--apply` 才允许写台账和执行恢复。缺少容器、无健康检查、服务长时间 restarting 时，应先检查 Compose 启动及退出原因；脚本不会通过反复重建容器掩盖故障。

## 在新部署中启用

路径不同请先修改 unit 文件。以下命令会启用自动恢复，应在完成隔离测试后由部署人员执行：

```sh
sudo install -m 0644 deploy/reliability/vlstream-stack.service /etc/systemd/system/
sudo install -m 0644 deploy/reliability/vlstream-recovery.service /etc/systemd/system/
sudo install -m 0644 deploy/reliability/vlstream-recovery.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now vlstream-stack.service
sudo systemctl enable --now vlstream-recovery.timer
systemctl list-timers 'vlstream-*' --no-pager
journalctl -u vlstream-recovery.service -n 80 --no-pager
```

计划维护前先停止 `vlstream-recovery.timer`，再停止 `vlstream-stack.service`。不要直接停止单个容器后让巡检和人工操作互相干扰。

## 仍由现场负责

- BIOS AC Power Recovery 设置与真实断电冷启动。
- 固定 SIP 出口、RTP/RTCP 端口范围、TCP/UDP 映射、防火墙/NAT 和上级媒体网段；不提供带现场 IP 的通用 NAT 脚本。
- MySQL `innodb_flush_log_at_trx_commit=1`、`sync_binlog=1` 的实际值与磁盘可靠性。
- 备份时间、保留策略、权限和恢复点确认；运行异常不得自动用旧备份覆盖数据库。
- 内核 panic 自动重启与硬件 watchdog（有硬件才可启用）。
- Redis 清空和状态重建后的本地逐路解码、各个已对接上级的注册/目录/点播/停止、至少一次续注册周期。

模板的单元测试和源码编译不等于这些现场验收已完成。请结合 [可靠性能力清单](../../RELIABILITY.md) 登记实际结果。
