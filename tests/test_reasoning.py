#!/usr/bin/env python3
"""Reasoning tests: every check runs the real Pellet reasoner (reasoner/SwrlReasoningRunner).

  T1  positive fixtures  - each rule's antecedent is satisfied -> expected triple is inferred
  T2  necessity          - the same run with ONE rule disabled -> its triple disappears
  T3  negative fixtures  - near-miss data -> nothing is inferred
  T4  original rule      - baseline ImprovementRecommendationRule over-recommends (cartesian product)
  T5  company isolation  - reasoning each company alone gives the same recommendations as the joint run
  T6  baseline           - the untouched baseline ontology yields 0 SWRL-dependent facts

Run:  python tests/test_reasoning.py      (requires kb/build/ontology-merged.ttl -> run build first)
Writes tests/results/reasoning-tests.json and exits non-zero on any failure.
"""
from __future__ import annotations
import json, subprocess, sys, tempfile
from pathlib import Path
import rdflib
from rdflib import Literal, Namespace, RDF, OWL, XSD

ROOT = Path(__file__).resolve().parents[1]
ISO = Namespace("http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#")
MERGED = ROOT / "kb" / "build" / "ontology-merged.ttl"
DATA = [ROOT / "data" / "qa-practices.ttl", ROOT / "data" / "mock-companies.ttl"]
SEP = ";" if sys.platform == "win32" else ":"
RESULTS = []


def reason(graphs, exclude=(), no_rules=False):
    """Combine graphs into ONE document, run Pellet, return the set of inferred triples."""
    g = rdflib.Graph()
    for x in graphs:
        if isinstance(x, rdflib.Graph):
            for t in x: g.add(t)
        else:
            g.parse(x, format="turtle")
    with tempfile.TemporaryDirectory() as d:
        src, out, rep = Path(d) / "in.ttl", Path(d) / "out.nt", Path(d) / "rep.json"
        g.serialize(src, format="turtle")
        cmd = ["java", "-Xmx2g", "-cp", f"{ROOT/'reasoner'/'lib'}/*{SEP}{ROOT/'reasoner'/'build'}",
               "iso29110.reasoning.SwrlReasoningRunner", "--in", str(src), "--out-inferred", str(out), "--report", str(rep)]
        for r in exclude: cmd += ["--exclude-rule", r]
        if no_rules: cmd.append("--no-rules")
        p = subprocess.run(cmd, capture_output=True, text=True)
        if p.returncode != 0:
            raise RuntimeError(p.stdout + p.stderr)
        res = rdflib.Graph()
        if out.stat().st_size: res.parse(out, format="nt")
        return set(res)


def check(test_id, desc, ok, detail=""):
    RESULTS.append({"id": test_id, "description": desc, "passed": bool(ok), "detail": detail})
    print(f"{'PASS' if ok else 'FAIL'}  {test_id:28s} {desc}" + (f"  [{detail}]" if detail and not ok else ""))


def ar(g, name, pct=None, bp=None, ae=None):
    n = ISO[name]
    g.add((n, RDF.type, OWL.NamedIndividual)); g.add((n, RDF.type, ISO.AssessmentResult))
    if pct is not None: g.add((n, ISO.hasPercentageAchievement, Literal(pct, datatype=XSD.integer)))
    if bp: g.add((n, ISO.assessesBasePractice, ISO[bp]))
    if ae:
        g.add((ISO[ae], RDF.type, ISO.AssessedEntity)); g.add((ISO[ae], RDF.type, OWL.NamedIndividual))
        g.add((ISO[ae], ISO.hasAssessmentResult, n))
    return n


