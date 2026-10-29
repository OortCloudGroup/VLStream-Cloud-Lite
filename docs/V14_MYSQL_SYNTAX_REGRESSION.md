# V14 MySQL 语法回归（仅显式启用）

`V14MysqlSyntaxProbe` 是测试源码工具，不启动 Spring、Flyway 或应用服务，不执行迁移。
默认输出 `SKIPPED`，不打开数据库连接。H2/路径检查不能代替这里的 MySQL 1064 验证。

## 固定边界

- 唯一目标：`192.168.60.77:32443/ry-wvp-local`；服务器 UUID 必须为
  `c89187b7-f09e-11ee-8e2b-1a041c82250c`。在读取权限数据前先核验 `DATABASE()` 和 `@@server_uuid`。
- 源文件固定为 `V1_2_14__vlstream_device_sharing.sql`，SHA-256 必须为
  `06474458446648A20FDFDE52741F46708DA949EFECB127F5F984D510B2434A72`。
- 只对源文件的两条 `sys_menu INSERT ... SELECT` 各做普通 `EXPLAIN`；随后各恢复 SELECT 行末
  多余的一个 `)`，要求两条坏 SQL 都返回 MySQL vendor code **1064**。
- 从不执行 `INSERT`、DDL、GRANT、`EXPLAIN ANALYZE` 或迁移其余语句；强制关闭多语句、自动重连、
  local infile。**不使用 READ ONLY 事务**，因为 MySQL 会以 1792 拒绝其中的 `EXPLAIN INSERT`。
- 前后比较 `sys_menu`、`sys_role`、`sys_role_menu`、`sys_user_role`、共享 grant/request 的行数与
  按主键排序、长度分隔的 SHA-256 内容摘要。共享表未创建时记录 ABSENT，不能在本测试创建。
  输出仅有计数/摘要，不打印行、凭据、连接 URL 或驱动错误原文。并发修改会使探针失败，应由操作者排除并发影响。

## 无凭据命令行的运行方式

从仓库根目录执行。运行中的服务使用原仓库 target 时，下面独立编译到唯一 Temp，避免修改它们：

```powershell
$probeClasses = Join-Path $env:TEMP ('wvp-v14-syntax-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $probeClasses | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -d $probeClasses `
  ruoyi-admin/src/test/java/com/ruoyi/web/service/V14MysqlSyntaxProbe.java
# $mysqlDriverJar 指向操作者已缓存的 mysql-connector-j 8.0.33 JAR；不下载依赖。
& "$env:JAVA_HOME/bin/java.exe" -cp "$probeClasses;$mysqlDriverJar" com.ruoyi.web.service.V14MysqlSyntaxProbe
```

实际运行前，由操作者从既有受保护配置**只在内存中**注入这些子进程环境变量，不提交、不回显，
不把值放在命令行参数或新配置文件：

| 环境变量 | 内容 |
| --- | --- |
| `WVP_V14_MYSQL_ENABLED` | 必须显式为 `true`，缺省/false 跳过 |
| `WVP_V14_MYSQL_URL` | 上述固定目标的 JDBC MySQL URL，禁止 URL 身份信息 |
| `WVP_V14_MYSQL_USER` / `WVP_V14_MYSQL_PASSWORD` | 操作者提供的现有凭据，不能由探针创建角色或授权 |
| `WVP_V14_MYSQL_MIGRATION` | 可选，显式源 V14 路径；缺省从 reactor/module cwd 定位并核对 SHA |

URL 仅允许可选 `useSSL/requireSSL/verifyServerCertificate/allowPublicKeyRetrieval/useUnicode` 布尔项、
`characterEncoding=utf8|UTF-8|utf-8`、`serverTimezone=UTC|Asia/Shanghai|GMT+8|GMT+08:00`（URL 编码）及
标准 `sslMode` 值；拒绝重复、未知项及 user/password、multi-query、会话/日志等选项。
等效系统属性前缀为 `wvp.v14.mysql.`，字段为 enabled/url/user/password/migration；优先使用环境注入，
避免凭据出现在进程命令行或 Maven 日志中。

成功需输出 `corrected_EXPLAIN=2; original_1064=2; server_uuid_verified=true` 和前后摘要一致。
退出码 0 但输出 SKIPPED 不代表已完成真实 MySQL 验证；任何错误退出码为 1，错误只保留安全 SQLState/编号。

`V14MysqlSyntaxProbeTest` 的离线用例验证默认跳过、目标/UUID/源 SHA 限制、坏括号构造、只读 SQL
类别和同数量内容变更检测；其中真实 MySQL 用例使用同一显式开关，默认 JUnit skip。
本工作项仅实现工具和离线回归；真实目标执行与结果记录由操作者单独完成。
