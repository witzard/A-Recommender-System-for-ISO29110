package com.example.Backend.service;

import com.example.Backend.sparql.SparqlTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capability A - parameter-based knowledge search.
 * Every selectable value is read from the ontology (search-options.rq); a request is accepted only if each
 * value is one of those ontology values, then it is BOUND into knowledge-search.rq.
 */
@Service
public class KnowledgeSearchService {

    static final List<String> PARAMETERS = List.of("process", "activity", "role", "workProduct", "state", "keyword");
    private static final Map<String, String> VARIABLE = Map.of(
            "process", "pProcess", "activity", "pActivity", "role", "pRole",
            "workProduct", "pWorkProduct", "state", "pState", "keyword", "pKeyword");
    private static final Set<String> IRI_PARAMETERS = Set.of("process", "activity", "workProduct", "state");
    private static final int MAX_KEYWORD = 60;

    private final FusekiService fuseki;
    private final Map<String, List<Map<String, String>>> optionsCache = new ConcurrentHashMap<>();

    public KnowledgeSearchService(FusekiService fuseki) {
        this.fuseki = fuseki;
    }

    public Map<String, List<Map<String, String>>> options() {
        if (optionsCache.isEmpty()) {
            Map<String, List<Map<String, String>>> grouped = new LinkedHashMap<>();
            for (String type : List.of("process", "activity", "role", "workProduct", "state")) {
                grouped.put(type, new ArrayList<>());
            }
            for (Map<String, String> row : fuseki.select(SparqlTemplate.load("search-options.rq"))) {
                Map<String, String> option = new LinkedHashMap<>();
                option.put("value", row.get("value"));
                option.put("label", row.get("label"));
                if (row.containsKey("parent")) {
                    option.put("process", row.get("parent"));
                }
                grouped.computeIfAbsent(row.get("type"), k -> new ArrayList<>()).add(option);
            }
            optionsCache.putAll(grouped);
        }
        return optionsCache;
    }

    public Map<String, Object> search(Map<String, String> request) {
        Map<String, String> params = validate(request);
        if (params.isEmpty()) {
            throw new IllegalArgumentException("Select at least one search parameter.");
        }
        Map<String, String> iris = new LinkedHashMap<>();
        Map<String, String> literals = new LinkedHashMap<>();
        params.forEach((name, value) -> (IRI_PARAMETERS.contains(name) ? iris : literals).put(VARIABLE.get(name), value));
        String sparql = SparqlTemplate.render(SparqlTemplate.load("knowledge-search.rq"), params.keySet());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("parameters", params);
        response.put("query", "knowledge-search.rq");
        response.put("results", fuseki.select(sparql, iris, literals));
        return response;
    }

    public Map<String, Object> taskDetails(String taskIri) {
        if (taskIri == null || !taskIri.startsWith(CompanyAssessmentService.ISO_NS) || !taskIri.matches("[\\w:/#.\\-]+")) {
            throw new IllegalArgumentException("Unknown task.");
        }
        List<Map<String, String>> inputs = new ArrayList<>(), outputs = new ArrayList<>(), basePractices = new ArrayList<>();
        Set<String> seenBp = new LinkedHashSet<>();
        for (Map<String, String> row : fuseki.select(SparqlTemplate.load("task-details.rq"), Map.of("pTask", taskIri), Map.of())) {
            switch (row.getOrDefault("kind", "")) {
                case "input" -> inputs.add(row);
                case "output" -> outputs.add(row);
                case "basePractice" -> {
                    if (seenBp.add(row.get("bpID"))) {
                        basePractices.add(row);
                    }
                }
                default -> { }
            }
        }
        return Map.of("task", taskIri, "inputs", inputs, "outputs", outputs, "relatedBasePractices", basePractices);
    }

    /** Keeps only known parameters whose value is an ontology option (keyword: bounded free text). */
    Map<String, String> validate(Map<String, String> request) {
        Map<String, String> clean = new LinkedHashMap<>();
        for (String name : PARAMETERS) {
            String value = request == null ? null : request.get(name);
            if (value == null || value.isBlank()) {
                continue;
            }
            value = value.trim();
            if (name.equals("keyword")) {
                value = value.replaceAll("\\p{Cntrl}", "");
                if (value.length() > MAX_KEYWORD) {
                    throw new IllegalArgumentException("Keyword is too long (max " + MAX_KEYWORD + " characters).");
                }
            } else {
                final String v = value;
                boolean known = options().getOrDefault(name, List.of()).stream().anyMatch(o -> v.equals(o.get("value")));
                if (!known) {
                    throw new IllegalArgumentException("Unknown " + name + " value.");
                }
            }
            clean.put(name, value);
        }
        return clean;
    }
}
