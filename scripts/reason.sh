#!/bin/sh
# Runs Pellet (SWRL + OWL) over the ontology and writes the inferred facts to kb/.
# Needs Java 11 or later. Run from the repository root:  sh scripts/reason.sh
# First the assessment data is checked (tests/validate_data.py): an error there would make the ontology inconsistent.
python3 tests/validate_data.py ontology/ISO29110V2_rev17.ttl || exit 1
java -Xmx2g -cp "reasoner/lib/*:reasoner/build" iso29110.reasoning.SwrlReasoningRunner \
  --in ontology/ISO29110V2_rev17.ttl \
  --out-inferred kb/inferred.nt \
  --report kb/reasoning-report.json
