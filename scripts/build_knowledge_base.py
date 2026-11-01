#!/usr/bin/env python3
"""Build the knowledge base that Fuseki serves.

Pipeline (each step is reproducible and writes its output under kb/build/):

 1. MERGE   ontology/ISO29110V2.ttl (baseline, never modified)
            - remove the superseded baseline rule "ImprovementRecommendationRule"
            - remove the hand-asserted placeholder recommendation  AE.01 recommend RP.01
            + ontology/extensions/assessment-recommendation-extension.ttl
            -> kb/build/ontology-merged.ttl
 2. REASON  run Pellet (reasoner/SwrlReasoningRunner) over merged ontology + data/*.ttl
            * full run (all rules)              -> inferred-full.nt
            * run with NO rules                 -> inferred-owl-only.nt
            * one run per rule with it disabled -> ablation/<rule>.nt
 3. SPLIT   inferred-swrl = full - owl-only   (facts that exist ONLY because rules fired)
            For every SWRL fact, "necessary rules" = rules whose removal makes it disappear.
 4. EXPLAIN build explanation records for each inferred recommend(?ae, ?rp)
 5. PACKAGE kb/build/dataset.trig  (default graph = asserted knowledge,
                                    named graphs = inferred-swrl / inferred-owl / explanation)

Usage:  python scripts/build_knowledge_base.py [--java java] [--exclude-rule LABEL ...]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

import rdflib
from rdflib import BNode, Literal, Namespace, RDF, RDFS, URIRef, XSD
from rdflib.collection import Collection

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "kb" / "build"
ISO = Namespace("http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#")
APP = Namespace("http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2-app#")
SWRL = Namespace("http://www.w3.org/2003/11/swrl#")

G_INF_SWRL = URIRef("urn:iso29110:graph:inferred-swrl")
G_INF_OWL = URIRef("urn:iso29110:graph:inferred-owl")
G_EXPLAIN = URIRef("urn:iso29110:graph:explanation")

BASELINE = ROOT / "ontology" / "ISO29110V2.ttl"
EXTENSION = ROOT / "ontology" / "extensions" / "assessment-recommendation-extension.ttl"
DATA = [ROOT / "data" / "qa-practices.ttl", ROOT / "data" / "mock-companies.ttl"]
SUPERSEDED_RULES = {"ImprovementRecommendationRule"}  # baseline versions replaced by the extension


# ----------------------------------------------------------------------------- helpers
def bnode_closure(g: rdflib.Graph, start) -> set:
    """All triples reachable from a blank node through blank nodes (a SWRL rule tree)."""
    triples, todo, seen = set(), [start], set()
    while todo:
        n = todo.pop()
        if n in seen:
            continue
        seen.add(n)
        for p, o in g.predicate_objects(n):
            triples.add((n, p, o))
            if isinstance(o, BNode):
                todo.append(o)
    return triples


def rule_label(g, imp) -> str:
    return str(g.value(imp, RDFS.label) or "UNLABELLED")


def rule_head_predicates(g, imp) -> list[str]:
    preds = []
    for atom in Collection(g, g.value(imp, SWRL.head)):
        p = g.value(atom, SWRL.propertyPredicate) or g.value(atom, SWRL.classPredicate)
        preds.append(str(p))
    return preds


def read_nt(path: Path) -> set:
    g = rdflib.Graph()
    if path.stat().st_size:
        g.parse(path, format="nt")
    return set(g)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_reasoner(java: str, inputs: list[Path], out: Path, report: Path, exclude=(), no_rules=False) -> dict:
    cp = f"{ROOT/'reasoner'/'lib'}/*{':' if sys.platform != 'win32' else ';'}{ROOT/'reasoner'/'build'}"
    cmd = [java, "-Xmx2g", "-cp", cp, "iso29110.reasoning.SwrlReasoningRunner"]
    for i in inputs:
        cmd += ["--in", str(i)]
    for r in exclude:
        cmd += ["--exclude-rule", r]
    if no_rules:
        cmd.append("--no-rules")
    cmd += ["--out-inferred", str(out), "--report", str(report)]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        sys.stderr.write(proc.stdout + proc.stderr)
        raise SystemExit(f"Reasoner failed (exit {proc.returncode}). Is the ontology consistent?")
    return json.loads(report.read_text())


# ----------------------------------------------------------------------------- pipeline
def merge(extra_excluded: set[str]) -> tuple[Path, dict]:
    g = rdflib.Graph()
    g.parse(BASELINE, format="turtle")
    removed_rules = []
    for imp in list(g.subjects(RDF.type, SWRL.Imp)):
        if rule_label(g, imp) in SUPERSEDED_RULES:
            for t in bnode_closure(g, imp):
                g.remove(t)
            removed_rules.append(rule_label(g, imp) or "ImprovementRecommendationRule")
    placeholder = [(ISO["AE.01"], ISO.recommend, ISO["RP.01"]), (ISO["RP.01"], ISO.recommendedBy, ISO["AE.01"])]
    removed_assertions = [t for t in placeholder if t in g]
    for t in removed_assertions:
        g.remove(t)
    g.parse(EXTENSION, format="turtle")
    out = BUILD / "ontology-merged.ttl"
    g.serialize(out, format="turtle")
    rules = {}
    for imp in g.subjects(RDF.type, SWRL.Imp):
        rules[rule_label(g, imp)] = {"head": rule_head_predicates(g, imp),
                                     "source": "extension" if (imp, RDFS.comment, None) in g and "2026-11" in str(g.value(imp, RDFS.comment)) else "baseline"}
    info = {"supersededBaselineRules": sorted(SUPERSEDED_RULES), "removedPlaceholderAssertions":
            [[str(s), str(p), str(o)] for s, p, o in removed_assertions], "rules": rules,
            "excludedByUser": sorted(extra_excluded)}
    return out, info


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--java", default="java")
    ap.add_argument("--exclude-rule", action="append", default=[], help="disable a rule (research option)")
    args = ap.parse_args()
    BUILD.mkdir(parents=True, exist_ok=True)
    (BUILD / "ablation").mkdir(exist_ok=True)
    excluded = set(args.exclude_rule)

    merged, merge_info = merge(excluded)
    inputs = [merged] + DATA
    # The reasoner MUST receive ONE document. OWL API parses each document on its own: in a data-only file
    # the ISO properties are undeclared, so values such as hasPercentageAchievement are read as annotations
    # and no SWRL rule can match them (observed: 0 SWRL inferences with separate files, 70+ with one file).
    combined = rdflib.Graph()
    for p in inputs:
        combined.parse(p, format="turtle")
    reasoning_input = BUILD / "reasoning-input.ttl"
    combined.serialize(reasoning_input, format="turtle")
    print(f"[1/5] merged ontology -> {merged.relative_to(ROOT)}  rules={sorted(merge_info['rules'])}")

    full_rep = run_reasoner(args.java, [reasoning_input], BUILD / "inferred-full.nt", BUILD / "report-full.json", exclude=excluded)
    owl_rep = run_reasoner(args.java, [reasoning_input], BUILD / "inferred-owl-only.nt", BUILD / "report-owl-only.json", no_rules=True)
    full, owl_only = read_nt(BUILD / "inferred-full.nt"), read_nt(BUILD / "inferred-owl-only.nt")
    swrl = full - owl_only
    print(f"[2/5] Pellet: consistent={full_rep['consistent']} full={len(full)} owl-only={len(owl_only)} swrl-dependent={len(swrl)}")

    enabled = [r for r in sorted(merge_info["rules"]) if r not in excluded]
    necessary: dict[tuple, list[str]] = {t: [] for t in swrl}
    rule_effect = {}
    for r in enabled:
        safe = r.replace("/", "_")
        run_reasoner(args.java, [reasoning_input], BUILD / "ablation" / f"{safe}.nt", BUILD / "ablation" / f"{safe}.json",
                     exclude=excluded | {r})
        without = read_nt(BUILD / "ablation" / f"{safe}.nt")
        lost = swrl - without
        rule_effect[r] = len(lost)
        for t in lost:
            necessary[t].append(r)
    print(f"[3/5] ablation: facts that disappear when each rule is disabled = {rule_effect}")

    # ------------------------------------------------------------------ explanations
    asserted = rdflib.Graph()
    for p in inputs:
        asserted.parse(p, format="turtle")
    union = rdflib.Graph()
    for t in asserted:
        union.add(t)
    for t in full:
        union.add(t)
    run_iri = APP[f"run-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}"]
    ex = rdflib.Graph()
    ex.bind("app", APP); ex.bind("iso", ISO)
    ex.add((run_iri, RDF.type, APP.ReasoningRun))
    ex.add((run_iri, APP.reasoner, Literal(full_rep["reasoner"])))
    ex.add((run_iri, APP.runAt, Literal(full_rep["runAt"], datatype=XSD.dateTime)))
    ex.add((run_iri, APP.consistent, Literal(full_rep["consistent"])))
    ex.add((run_iri, APP.swrlInferredCount, Literal(len(swrl))))
    for r in enabled:
        ex.add((run_iri, APP.enabledRule, Literal(r)))
    for r in sorted(excluded):
        ex.add((run_iri, APP.disabledRule, Literal(r)))
    for path in inputs:
        ex.add((run_iri, APP.inputSha256, Literal(f"{path.name}:{sha256(path)}")))

    # one provenance record per SWRL-dependent fact (rule attribution from ablation)
    stmt_iri = {}
    for i, (s, p, o) in enumerate(sorted(swrl)):
        st = APP[f"stmt-{hashlib.sha1(f'{s}{p}{o}'.encode()).hexdigest()[:12]}"]
        stmt_iri[(s, p, o)] = st
        ex.add((st, RDF.type, APP.InferredStatement))
        ex.add((st, RDF.subject, s)); ex.add((st, RDF.predicate, p)); ex.add((st, RDF.object, o))
        ex.add((st, APP.inReasoningRun, run_iri))
        for r in necessary[(s, p, o)]:
            ex.add((st, APP.necessaryRule, Literal(r)))
            if str(p) in merge_info["rules"].get(r, {}).get("head", []):
                ex.add((st, APP.concludedByRule, Literal(r)))   # rule whose head produces this predicate

    q = """PREFIX iso: <%s>
    SELECT ?ar ?bp ?rating ?pct WHERE {
      ?ae iso:hasAssessmentResult ?ar . ?ar iso:assessesBasePractice ?bp ; iso:hasDegreeAchievement ?rating .
      ?rp iso:addressesBasePractice ?bp . OPTIONAL { ?ar iso:hasPercentageAchievement ?pct } }""" % ISO
    n_exp = 0
    for (s, p, o) in sorted(swrl):
        if p != ISO.recommend:
            continue
        ae, rp = s, o
        for row in union.query(q, initBindings={"ae": ae, "rp": rp}):
            ar, bp, rating, pct = row
            rating_stmt = stmt_iri.get((ar, ISO.hasDegreeAchievement, rating))
            if rating_stmt is None:
                continue  # rating asserted, not inferred -> this binding is not a rule-derived path
            exp = APP[f"exp-{str(ae).split('#')[1]}-{str(rp).split('#')[1]}"]
            ex.add((exp, RDF.type, APP.RecommendationExplanation))
            ex.add((exp, APP.assessedEntity, ae))
            ex.add((exp, APP.recommendedPractice, rp))
            ex.add((exp, APP.triggeringResult, ar))
            ex.add((exp, APP.basePractice, bp))
            ex.add((exp, APP.inferredRating, rating))
            if pct is not None:
                ex.add((exp, APP.percentageAchievement, pct))
            ex.add((exp, APP.ratingInference, rating_stmt))
            ex.add((exp, APP.recommendationInference, stmt_iri[(s, p, o)]))
            ex.add((exp, APP.inReasoningRun, run_iri))
            ex.add((exp, APP.matchMethod, Literal(
                "Antecedent bindings reconstructed by SPARQL over asserted+inferred data; "
                "rule attribution from Pellet ablation runs.")))
            n_exp += 1
    print(f"[4/5] explanations: {n_exp} recommendation explanations, {len(swrl)} inferred-statement records")

    # ------------------------------------------------------------------ package
    ds = rdflib.Dataset()
    ds.bind("iso", ISO); ds.bind("app", APP)
    default = ds.default_graph
    for t in asserted:
        default.add(t)
    gs = ds.graph(G_INF_SWRL)
    for t in swrl:
        gs.add(t)
    go = ds.graph(G_INF_OWL)
    for t in owl_only:
        go.add(t)
    ge = ds.graph(G_EXPLAIN)
    for t in ex:
        ge.add(t)
    ds.serialize(BUILD / "dataset.trig", format="trig")
    ex.serialize(BUILD / "explanations.ttl", format="turtle")

    report = {
        "builtAt": datetime.now(timezone.utc).isoformat(),
        "reasoner": full_rep["reasoner"], "consistent": full_rep["consistent"],
        "inputs": {p.name: sha256(p) for p in inputs},
        "merge": merge_info,
        "counts": {"asserted": len(asserted), "inferredFull": len(full), "inferredOwlOnly": len(owl_only),
                   "inferredSwrl": len(swrl), "recommendationExplanations": n_exp},
        "ruleAblation_factsLostWhenDisabled": rule_effect,
        "graphs": {"asserted": "default graph", "inferredSwrl": str(G_INF_SWRL),
                   "inferredOwl": str(G_INF_OWL), "explanation": str(G_EXPLAIN)},
    }
    (BUILD / "reasoning-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"[5/5] dataset -> {(BUILD / 'dataset.trig').relative_to(ROOT)}  report -> kb/build/reasoning-report.json")


if __name__ == "__main__":
    main()
