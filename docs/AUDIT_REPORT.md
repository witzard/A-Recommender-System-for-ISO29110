# Audit and Implementation Report

Branch `opus/iso29110-prototype`. Every claim below is backed by a script or test in this repository;
the command that reproduces it is given in brackets.

## 1. Sources and conflicts

| Source | Finding | Resolution |
|---|---|---|
| `ontology/ISO29110V2.ttl` in `main` vs. researcher's uploaded copy | Uploaded copy is newer: adds AR-T01..AR-T08 (rating boundary fixtures), changes `hasPercentageAchievement` range decimal→integer, adds 5 `isActivityOf SI` links | Uploaded copy committed as the baseline (commit 8a8a472); older copy remains in git history |
| Prompt says "four existing SWRL rules" | The ontology contains **eight** (report Table 4-2, R1–R8) | All eight audited |
| Report: macro P=0.90 / R=1.00 / F1≈0.94 | Not reproducible: `Summary_Table.csv` gives R=1.00 for all 34 CQs; Q5's expected answer is "-" yet TP=1; most expected sets contain exactly the task the query returned; Q1/Q2 answers were hard-coded in `CompetencyQuestionService` | Treated as unverified; re-evaluated from scratch (section 5) |
| CQ wording uses "SA" and "Tester" | No such roles in the ontology (roles: CUS, PJM, TL, WT, AN, DES, PR) | Not used as a role filter (ambiguities A-01, A-02) |

## 2. Repository audit (before this branch)

| Component | Actual behaviour | Status |
|---|---|---|
| `pom.xml` | Spring Boot 4.1.1, `jena-arq` 6.2.0 only. **No reasoner, no OWL API** | SWRL could not run anywhere in the app |
| `RecommendationService` | Builds SPARQL with `CONTAINS(LCASE(name), topic)` and work-product state names; no assessment data used | Replaced |
| `CompetencyQuestionService` | Keyword lists per Q-number; hard-coded answer text for Q1/Q2; hard-coded "preferred" task IDs; `LIMIT 30/20` | Removed |
| `OptionsService` | Process list filtered to `PM`,`SI` by string; situations hard-coded | Replaced by `search-options.rq` |
| `FusekiService` | `solution.get(var).toString()` → typed literals rendered as `92^^xsd:integer` | Fixed (lexical forms) |
| `App.jsx` | One page; Process/Role/Topic/Situation form + clickable Q1–Q34 accordion | Replaced (Q-picker prohibited) |
| Tests | Only the Spring context-load test | Added (section 7) |

## 3. Ontology audit

`ISO29110V2.ttl`: 21 888 triples, 20 classes, 45 object properties, 52 data properties, 623 named individuals.
Pellet 2.3.1: **consistent** [`tests/test_reasoning.py` T6].

