# PPT 素材索引

图片按讲解顺序编号，均位于 `results/figures/`，使用 1600×900 PNG，可直接插入 16:9 幻灯片。结果表为 UTF-8 CSV，可用 Excel 打开后按 PPT 模板重新排版。

| 图片或表格 | 建议标题 | 讲解重点 | 数据来源 |
| --- | --- | --- | --- |
| `01_initial_election_timeline.png` | 初始 Leader 如何选出 | 节点超时、候选人自投与请求投票、取得 3/5 多数票后当选 | `raw/traces/reliable_baseline_trial_01.json` |
| `02_leader_failure_reelection_timeline.png` | Leader 断连后的重选 | 断开节点仍运行；剩余 4 个节点取得原 5 节点集群的多数票 | 同一轮 trace 的故障后事件 |
| `03_reelection_latency.png` | 参数对故障恢复耗时的影响 | 相同场景内比较平均耗时、波动与单轮散点 | `raw/trial_metrics.csv` 与 `tables/summary.csv` |
| `04_election_rounds.png` | 是否需要更多轮竞选 | 区分选举轮数和候选人发起次数；不要把轮数说成票数 | 同上 |
| `05_unexpected_leader_changes.png` | 无主动故障时的稳定性观察 | 对比累计换主次数；零次也要同时说明观察时长 | 同上 |
| `tables/summary.csv` | 对照实验结果表 | 成功数、平均初选和重选、P95、选举轮数、失败状态 | 自动汇总脚本 |
| `docs/questions-and-paxos.md` 中的表 | Raft 与 Paxos 对比 | 复制日志场景使用 Multi-Paxos 对比，性能不能仅按算法名称排序 | 课程参考论文 |

推荐讲解顺序：实现位置与流程 → 初选 → Leader 故障重选 → 三种参数的对比 → 必答 4、5 题 → Paxos 对比。整体 Raft 原理、2B、2C 和最终报告整合由其他成员按原分工完成。

`results/experiment-results.md` 可作为结果页的数值来源；`docs/data-dictionary.md` 可回答“这个耗时从哪里到哪里”“换主次数观察了多久”等追问。不要把单机网络模拟的毫秒数称作真实多机生产环境的指标。
