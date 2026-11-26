"""Run unmodified course 2A tests and collect real labrpc election measurements."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--go', default=os.environ.get('GO_EXE') or shutil.which('go'))
parser.add_argument('--trials', type=int, default=10)
parser.add_argument('--race', action='store_true', help='requires supported C compiler on Windows')
parser.add_argument('--tests-only', action='store_true')
parser.add_argument('--experiments-only', action='store_true')
args = parser.parse_args()
if not args.go or args.trials < 1:
    parser.error('provide --go PATH_TO_GO and a positive --trials')

results = root/'results'
raw = results/'raw'
raw.mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
scratch = root/'.run-cache'
for name in ['gocache', 'gopath', 'tmp']:
    (scratch/name).mkdir(parents=True, exist_ok=True)
env.update(GOTOOLCHAIN='local', GOPROXY='off', GOSUMDB='off', GOENV='off', GODEBUG='randseednop=0',
           GOCACHE=str(scratch/'gocache'), GOPATH=str(scratch/'gopath'),
           GOTMPDIR=str(scratch/'tmp'), TMP=str(scratch/'tmp'), TEMP=str(scratch/'tmp'),
           CGO_ENABLED='1' if args.race else '0')
module = root/'code/6.5840/src'
metadata = {
    'started_at_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
    'go_version': subprocess.check_output([args.go, 'version'], env=env, text=True).strip(),
    'platform': platform.platform(), 'machine': platform.machine(),
    'cpu_count': os.cpu_count(), 'race': args.race, 'trials_per_group': args.trials,
    'network': 'course labrpc: reliable; unreliable + LongReordering',
    'nodes': 5, 'quiet_seconds_per_trial': 2,
    'seed_base': 20261126, 'commands': [],
    'source_sha256': {str(p.relative_to(module)): hashlib.sha256(p.read_bytes()).hexdigest()
                      for p in sorted(module.rglob('*.go'))},
}
metadata_path = raw/'run_metadata.json'
if args.experiments_only and metadata_path.exists():
    old = json.loads(metadata_path.read_text(encoding='utf-8'))
    metadata['prior_validation_commands'] = old.get('commands', [])

def run(name, command, extra):
    command_env = dict(env, **extra)
    command = [str(args.go), *command]
    print('RUN', ' '.join(command), flush=True)
    started = time.monotonic()
    with (raw/name).open('w', encoding='utf-8', newline='') as log:
        process = subprocess.Popen(command, cwd=module/'raft', env=command_env,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding='utf-8', errors='replace')
        for line in process.stdout:
            log.write(line)
            log.flush()
            print(line, end='', flush=True)
        code = process.wait()
    public_extra = {k: '<2A>/results/raw' if k == 'RAFT_RESULTS_DIR' else v for k, v in extra.items()}
    metadata['commands'].append({'command': ['go', *command[1:]], 'environment': public_extra,
                                 'exit_code': code, 'duration_s': round(time.monotonic()-started, 3),
                                 'log': name})
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding='utf-8')
    return code

failed = False
race = ['-race'] if args.race else []
if not args.experiments_only:
    for profile in ['baseline', 'fast', 'stable']:
        code = run(f'validation_{profile}.log', ['test', *race, '-v', '-run', '2A$',
                   '-count=3' if profile == 'baseline' else '-count=1', '-timeout=240s'],
                   {'RAFT_ELECTION_PROFILE': profile})
        failed |= code != 0
        if failed:
            break
if not args.tests_only and not failed:
    failed |= run('election_experiments.log', ['test', *race, '-v', '-run', '^TestMemberAExperiment$',
                  '-count=1', '-timeout=20m'],
                  {'RAFT_EXPERIMENTS': '1', 'RAFT_TRIALS': str(args.trials),
                   'RAFT_RESULTS_DIR': str(raw)}) != 0
if (raw/'trial_metrics.csv').exists():
    subprocess.run([sys.executable, str(root/'scripts/summarize_results.py')], check=True)
sys.exit(int(failed))
