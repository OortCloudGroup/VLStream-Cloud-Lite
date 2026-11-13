# 生产 ZLM 心跳定时器缺失：诊断与授权修复记录

本文为脱敏后的生产问题记录：集群、命名空间、服务/Pod 名称、IP、镜像仓库和入口上下文均已替换为示例值；文中补丁不可直接应用。
目标：`<production-cluster>/<namespace>`，入口为 `<production-ui-context>`。
调查及修复时间：2026-10-16 10:26–11:08 UTC；补充验收尝试至 11:13 UTC。不是 VM 或开发环境。

## 最终状态：已修复

经用户明确授权，11:03:39 UTC 已在 Kuboard 保存 `zlm-media-server` Deployment，
仅为既有容器增加启动 `command` 和 `args`，在进程启动前补齐四个 hook 字段。
最终采用 Deployment 内联脚本；**没有创建 ConfigMap、挂载卷或更换镜像**。
下文早期“未执行”内容均为 10:42 UTC 阶段历史记录，不能作为当前状态。

- 新 ReplicaSet：`zlm-replicaset-rev5`（revision 5）。
- 新 Pod：`zlm-pod-rev5`，11:03:39 UTC 创建、
  11:03:41 UTC Ready，核验时 1/1、重启数 0。
- 状态接口 `GET /<wvp-context>/vlstream/device/media/status`
  返回 HTTP 200，实际响应：

  ```json
  {"msg":"操作成功","code":200,"data":{"available":true,"mediaServerId":"<media-server-id>"}}
  ```

- 父任务随后转达：用户在新 Pod 已运行约两分钟时也确认生产 ZLM 可用。
- 修复后的 75 秒被动抓包已启动，但浏览器调试连接中断，终端结果未能取回。
  因此不宣称已验证该段 POST 数量、间隔或 code=0 次数。
  本机 kubectl 未配置 current-context，无法据此继续读取生产结果。
  此验证限制没有构成应用异常证据；恢复已由正式接口及用户复核确认。
- 修复后停止所有生产写入；未再重启、修改 DB/Redis/Secret、模拟心跳或拉流。

实际应用的启动脚本、可复核补丁和窄范围回滚补丁分别见同目录：
`PROD_ZLM_HEARTBEAT_20261016.inline-start.sh`、
`PROD_ZLM_HEARTBEAT_20261016.applied-inline.strategic-merge-patch.yaml`、
`PROD_ZLM_HEARTBEAT_20261016.rollback.strategic-merge-patch.yaml`。
两个 YAML 均为战略合并补丁，**不能作为完整 Deployment 替换**。
旧服务端 ReplicaSet（revision 4）保留原 Pod 模板。

启动脚本只在容器内读取镜像原配置，追加非敏感 hook 覆盖，生成权限受限的
`/tmp/zlm-wvp-config.ini`；然后恢复原 umask，以原工作目录、证书及日志参数
exec 原二进制。既有安全配置与媒体参数从原文件原样继承，未输出其内容。
Kuboard 对 Deployment、Service、Ingress 三个原有对象的 dry-run 均通过；
保存涉及的三个对象中仅 Deployment 的上述 command/args 有语义变更。
Service 的端口映射、外部访问端口及 Ingress 均保持原值。

脚本已通过 `sh -n`；本地核对了同一提交的 mINI 解析器后值覆盖行为。
未改业务源码，因此未运行 Maven 测试。

### 11:13 UTC 补充验收受阻

按父任务要求，计划通过既有登录生产会话低频读取 media/status，跨至少 60 秒，
并只读核对近期是否有新离线日志。现有 Edge 连接可以列出标签，但绑定原生产页失败；
按浏览器文档在同一浏览器打开新的验收标签也在附加阶段失败。
未重新登录、未请求凭据、未修改配置，未重做抓包或执行高权限观测。
因此本轮没有获得新的状态采样或日志，不把之前单次 available=true 响应
升级为“跨 60 秒接口采样验收通过”；该项仍受浏览器连接阻塞，已停止尝试。
此前正式接口恢复及用户复核结论保持原有证据范围。

## 结论

ZLM 镜像内的启动配置未填写 `hook.on_server_keepalive` 和
`hook.on_server_started`。该版本仅在启动安装 WebHook 时创建心跳定时器；
URL 为空便提前返回，后续通过 API 写入 URL 不会补建定时器。
运行容器中后来写入的配置没有持久化到新 Pod。用户只重启 ZLM 后，
新容器重新使用镜像中的空 URL，故没有恢复心跳。

