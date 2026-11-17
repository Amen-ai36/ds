# 低时延样本筛选与偏移中位数

本改进在现有 Java NTP 核心外增加应用层筛选。每个时间源保留最近8条有效样本，选择 RTT 最低的3条，输出其偏移中位数。该模块用于减少偶发排队对单次校正量的影响。

## 代码位置

| 文件 | 位置与作用 |
| --- | --- |
| `NtpSampleFilter.java` | `Code/项目参考代码JAVA/TimeServiceClient_Library/src/TimeServiceClient_Library/`，筛选核心 |
| `FilteredClientDemo.java` | `Code/项目参考代码JAVA/TimeServiceClient_CONSOLE_uses_library/src/TimeServiceClient_CONSOLE_uses_library/`，调用原 NTP 核心并接入筛选器 |
| `tests/filter_replay/` | 本地 UDP 接入验证、构造场景与已有公网日志重放 |

`NTP_Client.java` 和原控制台入口的逻辑保持原样。新增入口与原入口可以分别运行。两个相关工程的 `javac.source`、`javac.target` 从1.7调整为1.8，以支持 `OptionalLong` 和方法引用。提交版使用普通静态内部类保存样本，算法与展示文档中的版本相同。

## 策略与调用约定

1. 只把通过原核心报文检查的成功样本送入筛选器。
2. 单个实例对应单个服务器，不混合不同时间源的偏移。
3. 最多保留8条样本，样本年龄超过60秒时移除。
4. 负 RTT 样本不入窗，少于3条时返回 `OptionalLong.empty()`。
5. 按 RTT 升序排列，同 RTT 优先较新样本，取前三条偏移的中位数。

样本年龄使用 `System.nanoTime()/1_000_000`，与 Unix 时间分开。输出 `correctedAtT4 = T4 + filteredOffset` 对应本次接收时刻。程序不修改操作系统时钟，也不在失败后输出未经更新的历史筛选读数。切换时间源时应新建实例或调用 `reset()`。

该策略是课程实验的简化实现，不等同于完整 RFC 5905 时钟过滤算法。自动备用源切换、时钟阶跃检测与加密认证尚未实现。

## 编译与运行

以下命令在仓库根目录运行。Java 源码面向 Java 8，测试脚本需要 Python 3.10 或更新版本，均不需要第三方 Python 包。

```powershell
python "实验1/tests/filter_replay/run_all.py" --java-home "C:\Program Files\Java\jdk-24"
```

若 JDK 已加入 PATH 或设置了 `JAVA_HOME`，省略 `--java-home`。脚本在新建临时目录中保存结果，不覆盖仓库已有实验记录。需要保留到指定位置时，显式加 `--output "你的结果目录"`。

脚本使用 JDK 9及以上版本时通过 `--release 8` 验证兼容性，使用 JDK 8时采用 `-source 1.8 -target 1.8`。本次验证使用 JDK 24.0.2，目标字节码版本为 Java 8。

手动编译新增客户端的示例：

```powershell
javac -encoding UTF-8 -d out "实验1/Code/项目参考代码JAVA/TimeServiceClient_Library/src/TimeServiceClient_Library/NTP_Client.java" "实验1/Code/项目参考代码JAVA/TimeServiceClient_Library/src/TimeServiceClient_Library/NtpSampleFilter.java" "实验1/Code/项目参考代码JAVA/TimeServiceClient_CONSOLE_uses_library/src/TimeServiceClient_CONSOLE_uses_library/FilteredClientDemo.java"
java -cp out TimeServiceClient_CONSOLE_uses_library.FilteredClientDemo ntp.aliyun.com
```

新增入口参数为 `<host> [port=123] [count=10] [intervalMs=5000] [timeoutMs=2000]`。前两次成功样本显示 `WARMING_UP_OR_REJECTED`，窗口具备足够样本后显示原始偏移、筛选偏移和校正读数。公网成功情况以实际运行结果为准。

## 评价方法

对照组直接采用本次偏移，改进组运行同一个 Java 筛选类。两组共享相同输入，筛选只使用当前及过去样本。仅在改进组已有输出的共同请求上比较，前两条热身样本不计入任一组的评价。

构造数据由已知偏移、单向时延和2 ms服务器处理时间生成完整四时间戳，保存在 `验证结果/controlled_timestamps.csv`。这些数据是受控输入，不是实测公网时延。P95采用 nearest-rank，即排序后第 `ceil(0.95*n)` 个数。

| 构造场景 | 共同请求 | 原始平均绝对误差 ms | 筛选平均绝对误差 ms | 原始P95 ms | 筛选P95 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| 突发单向排队 | 10 | 23.60 | 0.30 | 80 | 1 |
| 持续路径不对称 | 8 | 40.00 | 40.00 | 40 | 40 |
| 真实偏移阶跃 | 14 | 0.00 | 42.86 | 0 | 100 |

稳定真值100 ms伴随突发排队时，最大绝对误差由80 ms降至1 ms。持续不对称时，40 ms的偏差没有消失。真实偏移从100 ms变为200 ms后，旧低时延样本会拖慢跟踪，本构造场景直到第15个请求才跟随，即变化后的第7条、相隔30秒。

已有公网数据分别来自 `成员C/原始记录/公网-阿里云.csv`、`成员C/原始记录/公网-Cloudflare.csv`、`重新实验/直连正式数据.csv`。本次只进行离线重放，没有重新采集公网数据。每组比较请求3至10，共8条。

| 公网批次 | 原始偏移标准差 ms | 筛选偏移标准差 ms |
| --- | ---: | ---: |
| 组内阿里云 | 2.17 | 0.35 |
| Windows 阿里云 | 4.50 | 3.15 |
| Windows Cloudflare | 27.13 | 4.82 |

公网数据缺少独立时钟真值，因此表中只说明输出波动减小，不证明绝对精度提高。原始值和筛选值必须使用相同8条样本，不能用全10条原始数据与8条筛选结果直接比较。

## 已验证内容

- 新增类与原核心共同编译为 Java 8字节码。
- 本机实际 UDP 收发中，5次合法响应产生2次热身和3次筛选输出。
- 接收超时后不产生新的筛选读数。
- 负 RTT、样本过期和窗口重置检查通过。
- 构造场景和原有公网 CSV均由同一个 Java 筛选类重放。

`验证结果/` 保留本次可复算结果。`filter_replay.csv` 为逐条输出，`summary.json` 为汇总统计，`validation.txt` 为验证记录。再次运行会产生新的本地收发时间戳，统计用的重放输入保持相同。
