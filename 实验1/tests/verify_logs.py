"""用独立 CSV 读取和算术核对日志；不依赖 Java 的计算函数。"""
import csv
from pathlib import Path


def verify(path):
    with path.open(encoding='utf-8', newline='') as stream:
        rows = list(csv.DictReader(stream))
    assert rows, f'{path}: only header, no attempt rows'
    for row in rows:
        assert None not in row, f'{path}: CSV width mismatch'
        assert set(row) == set('request,server,status,reason,t1_unix_ms,t2_unix_ms,t3_unix_ms,t4_unix_ms,local_unix_ms,corrected_unix_ms,offset_ms,delay_ms,wait_ms,resolve_ms,sent,discarded_packets,origin_mismatch_packets,last_discard_reason,request_timestamp_hex,transport'.split(',')), f'{path}: missing or unexpected columns'
        assert row['reason'] and row['wait_ms'] and row['resolve_ms']
        if row['status'] == 'NTP_Success':
            t1, t2, t3, t4 = (int(row[f't{i}_unix_ms']) for i in range(1, 5))
            numerator = (t2-t1)+(t3-t4)
            # Java long 除法向零截断，避免 Python // 的负数向下取整差异。
            offset = (abs(numerator)//2) * (1 if numerator >= 0 else -1)
            assert int(row['offset_ms']) == offset
            assert int(row['delay_ms']) == (t4-t1)-(t3-t2)
            assert int(row['corrected_unix_ms']) == t4+offset
            assert int(row['local_unix_ms']) == t4
        else:
            assert row['offset_ms'] == '' and row['delay_ms'] == ''
            assert row['corrected_unix_ms'] == ''
    print(f'PASS {path}: {len(rows)} rows; formulas and failure fields verified')
    return rows

if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1] / '验证记录'
    rows = verify(root / 'local_regression_rows.csv')
    assert {r['status'] for r in rows} == {
        'NTP_Success', 'NTP_ReceiveFailed', 'NTP_SendFailed', 'NTP_ServerAddressNotSet'}
    assert int(rows[1]['origin_mismatch_packets']) == 1
    verify(root / 'dns_failure.csv')
    console = verify(root / 'console_local.csv')
    assert [r['status'] for r in console] == ['NTP_Success', 'NTP_ReceiveFailed']
    fresh = root.parent / '重新实验' / '直连正式数据.csv'
    if fresh.exists():
        verify(fresh)
