# 成员 C Raft 2C 持久化与恢复

2C 已合入 A、B 的共享 Go 模块。入口为 `Make()` 和 `MakeWithConfig()`，持久化代码在 [raft.go](../2A/code/6.5840/src/raft/raft.go)，任期和 Follower 日志更新在 [election.go](../2A/code/6.5840/src/raft/election.go)。

## 持久化接入

| 持久状态变化 | 保存位置 |
| --- | --- |
| 增加任期并投给自己 | `startElectionLocked()` 发出 `RequestVote` 前 |
| 请求或回复携带更高任期 | `becomeFollowerLocked()` 更新任期、重置 `votedFor` 后，即使后续拒绝投票或追加 |
| 授予新选票 | `RequestVote()` 返回同意前 |
| Leader 接收新命令 | `Start()` 追加本地日志后、发起复制前 |
| Follower 追加或替换条目 | `AppendEntries()` 修改日志后、返回成功前 |

`persist()` 在 `rf.mu` 保护下将 `currentTerm`、`votedFor` 和 `log` 一起编码，调用 `Persister.Save(raftstate, nil)`。没有改变持久状态的心跳或投票重试跳过编码。自定义 `Command` 类型需要在 Raft 启动前用 `labgob.Register` 注册。

`MakeWithConfig()` 先初始化默认状态，再读取 `Persister`。解码成功后一起恢复三个字段，保留索引 0 的哨兵。空数据按首次启动处理，解码失败会报告节点编号和错误。重启从 Follower 开始，提交与应用索引从 0 开始，由后续复制和 Leader 通知推进。Leader 当选时根据恢复后的日志末尾初始化复制下标。`Kill()` 与持久写入共用状态锁，确保停机返回后旧实例的迟到 RPC 不会改写重启所用的存储。

## 运行验证

从仓库根目录运行，默认使用 baseline 参数。race 检测需要支持 cgo 的 C 编译器。

```sh
cd "实验2/2A/code/6.5840/src"
go test -v ./raft -run '^Test(VoteSurvivesRestart|LogSurvivesRestart|Persist[123])2C$' -count=1 -timeout=120s
go test -v ./raft -run '2[ABC]$' -count=1 -timeout=300s
go test -race -v ./raft -run '2[ABC]$' -count=1 -timeout=360s
```

## 验证结果

在 Windows/amd64、Go 1.27.4 下运行了 5 项重启专项检查，全部通过。 [测试原始输出](validation.log)

可直接复制结果表和使用 16:9 重启对照图。 [2C 重启与回归结果表](results/2C_重启与回归结果表.md) · [全组实验结果与图表索引](../实验结果与图表索引.md)

| 用例 | 实测结果 |
| --- | --- |
| `TestVoteSurvivesRestart2C` | 节点 1 重启前后的 `term=2`、`votedFor=0` 一致。它以 Follower 恢复，拒绝同任期候选人 2 的请求。停机后的迟到心跳携带任期 3，恢复出的任期仍为 2 |
| `TestLogSurvivesRestart2C` | 节点 0 在任期 1 应用索引 1 的 `before`。重启并进入任期 2 后当选，追加索引 2 的 `after`，依次应用 `before`、`after` |
| `TestPersist12C`、`TestPersist22C`、`TestPersist32C` | 全员或单节点重启、分区与反复重启，以及 Leader 和一个 Follower 崩溃后的恢复均通过。集群恢复到多数节点后能够继续提交命令 |

[重启专项测试源码](../2A/code/6.5840/src/raft/persistence_test.go) [课程测试源码](../2A/code/6.5840/src/raft/test_test.go)

2A–2C 回归覆盖 2A 7 项、2B 10 项、2C 10 项。普通运行通过 27 项，用时 191.618 秒。启用 `-race` 后通过同一组 27 项，用时 196.394 秒，测试中未报告数据竞争。`labgob` 和 `labrpc` 的独立测试也通过。 [测试原始输出](validation.log)

## 报告与材料

[实验报告](../实验报告.md)包含 Raft 角色图、实测重选时序、参数对照、复制与重启结果，以及五道必答题和 Paxos 选答题。A 的原始轨迹、结果表、图表和复现实验脚本位于 `2A/`，B 的交接与结果分析位于 `2B/`。

[课程骨架预研补丁](raft-persistence.patch)对应接入 A、B 前的课程骨架。集成后的 2C 验收使用上面的共享模块。
