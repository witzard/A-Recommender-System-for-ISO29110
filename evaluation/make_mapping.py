# One-off authoring helper: writes cq_mapping.json (kept so the mapping is reviewable as code).
import json
Q = {l.split("\t")[0]: l.split("\t")[1].strip() for l in open("/mnt/user-data/uploads/Competency_Questions.txt", encoding="utf-8").read().splitlines()[1:] if l.strip()}
GROUP = {**{q: "Project Management" for q in [1,2,3,4,19]}, 5: "Development Environment",
         **{q: "Software Requirements & Traceability" for q in [6,7,11]},
         **{q: "Software Design & Construction" for q in [8,9,12,13,14]},
         **{q: "Testing & Defect Management" for q in [10,15,16,17,18,20,21,22,23,24]},
         **{q: "Documentation & Maintenance" for q in [25,26,27,28,31,32]},
         **{q: "Delivery & Baseline Management" for q in [29,30,34]}, 33: "Work Product Evaluation"}
M = [
 (1, {"process":"PM","keyword":"risk"}, ["PM.1.9"], "partial", "PM.1.9 identifies/documents risks. The ontology has NO selection criteria for risk topics, so the 'criteria' part is unsupported."),
 (2, {"role":"PJM","process":"PM","keyword":"deliver"}, ["PM.1.2","PM.1.3","PM.1.12"], "answerable", "Deliverables enter the plan via PM.1.12; delivery instructions agreed in PM.1.2; tasks to produce them identified in PM.1.3."),
 (3, {"role":"PJM","activity":"PM.04"}, ["PM.4.1","PM.4.2"], "answerable", "Activity PM.04 Project closure: customer signs acceptance record (PM.4.1) and it is stored (PM.4.2)."),
 (4, {"workProduct":"WP.10","keyword":"review"}, ["PM.1.13","SI.1.1"], "answerable", "Plan verified (PM.1.13) and reviewed with the work team (SI.1.1). 'SA' has no ontology role -> no role filter (ambiguity A-01)."),
 (5, {"workProduct":"WP.05"}, ["SI.1.2"], "partial", "SI.1.2 sets up the implementation environment. How the team's understanding is ensured is not modelled."),
 (6, {"activity":"SI.02","workProduct":"WP.13"}, ["SI.2.2","SI.2.3","SI.2.4","PM.3.3"], "answerable", "SRS documented/updated (SI.2.2), verified (SI.2.3), validated (SI.2.4); requirement changes are identified via change requests (PM.3.3)."),
 (7, {"role":"CUS","workProduct":"WP.13"}, ["SI.2.4"], "answerable", "Customer validates and approves the requirements specification (SI.2.4)."),
 (8, {"process":"SI","keyword":"baseline"}, ["SI.2.7","SI.3.8"], "answerable", "Specification baseline that design starts from (SI.2.7) and the design baseline (SI.3.8). Question is ambiguous between the two; both kept."),
 (9, {"workProduct":"WP.16","keyword":"review"}, ["SI.3.1","SI.4.2"], "partial", "Tasks assigned per plan (SI.3.1); programmer and designer review the design (SI.4.2). 'Understanding' itself is not modelled."),
 (10, {"workProduct":"WP.19","keyword":"consisten"}, ["SI.3.7"], "answerable", "SI.3.7 verifies consistency among requirements, design and test cases/procedures."),
 (11, {"workProduct":"WP.21","keyword":"update"}, ["SI.3.6","SI.4.6","SI.5.6"], "partial", "Traceability record updated in SI.3.6/SI.4.6/SI.5.6. No explicit link from a requirement CHANGE to these updates."),
 (12, {"role":"PR","workProduct":"WP.16"}, ["SI.4.1","SI.4.2"], "answerable", "Programmer receives tasks (SI.4.1) and reviews the software design (SI.4.2) to know what to build."),
 (13, {"workProduct":"WP.16","keyword":"requirement"}, ["SI.3.4"], "answerable", "SI.3.4 verifies design consistency with the requirements specification and traceability record."),
 (14, {"role":"PR","activity":"SI.04"}, ["SI.4.1"], "partial", "Work assigned in SI.4.1. 'When it is finished' (exit criteria/progress) is only indirectly modelled."),
 (15, {"role":"PR","keyword":"test"}, ["SI.4.4","SI.4.5"], "partial", "Unit test cases developed (SI.4.4) and executed (SI.4.5). 'Displays correctly' (UI checks) not modelled."),
 (16, {"activity":"SI.05","keyword":"assign"}, ["SI.5.1"], "answerable", "Duties for integration/testing are assigned in SI.5.1."),
 (17, {"process":"SI","keyword":"test"}, ["SI.4.5","SI.5.4"], "answerable", "Correct functioning is shown by unit tests (SI.4.5) and integration tests (SI.5.4)."),
 (18, {"role":"PR","keyword":"defect"}, ["SI.4.5","SI.5.5"], "answerable", "Defects found by unit tests and corrected (SI.4.5); defects corrected and re-tested (SI.5.5)."),
 (19, {"workProduct":"WP.09"}, ["PM.2.1","PM.3.1"], "answerable", "Execution monitored (PM.2.1) and progress evaluated against the plan/schedule (PM.3.1)."),
 (20, {"activity":"SI.05","workProduct":"WP.19"}, ["SI.5.2"], "partial", "SI.5.2 reviews test cases/procedures and sets the test environment. 'Tester' is not an ontology role (ambiguity A-02)."),
 (21, {"workProduct":"WP.19"}, ["SI.3.5","SI.3.7","SI.5.4"], "answerable", "Test cases from requirements (SI.3.5), verified for consistency (SI.3.7), executed (SI.5.4)."),
 (22, {"workProduct":"WP.20"}, ["SI.5.4"], "answerable", "Integration test results are documented in the test report (SI.5.4)."),
 (23, {"keyword":"defect"}, ["SI.4.5","SI.5.5"], "partial", "Defect correction with re-testing (SI.4.5, SI.5.5). Versions of software are not modelled."),
 (24, {"workProduct":"WP.19","keyword":"consisten"}, ["SI.3.7"], "answerable", "Same knowledge as Q10 from the tester's viewpoint: SI.3.7."),
 (25, {"workProduct":"WP.08"}, ["SI.5.7"], "answerable", "Programmer documents the product operation guide (SI.5.7)."),
 (26, {"workProduct":"WP.08","keyword":"verify"}, ["SI.5.8"], "answerable", "SI.5.8 verifies the guide against the software and obtains approval."),
 (27, {"workProduct":"WP.18"}, ["SI.2.5","SI.5.9"], "answerable", "Analyst documents the preliminary (SI.2.5) and final (SI.5.9) software user documentation."),
 (28, {"workProduct":"WP.18","state":"WPS.02"}, ["SI.5.10"], "answerable", "Ready for use when verified and approved by the customer (SI.5.10)."),
 (29, {"keyword":"deliver"}, ["PM.1.2","PM.1.12","SI.6.5","SI.6.6"], "answerable", "Delivery instructions (PM.1.2), deliverables in plan (PM.1.12), product baseline contents (SI.6.5), delivery preparation (SI.6.6)."),
 (30, {"activity":"SI.06","keyword":"deliver"}, ["SI.6.6"], "answerable", "TL assembles and prepares the delivery for the PJM (SI.6.6)."),
 (31, {"workProduct":"WP.11"}, ["PM.1.15","PM.1.16","PM.2.6","PM.2.7","SI.6.4"], "partial", "Repository established/filled/backed up/recovered (PM.1.15, 1.16, 2.6, 2.7); maintenance documentation baselined (SI.6.4). 'Maintenance handling' beyond documentation is not modelled."),
 (32, {"workProduct":"WP.06"}, ["SI.6.2","SI.6.3","SI.6.4"], "answerable", "Maintenance documentation written (SI.6.2), verified/approved (SI.6.3), added to the baseline (SI.6.4)."),
 (33, {"workProduct":"WP.23"}, ["PM.1.13","SI.2.3","SI.2.4","SI.2.6","SI.3.4","SI.3.7","SI.5.8","SI.5.10","SI.6.3"], "partial", "Verification/validation tasks per work product. The evaluation CRITERIA (WorkProductCharacteristic, 134 individuals) are not linked to any work product (hasCharacteristics has 0 uses) -> criteria part unsupported."),
 (34, {"keyword":"baseline"}, ["PM.1.10","PM.1.15","SI.6.5"], "partial", "Use controlled by the version control strategy (PM.1.10) and repository (PM.1.15); baseline contents identified in SI.6.5. Access control is not modelled."),
]
out = [{"id": f"Q{n}", "question": Q[f"Q{n}"], "group": GROUP[n], "parameters": p, "expectedTaskIDs": e,
        "answerability": s, "rationale": r} for n, p, e, s, r in M]
assert len(out) == 34 and sorted(int(x["id"][1:]) for x in out) == list(range(1, 35))
json.dump(out, open("cq_mapping.json", "w", encoding="utf-8"), indent=1, ensure_ascii=False)
print("written", len(out))
