import os
import subprocess
import sys

SONAR_SCANNER_PATH = "/home/hasher/programs/sonar-scanner-3.3.0.1492-linux/bin/sonar-scanner"

d = os.path.join(os.getcwd(), "repos")
projectDirs = [os.path.join(d, o) for o in os.listdir(d) if os.path.isdir(
    os.path.join(d, o)) and not o.startswith('.') and not o == 'node_modules' and not o == 'venv']


class Scanner:
    def scan(self):
        self._pre_scan()
        for i, projectDir in enumerate(projectDirs):
            print('=' * 80)
            print(projectDir, '{}/{}'.format(i + 1, len(projectDirs)))
            print('=' * 80)
            self.scan_project(projectDir)

    def scan_project(self, projectDir):
        subprocess.run([SONAR_SCANNER_PATH,
                        "-Dsonar.login=admin",
                        "-Dsonar.password=admin",
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

    def _pre_scan(self):
        pass


class MavenScanner(Scanner):
    def scan_project(self, projectDir):
        pom_paths = subprocess.run(["find", ".", "-name", "pom.xml"], stdout=subprocess.PIPE,
                                   cwd=projectDir).stdout.decode()
        assert pom_paths, "No pom.xml found"
        pom_path = sorted(pom_paths.splitlines(), key=lambda loc: len(loc))[0]
        pom_dir = os.path.dirname(os.path.join(projectDir, pom_path))
        subprocess.run(["mvn",
                        "sonar:sonar",
                        "-Dsonar.login=admin",
                        "-Dsonar.password=admin",
                        "-Dsonar.projectKey={}".format(os.path.basename(projectDir)),
                        "-Dsonar.host.url=http://localhost:9000",
                        "-Dsonar.cpd.cross_project=true",
                        "-Dsonar.cpd.minimumTokens=74",
                        "-Dsonar.coverage.exclusions=*",
                        "-Dsonar.global.exclusions=**/node_modules/**,**/venv/**,**/out/**,**/target/**,**/build/**,**/*.xml,**/htmlcov/**,**/*.html",
                        "-Dsonar.java.binaries=."
                        ], cwd=pom_dir)


class NodeScanner(Scanner):
    def _pre_scan(self):
        self.__copy_project_package_json_to_root()

        subprocess.run(["npm", "install"])

    def __copy_project_package_json_to_root(self):
        for projectDir in projectDirs:
            files = [os.path.join(projectDir, f) for f in os.listdir(projectDir) if
                     os.path.isfile(os.path.join(projectDir, f))]
            for file in files:
                if "package.json" in file:
                    with open(file) as f:
                        contents = f.read()
                        with open("package.json", "w") as p:
                            p.write(contents)
                            break


scanners = {
    "Maven": MavenScanner,
    "NodeJS": NodeScanner,
}


if __name__ == "__main__":
    assert len(sys.argv) == 3 and sys.argv[1] == "-project_type", "Must specify -project_type"
    scanners.get(sys.argv[2], Scanner)().scan()
