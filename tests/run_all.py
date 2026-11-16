"""Runs every test of the prototype in one go (2026-11-16) and prints one summary.

Steps (each can be skipped with --skip <name>):
  reason        Pellet + SWRL over ontology/*.ttl -> kb/inferred.nt, kb/reasoning-report.json (= scripts/reason.cmd)
  reasoning-tc  reasoning test cases of TEST_CASES (28): rev + test data, rev + conflict data, check_reasoning_<rev>.py
                (files in ../08_Revisions; skipped when that folder is not there)
  fuseki        Fuseki up (started from --fuseki-home if it is not running) and the dataset exists (created in memory if not)
  load          ontology -> default graph, kb/inferred.nt -> named graph (= scripts/load-fuseki.cmd)
  backend       Spring Boot answers /api/projects (started with mvnw spring-boot:run if it is not running)
  run-checks    tests/run_checks.py (reasoning counts, scores in both modes, coverage, recommendations, 34 CQs)
  api-tc        knowledge-search test cases TC-KS-* (../08_Revisions/2026-11-14_TestCases/run_api_testcases.py)
  npm-test      frontend tests (vitest)

Usage, from the repository root (Windows):  scripts\\run-all-tests.cmd [options]   or   python tests\\run_all.py [options]
  --restart-backend   stop the backend that is already running on port 8080 and start it again (needed after a Java change)
  --keep-backend      leave a backend started by this script running at the end (default: it is stopped)
  --fuseki-home DIR   Fuseki folder (default: %FUSEKI_HOME% or C:\\apache-jena-fuseki-6.2.0)
  --skip STEP         skip a step (repeatable), e.g. --skip npm-test
Needs Java 11+, Python 3.8+, Node.js (npm) and Fuseki; only the Python standard library is used.
Writes tests/results/run_all_latest.json and tests/results/run_all_latest.log.
"""
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RESEARCH = os.path.dirname(ROOT)
WIN = os.name == 'nt'
DATASET = 'ISO29110_RecommenderSystems'
FUSEKI = 'http://localhost:3030'
API = 'http://localhost:8080/api'
STEPS = ['reason', 'reasoning-tc', 'fuseki', 'load', 'backend', 'run-checks', 'api-tc', 'npm-test']
LOG = []


def say(msg=''):
    print(msg, flush=True)
    LOG.append(msg)


def run(cmd, cwd=ROOT, shell=False, timeout=1800):
    """Runs a command, streams its output, returns (exit code, output)."""
    say('  $ ' + (cmd if isinstance(cmd, str) else ' '.join(cmd)))
    env = dict(os.environ, PYTHONUTF8='1', PYTHONIOENCODING='utf-8')  # child scripts print / write Thai text
    p = subprocess.Popen(cmd, cwd=cwd, shell=shell, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         text=True, encoding='utf-8', errors='replace', env=env)
    out = []
    t0 = time.time()
    for line in p.stdout:
        line = line.rstrip('\n')
        out.append(line)
        print('    ' + line, flush=True)
        if time.time() - t0 > timeout:
            p.kill()
            break
    p.wait()
    LOG.extend('    ' + x for x in out)
    return p.returncode, '\n'.join(out)


def http(url, method='GET', data=None, headers=None, timeout=10):
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read()


def up(url):
    try:
        http(url, timeout=5)
        return True
    except Exception:
        return False


def wait_for(url, seconds):
    t0 = time.time()
    while time.time() - t0 < seconds:
        if up(url):
            return True
        time.sleep(3)
    return False


def ontology_file():
    files = sorted(glob.glob(os.path.join(ROOT, 'ontology', '*.ttl')))
    if len(files) != 1:
        raise RuntimeError('expected one ontology/*.ttl, found %d' % len(files))
    return files[0]


def rev_of(path):
    m = re.search(r'_(rev\d+)\.ttl$', path)
    return m.group(1) if m else 'rev'


