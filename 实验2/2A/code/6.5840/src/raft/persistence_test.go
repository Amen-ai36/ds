package raft

import (
	"testing"
	"time"

	"6.5840/labrpc"
)

func TestVoteSurvivesRestart2C(t *testing.T) {
	timing, _ := ElectionProfile("baseline")
	timing.ElectionMin, timing.ElectionMax = time.Minute, time.Minute
	peers := make([]*labrpc.ClientEnd, 3)
	storage := MakePersister()
	old := MakeWithConfig(peers, 1, storage, nil, timing)
	defer old.Kill()

	var vote RequestVoteReply
	old.RequestVote(&RequestVoteArgs{Term: 2, CandidateId: 0}, &vote)
	if !vote.VoteGranted || vote.Term != 2 {
		t.Fatalf("initial vote: %+v", vote)
	}
	t.Logf("node=1 before restart term=%d votedFor=0", vote.Term)

	old.Kill()
	var heartbeat AppendEntriesReply
	old.AppendEntries(&AppendEntriesArgs{Term: 3, LeaderId: 2}, &heartbeat)

	restarted := MakeWithConfig(peers, 1, storage, nil, timing)
	defer restarted.Kill()
	restarted.mu.Lock()
	term, votedFor, role := restarted.currentTerm, restarted.votedFor, restarted.role
	restarted.mu.Unlock()
	if term != 2 || votedFor != 0 || role != follower {
		t.Fatalf("restored term=%d votedFor=%d role=%d, want term=2 votedFor=0 role=follower", term, votedFor, role)
	}
	t.Logf("node=1 after restart term=%d votedFor=%d role=follower", term, votedFor)

	restarted.RequestVote(&RequestVoteArgs{Term: 2, CandidateId: 2}, &vote)
	if vote.VoteGranted || vote.Term != 2 {
		t.Fatalf("second candidate in the same term: %+v", vote)
	}
	t.Logf("node=1 competing vote term=%d candidate=2 granted=%t", vote.Term, vote.VoteGranted)
}

func TestLogSurvivesRestart2C(t *testing.T) {
	timing, _ := ElectionProfile("baseline")
	timing.ElectionMin, timing.ElectionMax = time.Minute, time.Minute
	peers := make([]*labrpc.ClientEnd, 1)
	storage := MakePersister()
	elect := func(rf *Raft) {
		rf.mu.Lock()
		defer rf.mu.Unlock()
		rf.startElectionLocked()
	}
	expectApplied := func(applyCh <-chan ApplyMsg, index int, command string) {
		t.Helper()
		select {
		case msg := <-applyCh:
			if !msg.CommandValid || msg.CommandIndex != index || msg.Command != command {
				t.Fatalf("applied %+v, want index=%d command=%q", msg, index, command)
			}
			t.Logf("applied index=%d command=%q", msg.CommandIndex, msg.Command)
		case <-time.After(3 * time.Second):
			t.Fatalf("timed out applying index=%d command=%q", index, command)
		}
	}

	before := make(chan ApplyMsg, 1)
	old := MakeWithConfig(peers, 0, storage, before, timing)
	defer old.Kill()
	elect(old)
	if index, term, ok := old.Start("before"); index != 1 || term != 1 || !ok {
		t.Fatalf("first Start returned index=%d term=%d leader=%t", index, term, ok)
	}
	expectApplied(before, 1, "before")
	old.Kill()

	after := make(chan ApplyMsg, 2)
	restarted := MakeWithConfig(peers, 0, storage, after, timing)
	defer restarted.Kill()
	if term, isLeader := restarted.GetState(); term != 1 || isLeader {
		t.Fatalf("restarted with term=%d leader=%t, want term=1 leader=false", term, isLeader)
	}
	elect(restarted)
	index, term, ok := restarted.Start("after")
	if index != 2 || term != 2 || !ok {
		t.Fatalf("Start after election returned index=%d term=%d leader=%t", index, term, ok)
	}
	t.Logf("node=0 new leader term=%d appended index=%d", term, index)
	expectApplied(after, 1, "before")
	expectApplied(after, 2, "after")
}
