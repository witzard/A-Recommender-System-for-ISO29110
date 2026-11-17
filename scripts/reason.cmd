@echo off
rem Runs Pellet (SWRL + OWL) over the ontology and writes the inferred facts to kb\.
rem Needs Java 11 or later. Run from the repository root:  scripts\reason.cmd
rem First the assessment data is checked (tests\validate_data.py): an error there would make the ontology inconsistent.
python tests\validate_data.py ontology\ISO29110V2_rev20.ttl || exit /b 1
java -Xmx2g -cp "reasoner\lib\*;reasoner\build" iso29110.reasoning.SwrlReasoningRunner ^
  --in ontology\ISO29110V2_rev20.ttl ^
  --out-inferred kb\inferred.nt ^
  --report kb\reasoning-report.json
