import os
import sys
import time
import subprocess
import collections
import json

SONAR_SCANNER_PATH = "/home/hasher/programs/sonar-scanner-3.3.0.1492-linux/bin/sonar-scanner"

d = os.getcwd()
projectDirs = [os.path.join(d, o) for o in os.listdir(d) if os.path.isdir(
    os.path.join(d, o)) and not o.startswith('.') and not o == 'node_modules' and not o == 'venv']


def run_analysis(projectDirs):
    for i, projectDir in enumerate(projectDirs):
        p = subprocess.run([SONAR_SCANNER_PATH,
                            "-Dsonar.projectKey={}".format(os.path.basename(projectDir)),
                            "-Dsonar.sources={}".format(projectDir),
                            "-Dsonar.host.url=http://localhost:9000",
                            "-DprojectBaseDir={}".format(projectDir),
                            "-Dsonar.cpd.cross_project=true",
                            "-Dsonar.cpd.minimumTokens=74",
                            "-Dsonar.coverage.exclusions=*",
                            "-Dsonar.global.exclusions=**/node_modules/**,**/venv/**,**/out/**,**/target/**,**/build/**,**/*.xml,**/htmlcov/**,**/*.html",
                            "-Dsonar.java.binaries=."
                            ], cwd=projectDir)
        print('='*80)
        print(projectDir, '{}/{}'.format(i, len(projectDirs)))
        print('='*80)


run_analysis(projectDirs)
