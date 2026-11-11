#!/usr/bin/env python3
"""独立重算任意一份客户端 CSV，导出逐行核对表和手算说明。仅使用 Python 标准库。"""
import argparse
import csv
import hashlib
import io
import sys
from pathlib import Path


def audit(source, destination):
    raw = source.read_bytes()
    rows = list(csv.DictReader(io.StringIO(raw.decode('utf-8-sig'))))
    if not rows:
        raise ValueError('CSV 没有尝试记录')
    required = set(('request server status reason t1_unix_ms t2_unix_ms t3_unix_ms '
                    't4_unix_ms local_unix_ms corrected_unix_ms offset_ms delay_ms '
                    'wait_ms resolve_ms sent discarded_packets origin_mismatch_packets '
                    'last_discard_reason request_timestamp_hex transport').split())
    results, details, errors = [], [], []
    seen_requests = set()
    for line, r in enumerate(rows, 2):
        entry = {'source_line': line, 'request': r.get('request', ''),
                 'status': r.get('status', ''), 'recomputed_offset_ms': '',
                 'recomputed_delay_ms': '', 'recomputed_corrected_unix_ms': '',
                 'check': 'PASS', 'detail': ''}
        try:
            if not required.issubset(r) or None in r or any(r[k] is None for k in required):
                raise ValueError('缺少字段或列数错误')
            if r['request'] in seen_requests:
                raise ValueError('重复请求序号')
            seen_requests.add(r['request'])
            if not r['reason'] or not r['transport']:
                raise ValueError('原因或运行通道为空')
            for key in ['wait_ms', 'resolve_ms', 'discarded_packets', 'origin_mismatch_packets']:
                if int(r[key]) < 0:
                    raise ValueError(f'{key} 为负')
            if int(r['origin_mismatch_packets']) > int(r['discarded_packets']):
                raise ValueError('不匹配包数量大于总丢弃数量')
            if r['status'] == 'NTP_Success':
                a, b, c, d = [int(r[f't{i}_unix_ms']) for i in range(1, 5)]
                numerator = (b-a)+(c-d)
                offset = (abs(numerator)//2) * (1 if numerator >= 0 else -1)
                delay = (d-a)-(c-b)
                corrected = d+offset
                entry.update(recomputed_offset_ms=offset, recomputed_delay_ms=delay,
                             recomputed_corrected_unix_ms=corrected)
                for key, expected in [('offset_ms',offset), ('delay_ms',delay),
                                      ('corrected_unix_ms',corrected), ('local_unix_ms',d)]:
                    if int(r[key]) != expected:
                        raise ValueError(f'{key}: 日志={r[key]}, 重算={expected}')
                if r['sent'] != 'true' or r['reason'] != 'OK':
                    raise ValueError('成功行的 sent/reason 不一致')
                token = r['request_timestamp_hex']
                if len(token) != 16:
                    raise ValueError('请求时间戳不是 8 字节')
                encoded = int(token,16)
                decoded = ((encoded >> 32)-2208988800)*1000 + (((encoded & 0xffffffff)*1000+2**31)//2**32)
                if decoded != a:
                    raise ValueError('原始请求时间戳与 T1 毫秒值不符')
                details.append(f"### 请求 {r['request']}（原 CSV 第 {line} 行）\n\n"
                    f"```text\nT1={a}\nT2={b}\nT3={c}\nT4={d}\n"
                    f"offset = (({b-a}) + ({c-d})) / 2 = {offset} ms（向零截断）\n"
                    f"delay = ({d-a}) - ({c-b}) = {delay} ms\n"
                    f"corrected = {d} + ({offset}) = {corrected}\n```\n")
            else:
                if r['status'] not in ['NTP_ServerAddressNotSet','NTP_SendFailed','NTP_ReceiveFailed']:
                    raise ValueError('未知状态码')
                if any(r[k] for k in ['t2_unix_ms','t3_unix_ms','t4_unix_ms','local_unix_ms',
                                      'corrected_unix_ms','offset_ms','delay_ms']):
                    raise ValueError('失败行含未验证的时间戳或计算结果')
                if r['status']=='NTP_ServerAddressNotSet' and (r['t1_unix_ms'] or r['sent']!='false'):
                    raise ValueError('地址失败不应发送请求')
                if r['status']=='NTP_SendFailed' and r['sent']!='false':
                    raise ValueError('发送失败的 sent 应为 false')
                if r['status']=='NTP_ReceiveFailed' and r['sent']!='true':
                    raise ValueError('接收失败应已有发送记录')
                entry['detail'] = r['reason']
        except (ValueError, KeyError, TypeError) as exc:
            entry['check']='FAIL'
            entry['detail']=str(exc)
            errors.append(f"第 {line} 行：{exc}")
        results.append(entry)
    destination.mkdir(parents=True, exist_ok=True)
    with (destination/'逐行核对表.csv').open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(results[0]))
        writer.writeheader();writer.writerows(results)
    digest=hashlib.sha256(raw).hexdigest()
    success=sum(r['status']=='NTP_Success' for r in rows)
    header=(f'# NTP 日志独立复算报告\n\n来源：`{source.name}`\n\n'
            f'SHA-256：`{digest}`\n\n'
            f'共 {len(rows)} 条尝试，其中 {success} 条成功，{len(rows)-success} 条失败；'
            f'核对结果：{len(results)-len(errors)} 条通过，{len(errors)} 条未通过。\n\n'
            '本报告从日志整数重新计算，不调用 Java 客户端的计算函数。'
            '核对通过表示日志内部算术及字段一致，不证明绝对时间精度、网络时延对称性或服务器真实性。'
            '失败行只核对状态和字段，不进行校时复算。\n\n')
    (destination/'复算报告.md').write_text(header+'\n'.join(errors)+'\n\n'+'\n'.join(details),encoding='utf-8')
    print(f'{source}: {len(results)-len(errors)}/{len(results)} 条核对通过；输出：{destination}')
    return not errors


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('csv',type=Path)
    parser.add_argument('--output',type=Path,help='默认输出到 CSV 旁边的 <文件名>-核对 目录')
    args=parser.parse_args()
    try:
        passed=audit(args.csv,args.output or args.csv.with_name(args.csv.stem+'-核对'))
        sys.exit(0 if passed else 1)
    except (OSError,ValueError) as exc:
        print(f'核对失败：{exc}',file=sys.stderr)
        sys.exit(2)
