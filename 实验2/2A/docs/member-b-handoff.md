# 成员 B 开发与成员 C 交接记录

本文记录 B 交接时的状态。集成后的持久化代码和重启测试见 [2C 说明](../../2C/README.md)。

## 工作过程

1. 从 `origin/second` 拉取远程引用并查看 `三人分工.md`。确认 B 的范围是 2B 日志追加/复制、冲突修复、提交应用、日志新旧投票检查，以及故障场景与少数派分析。
2. 将远程分支的 `实验2/2A/` 和分工文档导入本地。原实验二资料目录未被覆盖。
3. 在成员 A 的 2A 实现上扩展 Raft：引入带任期的 `LogEntry`；`Start` 追加本地条目并触发复制；AppendEntries 检查 `PrevLogIndex/PrevLogTerm` 并返回冲突提示；Leader 根据回复调整 `nextIndex` 并重试；仅提交当前任期且已复制到多数派的最大日志索引；applier 顺序发送已提交命令。
4. 在 RequestVote 中加入最后日志任期优先、索引次之的新旧比较。补充 Leader 当选时的复制索引初始化。
5. 安装 Go 1.27.4，使用 `gofmt` 格式化，并完成 2A/2B 普通测试和 race 测试。

## 接口与字段约定

- 日志使用 `rf.log[0]` 作为哨兵项；真实日志索引从 1 开始，`LogEntry` 字段为 `Command interface{}` 和 `Term int`。
- `RequestVoteArgs` 新增 `LastLogIndex`、`LastLogTerm`。
- `AppendEntriesArgs` 携带 `PrevLogIndex`、`PrevLogTerm`、`Entries`、`LeaderCommit`；回复携带 `ConflictTerm`、`ConflictIndex`。
- `commitIndex` 和 `lastApplied` 均为日志索引；命令通过原有 `applyCh` 以 `CommandValid=true` 投递。
- `nextIndex`、`matchIndex` 按 peer 下标保存；RPC 期间不持有 `rf.mu`，RPC 返回后校验角色和任期，避免旧回复更新新任期状态。

## 成员 C 接手事项

2C 尚未实现。请沿用现有字段，不要重复声明：`currentTerm`、`votedFor`、`log []LogEntry`。在 `persist()`/`readPersist()` 中按兼容顺序编码、解码这三项；每次改变任期、投票或日志后，回复 RPC 或启动复制前完成持久化。需要重点核对这些调用点：`becomeFollowerLocked`、`startElectionLocked`、`RequestVote`、`Start`、`AppendEntries`。

恢复时初始化 `commitIndex`、`lastApplied` 为 0，并在 `MakeWithConfig` 恢复持久状态之后设置各 peer 的复制索引；提交索引不应被误当作持久字段。当前 `Snapshot` 仍是 2D 空实现，日志哨兵/快照偏移需要由后续 2D 单独设计。

## 故障场景分析草稿

五节点集群中，提交需要至少三个节点确认。Leader 与两个节点组成多数派时可继续复制并提交；只剩两个节点互通时，即使它们的进程仍运行，也不能形成多数派，因此不能选出满足安全规则的新 Leader，也不能提交新的日志。少数派中已有但未提交的条目可能在恢复通信后被新 Leader 的日志覆盖。Follower 暂时断开时，Leader 保留其 `nextIndex` 并在重连后的 AppendEntries 冲突响应中回退，再补齐缺失日志。课程 2B 测试已覆盖这些复制、故障和冲突回退场景；重启恢复的实测记录待成员 C 完成 2C 后补充。

## 验证状态

- 已完成源码静态检查及交接说明。
- Go 1.27.4 已安装到 `/Users/veritas/.local/opt/go1.27.4`，并已通过普通测试与 race 测试。
- 成员 B 的场景分析中，少数派结论来自课程 2B 故障测试；更完整的重启恢复结果待成员 C 完成 2C 后补充。

## 2026-11-26 验证记录

- Go 工具链：`go1.27.4 darwin/arm64`，安装目录为 `/Users/veritas/.local/opt/go1.27.4`。
- 执行 `gofmt -w raft/*.go labrpc/*.go labgob/*.go`。
- 执行 `go test ./raft -run '2A|2B' -count=1 -timeout=240s`：通过，`ok 6.5840/raft`，约 69 秒。
- 执行 `go test -race ./raft -run '2A|2B' -count=1 -timeout=360s`：通过，`ok 6.5840/raft`，约 66 秒。
- 执行完整 `go test ./raft -count=1 -timeout=300s`：2A 全部通过、2B 全部通过；在 2C `TestPersist12C` 处失败，原因是 2C 持久化函数仍为空。该失败不归因于 2B。

## 成员 C 接手建议顺序

1. 先实现 `persist` 和 `readPersist`，保留日志哨兵项，并在恢复后让 `nextIndex` 在 Leader 当选时重新初始化。
2. 运行 `go test ./raft -run '2C$' -count=1`，再运行完整 `go test ./raft -count=1`。
3. 若 2C 通过，再与 B 联合检查重启后的日志复制；重点覆盖 `TestPersist12C`、`TestPersist22C`、`TestPersist32C` 和 `TestFigure82C`。
