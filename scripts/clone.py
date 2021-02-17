import os
import subprocess
import sys
import concurrent.futures

from more_itertools.recipes import grouper

absent = []


def https_to_ssh(url):
    return 'git@' + url.split('//')[1].replace('/', ':', 1)


def get_username(ssh_url):
    return ssh_url.split('/')[0].split(':')[1]


def get_repo(ssh_url):
    return ssh_url.split('/')[-1]


https_urls = [
   "https://git.<abc>.com/<abc>",
]

def batch_clone_or_update(https_urls, update=False):
    return [clone_or_update(https_url, update) for https_url in https_urls]

def clone_or_update(https_url, update=False):
    ssh_url = https_to_ssh(https_url)
    username = get_username(ssh_url)
    repo = get_repo(ssh_url)
    dir_name = username
    if os.path.isdir(dir_name):
        try:
            assert username in subprocess.run(
                ['git', 'remote', '-v'], stdout=subprocess.PIPE, cwd='./' + dir_name).stdout.decode()
            if update:
                subprocess.run(['git', 'pull'], cwd='./' + dir_name)
        except AssertionError as e:
            print(e, username, '*'*30, "repo not cloned correctly, try removing the folder and running again")
            raise
        return
    print(username)
    try:
        os.mkdir(dir_name)
        subprocess.run(['git', 'clone', ssh_url, dir_name])
        # os.rename(repo, dir_name)
        assert username in subprocess.run(
            ['git', 'remote', '-v'], stdout=subprocess.PIPE, cwd='./' + dir_name).stdout.decode()
    except Exception as e:
        print('username', e)
        return username


update = len(sys.argv) > 1 and sys.argv[1] == '-u'

executor = concurrent.futures.ThreadPoolExecutor(10)
futures = executor.map(clone_or_update, https_urls)

print('-' * 80)
print("scripts failed for the following repos, check permissions and urls and try again:")
print([future for future in futures if future])