这不是从“WVP 没收到心跳”反推的结论：旧、新进程均直接确认心跳定时器
共享指针为 null；新进程的静态初始化标记还确认函数在创建定时器之前返回。

## 重启前证据

- ZLM Pod：旧 ZLM Pod（名称已脱敏）。
- 镜像版本 `<image-version>`；既有只读 API 版本证据为 commit `1da300c`。
- 启动命令：`./MediaServer -s /opt/media/conf/default.pem -c /opt/media/conf/config.ini -l 0`。
- 当时文件大小 4244 字节，mtime `2026-10-12 10:10:56.607305800 +0800`。
  文件和运行配置均为 enable=1、alive_interval=10.0、mediaServerId 为脱敏值，
  keepalive URL 指向 WVP 的 `/index/hook/on_server_keepalive`。
- 固定 ELF 符号 `_ZL17g_keepalive_timer` 位于 `0xde4bb0`。
  按 ELF 类型和进程映射计算地址，仅只读该 shared_ptr 的 16 字节，结果为 null。
  没有附加调试器或发送信号。
- `getStatistic`、`getThreadsLoad`、`getWorkThreadsLoad` 均立即 code=0；
  统计返回中无 threadMem，排除等待全线程内存统计回调的分支。
- AF_PACKET 全接口被动观测 40 秒，开始 Unix 秒 `1792146647`，结束 `1792146687`。
  总 18 包，其中应用端口流量 12 包，真实 hook POST 0。
  观测中主动发起一次只读 GET 验证捕获有效：
  `<zlm-pod-ip>:<client-port> -> <wvp-pod-ip>:<service-port> GET <wvp-context>/index/hook/on_server_keepalive`；
  返回 `HTTP/1.1 200`、业务 code=500（GET 方法不支持）。没有发送模拟 POST。
- WVP 现有日志：18:09:58.718 连接媒体节点成功，18:09:58.853 配置成功，
  18:10:19.274 节点离线。该时间为日志的 UTC+8 时间。

纠正先前中间判断：不能把当天日志文件的日期作为进程启动日期；
文件 mtime 本身不能证明旧进程启动时已经存在正确 URL。
关于“启动时空 URL”的最终结论依据新 Pod 的启动文件与直接初始化状态。

## 用户重启后的核验

用户重启的是 ZLM，调查任务未执行重启。

- 新 Pod：新 ZLM Pod（名称已脱敏）。
- Deployment 显示镜像仍为 `<image-version>`，Ready 1/1，重启数 0。
  Pod 于 `2026-10-16 18:36:56 +0800` Ready。
- 10:39:53 UTC 从新 Pod 只读原启动文件，得到：

  ```ini
  [general]
  mediaServerId=<media-server-id>
  [hook]
  alive_interval=10.0
  enable=1
  on_server_keepalive=
  on_server_started=
  ```

- 新文件大小 3391 字节，mtime `2025-12-08 10:40:45 +0800`，
  与旧 Pod 后来写入的文件不同。新进程仍从相同 `-c` 路径加载。
- `/proc/1/mountinfo` 中没有 `/opt` 下的挂载点；配置位于容器根文件系统。
- 新二进制符号与旧二进制一致。只读固定符号的少量字节，得到：

  ```text
  timer_null=1
  hook_enable_guard=1
  keepalive_url_guard=1
  alive_interval_guard=0
  ```

  C++ 局部静态初始化 guard 表明：开关和 URL 已初始化，但间隔初始化尚未执行。
  对照源码，空 URL 条件正处于这些语句之间，函数在此提前返回。

- 新 Pod 全接口被动观测 75 秒：Unix `1792147126..1792147201`
  （10:38:46–10:40:01 UTC），结果 packets=0、hook_posts=0、code0_responses=0。
  此段没有主动探测流量；不能单独以零包判根因，需与上述启动配置和初始化状态合看。
- 10:38:59 UTC 读取 WVP 小量日志，仍只有 18:09 上线、18:10 离线，
  没有新 Pod 启动后的上线事件。因此未通过持续在线验收。

## WVP 地址及处理链

从当前 WVP Pod 的实际进程环境只读确认（Pod 名称已脱敏）：

