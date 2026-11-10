@echo off
rem Replaces the content of the Fuseki dataset: ontology -> default graph, Pellet output -> named graph.
rem Run from the repository root after scripts\reason.cmd:  scripts\load-fuseki.cmd
set DATA=http://localhost:3030/ISO29110_RecommenderSystems/data
curl -f -X PUT -H "Content-Type: text/turtle" --data-binary @ontology\ISO29110V2_rev12.ttl "%DATA%?default" || exit /b 1
curl -f -X PUT -H "Content-Type: application/n-triples" --data-binary @kb\inferred.nt "%DATA%?graph=urn:iso29110:graph:inferred" || exit /b 1
echo Loaded.
