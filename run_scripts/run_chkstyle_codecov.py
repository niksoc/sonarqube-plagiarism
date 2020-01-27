import os
import re
import subprocess, csv

d = os.getcwd()
projectDirs = [os.path.join(d, o) for o in os.listdir(d) if os.path.isdir(
    os.path.join(d, o)) and not o.startswith('.') and not o == 'node_modules' and not o == 'venv']


def checkstyle(projectDir):
    p = subprocess.run(["mvn",
                        "checkstyle:check",
                        ], cwd=projectDir, stdout=subprocess.PIPE)
    out = p.stdout.decode()
    m = re.search('You have (\d+) Checkstyle violations.', out)
    if m:
        return int(m.group(1))
    m = re.search('BUILD SUCCESS', out)
    if m:
        return 0
    return None


def run_cov(projectDir):
    p = subprocess.run(['mvn', 'jacoco:prepare-agent', 'install', 'jacoco:report'], cwd=projectDir, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    res = [0] * 6
    try:
        with open(projectDir + '/target/site/jacoco/jacoco.csv') as f:
            rows = csv.reader(f)
            for c, row in enumerate(rows):
                if c == 0:
                    continue
                for d, i in enumerate([5, 6, 7, 8, 11, 12]):
                    res[d] += int(row[i])
    except Exception as e:
        print(e)
    out = p.stdout.decode()
    m = re.search('Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: 0', out)
    if m:
     res.extend(m.groups())
    else:
     res.extend([None, None, None])
    return res


def run_analysis(projectDirs):
    cov = ['NAME', 'BRANCH_MISSED','BRANCH_COVERED','LINE_MISSED','LINE_COVERED','METHOD_MISSED','METHOD_COVERED', 'TOTAL TESTS', 'TEST FAILURES', 'TEST ERRORS', 'CHECKSTYLE_ERRORS']
    with open('results.csv', 'a') as f:
         w = csv.writer(f)
         #w.writerow(cov)
    for i, projectDir in enumerate(projectDirs):
        if i + 1 <= 45:
          continue
        print(projectDir)
        p = subprocess.run(["rm",
                            "checkstyle.xml",
                            ], cwd=projectDir)
        p = subprocess.run(["cp",
                            "checkstyle.xml",
                            projectDir + '/',
                            ])
        result = [projectDir.split('/')[-1]]
        result.extend(run_cov(projectDir))
        result.append(checkstyle(projectDir))

        # print(checkstyle(projectDir))
        # print('='*80)
        print(projectDir, '{}/{}'.format(i + 1, len(projectDirs)))
        print('=' * 80)

        with open('results.csv', 'a') as f:
         w = csv.writer(f)
         w.writerow(result)
    


run_analysis(projectDirs)

