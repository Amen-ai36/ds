# 实验二成员 B 过程记录与成员 C 交接

日期：2026 年 11 月 26 日

## 任务范围

根据远程 `second` 分支中的《三人分工》，成员 B 负责：

- Raft 2B 日志追加、复制、冲突处理、提交和 apply；
- RequestVote 的日志新旧检查；
- 正常复制、Follower 断开恢复、Leader 切换、少数节点存活等故障分析；
- 向成员 C 提供可直接接续的字段、接口和测试记录。

## 工作过程

1. 在 `ds实验` 仓库执行 `git fetch origin second`，获得 `origin/second`。
2. 检查远程分支，确认它已经包含成员 A 的 `实验2/2A` 交付和 `三人分工.md`，但没有完成 2B。
3. 使用 `git archive origin/second` 将远程 `实验2/2A` 导入本地实验目录，保留原有实验材料。
4. 在成员 A 的 2A 源码上实现 2B，代码位于 `实验2/2A/code/6.5840/src/raft/`。
5. 安装 Go 1.27.4，使用 `gofmt` 格式化并执行课程测试。
6. 将代码边界、测试结果和 2C 接入点写入 `实验2/2A/docs/member-b-handoff.md`。

## 已实现内容

- `LogEntry{Command, Term}`，日志索引从哨兵项 `log[0]` 后的 1 开始；
- `Start`：Leader 在当前任期追加命令，返回日志索引/任期/Leader 状态，并触发复制；
- `AppendEntries`：检查前置索引和任期，处理缺失日志、任期冲突和多余日志；
- Leader 维护 `nextIndex`、`matchIndex`，根据冲突回复回退并重试；
- 只在当前任期日志复制到多数节点后推进 `commitIndex`；
- applier 按索引顺序通过 `applyCh` 发送 `CommandValid=true` 的 `ApplyMsg`；
- RequestVote 使用“最后日志任期优先、索引次之”判断候选日志是否足够新；
- Leader/Follower 失败与重连场景复用课程 labrpc 测试。

## 测试结果

使用命令：

```sh
export PATH=/Users/veritas/.local/opt/go1.27.4/bin:$PATH
cd "/Users/veritas/Documents/buaa/大三上/分布式/ds实验/实验2/2A/code/6.5840/src"
go test ./raft -run '2A|2B' -count=1 -timeout=240s
go test -race ./raft -run '2A|2B' -count=1 -timeout=360s
```

两条命令均通过。完整 `go test ./raft` 中 2A 和 2B 全部通过，随后在 2C 持久化测试失败；这是因为 `persist()`/`readPersist()` 仍是骨架空实现，交给成员 C 接续。

## C 接手时必须保持的约定

- 不要重复声明 `currentTerm`、`votedFor`、`log`；这些字段已经存在于 `Raft`。
- `persist` 至少编码 `currentTerm`、`votedFor`、`log`，并在任期、投票、日志改变后调用。
- `readPersist` 恢复日志时保留 `log[0]` 哨兵项；恢复后 `commitIndex` 和 `lastApplied` 从 0 开始。
- `Snapshot` 仍未实现，2D 的日志偏移和快照状态不要在本次 2B 修改中混入。
- 完成 2C 后，先跑 2C 专项测试，再跑完整 raft 测试和 race 测试。

## 故障分析初稿

五节点集群需要至少三个节点形成多数派。Leader 和两个 Follower 可继续提交日志；只有两个节点互通时，节点进程仍可能运行，但不能选出多数派 Leader，也不能提交新日志。断开的 Follower 重连后，Leader 根据冲突回复回退 `nextIndex`，再补齐缺失日志。少数派中未提交的日志可能在新 Leader 建立后被覆盖。成员 C 完成持久化后，还需补充重启恢复的实测记录。
