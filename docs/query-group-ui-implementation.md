# Query, grouping, and UI implementation notes

## Changes in this branch

- The supporting-task SPARQL pattern now scopes tasks through the selected process:
  `Process -> hasActivity -> Activity -> hasTask -> Task`.
  This prevents a selection for PM from returning SI tasks (and vice versa) solely because role and work-product names match.
- Work-product matching uses the exact selected ontology label (case-insensitive) rather than partial substring matching, reducing accidental matches to similarly named artifacts.
- Recommendation query literal escaping covers backslash, double quote, newline, carriage return, and tab characters.
- The Fuseki query endpoint can be overridden through `FUSEKI_QUERY_ENDPOINT`; local default remains `http://localhost:3030/ISO29110_RecommenderSystems/query`.
- The frontend API base URL can be overridden through `VITE_API_BASE_URL`.
- The React UI includes a competency-question browser for all 34 questions, grouped into 8 knowledge areas.

## Run locally

Backend (from `backend/`):

```powershell
$env:FUSEKI_QUERY_ENDPOINT="http://localhost:3030/ISO29110_RecommenderSystems/query"
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Frontend (from `frontend/`):

```powershell
Copy-Item .env.example .env
npm ci
npm run validate:groups
npm run lint
npm run build
npm run dev
```

## Validation boundary

The grouping is a navigation/evaluation aid, not a claim that all expected answers are semantically correct. The local ontology query sanity check is separate from a live Fuseki integration test. Before reporting Precision, Recall, or F1, create a verified answer-level gold set for each competency question; the expected answer count alone is not sufficient.

The new unit tests validate the process scope included in the generated supporting-task query and reject an unsupported process before issuing SPARQL requests. Run the Maven test command above in the target environment before merging.
