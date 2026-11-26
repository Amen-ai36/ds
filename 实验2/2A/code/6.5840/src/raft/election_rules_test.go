package raft

import (
	"math/rand"
	"testing"
	"time"
)

func electionOnlyPeer() *Raft {
	c, _ := ElectionProfile("baseline")
	rf := &Raft{votedFor: -1, role: follower, timing: c, rng: rand.New(rand.NewSource(1))}
	rf.resetElectionLocked()
	return rf
}

func TestVoteOncePerTerm2A(t *testing.T) {
	rf := electionOnlyPeer()
	var reply RequestVoteReply
	rf.RequestVote(&RequestVoteArgs{Term: 2, CandidateId: 1}, &reply)
	if !reply.VoteGranted || reply.Term != 2 {
		t.Fatal("first eligible vote was rejected")
	}
	rf.RequestVote(&RequestVoteArgs{Term: 2, CandidateId: 2}, &reply)
	if reply.VoteGranted {
		t.Fatal("voted for two candidates in one term")
	}
	rf.RequestVote(&RequestVoteArgs{Term: 2, CandidateId: 1}, &reply)
	if !reply.VoteGranted {
		t.Fatal("a retry by the same candidate must remain idempotent")
	}
	rf.RequestVote(&RequestVoteArgs{Term: 3, CandidateId: 2}, &reply)
	if !reply.VoteGranted || rf.votedFor != 2 || rf.currentTerm != 3 {
		t.Fatal("new term did not allow a fresh vote")
	}
}

func TestHeartbeatTermRules2A(t *testing.T) {
	rf := electionOnlyPeer()
	rf.currentTerm, rf.votedFor, rf.role = 4, 2, candidate
	oldDeadline := time.Now().Add(-time.Second)
	rf.electionDeadline = oldDeadline
	var reply AppendEntriesReply
	rf.AppendEntries(&AppendEntriesArgs{Term: 3, LeaderId: 1}, &reply)
	if reply.Success || reply.Term != 4 || !rf.electionDeadline.Equal(oldDeadline) {
		t.Fatal("stale heartbeat changed the election timer")
	}
	rf.AppendEntries(&AppendEntriesArgs{Term: 4, LeaderId: 2}, &reply)
	if !reply.Success || rf.role != follower || rf.votedFor != 2 || !rf.electionDeadline.After(time.Now()) {
		t.Fatal("same-term heartbeat must step down, preserve vote, and reset timeout")
	}
	rf.role = leader
	rf.AppendEntries(&AppendEntriesArgs{Term: 5, LeaderId: 1}, &reply)
	if rf.role != follower || rf.currentTerm != 5 || rf.votedFor != -1 {
		t.Fatal("higher-term heartbeat must step down and clear the old vote")
	}
}

func TestRejectedVoteDoesNotResetTimer2A(t *testing.T) {
	rf := electionOnlyPeer()
	rf.currentTerm, rf.votedFor = 3, 1
	deadline := rf.electionDeadline
	var reply RequestVoteReply
	rf.RequestVote(&RequestVoteArgs{Term: 3, CandidateId: 2}, &reply)
	if reply.VoteGranted || !rf.electionDeadline.Equal(deadline) {
		t.Fatal("rejected request must not postpone the election")
	}
}