def reasoner(inputs, out_nt, report):
    cp = os.pathsep.join([os.path.join(ROOT, 'reasoner', 'lib', '*'), os.path.join(ROOT, 'reasoner', 'build')])
    cmd = ['java', '-Xmx2g', '-cp', cp, 'iso29110.reasoning.SwrlReasoningRunner']
    for i in inputs:
        cmd += ['--in', i]
    return run(cmd + ['--out-inferred', out_nt, '--report', report])


# ------------------------------------------------------------------ steps

def step_reason(a):
    onto = ontology_file()
    rel = os.path.relpath(onto, ROOT).replace('\\', '/')
    code, out = reasoner([rel], os.path.join('kb', 'inferred.nt'), os.path.join('kb', 'reasoning-report.json'))
    rep = json.load(open(os.path.join(ROOT, 'kb', 'reasoning-report.json'), encoding='utf-8'))
    ok = code == 0 and rep.get('consistent') is True
    return ok, '%s: consistent=%s, types %s / objectProps %s' % (os.path.basename(onto), rep.get('consistent'),
                                                               rep['inferred']['types'], rep['inferred']['objectPropertyValues'])


def step_reasoning_tc(a):
    onto = ontology_file()
    rev = rev_of(onto)
    checks = glob.glob(os.path.join(RESEARCH, '08_Revisions', '*', 'regression', 'check_reasoning_%s.py' % rev))
    tcdir = os.path.join(RESEARCH, '08_Revisions', '2026-11-14_TestCases')
    ttlpy = os.path.join(RESEARCH, '08_Revisions', '_tools', 'ttl.py')
    if len(checks) != 1 or not os.path.isdir(tcdir) or not os.path.isfile(ttlpy):
        return None, 'no check_reasoning_%s.py / test data in ../08_Revisions' % rev
    reg = os.path.dirname(checks[0])
    res = os.path.join(reg, 'results')
    os.makedirs(res, exist_ok=True)
    shutil.copyfile(os.path.join(ROOT, 'kb', 'inferred.nt'), os.path.join(res, 'inferred_%s.nt' % rev))
    shutil.copyfile(os.path.join(ROOT, 'kb', 'reasoning-report.json'), os.path.join(res, 'reasoning-report_%s.json' % rev))
    tmp = tempfile.mkdtemp(prefix='run_all_')
    try:
        reasoner([onto], os.path.join(res, 'inferred_%s_rerun.nt' % rev), os.path.join(tmp, 'rerun.json'))
        for name, extra, nt, rep in [('testdata', 'testdata_rev14.ttl', 'inferred_%s_testdata.nt' % rev, 'report_%s_testdata.json' % rev),
                                     ('conflict', 'testdata_conflict.ttl', None, 'report_%s_conflict.json' % rev)]:
            merged = os.path.join(tmp, '%s.ttl' % name)   # one file: the test data has no declarations of its own
            with open(merged, 'wb') as w:
                for f in (onto, os.path.join(tcdir, extra)):
                    w.write(open(f, 'rb').read())
                    w.write(b'\n')
            reasoner([merged], os.path.join(res, nt) if nt else os.path.join(tmp, 'conflict.nt'), os.path.join(res, rep))
        pkl = os.path.join(tmp, 'onto.pkl')
        run([sys.executable, ttlpy, onto, pkl])
        code, out = run([sys.executable, checks[0], pkl, reg])
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    m = re.search(r'PASS (\d+) / (\d+)', out)
    return code == 0 and bool(m) and m.group(1) == m.group(2), m.group(0) if m else 'no result line'


