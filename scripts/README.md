### Cloning/Updating Project Repos
1. Substitute the repo urls in clone.py
2. Run `python clone.py`, if there are conflicting folders, this skips cloning for them
3. Run `python clone.py -u` to clone new repos or update existing ones

### Running the Analysis (except for Java projects)
1. Install [sonarscanner](https://docs.sonarqube.org/latest/analysis/scan/sonarscanner/) and provide the path to the executable in run.py
2. Run `python run.py`
3. Lint and codecov reports have not been implemented, but to accomplish this analysis.ipynb can probably be extended to make api calls to the sonarqube `issues` api.

### Running the Analysis (Java projects)
1. Install maven
2. Run `python run_maven.py`
3. For checkstyle and codecoverage reports, run `python run_chkstyle_codecov.py`

### Analysis Results
1. Create a python `virtualenvironment` to run the following steps in
2. Install dependencies by running `pip install -r requirements.txt`
3. Run `jupyter notebook` and open `analysis.ipynb` from the notebook UI (Consult external resources for how to use jupyter notebooks)

### Javascript/Typescript Projects
 The node_modules folder containing all the dependencies must be at the root folder to remove the need for doing npm install in every project folder. Usually this is achieved by doing npm install in a project folder and then moving the resulting node_modules folder to the root.
