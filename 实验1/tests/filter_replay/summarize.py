import csv, json, math, statistics, sys
from pathlib import Path

root=Path(sys.argv[1]).resolve()
def read(p):
    with p.open(encoding='utf-8-sig',newline='') as f: return list(csv.DictReader(f))
def stats(a):
    return {'n':len(a),'mean':statistics.mean(a),'median':statistics.median(a),
            'sample_std':statistics.stdev(a) if len(a)>1 else 0,
            'min':min(a),'max':max(a),'p95_nearest_rank':sorted(a)[math.ceil(.95*len(a))-1]}
public={}
for name in ['public_aliyun_group','public_aliyun_windows','public_cloudflare']:
    rows=read(root/'data'/f'{name}.csv')
    good=[r for r in rows if r['status']=='NTP_Success']
    public[name]={'attempts':len(rows),'success':len(good),
                  'offset':stats([int(r['offset_ms']) for r in good]),
                  'delay':stats([int(r['delay_ms']) for r in good])}
a=read(root/'data/public_aliyun_windows.csv'); b=read(root/'data/public_cloudflare.csv')
pairs=[]
for x,y in zip(a,b):
    dt=abs(int(x['t1_unix_ms'])-int(y['t1_unix_ms']))
    if dt<=1000: pairs.append({'request':x['request'],'send_gap_ms':dt,
                             'offset_diff_ms':int(y['offset_ms'])-int(x['offset_ms'])})
replays={}
for r in read(root/'results/filter_replay.csv'): replays.setdefault(r['scenario'],[]).append(r)
evaluation={}
for name, rows in replays.items():
    common=[r for r in rows if r['filtered_offset_ms']!='']
    result={'total':len(rows),'comparable':len(common),'warmup_or_rejected':len(rows)-len(common),
            'raw_output':stats([int(r['raw_offset_ms']) for r in common]),
            'filtered_output':stats([int(r['filtered_offset_ms']) for r in common])}
    if common and common[0]['true_offset_ms']!='':
        result['raw_error']=stats([int(r['raw_abs_error_ms']) for r in common])
        result['filtered_error']=stats([int(r['filtered_abs_error_ms']) for r in common])
    result['outputs']=[r['filtered_offset_ms'] or None for r in rows]
    evaluation[name]=result
result={'public':public,'paired':pairs,'paired_abs_diff':stats([abs(r['offset_diff_ms']) for r in pairs]),
        'evaluation':evaluation}
(root/'results/summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
