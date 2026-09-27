package com.example.Backend.service;

import com.example.Backend.dto.RecommendRequest;
import org.springframework.stereotype.Service;

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

    public List<Map<String, String>> recommend(RecommendRequest request) {

        String process = normalize(request.getProcess());
        String topic = normalize(request.getTopic());

        if (process.isBlank()) {
            throw new IllegalArgumentException("Process is required.");
        }

        if (topic.isBlank()) {
            throw new IllegalArgumentException("Topic is required.");
        }

        String processIndividual = switch (process) {
            case "PM" -> "iso:PM";
            case "SI" -> "iso:SI";
            default -> throw new IllegalArgumentException(
                    "Unsupported process: " + request.getProcess());
        };

        String topicLiteral = escapeSparqlLiteral(request.getTopic());

        String sparql = ISO_PREFIX + """
            SELECT DISTINCT
                ?bpID
                ?bpDesc
                ?wpName
            WHERE {
                %s iso:hasBasePractice ?bp .

                ?bp iso:hasBasePracticeID ?bpID ;
                    iso:hasBasePracticeDescription ?bpDesc ;
                    iso:hasOutputWorkProduct ?owp .

                ?owp iso:hasOutputWorkProductName ?wpName .

                FILTER(
                    CONTAINS(
                        LCASE(STR(?wpName)),
                        LCASE("%s")
                    )
                )
            }
            ORDER BY ?bpID
            """.formatted(processIndividual, topicLiteral);

        return fusekiService.executeQuery(sparql);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String escapeSparqlLiteral(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace(""", "\"");
    }
}
