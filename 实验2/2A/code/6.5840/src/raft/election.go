package raft

import "time"

type nodeRole uint8

const (
	follower nodeRole = iota
	candidate
	leader
)

// ElectionEvent 只记录选举相关事件；Trace=false 时不收集。
// 时间为同一主机的 Unix 纳秒，实验导出时统一转换为相对毫秒。
type ElectionEvent struct {
	AtUnixNano int64  `json:"at_unix_nano"`
	Node       int    `json:"node"`
	Term       int    `json:"term"`
	Kind       string `json:"kind"`
	Peer       int    `json:"peer"`
	Votes      int    `json:"votes"`
}

func (rf *Raft) recordLocked(kind string, peer, votes int) {
	if rf.timing.Trace {
		rf.events = append(rf.events, ElectionEvent{
			AtUnixNano: time.Now().UnixNano(), Node: rf.me,
			Term: rf.currentTerm, Kind: kind, Peer: peer, Votes: votes,
		})
	}
}

// ElectionEvents 返回副本，调用者不能修改 Raft 内部的事件切片。
func (rf *Raft) ElectionEvents() []ElectionEvent {
	rf.mu.Lock()
	defer rf.mu.Unlock()
	return append([]ElectionEvent(nil), rf.events...)
}

func (rf *Raft) resetElectionLocked() {
	window := rf.timing.ElectionMax - rf.timing.ElectionMin
	timeout := rf.timing.ElectionMin
	if window > 0 {
		timeout += time.Duration(rf.rng.Int63n(int64(window)))
	}
	rf.electionDeadline = time.Now().Add(timeout)
}

// 收到更高任期时清除旧任期投票。此函数不重置选举计时器，
// 避免仅仅收到被拒绝的投票请求就无限延迟合法选举。
func (rf *Raft) becomeFollowerLocked(term int) {
	if term > rf.currentTerm {
		rf.currentTerm = term
		rf.votedFor = -1
		rf.persist() // 成员 C 实现该函数后，此路径立即获得持久化。
	}
	if rf.role != follower {
		rf.recordLocked("step_down", -1, 0)
	}
	rf.role = follower
}

func (rf *Raft) becomeLeaderLocked(votes int) {
	rf.role = leader
	rf.recordLocked("leader_elected", rf.me, votes)
	// 当选立即发送第一轮心跳，后续按心跳周期发送。
	rf.broadcastHeartbeatLocked()
}

func (rf *Raft) startElectionLocked() {
	rf.role = candidate
	rf.currentTerm++
	rf.votedFor = rf.me
	rf.persist()             // 在发送 RequestVote 之前保存新任期和自投。
	rf.resetElectionLocked() // 每轮重新随机化，分票后不会一直同步重试。
	args := RequestVoteArgs{Term: rf.currentTerm, CandidateId: rf.me}
	votes := 1
	rf.recordLocked("election_started", rf.me, votes)
	if votes > len(rf.peers)/2 { // 单节点集群也能靠自投当选。
		rf.becomeLeaderLocked(votes)
		return
	}
	for peer := range rf.peers {
		if peer == rf.me {
			continue
		}
		go func(server int) {
			var reply RequestVoteReply
			// RPC 期间不持有 rf.mu，远端延迟不会阻塞本地计时器。
			if !rf.sendRequestVote(server, &args, &reply) {
				return
			}
			rf.mu.Lock()
			defer rf.mu.Unlock()
			if rf.killed() {
				return
			}
			// 即使回复对应旧一轮请求，更高任期仍必须处理。
			if reply.Term > rf.currentTerm {
				rf.becomeFollowerLocked(reply.Term)
				return
			}
			// 过期回复不能给新一轮选举累计选票。
			if rf.role != candidate || rf.currentTerm != args.Term ||
				reply.Term != args.Term || !reply.VoteGranted {
				return
			}
			votes++ // 所有该闭包对 votes 的访问均受同一把 rf.mu 保护。
			rf.recordLocked("vote_received", server, votes)
			if votes > len(rf.peers)/2 {
				rf.becomeLeaderLocked(votes)
			}
		}(peer)
	}
}

// 2A 仅使用空 AppendEntries 心跳。日志字段和一致性检查由成员 B 补充。
type AppendEntriesArgs struct {
	Term     int
	LeaderId int
}

type AppendEntriesReply struct {
	Term    int
	Success bool
}

func (rf *Raft) AppendEntries(args *AppendEntriesArgs, reply *AppendEntriesReply) {
	rf.mu.Lock()
	defer rf.mu.Unlock()
	reply.Success = false
	if args.Term > rf.currentTerm {
		rf.becomeFollowerLocked(args.Term)
	}
	reply.Term = rf.currentTerm
	if rf.killed() || args.Term < rf.currentTerm {
		return
	}
	// 同任期合法 Leader 的心跳也使 Candidate 退回 Follower；
	// 同任期不能清空 votedFor，否则可能在该任期重复投给其他人。
	if rf.role != follower {
		rf.recordLocked("step_down", args.LeaderId, 0)
	}
	rf.role = follower
	rf.resetElectionLocked()
	reply.Success = true
}

func (rf *Raft) broadcastHeartbeatLocked() {
	rf.lastHeartbeat = time.Now()
	args := AppendEntriesArgs{Term: rf.currentTerm, LeaderId: rf.me}
	for peer := range rf.peers {
		if peer == rf.me {
			continue
		}
		go func(server int) {
			var reply AppendEntriesReply
			if !rf.peers[server].Call("Raft.AppendEntries", &args, &reply) {
				return
			}
			rf.mu.Lock()
			defer rf.mu.Unlock()
			if !rf.killed() && reply.Term > rf.currentTerm {
				rf.becomeFollowerLocked(reply.Term)
			}
		}(peer)
	}
}
