package com.example.Backend.service;

import com.example.Backend.dto.RecommendRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RecommendationService {

    private static final String ISO_PREFIX =
            "PREFIX iso: <http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#>\n";

    private final FusekiService fusekiService;

    public RecommendationService(FusekiService fusekiService) {
        this.fusekiService = fusekiService;
    }

    public Map<String, Object> recommend(RecommendRequest request) {

        String process = normalize(request.getProcess());
        String role = normalize(request.getRole());
        String topic = normalize(request.getTopic());
        String situation = normalize(request.getSituation());

        if (process.isBlank()) {
            throw new IllegalArgumentException("Process is required.");
        }

        if (role.isBlank()) {
            throw new IllegalArgumentException("Role is required.");
        }

        if (topic.isBlank()) {
            throw new IllegalArgumentException("Topic is required.");
        }

        if (situation.isBlank()) {
            throw new IllegalArgumentException("Situation is required.");
        }

        String processIndividual = switch (process) {
            case "PM" -> "iso:PM";
            case "SI" -> "iso:SI";
            default -> throw new IllegalArgumentException(
                    "Unsupported process: " + request.getProcess());
        };

        String situationCondition = buildSituationCondition(situation);
        String topicLiteral = escapeSparqlLiteral(request.getTopic().trim());
        String roleLiteral = escapeSparqlLiteral(request.getRole().trim());

        String basePracticeQuery = ISO_PREFIX + """
            SELECT DISTINCT
                ?bpID
                ?bpDesc
                ?wpName
                ?currentState
                ?finalState
            WHERE {
                %s iso:hasBasePractice ?bp .

                ?bp iso:hasBasePracticeID ?bpID ;
                    iso:hasBasePracticeDescription ?bpDesc ;
                    iso:hasOutputWorkProduct ?owp .

                ?owp iso:hasOutputWorkProductName ?wpName .

                OPTIONAL {
                    ?owp iso:hasCurrentState ?currentStateIndividual .
                    ?currentStateIndividual iso:hasWorkProductStateName ?currentState .
                }

                OPTIONAL {
                    ?owp iso:hasFinalState ?finalStateIndividual .
                    ?finalStateIndividual iso:hasWorkProductStateName ?finalState .
                }

                FILTER(
                    CONTAINS(
                        LCASE(STR(?wpName)),
                        LCASE("%s")
                    )
                )

                %s
            }
            ORDER BY ?bpID
            """.formatted(
                processIndividual,
                topicLiteral,
                situationCondition
            );

        String taskQuery = ISO_PREFIX + """
            SELECT DISTINCT
                ?taskID
                ?roleName
                ?wpName
                ?currentState
                ?finalState
            WHERE {
                %s iso:hasActivity ?activity .
                ?activity iso:hasTask ?task .

                ?task a iso:Task ;
                      iso:hasTaskID ?taskID ;
                      iso:hasOutputWorkProduct ?owp ;
                      iso:hasRole ?role .

                ?role iso:hasRoleName ?roleName .

                ?owp iso:hasOutputWorkProductName ?wpName .

                OPTIONAL {
                    ?owp iso:hasCurrentState ?currentStateIndividual .
                    ?currentStateIndividual iso:hasWorkProductStateName ?currentState .
                }

                OPTIONAL {
                    ?owp iso:hasFinalState ?finalStateIndividual .
                    ?finalStateIndividual iso:hasWorkProductStateName ?finalState .
                }

                FILTER(
                    CONTAINS(
                        LCASE(STR(?wpName)),
                        LCASE("%s")
                    )
                )

                FILTER(
                    LCASE(STR(?roleName)) = LCASE("%s")
                )

                %s
            }
            ORDER BY ?taskID
            """.formatted(
                processIndividual,
                topicLiteral,
                roleLiteral,
                situationCondition
            );

        List<Map<String, String>> basePractices =
                fusekiService.executeQuery(basePracticeQuery);

        List<Map<String, String>> supportingTasks =
                fusekiService.executeQuery(taskQuery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("basePractices", basePractices);
        result.put("supportingTasks", supportingTasks);

        return result;
    }

    private String buildSituationCondition(String situation) {
        return switch (situation) {
            case "NOT_APPROVED" -> """
                FILTER(
                    LCASE(STR(?currentState)) != "approved"
                )

                FILTER(
                    LCASE(STR(?finalState)) = "approved"
                )
                """;

            case "APPROVED" -> """
                FILTER(
                    LCASE(STR(?currentState)) = "approved"
                )
                """;

            default -> throw new IllegalArgumentException(
                    "Unsupported situation: " + situation);
        };
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private String escapeSparqlLiteral(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
