# 成员 B Raft 2B 日志复制

A 的 2A 选举、B 的 2B 日志复制和 C 的 2C 持久化共用 [Go 模块](../2A/code/6.5840/src/)。

- `raft.go` 实现 `Start()`、日志新旧判断、多数提交和有序 `ApplyMsg`。
- `election.go` 实现 `AppendEntries` 的前缀匹配、冲突提示、回退重试和日志复制。
- 交接时的字段约定、故障分析和测试记录见 [成员 B 过程记录](成员B过程记录与C交接.md) [开发交接详情](../2A/docs/member-b-handoff.md)。

## 运行测试

从仓库根目录进入 Go 模块，使用 Go 1.20 或更新版本。race 检测需要支持 cgo 的 C 编译器。

```sh
cd "实验2/2A/code/6.5840/src"
go test ./raft -run '2[AB]$' -count=1 -timeout=240s
go test -race ./raft -run '2[AB]$' -count=1 -timeout=360s
```

交接时 B 已通过 2A、2B 的普通和 race 测试。集成 2C 后的回归输出和场景结论见 [验证日志](../2C/validation.log) [实验报告](../实验报告.md#实验结果)。
