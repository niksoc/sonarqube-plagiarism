import csv
import datetime
import os
import shutil
import subprocess

import requests
from flask import Flask, request, Request, current_app, send_from_directory
from flask import render_template_string
from flask_login import LoginManager, login_user, UserMixin, login_required, logout_user
from flask_wtf import FlaskForm
from werkzeug.utils import redirect
from wtforms import TextAreaField, SelectField, validators, PasswordField, StringField

request: Request = request


class RepositoriesForm(FlaskForm):
    urls = TextAreaField("newline separated https urls, each should end with .git")


class ScanForm(FlaskForm):
    project_type = SelectField("Project Type", choices=["NodeJS", "Python", "Java", "Maven"])


class LoginForm(FlaskForm):
    password = PasswordField("admin password", validators=[
        validators.DataRequired(),
        validators.AnyOf("pass", message="Incorrect password")
    ])


class GetResultForGitUsernameForm(FlaskForm):
    git_username = StringField("enter your gitlab/github username")


FORM_TEMPLATE = """<form method="POST" action="{{ action }}">
        {{ error }}
        {% if form.errors %}
        <ul class="errors">
            {% for field_name, field_errors in form.errors|dictsort if field_errors %}
                {% for error in field_errors %}
                    <li>{{ form[field_name].label }}: {{ error }}</li>
                {% endfor %}
            {% endfor %}
          </ul>
          {% endif %}
                {% for field_name in form._fields %}
                    <p>
                       {% if "csrf" not in field_name %}
                         {{ form[field_name].label }}
                       {% endif %}
                       {{ form[field_name] }}
                    </p>
                {% endfor %}
          <input type="submit" value="Go">
        </form>"""

app: Flask = Flask(__name__)
app.config.update(
    DEBUG=True,
    SECRET_KEY=b'_5#y2L"F4Q8z\n\xec]/'
)
login_manager = LoginManager(app)
login_manager.login_view = "login"


class User(UserMixin):
    id = "admin"


admin_user = User()


@app.route("/", methods=["GET", "POST"])
def get_results_for_git_username():
    form = GetResultForGitUsernameForm()
    if form.validate_on_submit():
        if not os.path.exists(_abs("analysis_results.csv")):
            return render_template_string(FORM_TEMPLATE, form=form, action="/", error="Analysis not run yet")
        with open(_abs("analysis_results.csv")) as csvfile:
            for row in csv.reader(csvfile, delimiter=" ", quotechar="|"):
                if row[0] == form.git_username.data:
                    return f"{row[0]}, {row[1]} copied lines detected"
            return render_template_string(FORM_TEMPLATE, form=form, action="/", error="Repository not found")
    return render_template_string(FORM_TEMPLATE, form=form, action="/")


@app.route("/login", methods=["GET", "POST"])
def login():
    form = LoginForm()
    if form.validate_on_submit():
        login_user(admin_user)
        return redirect("/admin")
    return render_template_string(FORM_TEMPLATE, form=form, action="/login")


@app.route("/logout")
@login_required
def logout():
    logout_user()
    return redirect("/login")


@app.route("/admin")
@login_required
def main():
    try:
        with open("repos.txt") as f:
            repos = f.read().splitlines()
    except FileNotFoundError:
        repos = []
    return render_template_string("""
    <ul>
    <li><a href="/repositories">repositories</a></li>
    <li><a href="/clone">clone</a></li>
    <li><a href="/clone/logs">clone logs</a></li>
    <li><a href="/scan">scan</a></li>
    <li><a href="/scan/logs">scan logs</a></li>
    <li><a href="/analyse">analyse</a></li>
    <li><a href="/analyse/logs">analyse logs</a></li>
    <li><a href="/clear">clear</a></li>
    <li><a href="/logout">logout</a></li>
    </ul>
    current repositories: 
    <ul>{% for repo in repos %}<li>{{ repo }}</li>{% endfor %}
    """, repos=repos)


@app.route("/repositories", methods=["GET", "POST"])
@login_required
def repositories():
    form = RepositoriesForm()
    if form.validate_on_submit():
        _write_repositories_to_file(form.urls.data)
        return form.urls.data
    return render_template_string(FORM_TEMPLATE, form=form, action="/repositories")


@app.route("/clone", methods=["GET"])
@login_required
def clone():
    with open(_abs("repos.txt")) as repos:
        with open(_abs("clone.log"), "w") as log:
            subprocess.Popen(["python", _abs("clone.py"), "-u"], stdin=repos, stdout=log, stderr=log)
    return redirect("/admin")


@app.route("/clone/logs")
@login_required
def clone_logs():
    if not os.path.exists(_abs("clone.log")):
        return "No logs"
    with open(_abs("clone.log")) as log:
        return f"<pre>{log.read()}</pre>"


@app.route("/scan", methods=["GET", "POST"])
@login_required
def scan():
    form = ScanForm()
    if form.validate_on_submit():
        with open(_abs("scan.log"), "w") as log:
            subprocess.Popen(["python", _abs("scan.py"), "-project_type", form.project_type.data], stdout=log, stderr=log)
        return redirect("/admin")
    return render_template_string(FORM_TEMPLATE, form=form, action="/scan")


@app.route("/scan/logs")
@login_required
def scan_logs():
    if not os.path.exists(_abs("scan.log")):
        return "No logs"
    with open(_abs("scan.log")) as log:
        return f"<pre>{log.read()}</pre>"


@app.route("/analyse", methods=["POST"])
@login_required
def analyse():
    with open(_abs("analyse.log"), "w") as log:
        subprocess.run(["python", _abs("analysis.py")], stdout=log, stderr=log)
    return send_from_directory(directory=current_app.root_path, filename="analysis_results.csv")


@app.route("/analyse/logs")
@login_required
def analysis_logs():
    if not os.path.exists(_abs("analyse.log")):
        return "No logs"
    with open(_abs("analyse.log")) as log:
        return f"<pre>{log.read()}</pre>"


@app.route("/clear")
@login_required
def clear():
    try:
        shutil.rmtree("repos")
        os.remove("scan.log")
        os.remove("analysis.log")
        os.remove("clone.log")
        os.remove("repos.txt")
    except FileNotFoundError:
        pass
    try:
        r = requests.post(
            f"http://localhost:9000/api/projects/bulk_delete?analyzedBefore={datetime.date.today()+datetime.timedelta(days=1)}", auth=("admin", "admin"))
        r.raise_for_status()
    except Exception as e:
        return f"Clearing sonarqube projects failed with exception {e!r}"
    return "Successfully cleared data"


@login_manager.user_loader
def load_user(user_id):
    return admin_user


def _write_repositories_to_file(repos):
    with open(_abs("repos.txt"), "w") as f:
        f.write(repos)


def _abs(path):
    return os.path.join(current_app.root_path, path)


if __name__ == "__main__":
    app.run()
