#!/usr/bin/env python3
"""Generate the demonstration ABox files.

  data/mock-companies.ttl  three SYNTHETIC companies (A, B, C) with project, pre-assessment,
                           assessed entity, assessment results and evidence.
  data/qa-practices.ttl    one RecommendedPractice per Base Practice (1:1, generated).

Design rules (see docs/MOCK_COMPANIES.md):
  * Only RAW assessment data is asserted: the percentage achievement per Base Practice.
    The rating (Not/Partially/Largely/Completely achieved) is NOT asserted; it must be
    inferred by the existing SWRL rating rules.  Recommendations are never asserted.
  * All companies are assessed on the same 12 Base Practices so results are comparable.
  * Everything uses the ontology's existing vocabulary, plus the 4 properties / 1 class of
    ontology/extensions/assessment-recommendation-extension.ttl.

Usage:  python scripts/generate_demo_data.py
"""
from pathlib import Path
import rdflib
from rdflib import Literal, Namespace, RDF, RDFS, OWL, XSD

ROOT = Path(__file__).resolve().parents[1]
ISO = Namespace("http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#")

# Base Practice assessed -> ISO work product the evidence is about (WP.xx individuals).
ASSESSED_BPS = {
    "PM.BP.09": "WP.10",  # risks documented                      -> Project Plan
    "PM.BP.10": "WP.10",  # version control strategy             -> Project Plan
    "PM.BP.13": "WP.23",  # verify & approve project plan        -> Verification Record
    "PM.BP.14": "WP.10",  # customer accepts project plan        -> Project Plan
    "PM.BP.16": "WP.09",  # monitor plan execution               -> Progress Status Record
    "PM.BP.25": "WP.01",  # formal completion / acceptance       -> Acceptance Record
    "SI.BP.02": "WP.13",  # verify requirements specification    -> Requirement Specification
    "SI.BP.03": "WP.22",  # validate requirements with customer  -> Validation Record
    "SI.BP.06": "WP.16",  # verify software design               -> Software Design
    "SI.BP.08": "WP.19",  # verify test cases & procedures       -> Test Cases and Test Procedures
    "SI.BP.17": "WP.20",  # integration tests + test report      -> Test Report
    "SI.BP.20": "WP.17",  # incorporate items into the baseline  -> Software Product
}

