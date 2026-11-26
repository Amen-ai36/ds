package raft

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"testing"
	"time"

	"6.5840/labrpc"
)

type electionCluster struct {
	net    *labrpc.Network
	peers  []*Raft
	ends   [][]string
	online []bool
	start  time.Time
}

func newElectionCluster(n int, c ElectionConfig, delayed bool, seed int64) *electionCluster {
	x := &electionCluster{net: labrpc.MakeNetwork(), start: time.Now()}
	x.peers, x.ends, x.online = make([]*Raft, n), make([][]string, n), make([]bool, n)
	// 每次试验使用相同网络模式；延迟场景保留原 labrpc 的丢包和回复乱序。
	x.net.Reliable(!delayed)
	x.net.LongReordering(delayed)
	x.net.LongDelays(false)
	for i := 0; i < n; i++ {
		x.ends[i] = make([]string, n)
		ends := make([]*labrpc.ClientEnd, n)
		for j := 0; j < n; j++ {
			name := fmt.Sprintf("%d-%d", i, j)
			x.ends[i][j] = name
			ends[j] = x.net.MakeEnd(name)
			x.net.Connect(name, j)
		}
		c.Seed, c.Trace = seed+int64(i)*1000003, true
		x.peers[i] = MakeWithConfig(ends, i, MakePersister(), make(chan ApplyMsg), c)
		srv := labrpc.MakeServer()
		srv.AddService(labrpc.MakeService(x.peers[i]))
		x.net.AddServer(i, srv)
		x.online[i] = true
	}
	for i := 0; i < n; i++ {
		for j := 0; j < n; j++ {
			x.net.Enable(x.ends[i][j], true)
		}
	}
	return x
}

func (x *electionCluster) close() {
	for _, rf := range x.peers {
		rf.Kill()
	}
	x.net.Cleanup()
}

func (x *electionCluster) disconnect(node int) {
	x.online[node] = false
	for j := range x.peers {
		x.net.Enable(x.ends[node][j], false)
		x.net.Enable(x.ends[j][node], false)
	}
}

// 选择当前可通信节点中的最高任期 Leader。旧分区的自认 Leader 不计入。
func (x *electionCluster) leader() (int, int) {
	node, term := -1, -1
	for i, rf := range x.peers {
		if !x.online[i] {
			continue
		}
		t, yes := rf.GetState()
		if yes && t > term {
			node, term = i, t
		} else if yes && t == term {
			return -1, term
		}
	}
	return node, term
}

func (x *electionCluster) waitLeader(timeout, hold time.Duration) (int, int) {
	deadline, sameSince := time.Now().Add(timeout), time.Now()
	previousNode, previousTerm := -1, -1
	for time.Now().Before(deadline) {
		node, term := x.leader()
		if node != previousNode || term != previousTerm || node < 0 {
			sameSince = time.Now()
			previousNode, previousTerm = node, term
		} else if time.Since(sameSince) >= hold {
			return node, term
		}
		time.Sleep(5 * time.Millisecond)
	}
	return -1, -1
}

func (x *electionCluster) events() []ElectionEvent {
	var events []ElectionEvent
	for _, rf := range x.peers {
		events = append(events, rf.ElectionEvents()...)
	}
	sort.Slice(events, func(i, j int) bool { return events[i].AtUnixNano < events[j].AtUnixNano })
	return events
}

func promotionTime(events []ElectionEvent, node, term int) int64 {
	for _, e := range events {
		if e.Kind == "leader_elected" && e.Node == node && e.Term == term {
			return e.AtUnixNano
		}
	}
	return 0
}

func checkElectionSafety(t *testing.T, events []ElectionEvent, nodes int) {
	leaders := make(map[int]int)
	for _, e := range events {
		if e.Kind != "leader_elected" {
			continue
		}
		if old, ok := leaders[e.Term]; ok && old != e.Node {
			t.Errorf("two elected leaders in term %d: %d and %d", e.Term, old, e.Node)
		}
		if e.Votes <= nodes/2 {
			t.Errorf("node %d elected with only %d votes", e.Node, e.Votes)
		}
		leaders[e.Term] = e.Node
	}
}

func TestSingleNodeElection2A(t *testing.T) {
	c, _ := ElectionProfile("baseline")
	x := newElectionCluster(1, c, false, 37)
	defer x.close()
	if node, _ := x.waitLeader(2*time.Second, 20*time.Millisecond); node != 0 {
		t.Fatal("single-node cluster failed to elect itself")
	}
	checkElectionSafety(t, x.events(), 1)
}