def step_fuseki(a):
    started = False
    if not up(FUSEKI + '/$/ping'):
        home = a.fuseki_home
        bat = os.path.join(home, 'fuseki-server.bat' if WIN else 'fuseki-server')
        if not os.path.isfile(bat):
            return False, 'Fuseki is not running and %s was not found (use --fuseki-home)' % bat
        say('  starting Fuseki from %s (new window)' % home)
        if WIN:
            subprocess.Popen('start "Fuseki" /D "%s" "%s"' % (home, bat), shell=True)
        else:
            subprocess.Popen([bat], cwd=home, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if not wait_for(FUSEKI + '/$/ping', 120):
            return False, 'Fuseki did not start within 120 s'
        started = True
    try:
        http('%s/$/datasets/%s' % (FUSEKI, DATASET))
        note = 'dataset exists'
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
        body = urllib.parse.urlencode({'dbName': DATASET, 'dbType': 'mem'}).encode()
        http(FUSEKI + '/$/datasets', 'POST', body, {'Content-Type': 'application/x-www-form-urlencoded'})
        note = 'dataset created (in memory)'
    return True, ('started, ' if started else 'running, ') + note


def step_load(a):
    data = '%s/%s/data' % (FUSEKI, DATASET)
    onto = ontology_file()
    http(data + '?default', 'PUT', open(onto, 'rb').read(), {'Content-Type': 'text/turtle'}, timeout=300)
    http(data + '?graph=' + urllib.parse.quote('urn:iso29110:graph:inferred', safe=''), 'PUT',
         open(os.path.join(ROOT, 'kb', 'inferred.nt'), 'rb').read(), {'Content-Type': 'application/n-triples'}, timeout=300)
    q = 'SELECT ?g (COUNT(*) AS ?n) WHERE { { ?s ?p ?o BIND("default" AS ?g) } UNION { GRAPH <urn:iso29110:graph:inferred> { ?s ?p ?o } BIND("inferred" AS ?g) } } GROUP BY ?g'
    _, body = http('%s/%s/query?%s' % (FUSEKI, DATASET, urllib.parse.urlencode({'query': q})),
                   headers={'Accept': 'application/sparql-results+json'}, timeout=120)
    n = {b['g']['value']: b['n']['value'] for b in json.loads(body)['results']['bindings']}
    return True, 'default %s / inferred %s triples' % (n.get('default'), n.get('inferred'))


BACKEND = {'proc': None}


def pids_on_8080():
    if not WIN:
        return []
    out = subprocess.run(['netstat', '-ano', '-p', 'TCP'], capture_output=True, text=True).stdout
    return sorted({l.split()[-1] for l in out.splitlines() if re.search(r':8080\s', l) and 'LISTENING' in l})


def stop_pid(pid):
    if WIN:
        subprocess.run(['taskkill', '/T', '/F', '/PID', str(pid)], capture_output=True)
    else:
        subprocess.run(['kill', str(pid)])


def step_backend(a):
    if up(API + '/projects'):
        if not a.restart_backend:
            say('  WARNING: a backend is already running; it is used as it is. After a Java change use --restart-backend.')
            return True, 'already running (not restarted)'
        for pid in pids_on_8080():
            say('  stopping backend PID %s' % pid)
            stop_pid(pid)
        time.sleep(3)
    mvnw = os.path.join(ROOT, 'backend', 'mvnw.cmd' if WIN else 'mvnw')
    say('  starting backend: %s spring-boot:run (log: tests/results/backend.log)' % os.path.basename(mvnw))
    os.makedirs(os.path.join(HERE, 'results'), exist_ok=True)
    logf = open(os.path.join(HERE, 'results', 'backend.log'), 'w', encoding='utf-8')
    BACKEND['proc'] = subprocess.Popen([mvnw, '-q', 'spring-boot:run'], cwd=os.path.join(ROOT, 'backend'),
                                       stdout=logf, stderr=subprocess.STDOUT, shell=WIN)
    if not wait_for(API + '/projects', 420):
        return False, 'backend did not answer within 7 minutes (see tests/results/backend.log)'
    return True, 'started'


def step_run_checks(a):
    code, out = run([sys.executable, os.path.join(HERE, 'run_checks.py')])
    m = re.search(r'(\d+) checks, (\d+) failed', out)
    return code == 0, m.group(0) if m else 'no result line'


def step_api_tc(a):
    script = os.path.join(RESEARCH, '08_Revisions', '2026-11-14_TestCases', 'run_api_testcases.py')
    if not os.path.isfile(script):
        return None, 'run_api_testcases.py not found in ../08_Revisions'
    code, out = run([sys.executable, script], cwd=os.path.dirname(script))
    m = re.findall(r'(\d+)\s*/\s*(\d+)', out)
    return code == 0, ('%s/%s' % m[-1]) if m else 'exit code %d' % code


def step_npm_test(a):
    fe = os.path.join(ROOT, 'frontend')
    if not os.path.isdir(os.path.join(fe, 'node_modules')):
        run('npm ci', cwd=fe, shell=True)
    code, out = run('npm test', cwd=fe, shell=True)
    m = re.search(r'Tests\s+(.*)', out)
    return code == 0, re.sub(r'\x1b\[[0-9;]*m', '', m.group(1)).strip() if m else 'exit code %d' % code


FUNCS = {'reason': step_reason, 'reasoning-tc': step_reasoning_tc, 'fuseki': step_fuseki, 'load': step_load,
         'backend': step_backend, 'run-checks': step_run_checks, 'api-tc': step_api_tc, 'npm-test': step_npm_test}
NEEDS = {'load': 'fuseki', 'run-checks': 'backend', 'api-tc': 'backend'}


def main():
    try:
        sys.stdout.reconfigure(errors='replace')
    except Exception:
        pass
    ap = argparse.ArgumentParser(description='Run every test of the prototype.')
    ap.add_argument('--skip', action='append', default=[], choices=STEPS)
    ap.add_argument('--restart-backend', action='store_true')
    ap.add_argument('--keep-backend', action='store_true')
    ap.add_argument('--fuseki-home', default=os.environ.get('FUSEKI_HOME', r'C:\apache-jena-fuseki-6.2.0'))
    a = ap.parse_args()
    t_all = time.time()
    results = []
    state = {}
    try:
        for name in STEPS:
            say('\n=== %s' % name)
            if name in a.skip:
                results.append((name, 'SKIP', 'skipped (--skip)', 0))
                state[name] = None
                say('  skipped')
                continue
            need = NEEDS.get(name)
            if need and state.get(need) is False:
                results.append((name, 'SKIP', 'needs %s' % need, 0))
                state[name] = False
                continue
            t0 = time.time()
            try:
                ok, detail = FUNCS[name](a)
            except Exception as e:  # report and go on with the next step
                ok, detail = False, '%s: %s' % (type(e).__name__, e)
            state[name] = ok
            results.append((name, 'SKIP' if ok is None else 'PASS' if ok else 'FAIL', detail, time.time() - t0))
            say('  -> %s  %s' % (results[-1][1], detail))
    finally:
        if BACKEND['proc'] is not None and not a.keep_backend:
            say('\nstopping the backend started by this script')
            stop_pid(BACKEND['proc'].pid)
    say('\n' + '=' * 78)
    say('SUMMARY  (%s, %.0f s)' % (time.strftime('%Y-%m-%d %H:%M'), time.time() - t_all))
    for name, st, detail, sec in results:
        say('  %-13s %-4s  %-60s %5.0f s' % (name, st, detail[:60], sec))
    failed = [r for r in results if r[1] == 'FAIL']
    say('RESULT: %s' % ('ALL PASSED' if not failed else '%d step(s) FAILED: %s' % (len(failed), ', '.join(r[0] for r in failed))))
    os.makedirs(os.path.join(HERE, 'results'), exist_ok=True)
    json.dump({'runAt': time.strftime('%Y-%m-%dT%H:%M:%S'), 'ontology': os.path.basename(ontology_file()),
               'steps': [dict(step=n, status=s, detail=d, seconds=round(t, 1)) for n, s, d, t in results]},
              open(os.path.join(HERE, 'results', 'run_all_latest.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    open(os.path.join(HERE, 'results', 'run_all_latest.log'), 'w', encoding='utf-8').write('\n'.join(LOG) + '\n')
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
