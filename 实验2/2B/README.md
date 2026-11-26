# 成员 B：Raft 2B 日志复制

## 当前交付

实现位于 `../2A/code/6.5840/src/raft/`，基于远程 `second` 分支中的成员 A 2A 代码继续扩展，避免复制出两套互不一致的 2A/2B 源码。成员 A 的原始实验数据、问答和材料也已从 `origin/second` 拉取到 `实验2/2A/`。

- `raft.go`：日志条目、投票时的日志新旧判断、`Start`、多数提交与有序 `ApplyMsg`。
- `election.go`：AppendEntries 前缀匹配、冲突提示、日志回退重试与心跳复制。
- `docs/member-b-handoff.md`：开发过程、接口约定、验证状态及成员 C 接手事项。

## 运行测试

需要 Go 1.20 或更新版本。在 `../2A/code/6.5840/src` 执行：

```sh
export PATH=/Users/veritas/.local/opt/go1.27.4/bin:$PATH
cd ../2A/code/6.5840/src
go test ./raft -run '2A|2B' -count=1 -timeout=240s
go test -race ./raft -run '2A|2B' -count=1 -timeout=360s
```

Go 1.27.4 已安装并完成上述测试。普通测试和 race 测试均通过；完整 `go test ./raft` 在 2C 持久化阶段失败，因为 `persist()`/`readPersist()` 由成员 C 接续实现。
