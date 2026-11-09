# Changelog

## 2026-11-09 - ontology rev09 (advisor BP-WP table)

### Ontology
- `ontology/ISO29110V2_rev09.ttl` = `08_Revisions/2026-11-09_Round9_AdvisorTable/ISO29110V2_rev09.ttl`
  (SHA-256 `149e49703391bc0d92c2f105d03d97c588b41ece222a48b291b2eb3f49536a92`): RecommendBasePracticeRule uses the
  pre-assessment (advisor BP-WP table), SI.4.7 role TL, Verified/Validated state references corrected (72), WPC.22.11-12.
  `kb/` regenerated with `scripts/reason.sh` (consistent; inferred facts differ from rev08 only in recommendBasePractice
  110 -> 103). rev08 copy removed (still in `08_Revisions/2026-11-08_Round8_Outcome/`).

### Knowledge search
- G3 (`QueryTemplates.basePracticeOutcome`): the work-product filter and column use the work products of the Base
  Practice's pre-assessment instead of the asserted BP hasOutputWorkProduct; column/title "work products (advisor table)".

### Tests
- `tests/expected.json`: recommended Base Practices of every assessed entity (independent Python computation = Pellet,
  14/14), R6 PJ.02 BPs 36 -> 35, R8 PJ.03 BPs 15 -> 17, Q33 135 -> 137 WP-WPC pairs.
- Not run yet: Maven build, Spring Boot, run_checks.py, npm test (Windows).

## 2026-11-08 - ontology rev08, outcomes, parameter search (not committed)

### Ontology
- `ontology/ISO29110V2_rev08.ttl` = `08_Revisions/2026-11-08_Round8_Outcome/ISO29110V2_rev08.ttl`
  (SHA-256 `84ea349fbcdcf1c8cdc6bd2734eba32a0b3ee6bb4c240bf3b0c595667b7944c7`): recommendRole and RecommendRoleRule
  removed, recommendOutcome and RecommendOutcomeRule added, WPC.15.02 added. `kb/` regenerated with `scripts/reason.sh`
  (consistent; inferred facts differ from rev06b only by -52 recommendRole / +63 recommendOutcome).
- rev06b copy moved to `_TRASH/06_Prototype_removed_2026-11-08/`.

### Assessment page
- No recommended roles; the roles of each recommended task stay in the task table.
- Process outcomes per work product (`recommended-outcomes.rq`, with the Base Practices that link them).
- Drop-down "Show gaps rated": Not achieved / Partially achieved / both, applied to the gap table and the recommendations.

### Knowledge search
- `POST /api/search` replaces `POST /api/search/{T1|T2|T3}`: parameters only; `KnowledgeSearchService.choose` picks the
  queries (accepted / required parameters, most specific wins) and the results are returned together.
- New query G3 `QueryTemplates.basePracticeOutcome`; T2 accepts an optional state and topic phrases (all / any).
- `GET /api/search/options` returns `{value, label}` for every parameter (process, Base Practice, outcome added).
- The example questions (`frontend/src/examples.json`) are removed from the page; the 34 CQs are in `tests/cq_parameters.json`.

### Checked (Linux VM, 2026-11-08) / not checked
- Frontend tests 8/8 with a Node + jsdom runner that loads the same test file (Vitest itself needs the Windows binaries).
- Query code compiled with javac 11 (Spring/Jena stubbed) and the generated SPARQL run with rdflib on rev08 + kb:
  R1-R11 pass, outcomes of the 14 assessed entities equal tests/expected.json, the 33 CQs (all but Q33) return the
  Round 6 IDs on their group, Q33 = 135 pairs. Union adds Base Practice rows (G3) to 15 topic-only CQs.
- NOT checked: Maven build, Spring Boot run, Fuseki, `tests/run_checks.py` against the running system, `npm test`.

## 2026-11-04 - branch web/rev06b

### Ontology
- `ontology/ISO29110V2_rev06b.ttl` is a byte-identical copy of
  `08_Revisions/2026-11-03_Round5_SWRL/ISO29110V2_rev06b.ttl`
  (SHA-256 `0f281b67570102c78ce7817576808396a35c10748230c27273c70053e971072f`).
  It is stored with `-text` in `.gitattributes` so Git does not change its line endings.
- The application does not modify the ontology. Recommendations are taken only from the
  facts inferred by Pellet from the SWRL rules in this file.

### Removed (moved to `_TRASH/06_Prototype_removed_2026-11-04/` outside the repository)
- `ontology/ISO29110V2.ttl` (older baseline) and `ontology/extensions/` (properties and rules
  that rev06b does not contain or has replaced).
- `data/` (earlier demo companies) and `scripts/generate_demo_data.py`; rev06b contains its own
  Company A (ENT.01/PJ.01) and mock Companies B and C (ENT.02/PJ.02, ENT.03/PJ.03).
- `scripts/build_knowledge_base.py` and `kb/build/` (merge, ablation and TriG packaging steps).
- `evaluation/`, `tests/test_reasoning.py`, `tests/reference_api.py`, `tests/results/`, `docs/`
  (written for the earlier extension and demo data).
- `TaskController` (connection test endpoint), the Vite template README and unused icon.

### Reasoning
- `reasoner/build` recompiled with `--release 11` (runs on Java 11 or later); source unchanged.
- `scripts/reason.cmd|sh` writes `kb/inferred.nt` and `kb/reasoning-report.json`.
- `scripts/load-fuseki.cmd|sh` loads the ontology into the default graph and the inferred facts
  into the named graph `urn:iso29110:graph:inferred`.
- Check run on 2026-11-04: consistent, 12 rules, 88 inferred types and 989 inferred object
  property values in about 15 s.

### Backend
- Knowledge search: `QueryTemplates` (T1, T2, T3) follows `08_Revisions/_tools/templates.py`. Values chosen from a
  drop-down are compared with `=` (exact names from the ontology); keywords stay case-insensitive regular expressions.
  Each template is wrapped in an outer query that only adds display columns.
- Scores: `q1`-`q4` `*-v3.rq` are unchanged copies of `08_Revisions/2026-11-03_Round5_SWRL/queries/Q1-Q4_*_v3.rq`.
- Recommendations: `recommended-tasks.rq`, `recommended-roles.rq`, `recommended-base-practices.rq` read only the named graph
  of Pellet output. The extra columns (whether a task produces or uses the work product, which recommended tasks a role
  performs) are for display.
- Endpoints: see README. Removed: `/api/companies`, `/api/search` (old form), `/api/search/task`, `/api/tasks`.

### Frontend
- Two modes: knowledge search and assessment results with recommendations. Plain tables, no component library.
- `src/examples.json`: the 34 competency questions (English text from `07_PastWork/Question_query.xlsx`, sheet "table 2")
  with the parameters of `08_Revisions/2026-11-04_Round6_QueryTemplates/cq_parameters.json`.
- `package.json` still lists `@mui/*`, `@emotion/*` and `axios`, which are no longer imported; they can be removed with
  `npm uninstall @mui/material @mui/icons-material @emotion/react @emotion/styled axios` on a machine with internet access.

### Tests
- `tests/run_checks.py` + `tests/expected.json` (see README).
