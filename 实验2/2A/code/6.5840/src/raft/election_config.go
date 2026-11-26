package raft

import (
	"fmt"
	"os"
	"time"
)

// ElectionConfig 只控制选举的计时策略，不改变投票或多数派规则。
// 同一集群建议采用同一策略；Seed 仅供复现实验，0 表示自动生成。
type ElectionConfig struct {
	HeartbeatInterval time.Duration
	ElectionMin       time.Duration
	ElectionMax       time.Duration
	PollInterval      time.Duration
	Seed              int64
	Trace             bool
}

func ElectionProfile(name string) (ElectionConfig, error) {
	c := ElectionConfig{PollInterval: 10 * time.Millisecond}
	switch name {
	case "", "baseline":
		c.HeartbeatInterval = 120 * time.Millisecond
		c.ElectionMin, c.ElectionMax = 450*time.Millisecond, 750*time.Millisecond
	case "fast":
		c.HeartbeatInterval = 100 * time.Millisecond
		c.ElectionMin, c.ElectionMax = 300*time.Millisecond, 500*time.Millisecond
	case "stable":
		c.HeartbeatInterval = 150 * time.Millisecond
		c.ElectionMin, c.ElectionMax = 800*time.Millisecond, 1200*time.Millisecond
	default:
		return c, fmt.Errorf("unknown election profile %q", name)
	}
	return c, nil
}

func (c ElectionConfig) validate() error {
	if c.HeartbeatInterval < 100*time.Millisecond {
		return fmt.Errorf("heartbeat interval must be >= 100ms for the course's rate limit")
	}
	if c.ElectionMin <= c.HeartbeatInterval || c.ElectionMax < c.ElectionMin {
		return fmt.Errorf("require heartbeat < election min <= election max")
	}
	if c.PollInterval <= 0 || c.PollInterval >= c.ElectionMin {
		return fmt.Errorf("poll interval must be positive and smaller than election min")
	}
	return nil
}

func configuredElection() ElectionConfig {
	c, err := ElectionProfile(os.Getenv("RAFT_ELECTION_PROFILE"))
	if err != nil {
		panic(err)
	}
	return c
}
