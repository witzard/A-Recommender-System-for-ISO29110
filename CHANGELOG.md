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
