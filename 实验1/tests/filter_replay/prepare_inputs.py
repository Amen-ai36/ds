import csv
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
(root / 'results').mkdir(exist_ok=True)
fields = ['scenario', 'request', 'true_offset_ms', 'raw_offset_ms', 'delay_ms', 'elapsed_ms']
with (root / 'data/replay_input.csv').open('w', encoding='utf-8', newline='') as stream, \
     (root / 'data/controlled_timestamps.csv').open('w', encoding='utf-8', newline='') as stamps:
    out = csv.writer(stream); out.writerow(fields)
    ts = csv.writer(stamps)
    ts.writerow(['scenario', 'request', 'true_offset_ms', 'forward_ms', 'backward_ms',
                 't1', 't2', 't3', 't4', 'computed_offset_ms', 'computed_delay_ms'])
    scenarios = {
        'burst': ([100]*12, [100,102,101,180,99,98,170,100,101,20,99,100],
                  [20,24,22,200,20,26,180,20,22,210,20,20]),
        'asymmetry': ([100]*10, [140]*10, [100]*10),
        'clock_step': ([100]*8+[200]*8, [100]*8+[200]*8, [20]*8+[40]*8),
    }
    for scenario, (truths, offsets, delays) in scenarios.items():
        for i, (truth, offset, delay) in enumerate(zip(truths, offsets, delays), 1):
            forward = delay//2 + offset - truth
            backward = delay//2 - offset + truth
            assert forward >= 0 and backward >= 0
            t1 = 1_700_000_000_000 + (i-1)*5000
            t2 = t1 + forward + truth; t3 = t2 + 2
            t4 = t1 + forward + 2 + backward
            calculated = int(((t2-t1)+(t3-t4))/2)
            calculated_delay = (t4-t1)-(t3-t2)
            assert calculated == offset and calculated_delay == delay
            out.writerow([scenario, i, truth, offset, delay, (i-1)*5000])
            ts.writerow([scenario, i, truth, forward, backward, t1,t2,t3,t4,offset,delay])
    for scenario, filename in [('public_aliyun_windows','public_aliyun_windows.csv'),
                               ('public_cloudflare','public_cloudflare.csv'),
                               ('public_aliyun_group','public_aliyun_group.csv')]:
        with (root/'data'/filename).open(encoding='utf-8-sig',newline='') as source:
            rows = list(csv.DictReader(source))
        start = int(rows[0]['t1_unix_ms'])
        for r in rows:
            if r['status'] == 'NTP_Success':
                out.writerow([scenario,r['request'],'',r['offset_ms'],r['delay_ms'],int(r['t1_unix_ms'])-start])
print('Prepared controlled timestamp scenarios and original public CSV replay inputs')
