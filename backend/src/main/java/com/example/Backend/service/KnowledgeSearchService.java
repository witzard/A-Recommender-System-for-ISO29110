package com.example.Backend.service;

import com.example.Backend.sparql.QueryTemplates;
import com.example.Backend.sparql.SparqlTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knowledge search with the three query templates (T1, T2, T3). Drop-down values are read from the ontology
 * (search-options.rq) and a request is accepted only with those values. The generated SPARQL is returned with
 * the rows so that the page can show exactly what was run.
 */
@Service
public class KnowledgeSearchService {

    private static final int MAX_KEYWORD_LENGTH = 80;
    private static final int MAX_KEYWORDS = 8;

    private final FusekiService fuseki;
    private volatile Map<String, List<String>> options;

    public KnowledgeSearchService(FusekiService fuseki) {
        this.fuseki = fuseki;
    }

    public Map<String, List<String>> options() {
        Map<String, List<String>> cached = options;
        if (cached == null) {
            cached = new LinkedHashMap<>();
            for (String kind : List.of("role", "activity", "workProduct", "state")) {
                cached.put(kind, new ArrayList<>());
            }
            for (Map<String, String> row : fuseki.select(SparqlTemplate.load("search-options.rq"))) {
                List<String> values = cached.get(row.get("kind"));
                if (values != null && !values.contains(row.get("value"))) {
                    values.add(row.get("value"));
                }
            }
            options = cached;
        }
        return cached;
    }

    public Map<String, Object> search(String template, Map<String, Object> request) {
        String sparql;
        switch (template) {
            case "T1": {
                String role = choice(request, "role");
                String activity = choice(request, "activity");
                String workProduct = choice(request, "workProduct");
                List<String> kwAll = keywords(request, "kwAll");
                List<String> kwAny = keywords(request, "kwAny");
                if (role == null && activity == null && workProduct == null && kwAll.isEmpty() && kwAny.isEmpty()) {
                    throw new IllegalArgumentException("Choose at least one value or enter a keyword.");
                }
                sparql = QueryTemplates.taskFinder(role, activity, workProduct, kwAll, kwAny);
                break;
            }
            case "T2": {
                String workProduct = choice(request, "workProduct");
                String state = choice(request, "state");
                if (workProduct == null || state == null) {
                    throw new IllegalArgumentException("Choose a work product and a state.");
                }
                List<String> kw = keywords(request, "kwProducer");
                sparql = QueryTemplates.workProductStatus(workProduct, state, kw.isEmpty() ? null : kw.get(0));
                break;
            }
            case "T3":
                sparql = QueryTemplates.workProductCriteria(choice(request, "workProduct"));
                break;
            default:
                throw new IllegalArgumentException("Unknown template.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("template", template);
        out.put("sparql", sparql);
        out.put("rows", fuseki.select(sparql));
        return out;
    }

    /** A drop-down value: empty means "any"; otherwise it must be one of the ontology values. */
    private String choice(Map<String, Object> request, String name) {
        Object v = request.get(name);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        String value = v.toString();
        if (!options().getOrDefault(name, List.of()).contains(value)) {
            throw new IllegalArgumentException("Unknown value for " + name + ".");
        }
        return value;
    }

    /** Keywords as a list of phrases (a JSON array or one string). */
    private static List<String> keywords(Map<String, Object> request, String name) {
        Object v = request.get(name);
        List<String> out = new ArrayList<>();
        if (v instanceof List<?>) {
            for (Object x : (List<?>) v) {
                add(out, x);
            }
        } else {
            add(out, v);
        }
        if (out.size() > MAX_KEYWORDS) {
            throw new IllegalArgumentException("Use at most " + MAX_KEYWORDS + " phrases.");
        }
        return out;
    }

    private static void add(List<String> out, Object x) {
        if (x == null || x.toString().isBlank()) {
            return;
        }
        String s = x.toString().trim();
        if (s.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("A phrase can have at most " + MAX_KEYWORD_LENGTH + " characters.");
        }
        out.add(s);
    }
}
