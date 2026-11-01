#!/usr/bin/env python3
"""Reference implementation of the backend JSON contract (ApiController) over kb/build/dataset.trig.

It executes the SAME SPARQL files as the Java services and shapes responses the same way.  Used to
  1. test Workflow A (search) and Workflow B (assessment -> inferred recommendations) end-to-end at the
     data level without Fuseki (not available in the build sandbox), and
  2. produce frontend/src/__tests__/fixtures.json for the React UI tests.
It is a test double, not production code: the production path is Spring Boot -> Fuseki.
"""
import json, sys
from pathlib import Path
import rdflib
from rdflib import URIRef, Literal

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "evaluation"))
from run_cq_evaluation import render  # same block renderer as SparqlTemplate.java

ISO = "http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#"
SPARQL = ROOT / "backend/src/main/resources/sparql"
DS = rdflib.Dataset(); DS.parse(ROOT / "kb/build/dataset.trig", format="trig")


def q(name, iris=None, lits=None, params=None):
    text = (SPARQL / name).read_text(encoding="utf-8")
    if params is not None:
        text = render(text, params)
    b = {k: URIRef(v) for k, v in (iris or {}).items()}
    b.update({k: Literal(v) for k, v in (lits or {}).items()})
    rows = []
    for r in DS.query(text, initBindings=b):
        rows.append({k: str(v) for k, v in r.asdict().items() if v is not None})
    return rows


local = lambda iri: iri.split("#", 1)[1] if iri else iri


def companies():
    return [{"id": local(r["enterprise"]), "name": r["name"], "profile": r.get("profile", "")} for r in q("companies.rq")]


def assessment(cid):
    o = q("company-overview.rq", {"pEnterprise": ISO + cid})[0]
    ae = o["assessedEntity"]
    results, counts = [], {}
    for r in q("assessment-results.rq", {"pAssessedEntity": ae}):
        r["bpID"] = r["bpID"].strip()
        r["rating"] = local(r["rating"]) if "rating" in r else "Not_rated"
        r.setdefault("ratingSource", "none")
        counts[r["rating"]] = counts.get(r["rating"], 0) + 1
        results.append(r)
    recs = []
    for r in q("recommendations.rq", {"pAssessedEntity": ae}):
        r["bpID"] = r["bpID"].strip(); r["rating"] = local(r["rating"])
        r["necessaryRules"] = r.get("necessaryRules", "").split("|")
        recs.append(r)
    pick = lambda *ks: {k: o.get(k) for k in ks}
    return {"company": {"id": cid, "name": o["name"], "profile": o.get("profile", "")},
            "project": pick("project", "projectName", "projectDescription", "start", "end", "staff"),
            "assessment": pick("assessment", "assessmentDate", "consultantName", "assessedEntity"),
            "ratingCounts": counts, "results": results, "recommendations": recs}


def options():
    out = {k: [] for k in ("process", "activity", "role", "workProduct", "state")}
    for r in q("search-options.rq"):
        o = {"value": r["value"], "label": r["label"]}
        if "parent" in r: o["process"] = r["parent"]
        out[r["type"]].append(o)
    return out


def search(params):
    iris = {"pProcess", "pActivity", "pWorkProduct", "pState"}
    var = {"process": "pProcess", "activity": "pActivity", "role": "pRole", "workProduct": "pWorkProduct", "state": "pState", "keyword": "pKeyword"}
    b_i = {var[k]: v for k, v in params.items() if var[k] in iris}
    b_l = {var[k]: v for k, v in params.items() if var[k] not in iris}
    return {"parameters": params, "query": "knowledge-search.rq", "results": q("knowledge-search.rq", b_i, b_l, params)}


def task_details(iri):
    rows = q("task-details.rq", {"pTask": iri})
    seen, bps = set(), []
    for r in rows:
        if r.get("kind") == "basePractice" and r["bpID"] not in seen:
            seen.add(r["bpID"]); bps.append(r)
    return {"task": iri, "inputs": [r for r in rows if r.get("kind") == "input"],
            "outputs": [r for r in rows if r.get("kind") == "output"], "relatedBasePractices": bps}


if __name__ == "__main__":
    fixtures = {"companies": companies(), "assessments": {c["id"]: assessment(c["id"]) for c in companies()},
                "status": q("reasoning-status.rq")[0], "options": options()}
    example = {"role": "PR", "keyword": "defect"}            # = CQ Q18 parameters
    fixtures["searchExample"] = {"request": example, "response": search(example)}
    fixtures["taskDetailsExample"] = task_details(fixtures["searchExample"]["response"]["results"][0]["task"])
    out = ROOT / "frontend/src/__tests__/fixtures.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(fixtures, indent=1), encoding="utf-8")
    for cid, a in fixtures["assessments"].items():
        print(cid, a["company"]["name"], "results", len(a["results"]), "recommendations", len(a["recommendations"]), a["ratingCounts"])
    print("search", example, "->", [r["taskID"] for r in fixtures["searchExample"]["response"]["results"]])
    print("task details", {k: len(v) for k, v in fixtures["taskDetailsExample"].items() if isinstance(v, list)})
