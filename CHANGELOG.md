# Changelog

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
