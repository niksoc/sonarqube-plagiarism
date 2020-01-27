import os
import subprocess
import sys

absent = []


def https_to_ssh(url):
    return 'git@' + url.split('//')[1].replace('/', ':', 1)


def get_username(ssh_url):
    return ssh_url.split('/')[0].split(':')[1]


def get_repo(ssh_url):
    return ssh_url.split('/')[-1]


for https_url in [
        'https://git.abc.com/<username>/<project_name>',
]:
    ssh_url = https_to_ssh(https_url)
    username = get_username(ssh_url)
    repo = get_repo(ssh_url)
    dir_name = username
    if os.path.isdir(dir_name):
        try:
            assert username in subprocess.run(
                ['git', 'remote', '-v'], stdout=subprocess.PIPE, cwd='./' + dir_name).stdout.decode()
            if len(sys.argv) > 1 and sys.argv[1] == '-u':
                subprocess.run(['git', 'pull'], cwd='./' + dir_name)
        except AssertionError as e:
            print(e, username)
            raise
        continue
    print(username)
    subprocess.run(['git', 'clone', ssh_url])
    try:
        os.rename(repo, dir_name)
        assert username in subprocess.run(
            ['git', 'remote', '-v'], stdout=subprocess.PIPE, cwd='./' + dir_name).stdout.decode()
    except Exception as e:
        print('username', e)
        absent += [username]

print('-' * 80)
print(absent)