def main():
    tbox = rdflib.Graph(); tbox.parse(MERGED, format="turtle")
    qa = rdflib.Graph(); qa.parse(DATA[0], format="turtle")
    po_bp09 = next(tbox.objects(ISO["PM.BP.09"], ISO.hasProcessOutcome))

    # ------------------------------------------------------------------ positive fixtures
    pos = rdflib.Graph()
    boundaries = {0: "Not_achieved", 15: "Not_achieved", 16: "Partially_achieved", 50: "Partially_achieved",
                  51: "Largely_achieved", 90: "Largely_achieved", 91: "Completely_achieved", 100: "Completely_achieved"}
    for pct in boundaries: ar(pos, f"TEST-AR-{pct}", pct)
    ar(pos, "TEST-AR-N", 10, "PM.BP.09", "TEST-AE-1")
    ar(pos, "TEST-AR-P", 30, "SI.BP.08", "TEST-AE-1")
    owp = ISO["TEST-OWP-R3"]
    pos.add((owp, RDF.type, OWL.NamedIndividual)); pos.add((owp, RDF.type, ISO.OutputWorkProduct))
    pos.add((owp, ISO.hasProcessOutcome, po_bp09))
    for key, name in (("R7", "Requirement Specification (test)"), ("R8", "Test cases (test)")):
        w = ISO[f"TEST-OWP-{key}"]
        pos.add((w, RDF.type, OWL.NamedIndividual)); pos.add((w, RDF.type, ISO.OutputWorkProduct))
        pos.add((w, ISO.hasOutputWorkProductName, Literal(name)))
        pos.add((w, ISO.hasFinalState, ISO["WPS.02"]))          # WPS.02 = "Approved"
        pos.add((w, ISO.isEntityOf, ISO["TEST-AE-2"]))
        pos.add((ISO["TEST-AE-2"], RDF.type, OWL.NamedIndividual))

    rule_for = {"Not_achieved": "RatingNotAchievedRule", "Partially_achieved": "RatingPartiallyAchievedRule",
                "Largely_achieved": "RatingLargelyAchievedRule", "Completely_achieved": "RatingCompletelyAchievedRule"}
    cases = []  # (test id, triple, rule expected to be necessary or None)
    for pct, rating in boundaries.items():
        cases.append((f"rating-{pct}%->{rating}", (ISO[f"TEST-AR-{pct}"], ISO.hasDegreeAchievement, ISO[rating]), rule_for[rating]))
    cases.append(("recommend-not-achieved", (ISO["TEST-AE-1"], ISO.recommend, ISO["RP-PM.BP.09"]), "ImprovementRecommendationRule"))
    cases.append(("recommend-partial", (ISO["TEST-AE-1"], ISO.recommend, ISO["RP-SI.BP.08"]), "PartialImprovementRecommendationRule"))
    cases.append(("R3-owp-linked-to-bp", (owp, ISO.isOutputWorkProductOf, ISO["PM.BP.09"]), "BasePracticeOutputWPRule"))
    cases.append(("R7-hasEntity", (ISO["TEST-AE-2"], ISO.hasEntity, ISO["TEST-OWP-R7"]), None))
    cases.append(("R8-hasEntity", (ISO["TEST-AE-2"], ISO.hasEntity, ISO["TEST-OWP-R8"]), None))

    inferred = reason([tbox, qa, pos])
    for tid, t, _ in cases:
        check(f"T1:{tid}", "triple inferred with all rules enabled", t in inferred)
    recs = {o for s, p, o in inferred if s == ISO["TEST-AE-1"] and p == ISO.recommend}
    check("T1:recommend-specific", "only the 2 practices for the 2 gaps are recommended",
          recs == {ISO["RP-PM.BP.09"], ISO["RP-SI.BP.08"]}, str(sorted(str(r).split('#')[1] for r in recs)))

    # ------------------------------------------------------------------ necessity per rule
    for rule in sorted({r for _, _, r in cases if r}):
        without = reason([tbox, qa, pos], exclude=[rule])
        for tid, t, r in cases:
            if r == rule:
                check(f"T2:{tid}", f"disappears when {rule} is disabled", t not in without)
    no_rules = reason([tbox, qa, pos], no_rules=True)
    for key in ("R7", "R8"):
        t = (ISO["TEST-AE-2"], ISO.hasEntity, ISO[f"TEST-OWP-{key}"])
        check(f"T2:{key}-is-redundant", "hasEntity is ALSO inferred with NO rules (rule adds no knowledge: consequent = inverse of premise)",
              t in no_rules)
    # ------------------------------------------------------------------ negative fixtures
    neg = rdflib.Graph()
    ar(neg, "TEST-NEG-NOPCT", None, "PM.BP.09", "TEST-AE-N1")       # no percentage -> no rating, no recommendation
    ar(neg, "TEST-NEG-LARGE", 70, "PM.BP.10", "TEST-AE-N1")         # Largely -> no recommendation
    ar(neg, "TEST-NEG-NOBP", 5, None, "TEST-AE-N2")                  # Not_achieved but no Base Practice link
    ar(neg, "TEST-NEG-NOAE", 5, "SI.BP.02")                          # result not attached to any assessed entity
    inf_neg = reason([tbox, qa, neg])
    check("T3:no-percentage", "no rating inferred without a percentage",
          not any(s == ISO["TEST-NEG-NOPCT"] and p == ISO.hasDegreeAchievement for s, p, o in inf_neg))
    check("T3:largely-no-rec", "Largely achieved result -> no recommendation",
          not any(s == ISO["TEST-AE-N1"] and p == ISO.recommend for s, p, o in inf_neg))
    check("T3:no-bp-no-rec", "Not achieved but no assessesBasePractice -> no recommendation",
          not any(s == ISO["TEST-AE-N2"] and p == ISO.recommend for s, p, o in inf_neg))
    check("T3:no-ae-no-rec", "result without assessed entity -> rating inferred, but no recommendation",
          (ISO["TEST-NEG-NOAE"], ISO.hasDegreeAchievement, ISO.Not_achieved) in inf_neg
          and not any(p == ISO.recommend and o == ISO["RP-SI.BP.02"] for s, p, o in inf_neg))

    # ------------------------------------------------------------------ original (baseline) rule
    base = rdflib.Graph(); base.parse(ROOT / "ontology" / "ISO29110V2.ttl", format="turtle")
    one = rdflib.Graph()
    ar(one, "TEST-ORIG-AR", None, None, "TEST-ORIG-AE")
    one.add((ISO["TEST-ORIG-AR"], ISO.hasDegreeAchievement, ISO.Not_achieved))
    inf_orig = reason([base, qa, one])
    n = len({o for s, p, o in inf_orig if s == ISO["TEST-ORIG-AE"] and p == ISO.recommend})
    check("T4:original-rule-cartesian", "baseline rule recommends EVERY RecommendedPractice for one Not_achieved result",
          n >= 50, f"{n} practices recommended")

    # ------------------------------------------------------------------ company isolation
    comp = rdflib.Graph(); comp.parse(DATA[1], format="turtle")
    joint = reason([tbox, qa, comp])
    for key in "ABC":
        sub = rdflib.Graph()
        for s, p, o in comp:
            if f"-{key}1" in str(s) or str(s).endswith(f"ENT-{key}") or (str(s).endswith("CS-DEMO") and (p != ISO.preAssess or f"-{key}1" in str(o))):
                sub.add((s, p, o))
        alone = reason([tbox, qa, sub])
        ae = ISO[f"AE-{key}1"]
        r_joint = {o for s, p, o in joint if s == ae and p == ISO.recommend}
        r_alone = {o for s, p, o in alone if s == ae and p == ISO.recommend}
        check(f"T5:isolation-{key}", f"Company {key}: same recommendations alone and together ({len(r_joint)})", r_joint == r_alone)
        leak = {s for s, p, o in alone if p == ISO.recommend and s != ae}
        check(f"T5:no-leak-{key}", f"Company {key}: no recommendation for any other assessed entity", not leak)
    expected_counts = {"A": 0, "B": 4, "C": 9}
    for key, n in expected_counts.items():
        got = len({o for s, p, o in joint if s == ISO[f"AE-{key}1"] and p == ISO.recommend})
        check(f"T5:count-{key}", f"Company {key} receives {n} recommendations", got == n, f"got {got}")
    check("T5:B-missing-score", "Company B SI.BP.20 (no percentage) gets no rating",
          not any(s == ISO["AR-B1-SI.BP.20"] and p == ISO.hasDegreeAchievement for s, p, o in joint))

    # ------------------------------------------------------------------ baseline
    b_full, b_none = reason([base]), reason([base], no_rules=True)
    check("T6:baseline-zero-swrl", "baseline ontology alone: 0 SWRL-dependent facts", len(b_full - b_none) == 0,
          f"{len(b_full - b_none)} facts")

    out = ROOT / "tests" / "results"; out.mkdir(parents=True, exist_ok=True)
    (out / "reasoning-tests.json").write_text(json.dumps(RESULTS, indent=2), encoding="utf-8")
    failed = [r for r in RESULTS if not r["passed"]]
    print(f"\n{len(RESULTS) - len(failed)}/{len(RESULTS)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
