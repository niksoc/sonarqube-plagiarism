import os
import sys
import time
import subprocess
import collections
import json

d = os.getcwd()
projectDirs = [os.path.join(d, o) for o in os.listdir(d) if os.path.isdir(
    os.path.join(d, o)) and not o.startswith('.') and not o == 'node_modules' and not o == 'venv']


def run_analysis(projectDirs):
    errors = []
    for i, projectDir in enumerate(projectDirs):
        try:
          project = projectDir.split('/')[-1]
          p = subprocess.run(["mvn", "sonar:sonar"], cwd=projectDir)
        except Exception as e:
          print(e)
        print('='*80)
        print(projectDir, '{}/{}'.format(i, len(projectDirs)))
        print('='*80)
    print(errors) 

run_analysis(projectDirs)