| ID | Finding | Impact | Change | Essential? |
|---|---|---|---|---|
| O-01 | `AssessmentResult` is not linked to the Base Practice it rates | No recommendation can be specific | Added `assessesBasePractice` | Yes — done |
| O-02 | `RecommendedPractice` not linked to the gap it addresses; only placeholder `RP.01 "Practice_Name_01"` | Rule R4 cannot select a practice | Added `addressesBasePractice`; 50 practices generated 1:1 from Base Practices (`data/qa-practices.ttl`, labelled *not expert-validated*) | Yes — done |
| O-03 | No company-specific evidence model (`hasEntity` points to the standard's WP individuals) | Explanations cannot cite evidence | Added `AssessmentEvidence`, `hasEvidence`, `concernsWorkProduct`, `hasEvidenceDescription` | Yes — done |
| O-04 | `AE.01 recommend RP.01` asserted by hand | A recommendation that no rule produced | Removed in the merged build (baseline file untouched) | Yes — done |
| O-05 | No `AssessedEntity` has `hasAssessmentResult` (0 uses) | R4 can never fire on baseline data | Mock companies provide the links | Yes — done |
| O-06 | Property domains/ranges are lists of the class **and copies of its restrictions** (multiple `rdfs:domain` = intersection); several restrictions point the wrong way (`AssessedEntity ⊑ isResponsibilityOf some Project`, `Project ⊑ responsibleFor some AssessedEntity`) | Pellet infers `AE.01 a Project` and `PJ.01 a AssessedEntity`; same for every company | Not changed. Recommended: keep one named class per domain/range, move restrictions to `subClassOf`, invert the two restrictions | No (inferred-owl graph only; app never reads it) |
| O-07 | `AssessedEntity ⊑ recommend some RecommendedPractice`; `RecommendedPractice ⊑ recommendedBy some AssessedEntity` | States that every assessed entity has a recommendation — false for Company A | Not changed; recommend removing both axioms | No |
| O-08 | 48 Base Practices asserted `rdf:type Task`; all 333 conclusions of R3 already asserted | Inferred axioms appear to have been exported from Protégé into the asserted file; asserted/inferred no longer separable | Not changed; recommend removing the 48 types and the 306 BP→OWP copies or moving them to a separate file | No |
| O-09 | `WorkProductCharacteristic` (134 individuals) not linked to any work product (`hasCharacteristics`: 0 uses) | CQ Q33 "criteria" unanswerable | Not changed | No (affects Q33) |
| O-10 | Roles duplicated per process (`PM.ROLE.02` and `SI.ROLE.09` both "Project manager") | Search by role IRI would miss half | Search uses role abbreviation | Handled in query |
| O-11 | Base Practice IDs contain stray spaces (`"PM.BP1 "`) | Display / string matching | Trimmed in services | Handled |
| O-12 | `PreAssessment` has a restriction with no property/filler | Malformed axiom (ignored by Pellet) | Not changed | No |
| O-13 | SI.5.10 text says "approved by CUS", its output is modelled as ending `Validated` | CQ Q28 miss | Not changed (domain decision) | No |

Extension: `ontology/extensions/assessment-recommendation-extension.ttl` (4 object properties, 2 data properties, 1 class, 2 rules).

## 4. SWRL audit

Reasoner: **Pellet 2.3.1 through OWL API 3.4.3** (`reasoner/SwrlReasoningRunner.java`). Chosen because it executes DL-safe SWRL
with all built-ins used here (`greaterThanOrEqual`, `lessThanOrEqual`, `containsIgnoreCase`); HermiT does not support SWRL built-ins;
Fuseki does not execute SWRL. The jars come from the `owlready2` PyPI package because Maven Central was not reachable in the build
environment (`reasoner/lib`, LGPL/AGPL — see section 8).

| Rule | Baseline result (real run) | Diagnosis | Action | Tests |
|---|---|---|---|---|
| R1 ApprovedRequirementSpecRule | 0 new facts | Needs `isEntityOf` (0 uses). Consequent `hasEntity(?ae,?owp)` is the inverse of premise `isEntityOf(?owp,?ae)` → **can never add knowledge**. Also mixes the standard's *final* state with a company's actual state | Kept unchanged; redesign = future work | T1 fires; T2 proves the fact is also inferred with NO rules |
| R2 ApprovedTestCaseRule | 0 | Same as R1 | Kept | same |
| R3 BasePracticeOutputWPRule | 0 | All 333 conclusions already asserted (O-08); conclusion uses a Task property for a Base Practice | Kept | T1 fires on a new work product; T2 necessity |
| R4 ImprovementRecommendationRule | 0 | No AE→result links (O-05); `?rp` unconstrained → **recommends every practice** | **Revised**: joins `assessesBasePractice` and `addressesBasePractice` | T4 reproduces the original bug (51 practices for 1 result); T1/T2/T3 for the revision |
| R5–R8 Rating rules | 0 | Every test result already has its rating asserted | Unchanged; companies assert only percentages so ratings are inferred | T1/T2 on 0,15,16,50,51,90,91,100 |
| NEW PartialImprovementRecommendationRule | — | Without it 16% gives no recommendation while 15% does | Added; disable with `--exclude-rule` | T1/T2 |

Root cause of "rules not working" in the prototype: (a) no reasoner in the stack, (b) the data needed by the antecedents did not exist,
(c) a defect found during this work: OWL API reads each input document separately, so property values in a data-only file were
parsed as annotations → 0 inferences. The pipeline now gives the reasoner one combined document.

Materialisation: `scripts/build_knowledge_base.py` → 96 SWRL-dependent facts (35 ratings + 13 recommendations, each with its inverse).
Each fact's *necessary rules* = rules whose removal (separate Pellet run) makes it disappear. Ablation counts:
Completely 28, Largely 16, Not 20, Partially(rating) 32, ImprovementRecommendation 10, PartialImprovement 16, R1/R2/R3 0.

## 5. Competency questions

All 34 original questions kept verbatim (`evaluation/cq_mapping.json`). Expected answers rebuilt from task descriptions and committed
**before** the first query run (commit "CQ mapping + expected answers…"). Results: `evaluation/results/CQ_EVALUATION.md`.

| Run | Macro P | Macro R | Macro F1 | Micro P | Micro R | Micro F1 |
|---|---|---|---|---|---|---|
| v1 | 0.57 | 0.88 | 0.64 | 0.45 | 0.85 | 0.59 |
| v2 (state filter tied to selected work product) | 0.58* | 0.88 | 0.66* | 0.45 | 0.85 | 0.59 |

\* Q28 retrieves nothing in v2: precision undefined, excluded from macro P/F1 (33 CQs).
23 CQs answerable, 11 partially answerable (ontology lacks the "criteria"/"understanding"/"version" part). Main error sources:
keyword vocabulary (Q4 "review" vs "verify", Q9, Q34), broad single parameters (Q14, Q17, Q21), ontology data (Q28, O-13).
Old and new figures are **not comparable**: different expected answers and a different retrieval method.

## 6. Architecture

```
Protégé ─► ontology/ISO29110V2.ttl + extension + data/*.ttl
                 │  scripts/build_knowledge_base.py
                 ▼
     Pellet (SWRL)  ──►  kb/build/dataset.trig
                           default graph ............ asserted knowledge
                           urn:iso29110:graph:inferred-swrl ... rule-derived facts
                           urn:iso29110:graph:inferred-owl .... OWL-only entailments (audit)
                           urn:iso29110:graph:explanation ..... run metadata + rule attribution
                 │  load into Fuseki (replace whole dataset)
                 ▼
React SPA ──REST──► Spring Boot (ApiController) ──SPARQL (.rq files, bound params)──► Fuseki
```
Reasoning runs when the data changes (data is edited only in Protégé), not per click. Re-loading the TriG with PUT replaces every graph,
so stale inferences cannot survive. Fuseki must **not** use `unionDefaultGraph` (inferred ratings would be read as asserted).

## 7. Tests

| Suite | Result | What it proves |
|---|---|---|
| `tests/test_reasoning.py` (real Pellet runs) | 43/43 | rule firing, necessity, negatives, original-rule bug, company isolation, baseline 0 |
| `frontend` `npm test` (Vitest + jsdom, real `App.jsx`) | 5/5 | 3 companies, switching without navigation, explanations, parameter search, no data-entry form |
| `tests/reference_api.py` | runs | Workflow A and B end-to-end over the reasoned dataset with the backend's SPARQL files |
| Java/Python template parity | 34/34 identical | evaluation measures the backend's query |
| Backend compile | against API stubs only | **Not** a Maven build |

## 8. Not done / blocked

* Spring Boot build and live Fuseki: **blocked** (Maven Central and the Fuseki download were unreachable here). Run `mvnw spring-boot:run` locally.
* Browser test: **blocked** (headless Chromium hangs in the sandbox). UI tested in jsdom only; no screenshot review.
* Pellet 2.3.1 is AGPL-3 (dual licensed), OWL API 3 is LGPL. Fine for research; for production consider Openllet via Maven.
* QA practices are generated 1:1 from Base Practices — expert-authored QA guidance is future work.
