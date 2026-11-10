# Recommender Framework for ISO/IEC 29110 QA Compliance (research prototype)

A web page with two modes:

1. **ISO/IEC 29110 knowledge search by parameters** - the user selects parameters only (role, activity, process,
   Base Practice, process outcome, work product, work product state; topic phrases). No question is shown. The queries
   are grouped by the parameters they share (`08_Revisions/2026-11-08_QueryParameterGrouping/PARAMETER_GROUPING.md`):
   G1 tasks, G2 work product status / criteria, G3 Base Practice - process outcome - work product. The backend runs every
   query that uses the selected parameters, keeping the most specific ones, and returns their results together (union).
   The 34 competency questions are no longer on the page; they are the coverage test in `tests/cq_parameters.json`.
2. **Assessment results and recommendations** - for one project: profile, process / Base Practice / work product scores,
   characteristics rated Not achieved or Partially achieved (chosen in a drop-down), and for each work product the
   recommended tasks (with the roles that perform them), Base Practices and the process outcomes linked to it.

Recommendations are not computed by the application. They are the facts `recommendTask`, `recommendBasePractice`
and `recommendOutcome` that Pellet infers from the SWRL rules of the ontology; the backend only reads them.

## Layout

| Path | Content |
|---|---|
| `ontology/ISO29110V2_rev14.ttl` | Ontology (copy of `08_Revisions/2026-11-10_Round14_ScenariosOnly/ISO29110V2_rev14.ttl`, not modified) |
| `reasoner/` | `SwrlReasoningRunner` (Pellet 2.3.1 + OWL API 3.4.3) and its libraries |
| `scripts/reason.cmd`, `scripts/load-fuseki.cmd` | Run the reasoner; load the ontology and the inferred facts into Fuseki (`.sh` versions for Linux/macOS) |
| `kb/inferred.nt`, `kb/reasoning-report.json` | Output of the last reasoner run |
| `backend/` | Spring Boot API; SPARQL in `src/main/resources/sparql/`, templates in `QueryTemplates.java` |
| `frontend/` | React page (Vite) |
| `tests/run_checks.py`, `tests/expected.json`, `tests/cq_parameters.json` | Checks of the running system (reasoning, scores, recommendations, the 34 CQs as parameter searches) |

The ontology (rev14) holds three mock case-scenario companies (`rdfs:comment "MOCK DATA"`, labelled as such on the page):
S1 (ENT.04 / PJ.04), S2 (ENT.05 / PJ.05) and S3 (ENT.06 / PJ.06) with 25 % / 50 % / 80 % of their 23 work products assessed
(Partial and Fully); the other work products keep their assessment results without a percentage. S1 does not deliver WP.08
and WP.18 (ExcludedConditionalWorkProductRule). The earlier companies A-C (PJ.01-PJ.03) were removed in rev14
(see `08_Revisions/2026-11-10_Round14_ScenariosOnly`).

## Run

Requirements: Java 17 or later (backend; the reasoner needs 11 or later), Node.js 20 or later, Apache Jena Fuseki.

1. **Fuseki**: `cd C:\apache-jena-fuseki-6.2.0` then `.\fuseki-server.bat`. Create the dataset `ISO29110_RecommenderSystems`
   (in-memory or TDB2; leave *union default graph* off).
2. **Load the data** (from the repository root): `scripts\load-fuseki.cmd`
   - default graph: `ontology/ISO29110V2_rev14.ttl`
   - named graph `urn:iso29110:graph:inferred`: `kb/inferred.nt`
3. **Backend**: `cd backend` then `.\mvnw.cmd spring-boot:run` (http://localhost:8080/api/projects lists 3 projects).
4. **Frontend**: `cd frontend`, `npm install`, `npm run dev`, open http://localhost:5173.

After a change to the ontology, run `scripts\reason.cmd` (writes `kb/inferred.nt` and `kb/reasoning-report.json`) and
then `scripts\load-fuseki.cmd` again. The reasoner stops without writing anything if the ontology is inconsistent.

## Tests

With Fuseki and the backend running:

```
python tests\run_checks.py
```

96 checks: inferred-fact counts (GapResult, S1-S3 tasks / Base Practices / outcomes, R8, R10, no recommendRole), process
scores of the three scenario projects, tasks / process outcomes / Base Practices of the 15 assessed entities with
recommendations, and the 34 competency questions sent as parameters
(the query of the answering group must return the same task / Base Practice IDs as Round 6; Q33 = 137 pairs, rev09).

Frontend: `cd frontend` then `npm test` (Vitest; the API is answered from `src/__tests__/fixtures.json`).

## API

| Method | Path | Returns |
|---|---|---|
| GET | `/api/search/options` | values of the drop-downs (role, activity, workProduct, state) |
| POST | `/api/search/T1` | `{role, activity, workProduct, kwAll: [], kwAny: []}` -> `{sparql, rows}` |
| POST | `/api/search/T2` | `{workProduct, state, kwProducer}` -> `{sparql, rows}` |
| POST | `/api/search/T3` | `{workProduct}` (empty = all) -> `{sparql, rows}` |
| GET | `/api/projects` | projects with assessment data |
| GET | `/api/projects/{id}/assessment` | profile, scores, characteristics not achieved, recommendations |
| GET | `/api/status` | number of triples in the default graph and in the inferred graph |
| GET | `/api/reasoning-report` | `kb/reasoning-report.json` |
