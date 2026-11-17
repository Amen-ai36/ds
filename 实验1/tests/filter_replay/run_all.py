"""Compile the Java 8 modules, exercise local UDP integration, and replay data."""
import argparse,os,re,shutil,socket,struct,subprocess,sys,tempfile,threading,time
from pathlib import Path

HERE=Path(__file__).resolve().parent
LAB=HERE.parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-home',type=Path)
parser.add_argument('--output',type=Path,help='Write results here; default is a new temporary directory')
args=parser.parse_args()
out=args.output.resolve() if args.output else Path(tempfile.mkdtemp(prefix='ntp-filter-'))
(out/'data').mkdir(parents=True,exist_ok=True);(out/'results').mkdir(exist_ok=True)
home=args.java_home or (Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else None)
suffix='.exe' if os.name=='nt' else ''
java=str(home/'bin'/('java'+suffix)) if home else shutil.which('java')
javac=str(home/'bin'/('javac'+suffix)) if home else shutil.which('javac')
if not java or not javac:raise SystemExit('JDK required: set JAVA_HOME or pass --java-home')
env=os.environ.copy();env['PYTHONIOENCODING']='utf-8'
log=[]
def run(command):
    completed=subprocess.run(command,cwd=out,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=60)
    if completed.returncode:raise RuntimeError((completed.stdout or '')+'\n'+(completed.stderr or ''))
    return completed.stdout,completed.stderr

files={
    'public_aliyun_group.csv':LAB/'重新实验/直连正式数据.csv',
    'public_aliyun_windows.csv':LAB/'成员C/原始记录/公网-阿里云.csv',
    'public_cloudflare.csv':LAB/'成员C/原始记录/公网-Cloudflare.csv',
}
for name,source in files.items():shutil.copyfile(source,out/'data'/name)
run([sys.executable,str(HERE/'prepare_inputs.py'),str(out)])
project=LAB/'Code/项目参考代码JAVA'
lib=project/'TimeServiceClient_Library/src/TimeServiceClient_Library'
console=project/'TimeServiceClient_CONSOLE_uses_library/src/TimeServiceClient_CONSOLE_uses_library'
sources=[lib/'NTP_Client.java',lib/'NtpSampleFilter.java',console/'TimeServiceClient_CONSOLE_uses_library.java',console/'FilteredClientDemo.java',HERE/'FilterReplay.java']
version=subprocess.run([javac,'-version'],capture_output=True,text=True,encoding='utf-8',timeout=15)
version_text=(version.stdout+version.stderr).strip()
assert version.returncode==0,version_text
release=['-source','1.8','-target','1.8'] if 'javac 1.8' in version_text else ['--release','8']
run([javac,'-J-Duser.language=en','-J-Duser.country=US',*release,'-encoding','UTF-8','-d',str(out/'out'),*map(str,sources)])
log.append('PASS Java 8 compilation: '+version_text)
stdout,_=run([java,'-cp',str(out/'out'),'FilterReplay',str(out/'data/replay_input.csv'),str(out/'results/filter_replay.csv')])
log.append(stdout.strip())

def stamp(ms):return struct.pack('!II',ms//1000+2208988800,ms%1000*2**32//1000)
def integration(count,reply_count):
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as server:
        server.bind(('127.0.0.1',0));server.settimeout(10);errors=[]
        def reply():
            try:
                for index in range(count):
                    request,address=server.recvfrom(512)
                    if index>=reply_count:continue
                    now=time.time_ns()//1_000_000+100
                    response=bytearray(48);response[0]=0x24;response[1]=2
                    response[24:32]=request[40:48]
                    response[32:40]=stamp(now);response[40:48]=stamp(now)
                    server.sendto(response,address)
            except Exception as exc:errors.append(repr(exc))
        peer=threading.Thread(target=reply);peer.start()
        stdout,_=run([java,'-cp',str(out/'out'),
            'TimeServiceClient_CONSOLE_uses_library.FilteredClientDemo',
            '127.0.0.1',str(server.getsockname()[1]),str(count),'10','200'])
        peer.join(timeout=12)
        assert not peer.is_alive() and not errors,errors
        return stdout

normal=integration(5,5)
assert normal.count('WARMING_UP_OR_REJECTED')==2,normal
assert len(re.findall(r'\bfiltered=',normal))==3,normal
failure=integration(2,1)
assert 'TIMEOUT' in failure and 'filtered=' not in failure,failure
log.append('PASS packaged client UDP integration: 2 warmup replies and 3 filtered outputs')
log.append('PASS timeout: failed exchange does not produce a filtered time')
(out/'results/client_udp_output.txt').write_text(normal+'\n'+failure,encoding='utf-8')
run([sys.executable,str(HERE/'summarize.py'),str(out)])
log.append('PASS controlled and historical public data replay completed')
(out/'results/validation.txt').write_text('\n'.join(log)+'\n',encoding='utf-8')
print('\n'.join(log));print('Results: '+str(out))