// 此测试默认跳过，必须显式设置 RAFT_EXPERIMENTS=1 才会采集数据。
// 测量时钟是本机真实时钟，网络使用课程自带 labrpc，而不是数学模拟。
func TestMemberAExperiment(t *testing.T) {
	if os.Getenv("RAFT_EXPERIMENTS") != "1" {
		t.Skip("run scripts/run_experiments.py to collect experiment data")
	}
	trials := 10
	if s := os.Getenv("RAFT_TRIALS"); s != "" {
		n, err := strconv.Atoi(s)
		if err != nil || n < 1 {
			t.Fatal("RAFT_TRIALS must be a positive integer")
		}
		trials = n
	}
	out := os.Getenv("RAFT_RESULTS_DIR")
	if out == "" {
		t.Fatal("RAFT_RESULTS_DIR must name an output directory")
	}
	if err := os.MkdirAll(filepath.Join(out, "traces"), 0755); err != nil {
		t.Fatal(err)
	}
	f, err := os.Create(filepath.Join(out, "trial_metrics.csv"))
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	w := csv.NewWriter(f)
	defer w.Flush()
	w.Write([]string{"scenario", "profile", "trial", "nodes", "seed", "status", "initial_leader", "initial_term", "initial_ms", "old_leader", "old_term", "new_leader", "new_term", "reelection_ms", "election_rounds", "campaigns", "unexpected_leader_changes", "quiet_term_changes", "quiet_seconds", "rpc_count", "rpc_bytes"})
	for _, scenario := range []string{"reliable", "lossy_reordered"} {
		for trial := 1; trial <= trials; trial++ {
			// 轮换运行顺序，减轻机器随时间负载变化造成的偏差。
			profiles := []string{"baseline", "fast", "stable"}
			for k := 0; k < 3; k++ {
				profile := profiles[(k+trial-1)%3]
				c, _ := ElectionProfile(profile)
				seed := int64(20261126 + trial*101)
				rand.Seed(seed) // 重置 labrpc 的伪随机源；调度仍会影响具体随机调用顺序。
				x := newElectionCluster(5, c, scenario == "lossy_reordered", seed)
				row := []string{scenario, profile, strconv.Itoa(trial), "5", strconv.FormatInt(seed, 10), "ok", "", "", "", "", "", "", "", "", "", "", "", "", "2.000", "", ""}
				initialNode, initialTerm := x.waitLeader(5*time.Second, 2*c.HeartbeatInterval)
				var quietStart, quietEnd, faultAt int64
				oldNode, oldTerm, newNode, newTerm := -1, -1, -1, -1
				if initialNode < 0 {
					row[5] = "initial_timeout"
				} else {
					row[6], row[7] = strconv.Itoa(initialNode), strconv.Itoa(initialTerm)
					quietStart = time.Now().UnixNano()
					time.Sleep(2 * time.Second)
					quietEnd = time.Now().UnixNano()
					oldNode, oldTerm = x.waitLeader(5*time.Second, 2*c.HeartbeatInterval)
					if oldNode < 0 {
						row[5] = "unstable_before_fault"
					} else {
						row[9], row[10] = strconv.Itoa(oldNode), strconv.Itoa(oldTerm)
						faultAt = time.Now().UnixNano()
						x.disconnect(oldNode)
						newNode, newTerm = x.waitLeader(5*time.Second, 2*c.HeartbeatInterval)
						if newNode < 0 {
							row[5] = "reelection_timeout"
						} else {
							row[11], row[12] = strconv.Itoa(newNode), strconv.Itoa(newTerm)
						}
					}
				}
				events := x.events()
				if initialNode >= 0 {
					at := promotionTime(events, initialNode, initialTerm)
					row[8] = fmt.Sprintf("%.3f", float64(at-x.start.UnixNano())/1e6)
				}
				lastNode, lastTerm, changes, termChanges := initialNode, initialTerm, 0, 0
				for _, e := range events {
					if e.Kind == "leader_elected" && e.AtUnixNano >= quietStart && e.AtUnixNano < quietEnd {
						if e.Node != lastNode {
							changes++
						}
						if e.Term != lastTerm {
							termChanges++
						}
						lastNode, lastTerm = e.Node, e.Term
					}
				}
				if initialNode >= 0 {
					row[16], row[17] = strconv.Itoa(changes), strconv.Itoa(termChanges)
					row[18] = fmt.Sprintf("%.6f", float64(quietEnd-quietStart)/1e9)
				} else {
					row[18] = ""
				}
				if newNode >= 0 {
					at := promotionTime(events, newNode, newTerm)
					row[13] = fmt.Sprintf("%.3f", float64(at-faultAt)/1e6)
					rounds, campaigns := map[int]bool{}, 0
					for _, e := range events {
						if e.Node != oldNode && e.Kind == "election_started" && e.AtUnixNano >= faultAt && e.AtUnixNano <= at {
							rounds[e.Term] = true
							campaigns++
						}
					}
					row[14], row[15] = strconv.Itoa(len(rounds)), strconv.Itoa(campaigns)
				}
				row[19], row[20] = strconv.Itoa(x.net.GetTotalCount()), strconv.FormatInt(x.net.GetTotalBytes(), 10)
				trace := struct {
					Scenario  string          `json:"scenario"`
					Profile   string          `json:"profile"`
					Trial     int             `json:"trial"`
					Start     int64           `json:"start_unix_nano"`
					Fault     int64           `json:"fault_unix_nano"`
					OldLeader int             `json:"old_leader"`
					Events    []ElectionEvent `json:"events"`
				}{scenario, profile, trial, x.start.UnixNano(), faultAt, oldNode, events}
				data, err := json.MarshalIndent(trace, "", "  ")
				if err != nil {
					t.Fatal(err)
				}
				name := fmt.Sprintf("%s_%s_trial_%02d.json", scenario, profile, trial)
				if err := os.WriteFile(filepath.Join(out, "traces", name), data, 0644); err != nil {
					t.Fatal(err)
				}
				checkElectionSafety(t, events, 5)
				x.close()
				if err := w.Write(row); err != nil {
					t.Fatal(err)
				}
				w.Flush()
				if err := w.Error(); err != nil {
					t.Fatal(err)
				}
				t.Logf("%s %s trial=%d status=%s initial=%sms reelection=%sms rounds=%s", scenario, profile, trial, row[5], row[8], row[13], row[14])
			}
		}
	}
}
