# B 成员反馈核查与修复

## 1. 对旧第 5 条记录的核查结论

原始 `ntp_measurements.csv` 保留不变。第 4 条接收失败，第 5 条记录 offset=-2589 ms、delay=5508 ms。

检查旧代码后确认：旧实现直接使用回包 Origin Timestamp 作为 T1，没有核对它是否与本次发出的 Transmit Timestamp 一致。因此，“第 5 次接收到了第 4 次请求的迟到响应”是合理假设。但原始日志未记录四时间戳、回包来源或请求匹配信息，也没有当时的抓包文件，**无法从现有记录确认这就是实际原因**。原先把第 5 条直接归因于网络抖动的表述不够严谨，应将其视为原因未确认的异常记录，不用于精度结论。

## 2. 请求与回包匹配修复

修改 `Code/项目参考代码JAVA/TimeServiceClient_Library/src/TimeServiceClient_Library/NTP_Client.java`：

- 保存本次请求 Transmit Timestamp 的原始 8 字节；T1 使用客户端本次发送前的本地时间。
- 校验响应 IP 和源端口。
- 将响应第 24–31 字节的 Origin Timestamp 与本次请求时间戳逐字节比较。
- 旧包、短报文、错误头部、未同步服务器及零服务器时间戳不参与计算。
- 遇到无效包继续等待当前请求响应，使用单调时钟维护本次总超时，不因反复收到无效包延长等待期限。
- 日志记录总丢包数、Origin 不匹配包数和最后一次丢弃原因。
- 地址重新解析失败时清空旧地址，防止误用上次服务器。

这项匹配检查用于防止旧响应混入当前请求，不构成经过加密认证的服务器身份验证。

## 3. 可复算日志

修改 `Code/项目参考代码JAVA/TimeServiceClient_CONSOLE_uses_library/src/TimeServiceClient_CONSOLE_uses_library/TimeServiceClient_CONSOLE_uses_library.java`。所有取时尝试统一调用 `writeMeasurement`，不再按成功/失败分散写日志。

字段：

| 字段 | 含义 |
|---|---|
| request / server | 尝试序号 / 服务器 |
| status / reason | 结果码 / 具体原因或异常信息 |
| t1_unix_ms–t4_unix_ms | 四时间戳，Unix 毫秒 |
| local_unix_ms | 成功响应的客户端接收时间，等于 T4 |
| corrected_unix_ms | T4 + offset |
| offset_ms / delay_ms | 偏移 / 网络往返时延 |
| wait_ms | 成功发送后的接收等待耗时；发送失败时为发送尝试耗时 |
| resolve_ms | 最近一次地址解析耗时，独立于接收等待 |
| sent | 本次 UDP 是否成功发送 |
| discarded_packets | 本次等待中丢弃的无效响应数量 |
| origin_mismatch_packets | 本次等待中时间戳不匹配的包数 |
| last_discard_reason | 最后一次丢弃原因 |
| request_timestamp_hex | 本次发出的完整 8 字节 NTP 时间戳，十六进制 |

地址解析失败没有发送请求，因此 wait_ms=0；解析耗时见 resolve_ms。失败时无法取得的时间戳和计算结果留空，不填伪造的零。日志创建或写入失败会明确报错。

计算统一使用整数毫秒：

```text
offset = ((T2 - T1) + (T3 - T4)) / 2
delay = (T4 - T1) - (T3 - T2)
corrected = T4 + offset
```

除以 2 时采用 Java 整数除法（向零截断）；NTP 小数部分四舍五入到毫秒。因此有毫秒量化误差，不应把日志当作亚毫秒精度测量。

## 4. 验证方法与记录

在项目根目录执行：