```text
ZLMEDIAKIT_HOST=<zlm-service-name>
ZLMEDIAKIT_HTTP_PORT=<configured-port>
```

WVP 调 ZLM API 与 ZLM 回调 WVP 是两个方向。上述环境配置第一条链路，
ZLM 的 hook URL 配置第二条链路。WVP 媒体地址未丢失。

本地处理链：keepalive 按 mediaServerId 从 Redis 媒体节点 hash 查记录；
找到才发布异步事件，再刷新实例内 `zlm-keepalive-{id}` 延迟任务，20 秒未续期则离线。
接口即使找不到节点也返回 code=0，故仅 HTTP/业务成功不能证明续期。
当前生产实证已经在 POST 发出之前失败，未执行 Redis/DB 修改或模拟续期。

历史源码基线 `ceadf74` 的离线重连任务实际为每小时一次，注释却写每 10 秒；
次级队列时间判断也有反向条件。当前工作树已有其他任务的修复。
此记录未读取生产整 JAR 来核验该调度注解，不把源码推断当作部署版本实证。

## 最小处理动作（10:42 UTC 阶段建议，后续已授权执行）

将下列非敏感字段合并到 ZLM **持久的启动配置**，路径仍为
`/opt/media/conf/config.ini`，保留其余现有安全配置、端口和媒体参数：

```ini
[hook]
enable=1
alive_interval=10
on_server_started=http://wvp-service.example.invalid:8080/index/hook/on_server_started
on_server_keepalive=http://wvp-service.example.invalid:8080/index/hook/on_server_keepalive
```

可使用现有持久配置机制或受控镜像修正；不能只在当前容器内改文件，
也不能只在启动之后调用 setServerConfig。持久配置应在 ZLM 进程启动前可见。
随后由用户只重启 ZLM，确认自然 on_server_started、每约 10 秒 keepalive、
响应 code=0，且 WVP 连续至少 60 秒保持在线（跨越 20 秒阈值）。
截至 10:42 UTC 该动作尚未执行；后续实际应用与恢复结果见本文顶部。
无需按本根因修改 WVP 媒体地址或密钥。

## 只读诊断阶段操作边界（截至 10:42 UTC）

本任务只读生产，小量日志/文件/符号及有时限被动观测；诊断进程设 64 MiB 虚拟内存上限。
没有整 JAR 读取、部署、配置写入、DB/Redis/Secret 修改、拉流或模拟心跳。
旧 ZLM exec 退出 137 对应用户更新 Pod；本任务没有发送停止/重启命令。
前置任务已披露的 WVP OOM 事故见原交接记录，本任务没有重做其整包读取。

## 历史候选：ConfigMap 拼接（未采用，禁止按此直接应用）

**以下方案最终未采用，已由顶部的内联脚本方案取代。** 用户当时要求先比较 Windows Docker 的正常部署；
ConfigMap 拼接只是尚未执行的候选，不代表所有 ZLM 都需要手配，
也不应在完成本地实际运行差异核对前应用。

后续在独立 YAML 预览中逐段只读核对当前 Deployment：未定义 volumes、
volumeMounts、command 或 args；没有提供该文件的既有 ConfigMap 名称/key。
原始文件路径仍为 `/opt/media/conf/config.ini`，内容由镜像提供。

为避免把原文件中的现有密钥复制到 ConfigMap，可只持久化非敏感 hook 覆盖片段。
以下资源名称是建议新建的名称，不是声称线上已经存在。

1. 在 `<production-cluster>/<namespace>` 的“配置中心 → 配置字典”新建：

   ```yaml
   apiVersion: v1
   kind: ConfigMap
   metadata:
     name: zlm-hooks-example
     namespace: example-namespace
   data:
     hooks.ini: |
       [hook]
       enable=1
       alive_interval=10
       on_server_started=http://wvp-service.example.invalid:8080/index/hook/on_server_started
       on_server_keepalive=http://wvp-service.example.invalid:8080/index/hook/on_server_keepalive
   ```

