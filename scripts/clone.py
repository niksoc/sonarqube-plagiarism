import concurrent.futures
import os
import shutil
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


def clone_or_update(https_url, update=False):
    ssh_url = https_to_ssh(https_url)
    username = get_username(ssh_url)
    dir_name = username
    dir_path = f"repos/{dir_name}"

    print(username)

    if os.path.isdir(dir_path):
        if username not in subprocess.run(
                ['git', 'remote', '-v'], stdout=subprocess.PIPE, cwd=dir_path).stdout.decode():
            shutil.rmtree(dir_path)
        elif update:
            subprocess.run(['git', 'pull'], cwd=dir_path, timeout=30)
            return

    try:
        os.makedirs(dir_path, exist_ok=True)
        subprocess.run(['git', 'clone', ssh_url, dir_name], cwd="./repos", timeout=30)
        assert username in subprocess.run(
            ['git', 'remote', '-v'], stdout=subprocess.PIPE, cwd=dir_path).stdout.decode()
    except Exception as e:
        print('username', e)
        return https_url


os.makedirs("repos", exist_ok=True)
update = len(sys.argv) > 1 and sys.argv[1] == '-u'

executor = concurrent.futures.ThreadPoolExecutor(10)
futures = executor.map(clone_or_update, https_urls, [update] * len(https_urls))

print('-' * 80)
print(f"scripts failed for the following repos, check permissions and urls and try again:"
      f" {[future for future in futures if future]}")
