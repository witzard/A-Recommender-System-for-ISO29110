package com.example.Backend.controller;


import com.example.Backend.service.FusekiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final FusekiService fusekiService;

    public RecommendController(FusekiService fusekiService) {
        this.fusekiService = fusekiService;
    }

    @GetMapping("/test")
    public Map<String, Object> testFuseki() {

        String sparql = """
            PREFIX iso: <http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#>

            SELECT DISTINCT
                ?bpID
                ?bpDesc
                ?wpName
            WHERE {
                iso:PM iso:hasBasePractice ?bp .

                ?bp iso:hasBasePracticeID ?bpID ;
                    iso:hasBasePracticeDescription ?bpDesc ;
                    iso:hasOutputWorkProduct ?owp .

                ?owp iso:hasOutputWorkProductName ?wpName .

                FILTER(
                    CONTAINS(
                        LCASE(STR(?wpName)),
                        "project plan"
                    )
                )
            }
            ORDER BY ?bpID
            """;

        List<Map<String, String>> results =
                fusekiService.executeQuery(sparql);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("success", true);
        response.put("source", "Fuseki");
        response.put("count", results.size());
        response.put("results", results);

        return response;
    }
}