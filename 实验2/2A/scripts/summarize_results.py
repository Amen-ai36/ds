"""Summarize recorded measurements; never invent missing or failed values."""
from collections import Counter, defaultdict
import csv
import json
import math
from pathlib import Path
import statistics

root = Path(__file__).resolve().parents[1]
results = root/'results'
raw = results/'raw'
tables = results/'tables'
tables.mkdir(parents=True, exist_ok=True)
with (raw/'trial_metrics.csv').open(encoding='utf-8', newline='') as f:
    rows = list(csv.DictReader(f))
groups = defaultdict(list)
for row in rows:
    groups[row['scenario'], row['profile']].append(row)

def values(data, key):
    return [float(row[key]) for row in data if row[key] != '']

def mean(data):
    return statistics.mean(data) if data else None

def fmt(value, decimals=2):
    return '' if value is None else f'{value:.{decimals}f}'

summaries = []
chart_groups = []
for scenario in ['reliable', 'lossy_reordered']:
    for profile in ['baseline', 'fast', 'stable']:
        data = groups[scenario, profile]
        if not data:
            continue
        latencies = values(data, 'reelection_ms')
        initial = values(data, 'initial_ms')
        statuses = Counter(row['status'] for row in data)
        quiet_rows = [row for row in data if row['initial_leader'] != '']
        summary = {
            'scenario': scenario, 'profile': profile, 'trials': len(data),
            'successful_trials': statuses['ok'], 'reelection_samples': len(latencies),
            'initial_mean_ms': mean(initial), 'reelection_mean_ms': mean(latencies),
            'reelection_median_ms': statistics.median(latencies) if latencies else None,
            'reelection_p95_ms': sorted(latencies)[math.ceil(.95*len(latencies))-1] if latencies else None,
            'reelection_std_ms': statistics.stdev(latencies) if len(latencies) > 1 else 0 if latencies else None,
            'reelection_min_ms': min(latencies) if latencies else None,
            'reelection_max_ms': max(latencies) if latencies else None,
            'election_rounds_mean': mean(values(data, 'election_rounds')),
            'campaigns_mean': mean(values(data, 'campaigns')),
            'unexpected_leader_changes_total': sum(values(quiet_rows, 'unexpected_leader_changes')),
            'quiet_term_changes_total': sum(values(quiet_rows, 'quiet_term_changes')),
            'quiet_observation_seconds': sum(values(quiet_rows, 'quiet_seconds')),
            'rpc_count_mean': mean(values(data, 'rpc_count')),
            'failure_statuses': '; '.join(f'{k}:{v}' for k, v in sorted(statuses.items()) if k != 'ok'),
        }
        summaries.append(summary)
        chart_groups.append(dict(summary, reelection_samples_ms=latencies))

with (tables/'summary.csv').open('w', encoding='utf-8-sig', newline='') as f:
    w = csv.DictWriter(f, fieldnames=list(summaries[0]))
    w.writeheader()
    w.writerows(summaries)
(tables/'chart_data.json').write_text(json.dumps(chart_groups, ensure_ascii=False, indent=2), encoding='utf-8')

with (tables/'all_election_events.csv').open('w', encoding='utf-8-sig', newline='') as f:
    w = csv.writer(f)
    w.writerow(['scenario', 'profile', 'trial', 'relative_ms', 'node', 'term', 'event', 'peer', 'votes'])
    for p in sorted((raw/'traces').glob('*.json')):
        trace = json.loads(p.read_text(encoding='utf-8'))
        for e in trace['events']:
            w.writerow([trace['scenario'], trace['profile'], trace['trial'],
                        f"{(e['at_unix_nano']-trace['start_unix_nano'])/1e6:.3f}",
                        e['node'], e['term'], e['kind'], e['peer'], e['votes']])

lines = ['# 成员 A 选举实验实测结果', '',
         '以下数据来自 `raw/trial_metrics.csv`；原始事件、测试输出和环境记录均保留在 `raw/`。', '',
         '使用同一主机的 5 个 Go 节点和课程自带 labrpc 网络，按三种参数策略、两种网络场景重复运行。'
         '每轮无主动故障观察 2 秒，再断开当时 Leader 的所有网络连接。详见 [数据口径](../docs/data-dictionary.md)。', '',
         '## 重选耗时与轮数', '',
         '| 网络场景 | 策略 | 成功试验/总数 | 平均初选 ms | 平均重选 ms | 重选 P95 ms | 平均选举轮数 |',
         '| --- | --- | --- | --- | --- | --- | --- |']
for s in summaries:
    lines.append(f"| {s['scenario']} | {s['profile']} | {s['successful_trials']}/{s['trials']} | "
                 f"{fmt(s['initial_mean_ms'])} | {fmt(s['reelection_mean_ms'])} | "
                 f"{fmt(s['reelection_p95_ms'])} | {fmt(s['election_rounds_mean'])} |")
lines += ['', '## 无主动故障窗口的换主观察', '',
          '| 网络场景 | 策略 | 累计观察秒数 | Leader 节点变化次数 | 当选任期变化次数 | 失败状态 |',
          '| --- | --- | --- | --- | --- | --- |']
for s in summaries:
    lines.append(f"| {s['scenario']} | {s['profile']} | {fmt(s['quiet_observation_seconds'])} | "
                 f"{s['unexpected_leader_changes_total']:.0f} | {s['quiet_term_changes_total']:.0f} | "
                 f"{s['failure_statuses'] or '无'} |")
lines += ['', '## 如何使用这些结果', '',
          '- 耗时、轮数的统计只使用有对应测量值的试验，失败数单独列出，不能用 0 毫秒填补失败。',
          '- 同一任期的不同候选人计为同一选举轮；候选人发起次数另见 `summary.csv` 的 `campaigns_mean`。',
          '- 图中的散点表示单次测量，误差线为样本标准差；P95 使用最近秩法。',
          '- 观察窗口较短，换主次数为 0 只代表本批观察记录，不能推出永久稳定。',
          '- 延迟场景由 labrpc 产生丢包与回复延迟，RPC 的调度和随机调用顺序不完全相同；没有多机网络性能或 Paxos 吞吐量实测。',
          '- 初选与重选时间线选用可靠网络下 baseline 的第 1 轮；节点编号与时间直接取自该轮 trace。', '']
(results/'experiment-results.md').write_text('\n'.join(lines), encoding='utf-8')
print(f'Summarized {len(rows)} recorded trials into {len(summaries)} groups')
