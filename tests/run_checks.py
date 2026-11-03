"""Checks the running prototype against the expected values in tests/expected.json.

  1. Reasoning   - R1-R10 counted with SPARQL on the Fuseki dataset (named graph of Pellet output).
  2. Scores      - process scores of PJ.01-PJ.03 from GET /api/projects/{id}/assessment.
  3. Recommend.  - tasks, roles and Base Practices of every assessed entity with a gap.
  4. Templates   - the 34 example questions sent to POST /api/search/{T1|T2|T3}; the task / BP IDs
                   returned must equal eval_templates.tsv of Round 6.

Needs Python 3.8+ only (standard library). Start Fuseki (data loaded) and the backend first:
  python tests/run_checks.py [--fuseki http://localhost:3030/ISO29110_RecommenderSystems/query] [--api http://localhost:8080/api]
"""
import argparse
import json
import os
import re
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


def get(url):
    with urllib.request.urlopen(url, timeout=120) as r:
        return json.load(r)


def post(url, data):
    req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def sparql(endpoint, query):
    url = endpoint + '?' + urllib.parse.urlencode({'query': query})
    req = urllib.request.Request(url, headers={'Accept': 'application/sparql-results+json'})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)['results']['bindings']


def units(rows):
    """Task IDs (PM.1.9) and BP IDs (PM.BP27) in a result, as counted in Round 4 / Round 6."""
    u = set()
    for r in rows:
        for v in r.values():
            m = re.search(r'#(PM|SI)\.PT\.(\d+)\.(\d+)$', v or '')
            if m:
                u.add('%s.%d.%d' % (m.group(1), int(m.group(2)), int(m.group(3))))
            m = re.fullmatch(r'(PM|SI)\.BP\.?(\d+)', (v or '').split('#')[-1].strip())
            if m:
                u.add('%s.BP%d' % (m.group(1), int(m.group(2))))
    return u


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--fuseki', default='http://localhost:3030/ISO29110_RecommenderSystems/query')
    ap.add_argument('--api', default='http://localhost:8080/api')
    a = ap.parse_args()
    exp = json.load(open(os.path.join(HERE, 'expected.json'), encoding='utf-8'))
    examples = json.load(open(os.path.join(ROOT, 'frontend', 'src', 'examples.json'), encoding='utf-8'))
    results = []

    def check(name, ok, detail=''):
        results.append(ok)
        print(('PASS ' if ok else 'FAIL ') + name + ('' if ok else '  ' + detail))

    print('1. Reasoning (Fuseki)')
    for name, c in exp['reasoning'].items():
        n = int(sparql(a.fuseki, c['sparql'])[0]['n']['value'])
        check(name, n == c['expected'], 'got %d, expected %d' % (n, c['expected']))

    print('2. Scores and 3. Recommendations (API)')
    got_recs = {}
    for pj, want in exp['processScores'].items():
        data = get('%s/projects/%s/assessment' % (a.api, pj))
        got = {p['process']: round(float(p['score']), 4) for p in data['processScores']}
        check('%s process scores' % pj, got == want, 'got %s, expected %s' % (got, want))
        for r in data['recommendations']:
            got_recs[r['ae']] = {'tasks': sorted(t['taskID'] for t in r['tasks']),
                                 'roles': sorted(x['roleName'] for x in r['roles']),
                                 'basePractices': sorted(b['bpID'] for b in r['basePractices'])}
    check('assessed entities with recommendations', sorted(got_recs) == sorted(exp['recommendations']),
          'got %s' % sorted(got_recs))
    for ae, want in exp['recommendations'].items():
        g = got_recs.get(ae, {})
        for k in ('tasks', 'roles', 'basePractices'):
            check('%s %s' % (ae, k), g.get(k) == sorted(want[k]), 'got %s, expected %s' % (g.get(k), sorted(want[k])))

    print('4. Templates (API)')
    for x in examples:
        res = post('%s/search/%s' % (a.api, x['template']), x['params'])
        want = exp['templates'][x['id']]
        if x['template'] == 'T3':
            pairs = {(r.get('wp'), r.get('wpc')) for r in res['rows'] if '#WPC.' in (r.get('wpc') or '')}
            got = '%d WP-WPC pairs' % len(pairs)
        else:
            got = ' '.join(sorted(units(res['rows'])))
        check('%s (%s)' % (x['id'], x['template']), got == want, 'got [%s], expected [%s]' % (got, want))

    failed = results.count(False)
    print('\n%d checks, %d failed' % (len(results), failed))
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
