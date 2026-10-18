# Recommendation query process filter

This branch fixes a concrete retrieval bug: the Supporting Tasks query previously did not constrain tasks to the selected process, so results could mix PM and SI tasks.

Changes:
- Join Task -> Activity using `isTaskOf`, then Activity -> Process using `isActivityOf`.
- Keep the existing `basePractices` and `supportingTasks` response keys and add counts.
- Return a clear validation error for a null request body.
- Add focused unit tests for process scoping, result counts, and invalid input.

The Task -> Activity -> Process path is present in `ontology/ISO29110V2.ttl`. The complete Spring Boot + Fuseki application has not been run in this environment, so end-to-end success is not claimed. Run `./mvnw test` from `backend/`, then smoke-test `/api/options` and `/api/recommend` with Fuseki running.
