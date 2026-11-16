"""Checks the assessment data before the reasoner runs (application-level validation, 2026-11-16).

Why: the ontology allows one rating per assessment result (hasDegreeAchievement is Functional and the ratings are
AllDifferent). A percentage on a work product that the project excludes gets a rating from R1-R4 *and* Not applicable
from R8, so Pellet reports the whole ontology inconsistent and writes nothing - for every project, without saying where.
SWRL cannot express "no percentage" or "not excluded" (open world, no negation), so the check is made here, on the
data as entered, with a message that names the entry. The OWL axioms stay as the last safety net (TEST_CASES TC-OWL-03).

Checks (each finding is an error):
  V1  a project excludes a work product (excludesWorkProduct) but an assessment result of that work product has a percentage
  V2  a percentage outside 0-100 (it would match no rating range)
  V3  an assessment result with more than one percentage (it would get two ratings)

Usage:  python tests/validate_data.py <file.ttl> [<file.ttl> ...]      exit code 0 = no problem, 1 = problems found
Only the Python standard library is used (a small Turtle reader for the subset written by Protege / the round scripts).
"""
import re
import sys

ISO = 'http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#'
RDF_TYPE = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'
TOKEN = re.compile(r'''\s+|#[^\n]*|(<[^>]*>)|("""[\s\S]*?"""|"(?:[^"\\]|\\.)*")(\^\^(?:<[^>]*>|[\w\-]*:[\w\-.]*)|@[\w\-]+)?|(@prefix|@base)|([\[\]\(\);,.])|((?:[\w\-]*):(?:[\w\-.%/#]*[\w\-%/#])?|a|true|false|[+-]?\d+(?:\.\d+)?)''')


def parse_turtle(src):
    """Returns a list of (s, p, o); literals are ('LIT', lexical form)."""
    toks, pos = [], 0
    while pos < len(src):
        m = TOKEN.match(src, pos)
        if not m:
            raise ValueError('Turtle syntax error at line %d' % (src[:pos].count('\n') + 1))
        pos = m.end()
        if m.group(1):
            toks.append(('I', m.group(1)[1:-1]))
        elif m.group(2):
            toks.append(('L', m.group(2)))
        elif m.group(4):
            toks.append(('K', m.group(4)))
        elif m.group(5):
            toks.append(('P', m.group(5)))
        elif m.group(6):
            toks.append(('N', m.group(6)))
    pref, triples, state = {}, [], {'i': 0, 'bn': 0, 'base': ''}

    def blank():
        state['bn'] += 1
        return '_:b%d' % state['bn']

    def term():
        t = toks[state['i']]
        state['i'] += 1
        if t[0] == 'I':
            return t[1] if ':' in t[1] else state['base'] + t[1]
        if t[0] == 'L':
            return ('LIT', t[1].strip('"'))
        if t[0] == 'N':
            s = t[1]
            if s == 'a':
                return RDF_TYPE
            if s in ('true', 'false') or re.match(r'[+-]?\d', s):
                return ('LIT', s)
            p, local = s.split(':', 1)
            return pref[p] + local
        if t == ('P', '['):
            b = blank()
            if toks[state['i']] == ('P', ']'):
                state['i'] += 1
                return b
            plist(b)
            state['i'] += 1
            return b
        if t == ('P', '('):
            items = []
            while toks[state['i']] != ('P', ')'):
                items.append(term())
            state['i'] += 1
            return blank() if items else 'nil'
        raise ValueError('unexpected token %r' % (t,))

    def plist(s):
        while True:
            p = term()
            while True:
                triples.append((s, p, term()))
                if toks[state['i']] == ('P', ','):
                    state['i'] += 1
                    continue
                break
            if toks[state['i']] == ('P', ';'):
                state['i'] += 1
                if toks[state['i']] in (('P', '.'), ('P', ']')):
                    return
                continue
            return

    while state['i'] < len(toks):
        t = toks[state['i']]
        if t[0] == 'K':
            if t[1] == '@prefix':
                pref[toks[state['i'] + 1][1].rstrip(':')] = toks[state['i'] + 2][1]
                state['i'] += 4
            else:
                state['base'] = toks[state['i'] + 1][1]
                state['i'] += 3
            continue
        s = term()
        if toks[state['i']] != ('P', '.'):
            plist(s)
        state['i'] += 1
    return triples


def validate(triples):
    local = lambda x: x[len(ISO):] if isinstance(x, str) and x.startswith(ISO) else x
    sp = {}
    for s, p, o in triples:
        sp.setdefault(local(s), {}).setdefault(local(p), []).append(local(o))
    ae_of_project = {}
    for s, props in sp.items():
        for pj in props.get('responsibleFor', []):
            ae_of_project.setdefault(pj, set()).add(s)
        for ae in props.get('isResponsibilityOf', []):
            ae_of_project.setdefault(s, set()).add(ae)
    errors = []
    for pj, props in sorted(sp.items()):
        for wp in props.get('excludesWorkProduct', []):
            for ae in sorted(ae_of_project.get(pj, ())):
                if wp not in sp[ae].get('hasEntity', []):
                    continue
                for ar in sorted(sp[ae].get('hasAssessmentResult', [])):
                    pct = sp.get(ar, {}).get('hasPercentageAchievement', [])
                    if pct:
                        errors.append('V1 %s excludes %s, but %s has a percentage (%s): remove the percentage or the exclusion'
                                      % (pj, wp, ar, ', '.join(v[1] for v in pct)))
    for ar, props in sorted(sp.items()):
        pct = props.get('hasPercentageAchievement', [])
        for v in pct:
            try:
                ok = 0 <= float(v[1]) <= 100
            except (TypeError, ValueError):
                ok = False
            if not ok:
                errors.append('V2 %s has the percentage %s, outside 0-100' % (ar, v[1] if isinstance(v, tuple) else v))
        if len(pct) > 1:
            errors.append('V3 %s has %d percentages (%s): keep one' % (ar, len(pct), ', '.join(v[1] for v in pct)))
    return errors


def main(paths):
    triples = []
    for path in paths:
        triples += parse_turtle(open(path, encoding='utf-8').read())
    errors = validate(triples)
    for e in errors:
        print(e)
    print('%d problem(s) in the assessment data' % len(errors))
    return 1 if errors else 0


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1:]))