# Percentage achievement per Base Practice (None = not recorded -> no rating can be inferred).
# Boundary values (15/16, 50/51, 90/91) are used on purpose to test the rating rules.
COMPANIES = {
    "A": {
        "name": "Company A",
        "profile": "Synthetic demo company. Mature practices: every assessed Base Practice is Largely or Completely achieved. Expected to receive no improvement recommendation.",
        "project": ("Online Clinic Appointment System", "Web system for booking and managing clinic appointments.", "2026-03-02", "2026-09-30", 6),
        "date": "2026-09-14",
        "scores": {"PM.BP.09": 95, "PM.BP.10": 100, "PM.BP.13": 92, "PM.BP.14": 100, "PM.BP.16": 85, "PM.BP.25": 100,
                   "SI.BP.02": 91, "SI.BP.03": 96, "SI.BP.06": 90, "SI.BP.08": 93, "SI.BP.17": 88, "SI.BP.20": 100},
        "evidence": {
            "PM.BP.09": "Project plan has a maintained risk register with owners and mitigation actions.",
            "PM.BP.10": "Version control strategy documented in the project plan and followed in the repository.",
            "PM.BP.13": "Verification record shows the project plan was reviewed and approved by the project manager.",
            "PM.BP.14": "Customer signed the project plan acceptance.",
            "PM.BP.16": "Progress status records exist for most weeks; two weeks missing.",
            "PM.BP.25": "Acceptance record signed by the customer at project closure.",
            "SI.BP.02": "Requirements specification verified; verification record attached.",
            "SI.BP.03": "Validation record with customer sign-off available.",
            "SI.BP.06": "Software design verified; minor traceability gaps noted and corrected.",
            "SI.BP.08": "Test cases verified against requirements and design.",
            "SI.BP.17": "Integration tests executed and test report produced; one test run not documented.",
            "SI.BP.20": "Baseline contains test cases, software, traceability record and test report.",
        },
    },
    "B": {
        "name": "Company B",
        "profile": "Synthetic demo company. Mixed maturity: most practices achieved, a few clear gaps, and one result with no recorded percentage.",
        "project": ("Inventory Management Web Application", "Stock and supplier management for a small retailer.", "2026-04-01", "2026-10-15", 5),
        "date": "2026-09-21",
        "scores": {"PM.BP.09": 40, "PM.BP.10": 96, "PM.BP.13": 51, "PM.BP.14": 100, "PM.BP.16": 70, "PM.BP.25": 92,
                   "SI.BP.02": 50, "SI.BP.03": 80, "SI.BP.06": 91, "SI.BP.08": 10, "SI.BP.17": 16, "SI.BP.20": None},
        "evidence": {
            "PM.BP.09": "Risks are mentioned in meeting notes but are not documented in the project plan.",
            "PM.BP.10": "Version control strategy documented and used.",
            "PM.BP.13": "Project plan reviewed, but verification results were only partly recorded.",
            "PM.BP.14": "Customer accepted the project plan by signed e-mail.",
            "PM.BP.16": "Progress is tracked in a spreadsheet; actual effort is not always recorded.",
            "PM.BP.25": "Acceptance record signed at the end of the project.",
            "SI.BP.02": "Requirements were reviewed informally; correctness and testability checks are not documented.",
            "SI.BP.03": "Customer validated most requirements in a workshop; validation record available.",
            "SI.BP.06": "Software design verified and approved by the designer.",
            "SI.BP.08": "Test cases exist but were never verified against the requirements or design.",
            "SI.BP.17": "Integration testing done ad hoc; no test report produced.",
            "SI.BP.20": "Not yet assessed: the assessor could not inspect the repository (percentage not recorded).",
        },
    },
    "C": {
        "name": "Company C",
        "profile": "Synthetic demo company. Low maturity: several Base Practices are Not or Partially achieved.",
        "project": ("School Fee Payment Mobile App", "Mobile application for parents to pay school fees.", "2026-05-04", "2026-11-30", 4),
        "date": "2026-09-28",
        "scores": {"PM.BP.09": 0, "PM.BP.10": 5, "PM.BP.13": 30, "PM.BP.14": 60, "PM.BP.16": 25, "PM.BP.25": 92,
                   "SI.BP.02": 45, "SI.BP.03": 55, "SI.BP.06": 15, "SI.BP.08": 0, "SI.BP.17": 20, "SI.BP.20": 35},
        "evidence": {
            "PM.BP.09": "No risk identification found in any project document.",
            "PM.BP.10": "No version control strategy; source code shared by copying folders.",
            "PM.BP.13": "Project plan exists but was never verified or approved.",
            "PM.BP.14": "Customer saw the plan in a meeting; acceptance only partly documented.",
            "PM.BP.16": "Progress is discussed verbally; very few progress status records.",
            "PM.BP.25": "Acceptance record signed by the customer.",
            "SI.BP.02": "Requirements document exists; some requirements are ambiguous and untestable.",
            "SI.BP.03": "Customer reviewed screen mock-ups; formal validation record incomplete.",
            "SI.BP.06": "Design document is outdated and was not verified.",
            "SI.BP.08": "No test cases or test procedures were written before integration.",
            "SI.BP.17": "Some integration tests run; results not recorded in a test report.",
            "SI.BP.20": "Only the source code is stored; test and traceability records are missing from the baseline.",
        },
    },
}

CONSULTANT = ("CS-DEMO", "Demo Assessor (synthetic)", "Prototype demonstration", "Software process assessment", 5)


