# 数据与图表口径

所有计时数据由 Go 程序运行课程 `labrpc` 网络时采集，使用本机实际时钟。它们描述的是同一台电脑上多个 Raft 节点的网络模拟实验，不是多台真实服务器的网络性能。

## 实验分组

每组使用 5 个节点，启动后等待一个 Leader 连续保持至少两个心跳周期。随后进行 2 秒没有主动注入故障的观察，再模拟断开当时的 Leader 的全部入站和出站连接，观察其余 4 个节点重选。断开的是网络，节点进程仍然运行。旧分区中原 Leader 的自认状态不计入剩余多数派的 Leader 判断。

| 参数策略 | 心跳间隔 | 随机选举超时 | 设计目标 |
| --- | --- | --- | --- |
| baseline | 120 ms | [450, 750) ms | 固定基线 |
| fast | 100 ms | [300, 500) ms | 缩短检测等待；需要观察延迟与重试的代价 |
| stable | 150 ms | [800, 1200) ms | 留出更长通信余量；接受更慢故障检测 |

上述目标是设计动机，实际快慢和稳定性必须根据结果判断。不是三种新的共识算法，也没有改变多数票或任期规则。

| 网络场景 | 课程网络设置 | 解释 |
| --- | --- | --- |
| reliable | `Reliable(true)`，不启用 `LongReordering` | 不主动丢包或延迟回复的参考场景 |
| lossy_reordered | `Reliable(false)` 与 `LongReordering(true)` | 课程网络的随机丢包、短延迟，以及较长的回复延迟和乱序；不是固定 RTT 的 WAN 基准 |

同一轮次对三种策略使用同一个随机种子，并轮换三种策略的执行顺序。Go 调度和并发 RPC 会影响随机数调用顺序，因此种子相同不代表每条网络消息经历完全相同的故障；应理解为相同节点数、故障类型及随机分布下的重复试验。

## 文件对应关系

| 文件 | 内容 | PPT 用途 |
| --- | --- | --- |
| `results/raw/validation_baseline.log` | 基线参数下的原始 2A 测试及补充规则检查 | 测试通过证据 |
| `results/raw/validation_fast.log`、`validation_stable.log` | 两种可切换策略的 2A 回归测试 | 改进后回归证据 |
| `results/raw/election_experiments.log` | 每次试验的状态和关键测量值 | 定位具体轮次 |
| `results/raw/trial_metrics.csv` | 一次试验一行，保留失败状态和原始指标 | 可自行在 Excel 重新作图 |
| `results/raw/traces/*.json` | 节点编号、任期、投票、当选和退位的精确事件 | 初选与重选过程的证据 |
| `results/raw/run_metadata.json` | Go 版本、系统、命令、退出码、种子、源文件 SHA-256 | 说明实验环境与可复现性 |
| `results/tables/summary.csv` | 按网络场景和参数策略汇总 | PPT 结果表 |
| `results/experiment-results.md` | 自动汇总的表格、失败状态及说明 | 报告结果部分 |
| `results/figures/` | 对比图与初选、重选时间线 | 可直接插入 PPT |

没有运行脚本时，结果文件不会凭空生成。最终以已有原始文件和运行记录为准。

## trial_metrics.csv 字段

| 字段 | 含义与单位 |
| --- | --- |
| scenario / profile / trial | 网络场景、策略名及轮次 |
| nodes / seed | 节点数与该轮种子 |
| status | `ok`、`initial_timeout`、`unstable_before_fault` 或 `reelection_timeout`；失败必须保留 |
| initial_leader / initial_term | 初始稳定 Leader 的节点编号和任期；编号从 0 开始 |
| initial_ms | 从开始构造集群到该初始 Leader 实际当选的毫秒数 |
| old_leader / old_term | 断连时选择的 Leader 及其任期 |
| new_leader / new_term | 剩余可通信节点中，新稳定 Leader 的编号及任期 |
| reelection_ms | 从开始断开旧 Leader，到选定的新 Leader 实际当选的毫秒数；不包含之后确认其稳定性的等待 |
| election_rounds | 断连至新 Leader 当选之间，剩余节点发起竞选的不同任期数量；不是收到的票数 |
| campaigns | 同一时间范围内，剩余节点发起竞选的总次数；同一任期多个候选人会分别计数 |
| unexpected_leader_changes | 2 秒无主动故障观察期内，当选事件中 Leader 节点编号变化的次数 |
| quiet_term_changes | 同一观察期内，当选 Leader 的任期变化次数；同一节点再次当选也计入 |
| quiet_seconds | 无主动故障观察期的实际长度；汇总时报告每次试验变化次数及总观察时间 |
| rpc_count / rpc_bytes | 到该轮结束采样时，课程网络累计的 RPC 数和字节数；包含启动、观察与故障重选各阶段 |

初选或重选超时，相关耗时字段留空，不能用 0 代替失败。等待稳定 Leader 的上限为 5 秒，其中包含连续保持两个心跳周期的确认要求，因此它比只观察到一次瞬时当选更严格。

## 汇总和图表的限制

耗时统计只汇总有对应测量值的试验，同时单独报告成功数和各失败状态。P95 使用最近秩法；每组默认只有 10 次，它主要反映这批样本的尾部，不能解释为精确的总体 P95。误差线使用样本标准差，并同时保留每轮散点。

非预期换主为 0，只表示这批短观察窗口内没有观察到节点编号变化，不证明任意网络条件下永不换主。选举测试验证的安全性主要是每次当选取得多数票、同一任期不出现两个实际当选节点；完整日志一致性还需要成员 B 的 2B 和成员 C 的 2C。

RPC 总数受试验实际持续时间影响，不能只凭其总量比较固定时间内的吞吐，也不能据此推导客户端操作吞吐量。Paxos 本次只有文献对比，没有生成 Paxos 的“实测”曲线。
