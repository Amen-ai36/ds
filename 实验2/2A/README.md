# 成员 A 领导选举与参数改进

本目录按仓库 `三人分工.md` 完成成员 A 的范围：MIT 2A 选举、初始选举与 Leader 断连重选、参数改进与对照实验、必答第 4、5 题及三个 Paxos 选答题。代码依据桌面 `实验2.zip` 中的课程骨架，保留原始测试和网络库。

## 从哪里看

| 需要的内容 | 位置 |
| --- | --- |
| 可运行的 2A–2C 模块 | `code/6.5840/src/` |
| 选举主要实现 | `code/6.5840/src/raft/raft.go`、`election.go` |
| 可切换参数策略 | `code/6.5840/src/raft/election_config.go` |
| 固定参数基线源码 | `baseline/`；[改进说明](docs/implementation-and-improvement.md) |
| 原始测试输出与每轮实验记录 | `results/raw/` |
| 实测汇总与结果表 | [实验结果](results/experiment-results.md)、`results/tables/summary.csv` |
| 初选、重选时间线及对比图 | `results/figures/` |
| 每个指标和文件是什么 | [数据字典](docs/data-dictionary.md) |
| 报告整合用文字 | [成员 A 报告材料](docs/member-a-report.md) |
| 必答题与 Paxos 对比 | [问答与对比表](docs/questions-and-paxos.md) |
| PPT 图片与讲解对应 | [PPT 素材索引](docs/ppt-materials.md) |

## 运行原始 2A 测试

需要 Go 1.20 或更高版本；本次具体版本以 `results/raw/run_metadata.json` 为准。本模块仅使用 Go 标准库和资料包中的 `labgob`、`labrpc`，不需要下载第三方 Go 依赖。

```sh
cd code/6.5840/src/raft
go test -v -run '2A$' -count=1
```

默认采用 baseline 策略。在 PowerShell 中切换策略：

```powershell
$env:RAFT_ELECTION_PROFILE = 'fast'  # baseline、fast、stable
go test -v -run '2A$' -count=1
```

Linux/macOS 可以使用 `RAFT_ELECTION_PROFILE=stable go test -v -run '2A$' -count=1`。

课程原始的三个 2A 测试分别检查初始选举、断连重选和多次选举。补充测试检查同任期单候选人投票、心跳任期规则、拒绝投票不重置超时以及单节点自投选举。`config.go`、`test_test.go`、`labrpc`、`labgob` 保持资料包原样。

## 一次生成实验数据

在本目录运行：

```sh
python scripts/run_experiments.py --go /path/to/go --trials 10
```

若 Go 已在 PATH，可以省略 `--go`。具备支持的 C 编译器时，增加 `--race` 检测数据竞争。脚本先以 baseline 重复跑三遍 2A，再分别对 fast、stable 回归；通过后才运行三种策略、两种网络场景的对照实验。每个分组默认 10 次，自动导出 CSV、JSON 事件和 Markdown 汇总。

只回归用 `--tests-only`，只采集用 `--experiments-only`。已有 CSV 可通过 `python scripts/summarize_results.py` 重新汇总。Windows 上运行 `pwsh -File scripts/make_figures.ps1` 从已有数据生成 PNG。

`scripts/github-actions-2a.yml` 是可选的在线运行配置，只有放到仓库根目录 `.github/workflows/` 并手动启动才会运行。本交付保留它在 2A 内，没有替其他成员修改仓库级配置。

## 与成员 B、C 接入

当前 `code/` 汇合了 A 的领导选举、B 的日志复制和 C 的持久化。`Make()`、`GetState()`、`Start()`、`ApplyMsg`、`Kill()` 和 `Snapshot()` 沿用课程接口。集成代码的使用方式和重启测试见 [2C 说明](../2C/README.md)。

初选与重选图来自 A 的原始选举事件。参数对照使用当时的 2A 版本和记录中的工具链，Paxos 对比依据论文。 [选举数据与分析](results/experiment-results.md) [集成后的完整报告](../实验报告.md)