2. 编辑 `zlm-media-server` Deployment，给现有容器合并以下字段；现有 image、
   ports、env、资源限制及 Service/Ingress 保持原值：

   ```yaml
   # spec.template.spec.containers 中原来的 name: zlm-media-server 容器内：
   command: ["/bin/sh", "-ec"]
   args:
     - |
       cd /opt/media/bin
       zlm_previous_umask=$(umask)
       umask 077
       { cat /opt/media/conf/config.ini; printf '\n'; cat /etc/zlm-wvp-hooks/hooks.ini; } > /tmp/zlm-wvp-config.ini
       umask "$zlm_previous_umask"
       exec /opt/media/bin/MediaServer -s /opt/media/conf/default.pem -c /tmp/zlm-wvp-config.ini -l 0
   volumeMounts:
     - name: wvp-hooks
       mountPath: /etc/zlm-wvp-hooks
       readOnly: true
   ```

   在 `spec.template.spec` 下新增与 containers 同级的卷：

   ```yaml
   volumes:
     - name: wvp-hooks
       configMap:
         name: zlm-hooks-example
         items:
           - key: hooks.ini
             path: hooks.ini
   ```

   挂载映射：ConfigMap `zlm-hooks-example` / key `hooks.ini`
   → `/etc/zlm-wvp-hooks/hooks.ini`。
   新启动命令将原始完整配置与覆盖片段合并，实际 `-c` 使用
   `/tmp/zlm-wvp-config.ini`。该 ZLM 提交的 mINI 解析器对重复节/键按后值覆盖，
   已本地核对 `ZLToolKit/src/Util/mini.h` 的 parse 实现。
   不输出、不替换原密钥；原文件的其余字段原样带入。

3. 用户保存 Deployment 后会滚动创建新 Pod，通常不必再点一次“重启”。
   新 Pod 启动前已有 hook URL 才能建立心跳定时器。随后只读观察至少 60 秒，
   核验自然 POST、业务 code=0 和 WVP 持续在线。此步骤尚未由本任务执行。

### 独立审查文件与最后核验

- `PROD_ZLM_HEARTBEAT_20261016.configmap.yaml`：脱敏后的 ConfigMap 示例，无既有密钥。
- `PROD_ZLM_HEARTBEAT_20261016.deployment.strategic-merge-patch.yaml`：按容器名称合并的
  strategic merge patch。不是完整 Deployment，不能整体替换 Kuboard 中的原 YAML。
  原镜像、env、ports、资源限制、探针、Service/Ingress 不在补丁修改范围。
- 本地 ZLM HEAD 为 `1da300cf3e58ab63f5b0ef470bbb1224da3ab4a3`，gitlink 指向
  ZLToolKit `493d14e1682cc0c79e72b3893dd98f865d45b8e9`；本地子模块恰为同一提交，
  `src/Util/mini.h` 相对该提交无修改。该文件 parse() 第 51–63 行重复设置相同的
  `tag.key`，使用 `(*this)[symbol] = value`，所以后一个 hook 值覆盖前值。
  loadIniConfig() 先将整文件解析到临时 mINI，再复制最终键值；合并文件是同一次 parse。
- 生产此前小量只读证据已确认：工作目录 `/opt/media/bin`；
  `/opt/media/bin/MediaServer` 有执行权限；`/opt/media/conf/default.pem` 存在；
  Kuboard exec 使用 `/bin/sh`。原 PID 1 完整参数为
  `./MediaServer -s /opt/media/conf/default.pem -c /opt/media/conf/config.ini -l 0`。
  新命令保留 `-s`、`-l` 原参数及原工作目录，仅令 `-c` 指向合并文件。
- `umask 077` 仅在创建临时合并配置时使用，随后恢复原 umask，再 exec ZLM，
  保持应用后续录像/HLS 等文件的原始创建权限。包含原密钥的临时合并文件仅容器用户可读写；原文件只读取、不修改，
  内容未发送到本地、终端或 ConfigMap。ConfigMap 仅有四个非敏感 hook 字段。
- 本地普通权限 Docker 访问受限后，后续窄范围 docker ps/image ls 获准执行；
  完整 inspect 再过滤被自动审批拒绝，未执行，也未绕过获取完整环境变量。
  未拉取镜像或启动本地容器。未以本地用途不同的 Dockerfile.local
  断言生产镜像的 ENTRYPOINT 元数据，实际修复使用生产已核对的 PID 1 参数。
- 复制完整生产 YAML 到剪贴板的请求被自动审批拒绝，未执行；
  改用保留的服务端 revision 4 和仅移除 command/args 的本地窄范围回滚补丁。
  该替代方案未阻塞用户已授权的生产修复。
