import os
from collections import defaultdict, Counter

import matplotlib.pyplot as plt
import networkx as nx
import pandas as pd
import requests

# directories at root level which are not projects
EXCLUDE_FOLDERS = ['venv', 'node_modules']

IGNORE_LINE_IF_DUPS_MORE_THAN = 3  # if line is duplicated across many people, then it's probably necessary common code and not copying
MINIMUM_SPREAD = -1  # `spread` means number of files which have common content between the two projects
TOTAL_DUP_LINES_FILTER = 50  # total number of lines which have to be duplicate in the entire project

NUM_DUPLICATED_LINES_URL = 'http://localhost:9000/api/measures/component?component={project}&metricKeys=duplicated_lines'
COMPONENTS_URL = 'http://localhost:9000/api/components/tree?component={project}&ps=500&qualifiers=FIL'
DUPLICATIONS_URL = 'http://localhost:9000/api/duplications/show?key={component}'
session = requests.session()
session.auth = ("admin", "admin")

projects = [os.path.join("repos", dirname) for dirname in os.listdir("repos") if
            dirname not in EXCLUDE_FOLDERS and os.path.isdir(os.path.join("repos", dirname))]


def get_num_duplicated_lines(project):
    r = session.get(NUM_DUPLICATED_LINES_URL.format(project=project)).json()
    return int(r['component']['measures'][0]['value'])


get_num_duplicated_lines(projects[0])

projects = [project for project in projects if get_num_duplicated_lines(project) > TOTAL_DUP_LINES_FILTER]


def get_components(project):
    """you can say that in sonarqube, components are files of the project"""
    r = session.get(COMPONENTS_URL.format(project=project)).json()
    return [component['key'] for component in r['components']]


components = {project: get_components(project) for project in projects}


def get_duplicate_refs_for_each_line(duplications):
    dup_line = defaultdict(set)

    for duplication in duplications:
        orig_block = duplication['blocks'][0]

        assert orig_block['_ref'] == '1'

        refs = {block['_ref'] for block in duplication['blocks'][1:]}
        for line_num in range(orig_block['from'], orig_block['from'] + orig_block['size']):
            dup_line[line_num].update(refs)

    return dup_line


def get_count_in_component(component):
    r = session.get(DUPLICATIONS_URL.format(component=component)).json()
    duplications = r['duplications']
    other_components = r['files']

    dup_line = get_duplicate_refs_for_each_line(duplications)

    dup_line = {line_num: refs for line_num, refs in dup_line.items() if len(refs) < IGNORE_LINE_IF_DUPS_MORE_THAN}

    refs = [ref for refs in dup_line.values() for ref in refs]
    projects = [other_components[ref]['project'] for ref in refs]

    return Counter(projects)


def get_stats(project):
    proj_num_lines = Counter()
    spreads = Counter()
    for component in components[project]:
        component_count = get_count_in_component(component)
        spreads.update(component_count.keys())
        proj_num_lines.update(component_count)

    return [{'project': project, 'num_lines': num_lines, 'spread': spreads[project]} for project, num_lines in
            proj_num_lines.items()
            if spreads[project] > MINIMUM_SPREAD]


data = {}
for project in projects:
    for stat in get_stats(project):
        key = tuple(sorted((stat['project'], project)))
        if data.get(key, {'num_lines': 0})['num_lines'] > stat['num_lines']:
            continue
        del stat['project']
        data[key] = stat

data = [{'project1': project1, 'project2': project2, **stat} for (project1, project2), stat in data.items()]
df = pd.DataFrame(data)

df.to_csv('analysis_results.csv')

print(df)

df = df.loc[df['num_lines'] > 50]

df_normalized = df.copy()
df_num_lines = df_normalized['num_lines']
df_normalized['num_lines'] = (df_num_lines - df_num_lines.min()) / (df_num_lines.max() - df_num_lines.min())
G = nx.Graph()
G.add_weighted_edges_from(df_normalized[['project1', 'project2', 'num_lines']].itertuples(index=False))
for connected_component in nx.connected_components(G):
    print(connected_component)

plt.figure(num=None, figsize=(12, 12), dpi=80, facecolor='w', edgecolor='k')
pos = nx.spring_layout(G)

nx.draw_networkx_nodes(G, pos, node_size=3)

nx.draw_networkx_edges(G, pos, edgelist=G.edges(), width=1)

nx.draw_networkx_labels(G, pos, font_size=10, font_family='sans-serif')

plt.axis('off')
plt.show()
