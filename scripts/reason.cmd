@echo off
rem Runs Pellet (SWRL + OWL) over the ontology and writes the inferred facts to kb\.
rem Needs Java 11 or later. Run from the repository root:  scripts\reason.cmd
java -Xmx2g -cp "reasoner\lib\*;reasoner\build" iso29110.reasoning.SwrlReasoningRunner ^
  --in ontology\ISO29110V2_rev14.ttl ^
  --out-inferred kb\inferred.nt ^
  --report kb\reasoning-report.json
