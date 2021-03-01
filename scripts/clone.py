import concurrent.futures
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


https_urls = [line.strip() for line in sys.stdin.readlines()]
print(https_urls)


def batch_clone_or_update(https_urls, update=False):
    return [clone_or_update(https_url, update) for https_url in https_urls]


def clone_or_update(https_url, update=False):
    ssh_url = https_to_ssh(https_url)
    username = get_username(ssh_url)
    dir_name = username
    dir_path = f"repos/{dir_name}"
    if os.path.isdir(dir_path):
        try:
            assert username in subprocess.run(
                ['git', 'remote', '-v'], stdout=subprocess.PIPE, cwd=dir_path).stdout.decode()
            if update:
                subprocess.run(['git', 'pull'], cwd=dir_path)
        except AssertionError as e:
            print(e, username, '*' * 30, "repo not cloned correctly, try removing the folder and running again")
            raise
        return
    print(username)
    try:
        os.makedirs(dir_path, exist_ok=True)
        subprocess.run(['git', 'clone', ssh_url, dir_name], cwd="./repos")
        assert username in subprocess.run(
            ['git', 'remote', '-v'], stdout=subprocess.PIPE, cwd=dir_path).stdout.decode()
    except Exception as e:
        print('username', e)
        return https_url


os.makedirs("repos", exist_ok=True)
update = len(sys.argv) > 1 and sys.argv[1] == '-u'

executor = concurrent.futures.ThreadPoolExecutor(10)
futures = executor.map(clone_or_update, https_urls)

print('-' * 80)
print(f"scripts failed for the following repos, check permissions and urls and try again:"
      f" {[future for future in futures if future]}")
