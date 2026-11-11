# 实验 1：成员 B 任务完成总结

## 一、负责内容

成员 B 负责“客户端实现与演示准备”，具体包括：

- 在参考代码基础上完善 NTP 客户端；
- 实现 NTP 时间戳解析和时间偏移计算；
- 增加超时、异常和失败处理；
- 输出可用于演示的同步结果；
- 保存实验日志，供成员 C 进行测试分析；
- 整理运行方法和演示步骤，供成员 D 使用。

## 二、代码修改

### 1. NTP 客户端库

修改文件：

`Code/项目参考代码JAVA/TimeServiceClient_Library/src/TimeServiceClient_Library/NTP_Client.java`

完成的功能：

- 使用 UDP 向 NTP 服务器发送请求；
- 在请求中写入客户端发送时间戳 T1；
- 解析服务器接收时间戳 T2；
- 解析服务器发送时间戳 T3；
- 在客户端接收响应时记录 T4；
- 支持 NTP 时间戳整数部分和小数部分的转换；
- 将 NTP 纪元时间转换为 Unix 时间；
- 检查响应长度，避免解析无效报文；
- 保留 500 ms 接收超时，防止服务器无响应时程序长时间阻塞。

使用的计算公式为：

```text
时钟偏移 offset = ((T2 - T1) + (T3 - T4)) / 2
往返时延 delay = (T4 - T1) - (T3 - T2)
校正时间 = 客户端接收时间 T4 + offset
```

客户端结果对象新增了以下数据：

- `lOffsetMillis`：时钟偏移，单位为毫秒；
- `lDelayMillis`：网络往返时延，单位为毫秒；
- `lLocalUnixTimeMillis`：客户端本地接收时间；
- `lCorrectedUnixTimeMillis`：应用偏移后的校正时间。

### 2. 控制台客户端

修改文件：

`Code/项目参考代码JAVA/TimeServiceClient_CONSOLE_uses_library/src/TimeServiceClient_CONSOLE_uses_library/TimeServiceClient_CONSOLE_uses_library.java`

完成的功能：

- 调用 NTP 客户端库周期性请求时间；
- 输出校正后的 Unix 时间和时分秒；
- 输出时钟偏移和网络往返时延；
- 区分服务器地址错误、发送失败和接收失败；
- 在接收失败后继续运行后续请求；
- 生成 `ntp_measurements.csv` 测量日志；
- 程序退出时关闭 UDP 套接字和日志文件。

日志字段如下：

```text
request,status,corrected_unix_ms,offset_ms,delay_ms
```

## 三、运行环境

- 操作系统：macOS
- Java：Java 25 LTS
- 编译器：`javac 25.0.5`
- 通信方式：UDP
- NTP 服务端口：123
- 测试服务器：`time.nist.gov`

编译命令：

```bash
cd "/Users/veritas/Documents/ds实验"
rm -rf out
mkdir out
javac -encoding UTF-8 -d out \
  "实验1/Code/项目参考代码JAVA/TimeServiceClient_Library/src/TimeServiceClient_Library/NTP_Client.java" \
  "实验1/Code/项目参考代码JAVA/TimeServiceClient_CONSOLE_uses_library/src/TimeServiceClient_CONSOLE_uses_library/TimeServiceClient_CONSOLE_uses_library.java"
```

运行命令：

```bash
java -cp out TimeServiceClient_CONSOLE_uses_library.TimeServiceClient_CONSOLE_uses_library 1
```

## 四、实际测试结果

程序成功解析 `time.nist.gov`，并完成多次 NTP 请求。一次测试结果如下：

| 请求 | 状态 | 时钟偏移 | 往返时延 |
|---:|---|---:|---:|
| 1 | 成功 | -4 ms | 318 ms |
| 2 | 成功 | 1 ms | 319 ms |
| 3 | 成功 | 41 ms | 229 ms |
| 4 | 接收超时 | — | — |
| 5 | 成功但数据异常 | -2589 ms | 5508 ms |

测试说明：

- 前三次请求证明客户端可以正常完成 NTP 通信和偏移计算；
- 第四次请求验证了超时处理；
- 第五次是原因未确认的异常记录：旧实现未匹配请求时间戳，可能混入旧响应，不能直接归因于网络抖动；
- 程序在超时后没有退出，仍然能够继续处理后续请求。

## 五、交付物

- 完善后的 NTP 客户端源代码；
- 完善后的控制台演示程序；
- 编译输出目录 `out`；
- 测量日志 `ntp_measurements.csv`；
- 本总结文档。

## 六、已知问题与后续改进

当前程序能够完成基本 NTP 同步和误差测量，但仍可以进一步改进：

1. 已补充原始时间戳和回包来源匹配、固定总等待期限及四时间戳/失败原因日志，详见《B成员反馈修复说明.md》；
2. 对异常大的 `offset` 和 `delay` 进行过滤；
3. 同时请求多个 NTP 服务器，并选择延迟较小或结果较稳定的服务器；
4. 连续采样多次后使用中位数或平均值提高稳定性；
5. 增加模拟本地时钟偏差参数，方便演示校时效果；
6. 在 GUI 客户端中显示本地时间、校正时间、偏移和网络时延。

## 七、演示流程

1. 编译两个 Java 源文件；
2. 运行控制台客户端；
3. 展示 NTP 服务器地址和端口；
4. 展示成功请求的校正时间、偏移和往返时延；
5. 展示超时请求仍能继续运行；
6. 打开 `ntp_measurements.csv`，说明日志字段和异常数据；
7. 结合测试结果说明网络延迟、抖动和服务器不可用会影响同步精度。

## 八、任务完成结论

成员 B 的必需客户端实现和演示准备工作已经完成。程序能够通过 NTP 获取网络时间，基于四时间戳计算时钟偏移和网络时延，输出校正后的时间，并对超时和失败情况进行处理。生成的 CSV 日志可以直接交给成员 C 进行实验数据分析，运行命令和演示流程可以交给成员 D 使用。

## 九、反馈修复更新

已按成员反馈补充请求响应匹配、可复算日志、失败原因与耗时，并用本地 UDP 服务端测试迟到包处理。旧第 5 条记录不能追溯确认成因，保留原始文件。新增 CSV 字段和复现命令见 [B成员反馈修复说明](B成员反馈修复说明.md)。

## 十、正式直连重跑结果

关闭代理后已完成 10 次新的公网直连请求，全部成功，完整四时间戳与公式复算通过。后续交接以 [重新实验/交接说明.md](重新实验/交接说明.md) 和该目录的《直连正式数据.csv》为准；旧数据保留存档。
