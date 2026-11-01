#!/usr/bin/env python3
"""Re-evaluate the 34 original competency questions against the PARAMETER-BASED search.

For each CQ (evaluation/cq_mapping.json): parameters -> the same SPARQL template the backend runs
(backend/src/main/resources/sparql/knowledge-search.rq) -> retrieved Task resources -> compare with the
expected Task resources -> TP/FP/FN, Precision, Recall, F1.  Data = asserted knowledge only
(kb/build/reasoning-input.ttl, i.e. the Fuseki default graph).  Undefined ratios are reported as null
and excluded from macro averages (counts of excluded CQs are reported).
"""
import csv, json, re, sys
from pathlib import Path
import rdflib
from rdflib import Literal, URIRef

ROOT = Path(__file__).resolve().parents[1]
ISO = "http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#"
TEMPLATE = (ROOT / "backend/src/main/resources/sparql/knowledge-search.rq").read_text(encoding="utf-8")
PARAM_VAR = {"process": "pProcess", "activity": "pActivity", "role": "pRole", "workProduct": "pWorkProduct",
             "state": "pState", "keyword": "pKeyword"}


def render(template: str, params: dict) -> str:
    """Keep '#BEGIN name ... #END' blocks only for supplied parameters (mirrors SparqlTemplate.java)."""
    out, keep = [], True
    for line in template.splitlines():
        m = re.match(r"\s*#BEGIN\s+(\w+)", line)
        if m:
            keep = m.group(1) in params; continue
        if re.match(r"\s*#END", line):
            keep = True; continue
        if keep:
            out.append(line)
    return "\n".join(out)


def to_term(name: str, value: str):
    if name == "process": return URIRef(ISO + value)
    if name == "activity": return URIRef(ISO + value.replace(".", ".ACT."))   # PM.04 -> PM.ACT.04
    if name in ("workProduct", "state"): return URIRef(ISO + value)
    return Literal(value)


def search(g, params):
    q = render(TEMPLATE, params)
    bindings = {PARAM_VAR[k]: to_term(k, v) for k, v in params.items()}
    return [str(r.taskID) for r in g.query(q, initBindings=bindings)]


def ratio(a, b):
    return None if b == 0 else a / b


def main():
    g = rdflib.Graph(); g.parse(ROOT / "kb/build/reasoning-input.ttl", format="turtle")
    task_ids = {str(o) for o in g.objects(None, URIRef(ISO + "hasTaskID"))}
    mapping = json.loads((ROOT / "evaluation/cq_mapping.json").read_text(encoding="utf-8"))
    rows = []
    for cq in mapping:
        missing = [t for t in cq["expectedTaskIDs"] if t not in task_ids]
        if missing: sys.exit(f"{cq['id']}: expected IDs not in ontology: {missing}")
        exp, ret = set(cq["expectedTaskIDs"]), search(g, cq["parameters"])
        rset = set(ret)
        tp, fp, fn = len(exp & rset), len(rset - exp), len(exp - rset)
        p, r = ratio(tp, tp + fp), ratio(tp, tp + fn)
        f1 = None if p is None or r is None else (0.0 if p + r == 0 else 2 * p * r / (p + r))
        rows.append({**{k: cq[k] for k in ("id", "group", "answerability")}, "parameters": cq["parameters"],
                     "expected": sorted(exp), "retrieved": sorted(rset), "TP": tp, "FP": fp, "FN": fn,
                     "precision": p, "recall": r, "f1": f1, "falsePositives": sorted(rset - exp), "missed": sorted(exp - rset)})
    def macro(key, subset=rows):
        vals = [x[key] for x in subset if x[key] is not None]
        return (sum(vals) / len(vals) if vals else None, len(subset) - len(vals))
    TP, FP, FN = (sum(x[k] for x in rows) for k in ("TP", "FP", "FN"))
    mp, mr = TP / (TP + FP), TP / (TP + FN)
    summary = {"questions": len(rows),
               "macro": {k: macro(k)[0] for k in ("precision", "recall", "f1")},
               "macroUndefinedExcluded": {k: macro(k)[1] for k in ("precision", "recall", "f1")},
               "micro": {"precision": mp, "recall": mr, "f1": 2 * mp * mr / (mp + mr), "TP": TP, "FP": FP, "FN": FN},
               "byAnswerability": {a: {"count": len([x for x in rows if x["answerability"] == a]),
                                       **{k: macro(k, [x for x in rows if x["answerability"] == a])[0] for k in ("precision", "recall", "f1")}}
                                   for a in sorted({x["answerability"] for x in rows})}}
    out = ROOT / "evaluation" / "results"; out.mkdir(exist_ok=True)
    (out / "cq_results.json").write_text(json.dumps({"summary": summary, "perQuestion": rows}, indent=1), encoding="utf-8")
    fmt = lambda v: "n/a" if v is None else f"{v:.2f}"
    with open(out / "cq_results.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["CQ", "Group", "Answerability", "Parameters", "Expected", "Retrieved", "TP", "FP", "FN", "Precision", "Recall", "F1", "False positives", "Missed"])
        for x in rows:
            w.writerow([x["id"], x["group"], x["answerability"], json.dumps(x["parameters"]), len(x["expected"]), len(x["retrieved"]),
                        x["TP"], x["FP"], x["FN"], fmt(x["precision"]), fmt(x["recall"]), fmt(x["f1"]), " ".join(x["falsePositives"]), " ".join(x["missed"])])
    for x in rows:
        print(f"{x['id']:4s} exp={len(x['expected'])} ret={len(x['retrieved']):2d} TP={x['TP']} FP={x['FP']:2d} FN={x['FN']} "
              f"P={fmt(x['precision'])} R={fmt(x['recall'])} F1={fmt(x['f1'])} FP:{' '.join(x['falsePositives'])[:60]} MISS:{' '.join(x['missed'])}")
    print(json.dumps(summary, indent=1))

if __name__ == "__main__":
    main()
