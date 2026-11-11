"""独立本地 UDP 模拟服务端，验证控制台端到端写入成功及超时行。"""
import socket
import struct
import subprocess
import threading
import time
from pathlib import Path

root = Path(__file__).resolve().parents[2]
records = root / '实验1/验证记录'

def timestamp():
    now = time.time()
    return struct.pack('!II', int(now) + 2208988800, int((now % 1) * 2**32))

with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as server:
    server.bind(('127.0.0.1', 0))
    server.settimeout(3)
    errors = []
    def respond():
        try:
            request, addr = server.recvfrom(512)
            reply = bytearray(48)
            reply[0:2] = bytes([0x24, 2])
            reply[24:32] = request[40:48]
            reply[32:40] = timestamp()
            reply[40:48] = timestamp()
            server.sendto(reply, addr)
            server.recvfrom(512)  # 第二次请求刻意不回复，触发超时
        except Exception as exc:
            errors.append(exc)
    thread = threading.Thread(target=respond)
    thread.start()
    run = subprocess.run([
        'java', '-cp', 'out',
        'TimeServiceClient_CONSOLE_uses_library.TimeServiceClient_CONSOLE_uses_library',
        '127.0.0.1', '2', '100', str(server.getsockname()[1]), '200',
        str(records / 'console_local.csv')], cwd=root, capture_output=True, text=True, timeout=5)
    thread.join(timeout=4)
    assert not errors and run.returncode == 0, (errors, run.stderr)
    (records / '本地控制台运行记录.txt').write_text('本地模拟服务器记录，不是公网精度测量。\n'+run.stdout, encoding='utf-8')
    print(run.stdout)
