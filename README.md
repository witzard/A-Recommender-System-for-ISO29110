# Recommender Framework for ISO/IEC 29110 QA Compliance (research prototype)

A web page with two modes:

1. **ISO/IEC 29110 knowledge search** - three query templates. The user picks values (read from the ontology) and
   phrases; the backend builds the SPARQL query, runs it and returns the rows and the query text.
   - T1 Task finder: role, activity, work product, phrases that the task description must contain (all / any)
   - T2 Work product status: work product, final state, phrase in the producing task or Base Practice
   - T3 Work product criteria: characteristics used to assess a work product
   The 34 competency questions used to evaluate the ontology are listed as example questions; selecting one fills in the
   template parameters used for it in the evaluation.
2. **Assessment results and recommendations** - for one project: profile, process / Base Practice / work product scores,
   characteristics rated Not achieved or Partially achieved, and the recommended tasks, roles and Base Practices for each
   work product with the reason.

Recommendations are not computed by the application. They are the facts `recommendTask`, `recommendRole` and
`recommendBasePractice` that Pellet infers from the SWRL rules of the ontology; the backend only reads them.

## Layout

| Path | Content |
|---|---|
| `ontology/ISO29110V2_rev06b.ttl` | Ontology (copy of `08_Revisions/2026-11-03_Round5_SWRL/ISO29110V2_rev06b.ttl`, not modified) |
| `reasoner/` | `SwrlReasoningRunner` (Pellet 2.3.1 + OWL API 3.4.3) and its libraries |
| `scripts/reason.cmd`, `scripts/load-fuseki.cmd` | Run the reasoner; load the ontology and the inferred facts into Fuseki (`.sh` versions for Linux/macOS) |
| `kb/inferred.nt`, `kb/reasoning-report.json` | Output of the last reasoner run |
| `backend/` | Spring Boot API; SPARQL in `src/main/resources/sparql/`, templates in `QueryTemplates.java` |
| `frontend/` | React page (Vite) |
| `tests/run_checks.py`, `tests/expected.json` | Checks of the running system against the expected values of rounds 5 and 6 |

Company A (ENT.01 / PJ.01) is the case from the advisor's table. Companies B and C (ENT.02 / PJ.02, ENT.03 / PJ.03) are
mock data (`rdfs:comment "MOCK DATA"`) and are labelled as such on the page.

## Run

Requirements: Java 17 or later (backend; the reasoner needs 11 or later), Node.js 20 or later, Apache Jena Fuseki.

1. **Fuseki**: `cd C:\apache-jena-fuseki-6.2.0` then `.\fuseki-server.bat`. Create the dataset `ISO29110_RecommenderSystems`
   (in-memory or TDB2; leave *union default graph* off).
2. **Load the data** (from the repository root): `scripts\load-fuseki.cmd`
   - default graph: `ontology/ISO29110V2_rev06b.ttl`
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

90 checks: R1-R10 (reasoning), process scores of the three projects, tasks / roles / Base Practices of the 14 assessed
entities with recommendations, and the 34 example questions (same task / Base Practice IDs as Round 6).

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
