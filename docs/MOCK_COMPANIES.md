# Mock companies (synthetic demonstration data)

All three companies are invented. Each has one Project, one PreAssessment (by `CS-DEMO`), one AssessedEntity and 12 AssessmentResults
on the same Base Practices: PM.BP.09, 10, 13, 14, 16, 25 and SI.BP.02, 03, 06, 08, 17, 20. Only the **percentage** is asserted;
ratings and recommendations are inferred. Each result has one `AssessmentEvidence` record pointing to a generic work product (WP.xx).

| Company | Scenario | Scores (%) PM 09/10/13/14/16/25 · SI 02/03/06/08/17/20 | Inferred ratings | Recommendations (inferred) |
|---|---|---|---|---|
| A – Online Clinic Appointment System | mature | 95/100/92/100/85/100 · 91/96/90/93/88/100 | 9 F, 3 L | **0** |
| B – Inventory Management Web Application | mixed | 40/96/51/100/70/92 · 50/80/91/10/16/– | 4 F, 3 L, 3 P, 1 N, 1 not rated | **4**: PM.BP9 (P), SI.BP2 (P), SI.BP8 (N), SI.BP17 (P) |
| C – School Fee Payment Mobile App | weak | 0/5/30/60/25/92 · 45/55/15/0/20/35 | 1 F, 2 L, 5 P, 4 N | **9**: PM.BP9, 10, 13, 16; SI.BP2, 6, 8, 17, 20 |

Boundary values tested on purpose: 15→N, 16→P, 50→P, 51→L, 90→L, 91→F. Company B's SI.BP.20 has no percentage (incomplete
information): no rating rule can fire, so the UI shows "not rated" and no recommendation is invented (Open World Assumption).
Isolation: reasoning each company alone gives the same recommendations as reasoning all three together (tests T5).

IRIs: `iso:ENT-A`, `iso:PJ-A1`, `iso:PAS-A1`, `iso:AE-A1`, `iso:AR-A1-PM.BP.09`, `iso:EV-A1-PM.BP.09` (same pattern for B, C).