def build_companies(g: rdflib.Graph) -> None:
    cs = ISO[CONSULTANT[0]]
    g.add((cs, RDF.type, OWL.NamedIndividual)); g.add((cs, RDF.type, ISO.Consultant))
    g.add((cs, ISO.hasConsultantName, Literal(CONSULTANT[1])))
    g.add((cs, ISO.hasAffiliation, Literal(CONSULTANT[2])))
    g.add((cs, ISO.hasExpertType, Literal(CONSULTANT[3])))
    g.add((cs, ISO.hasExperienceYear, Literal(CONSULTANT[4], datatype=XSD.integer)))
    for key, c in COMPANIES.items():
        ent, pj, pas, ae = ISO[f"ENT-{key}"], ISO[f"PJ-{key}1"], ISO[f"PAS-{key}1"], ISO[f"AE-{key}1"]
        for ind, cls in ((ent, ISO.Enterprise), (pj, ISO.Project), (pas, ISO.PreAssessment), (ae, ISO.AssessedEntity)):
            g.add((ind, RDF.type, OWL.NamedIndividual)); g.add((ind, RDF.type, cls))
        g.add((ent, ISO.hasEnterpriseName, Literal(c["name"])))
        g.add((ent, RDFS.comment, Literal(c["profile"])))
        g.add((ent, ISO.hasProject, pj))
        name, desc, start, end, staff = c["project"]
        g.add((pj, ISO.hasProjectName, Literal(name)))
        g.add((pj, ISO.hasProjectDescription, Literal(desc)))
        g.add((pj, ISO.hasStartingDate, Literal(start)))
        g.add((pj, ISO.hasEndingDate, Literal(end)))
        g.add((pj, ISO.hasNumberofStaff, Literal(staff, datatype=XSD.integer)))
        g.add((cs, ISO.preAssess, pas))
        g.add((pas, ISO.hasAssessmentDate, Literal(c["date"], datatype=XSD.date)))
        g.add((pas, ISO.hasAssessedEntity, ae))
        g.add((ae, ISO.responsibleFor, pj))
        for bp, wp in ASSESSED_BPS.items():
            ar, ev = ISO[f"AR-{key}1-{bp}"], ISO[f"EV-{key}1-{bp}"]
            g.add((ar, RDF.type, OWL.NamedIndividual)); g.add((ar, RDF.type, ISO.AssessmentResult))
            g.add((ae, ISO.hasAssessmentResult, ar))
            g.add((ar, ISO.assessesBasePractice, ISO[bp]))
            score = c["scores"][bp]
            if score is not None:
                g.add((ar, ISO.hasPercentageAchievement, Literal(score, datatype=XSD.integer)))
            g.add((ev, RDF.type, OWL.NamedIndividual)); g.add((ev, RDF.type, ISO.AssessmentEvidence))
            g.add((ar, ISO.hasEvidence, ev))
            g.add((ev, ISO.concernsWorkProduct, ISO[wp]))
            g.add((ev, ISO.hasEvidenceDescription, Literal(c["evidence"][bp])))


def build_qa_practices(g: rdflib.Graph, ontology: rdflib.Graph) -> int:
    q = """PREFIX iso: <%s>
           SELECT ?bp ?id ?desc WHERE { ?bp a iso:BasePractice ; iso:hasBasePracticeID ?id ;
                                             iso:hasBasePracticeDescription ?desc } ORDER BY ?bp""" % ISO
    n = 0
    for bp, bp_id, desc in ontology.query(q):
        local = str(bp).split("#")[1]
        rp = ISO[f"RP-{local}"]
        clean_id = " ".join(str(bp_id).split())          # baseline IDs contain stray spaces ("PM.BP1 ")
        text = " ".join(str(desc).split())
        first = text.split(". ")[0].rstrip(".")
        g.add((rp, RDF.type, OWL.NamedIndividual)); g.add((rp, RDF.type, ISO.RecommendedPractice))
        g.add((rp, ISO.hasPracticeName, Literal(f"Strengthen {clean_id}: {first}")))
        g.add((rp, ISO.hasPracticeDescription, Literal(text)))
        g.add((rp, ISO.addressesBasePractice, bp))
        g.add((rp, RDFS.comment, Literal("Generated 1:1 from the Base Practice (scripts/generate_demo_data.py). "
                                         "Not an expert-validated QA guidance text.")))
        n += 1
    return n


def main() -> None:
    ontology = rdflib.Graph(); ontology.parse(ROOT / "ontology" / "ISO29110V2.ttl", format="turtle")
    (ROOT / "data").mkdir(exist_ok=True)

    g = rdflib.Graph(); g.bind("iso", ISO)
    build_companies(g)
    header = "# GENERATED by scripts/generate_demo_data.py - SYNTHETIC demonstration data, not real companies.\n"
    (ROOT / "data" / "mock-companies.ttl").write_text(header + g.serialize(format="turtle"), encoding="utf-8")

    p = rdflib.Graph(); p.bind("iso", ISO)
    n = build_qa_practices(p, ontology)
    header = "# GENERATED by scripts/generate_demo_data.py - one RecommendedPractice per Base Practice (1:1).\n"
    (ROOT / "data" / "qa-practices.ttl").write_text(header + p.serialize(format="turtle"), encoding="utf-8")
    print(f"mock-companies.ttl: {len(g)} triples; qa-practices.ttl: {n} practices ({len(p)} triples)")


if __name__ == "__main__":
    main()
