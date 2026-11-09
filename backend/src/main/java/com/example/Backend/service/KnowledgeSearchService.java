package com.example.Backend.service;

import com.example.Backend.sparql.QueryTemplates;
import com.example.Backend.sparql.SparqlTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Knowledge search by parameters (08_Revisions/2026-11-08_QueryParameterGrouping/PARAMETER_GROUPING.md).
 * The user selects parameters only; no question is shown. Each query declares the parameters it accepts and the ones
 * it requires. With S = the selected parameters:
 *   1. a query is a candidate when it accepts at least one parameter of S and every parameter it requires is in S;
 *   2. a candidate is dropped when the parameters of S it uses are a proper subset of those used by another candidate
 *      (the most specific query answers);
 *   3. all remaining candidates run and their results are returned together (union), each labelled with its group.
 * Drop-down values are read from the ontology (search-options.rq) and a request is accepted only with those values.
 */
@Service
public class KnowledgeSearchService {

    private static final int MAX_KEYWORD_LENGTH = 80;
    private static final int MAX_KEYWORDS = 8;
    static final List<String> PARAMETERS = List.of("role", "activity", "process", "basePractice", "outcome", "workProduct", "state", "topic");

    /** One query of a parameter group: accepted and required parameters. */
    static final class QueryDef {
        private final String id, group, title;
        private final Set<String> accepts, requires;

        QueryDef(String id, String group, String title, Set<String> accepts, Set<String> requires) {
            this.id = id;
            this.group = group;
            this.title = title;
            this.accepts = accepts;
            this.requires = requires;
        }

        String id() { return id; }
        String group() { return group; }
        String title() { return title; }
        Set<String> accepts() { return accepts; }
        Set<String> requires() { return requires; }
    }

    static final List<QueryDef> QUERIES = List.of(
            new QueryDef("tasks", "G1", "Tasks", Set.of("role", "activity", "topic"), Set.of()),
            new QueryDef("workProductStatus", "G2", "Tasks and Base Practices that output the work product",
                    Set.of("workProduct", "state", "topic"), Set.of("workProduct")),
            new QueryDef("workProductCriteria", "G2", "Assessment criteria of the work product",
                    Set.of("workProduct"), Set.of("workProduct")),
            new QueryDef("basePracticeOutcome", "G3", "Base Practices, process outcomes and output work products",
                    Set.of("process", "basePractice", "outcome", "workProduct", "topic"), Set.of()));

    private final FusekiService fuseki;
    private volatile Map<String, List<Map<String, String>>> options;

    public KnowledgeSearchService(FusekiService fuseki) {
        this.fuseki = fuseki;
    }

    /** Parameter values: {kind: [{value, label}]}. */
    public Map<String, List<Map<String, String>>> options() {
        Map<String, List<Map<String, String>>> cached = options;
        if (cached == null) {
            cached = new LinkedHashMap<>();
            for (String kind : List.of("role", "activity", "process", "basePractice", "outcome", "workProduct", "state")) {
                cached.put(kind, new ArrayList<>());
            }
            for (Map<String, String> row : fuseki.select(SparqlTemplate.load("search-options.rq"))) {
                List<Map<String, String>> values = cached.get(row.get("kind"));
                String value = row.get("value");
                if (values != null && values.stream().noneMatch(v -> v.get("value").equals(value))) {
                    values.add(Map.of("value", value, "label", row.getOrDefault("label", value)));
                }
            }
            options = cached;
        }
        return cached;
    }

    /** Chooses the queries for the selected parameters (rules 1-2 above). Package-private for tests. */
    static List<QueryDef> choose(Set<String> selected) {
        List<QueryDef> candidates = new ArrayList<>();
        for (QueryDef q : QUERIES) {
            if (!used(q, selected).isEmpty() && selected.containsAll(q.requires())) {
                candidates.add(q);
            }
        }
        List<QueryDef> out = new ArrayList<>();
        for (QueryDef q : candidates) {
            Set<String> mine = used(q, selected);
            boolean lessSpecific = false;
            for (QueryDef other : candidates) {
                Set<String> theirs = used(other, selected);
                if (other != q && theirs.containsAll(mine) && theirs.size() > mine.size()) {
                    lessSpecific = true;
                }
            }
            if (!lessSpecific) {
                out.add(q);
            }
        }
        return out;
    }

    private static Set<String> used(QueryDef q, Set<String> selected) {
        Set<String> s = new LinkedHashSet<>(q.accepts());
        s.retainAll(selected);
        return s;
    }

    public Map<String, Object> search(Map<String, Object> request) {
        String role = choice(request, "role");
        String activity = choice(request, "activity");
        String process = choice(request, "process");
        String basePractice = choice(request, "basePractice");
        String outcome = choice(request, "outcome");
        String workProduct = choice(request, "workProduct");
        String state = choice(request, "state");
        List<String> kwAll = keywords(request, "kwAll");
        List<String> kwAny = keywords(request, "kwAny");

        Set<String> selected = new LinkedHashSet<>();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("role", role);
        values.put("activity", activity);
        values.put("process", process);
        values.put("basePractice", basePractice);
        values.put("outcome", outcome);
        values.put("workProduct", workProduct);
        values.put("state", state);
        values.put("topic", kwAll.isEmpty() && kwAny.isEmpty() ? null : "x");
        values.forEach((k, v) -> {
            if (v != null) {
                selected.add(k);
            }
        });
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Select at least one parameter or enter a topic.");
        }

        List<QueryDef> chosen = choose(selected);
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("No query uses only these parameters. A work product state needs a work product.");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (QueryDef q : chosen) {
            String sparql;
            switch (q.id()) {
                case "tasks":
                    sparql = QueryTemplates.taskFinder(role, activity, null, kwAll, kwAny);
                    break;
                case "workProductStatus":
                    sparql = QueryTemplates.workProductStatus(workProduct, state, kwAll, kwAny);
                    break;
                case "workProductCriteria":
                    sparql = QueryTemplates.workProductCriteria(workProduct);
                    break;
                default:
                    sparql = QueryTemplates.basePracticeOutcome(process, basePractice, outcome, workProduct, kwAll, kwAny);
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("query", q.id());
            r.put("group", q.group());
            r.put("title", q.title());
            r.put("parameters", new ArrayList<>(used(q, selected)));
            r.put("sparql", sparql);
            r.put("rows", fuseki.select(sparql));
            results.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("selected", new ArrayList<>(selected));
        out.put("results", results);
        return out;
    }

    /** A drop-down value: empty means "not selected"; otherwise it must be one of the ontology values. */
    private String choice(Map<String, Object> request, String name) {
        Object v = request.get(name);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        String value = v.toString();
        if (options().getOrDefault(name, List.of()).stream().noneMatch(o -> o.get("value").equals(value))) {
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
