"""Create a separate runnable baseline checkout without overwriting current code."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
destination = root/'.baseline-run'
shutil.copytree(root/'code/6.5840', destination, dirs_exist_ok=True)
for p in (root/'baseline').glob('*.go'):
    shutil.copy2(p, destination/'src/raft'/p.name)
print(destination.resolve())