```bash
javac -encoding UTF-8 -d out \
  "实验1/Code/项目参考代码JAVA/TimeServiceClient_Library/src/TimeServiceClient_Library/NTP_Client.java" \
  "实验1/Code/项目参考代码JAVA/TimeServiceClient_CONSOLE_uses_library/src/TimeServiceClient_CONSOLE_uses_library/TimeServiceClient_CONSOLE_uses_library.java" \
  "实验1/tests/NtpRegressionTest.java"
java -cp out NtpRegressionTest
python3 "实验1/tests/run_console_mock.py"
python3 "实验1/tests/verify_logs.py"
```

本地测试使用独立 UDP 服务端，**它是模拟场景，不是公网精度数据**：

1. 第一次请求不立即回复，让客户端超时。
2. 收到第二次请求后，先发送第一次请求的迟到回包，再发送短报文和未同步报文，最后发送第二次的合法回包。
3. 验证客户端只用第二次的响应计算，Origin 不匹配包数量为 1。
4. 用持续无效包验证总等待期限不会被延长。
5. 关闭客户端套接字验证 SEND_FAILED；使用非法地址验证地址解析失败和旧地址清除。
6. CSV 独立核算脚本逐行验证四时间戳公式和失败字段。

文件说明：

- `验证记录/响应匹配回归测试.txt`：本地回归测试输出。
- `验证记录/local_regression_rows.csv`：本地模拟成功、超时、发送失败、解析失败记录。
- `验证记录/console_local.csv` 和 `本地控制台运行记录.txt`：独立模拟服务器与完整控制台程序端到端运行的成功/超时记录。
- `验证记录/dns_failure.csv`：控制台实际运行的地址失败日志。
- `验证记录/ntp_matched.csv`：修复后公网请求记录（成功或失败均如实保存）。
- `验证记录/公网运行记录.txt`：对应控制台输出。
- `验证记录/ntp_direct_ip.csv` 和 `公网IP运行记录.txt`：使用此前实验得到的 IP 地址 129.6.15.25 直接请求，排除本次域名解析失败的影响；响应是否成功以日志为准。

### 控制台参数

```text
<服务器编号或主机名> [次数=5] [间隔毫秒=5000] [端口=123] [超时毫秒=500] [CSV路径=ntp_measurements.csv]
```

公网演示示例（显式指定新文件，避免覆盖旧数据）：

```bash
java -cp out TimeServiceClient_CONSOLE_uses_library.TimeServiceClient_CONSOLE_uses_library \
  1 5 5000 123 1000 "实验1/验证记录/ntp_matched.csv"
```

成员 A/C 可选取新的成功行，直接用 T1–T4 手算核对；成员 D 可用本地测试稳定演示迟到包处理。不能根据模拟测试断言旧第 5 条的实际成因，也不能仅凭 offset 大小断言绝对时间精度。

## 5. 本次实测结论

- Java 编译通过，本地回归测试全部通过。
- 控制台端到端测试得到一条成功和一条超时行；成功行包含完整四时间戳。
- 各 CSV 已通过独立脚本核算字段、公式和空值规则。
- 本次域名请求发生 DNS 失败；直接请求 129.6.15.25 的三次尝试均为约 1001 ms 接收超时。因此，本次没有获得公网成功响应，不能声称已在当前公网环境验证同步精度。
- 本地迟到包测试验证了防止旧响应混入计算的行为；这不能追溯证明旧第 5 条的实际成因。

### 一条可手算记录（本地模拟）

从 `local_regression_rows.csv` 的第二行取 T1–T4，使用原始整数毫秒代入公式即可复算。具体值以 CSV 为准，避免将再次运行后变化的时间戳与旧输出混用。

## 6. 更新：关闭代理后的正式重跑

关闭代理后域名解析与直连请求均已恢复，修复版客户端的 10 次公网直连请求全部成功，详见《重新实验/交接说明.md》。第 5 节所述失败仅代表当时的网络条件，不代表当前状态。本轮正式数据使用《重新实验/直连正式数据.csv》，不混入此前失败或代理采样。CSV 现另有 transport 字段，DIRECT_UDP 表示直连，SOCKS5_UDP 表示显式可选代理通道。
