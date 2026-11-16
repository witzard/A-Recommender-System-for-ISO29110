package com.example.Backend.service;

import com.example.Backend.sparql.SparqlTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Assessment results and recommendations of one project.
 * Scores come from the SPARQL queries Q1-Q3 v3 (computed from the percentages), characteristics that are not
 * achieved from Q4 v3, and ratings, recommended tasks / Base Practices and linked process outcomes from the facts
 * inferred by Pellet (SWRL rules in the ontology). Roles are not recommended (rev08): the roles of each recommended task
 * are read from Task hasRole. This class only groups and formats rows; it contains no recommendation logic.
 * Two score modes (2026-11-16): "score" = Q1-Q3 v3, every assessment result counts (no percentage or N/A = 0, advisor
 * convention); "scoreAssessed" = q1-q3-*-assessed.rq, only results with a percentage. "coverage" says how many work
 * products are assessed, Not applicable (R8) or not assessed yet, from the ratings inferred by Pellet.
 */
@Service
public class AssessmentService {

    public static final String ISO = "http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#";
    private static final String ID_PATTERN = "[A-Za-z0-9._-]{1,64}";

    private final FusekiService fuseki;

    public AssessmentService(FusekiService fuseki) {
        this.fuseki = fuseki;
    }

    public List<Map<String, Object>> projects() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, String> r : fuseki.select(SparqlTemplate.load("projects.rq"))) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", local(r.get("pj")));
            p.put("projectName", r.get("projectName"));
            p.put("enterprise", local(r.get("ent")));
            p.put("enterpriseName", r.get("enterpriseName"));
            p.put("mock", r.getOrDefault("projectComment", "").contains("MOCK DATA")
                    || r.getOrDefault("enterpriseComment", "").contains("MOCK DATA"));
            out.add(p);
        }
        return out;
    }

    public Map<String, Object> assessment(String projectId) {
        Map<String, Object> project = null;
        if (projectId != null && projectId.matches(ID_PATTERN)) {
            for (Map<String, Object> p : projects()) {
                if (projectId.equals(p.get("id"))) {
                    project = new LinkedHashMap<>(p);
                }
            }
        }
        if (project == null) {
            throw new IllegalArgumentException("Unknown project.");
        }
        String pj = ISO + projectId;
        Map<String, String> bind = Map.of("pPj", pj);

        // (a) profile
        List<Map<String, String>> excluded = new ArrayList<>();
        for (Map<String, String> r : select("project-profile.rq", bind)) {
            if ("excludesWorkProduct".equals(r.get("property"))) {
                excluded.add(Map.of("wpID", r.get("wpID"), "wpName", r.get("value")));
            } else {
                project.put(r.get("property"), r.get("value"));
            }
        }
        project.put("excludedWorkProducts", excluded);

        // (b) scores: Q3 process, Q2 Base Practice, Q1 work product (rows of all projects, filtered here)
        Map<String, Map<String, String>> processAssessed = new LinkedHashMap<>();
        for (Map<String, String> r : select("q3-process-score-assessed.rq", Map.of())) {
            if (pj.equals(r.get("pj"))) {
                processAssessed.put(local(r.get("proc")), r);
            }
        }
        List<Map<String, String>> processScores = new ArrayList<>();
        for (Map<String, String> r : select("q3-process-score-v3.rq", Map.of())) {
            if (pj.equals(r.get("pj"))) {
                String proc = local(r.get("proc"));
                Map<String, String> a = processAssessed.getOrDefault(proc, Map.of());
                Map<String, String> p = new LinkedHashMap<>();
                p.put("process", proc);
                p.put("nBP", r.get("nBP"));
                p.put("score", r.get("processScore"));
                p.put("nBPAssessed", a.getOrDefault("nBP", "0"));
                p.put("scoreAssessed", a.get("processScore"));
                processScores.add(p);
            }
        }
        Map<String, Map<String, String>> bpInfo = new LinkedHashMap<>();
        for (Map<String, String> r : select("base-practices.rq", Map.of())) {
            bpInfo.put(r.get("bp"), r);
        }
        Map<String, Map<String, String>> bpAssessed = new LinkedHashMap<>();
        for (Map<String, String> r : select("q2-base-practice-score-assessed.rq", Map.of())) {
            if (pj.equals(r.get("pj"))) {
                bpAssessed.put(r.get("bp"), r);
            }
        }
        List<Map<String, String>> bpScores = new ArrayList<>();
        for (Map<String, String> r : select("q2-base-practice-score-v3.rq", Map.of())) {
            if (pj.equals(r.get("pj"))) {
                Map<String, String> info = bpInfo.getOrDefault(r.get("bp"), Map.of());
                Map<String, String> b = new LinkedHashMap<>();
                b.put("bpID", info.getOrDefault("bpID", local(r.get("bp"))));
                b.put("description", info.getOrDefault("description", ""));
                b.put("nWP", r.get("nWP"));
                b.put("score", r.get("bpScore"));
                Map<String, String> a = bpAssessed.getOrDefault(r.get("bp"), Map.of());
                b.put("nWPAssessed", a.getOrDefault("nWP", "0"));
                b.put("scoreAssessed", a.get("bpScore"));
                bpScores.add(b);
            }
        }
        bpScores.sort(Comparator.comparing((Map<String, String> b) -> b.get("bpID").replaceAll("\\d+$", ""))
                .thenComparingInt(b -> trailingNumber(b.get("bpID"))));

        Map<String, String> wpScore = new LinkedHashMap<>();
        for (Map<String, String> r : select("q1-work-product-score-v3.rq", Map.of())) {
            if (pj.equals(r.get("pj"))) {
                wpScore.put(r.get("ae"), r.get("wpScore"));
            }
        }
        Map<String, String> wpScoreAssessed = new LinkedHashMap<>();
        for (Map<String, String> r : select("q1-work-product-score-assessed.rq", Map.of())) {
            if (pj.equals(r.get("pj"))) {
                wpScoreAssessed.put(r.get("ae"), r.get("wpScore"));
            }
        }
        Map<String, Map<String, Object>> workProducts = new TreeMap<>();
        Map<String, String> wpOfAe = new LinkedHashMap<>();
        Map<String, Map<String, Object>> byWp = new LinkedHashMap<>();
        for (Map<String, String> r : select("assessed-entities.rq", bind)) {
            Map<String, Object> w = workProducts.computeIfAbsent(r.get("wpID"), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ae", local(r.get("ae")));
                m.put("wpID", r.get("wpID"));
                m.put("wpName", r.get("wpName"));
                m.put("score", wpScore.get(r.get("ae")));
                m.put("scoreAssessed", wpScoreAssessed.get(r.get("ae")));
                m.put("ratings", new LinkedHashMap<String, String>());
                return m;
            });
            @SuppressWarnings("unchecked")
            Map<String, String> ratings = (Map<String, String>) w.get("ratings");
            ratings.put(r.get("rating"), r.get("nItems"));
            wpOfAe.put(r.get("ae"), r.get("wp"));
            byWp.put(r.get("wp"), w);
        }
        Map<String, Object> coverage = coverage(workProducts.values());

        // (c) characteristics rated Not achieved / Partially achieved (Q4 v3)
        List<Map<String, String>> gaps = new ArrayList<>();
        for (Map<String, String> r : select("q4-characteristic-gaps-v3.rq", Map.of())) {
            if (pj.equals(r.get("pj"))) {
                Map<String, Object> w = byWp.get(r.get("wp"));
                Map<String, String> g = new LinkedHashMap<>();
                g.put("wpID", w == null ? local(r.get("wp")) : String.valueOf(w.get("wpID")));
                g.put("wpName", w == null ? "" : String.valueOf(w.get("wpName")));
                g.put("characteristic", local(r.get("wpc")));
                g.put("item", r.getOrDefault("item", ""));
                g.put("rating", local(r.get("rating")));
                g.put("percentage", r.get("pct"));
                gaps.add(g);
            }
        }

        // (d) recommendations inferred by Pellet, grouped by assessed entity
        Map<String, Map<String, Object>> recs = new TreeMap<>();
        for (Map<String, String> r : select("recommended-tasks.rq", bind)) {
            Map<String, String> t = new LinkedHashMap<>();
            t.put("taskID", r.get("taskID"));
            t.put("description", r.get("description"));
            t.put("roles", r.getOrDefault("roles", ""));
            t.put("link", "true".equals(r.get("producesWP")) ? "produces" : "true".equals(r.get("usesWP")) ? "uses" : "");
            list(recFor(recs, r.get("ae"), wpOfAe, byWp, gaps), "tasks").add(t);
        }
        for (Map<String, String> r : select("recommended-base-practices.rq", bind)) {
            list(recFor(recs, r.get("ae"), wpOfAe, byWp, gaps), "basePractices")
                    .add(Map.of("bpID", r.get("bpID"), "description", r.get("description")));
        }
        // (e) process outcomes linked to the work product with a gap (recommendOutcome, rev08)
        for (Map<String, String> r : select("recommended-outcomes.rq", bind)) {
            String po = local(r.get("po"));
            Map<String, String> o = new LinkedHashMap<>();
            o.put("outcome", po);
            o.put("process", po.contains(".") ? po.substring(0, po.indexOf('.')) : "");
            o.put("outcomeID", r.get("poID"));
            o.put("description", r.get("description"));
            o.put("basePractices", r.getOrDefault("bps", ""));
            list(recFor(recs, r.get("ae"), wpOfAe, byWp, gaps), "outcomes").add(o);
        }
        for (Map<String, Object> rec : recs.values()) {
            sortBy(list(rec, "tasks"), "taskID");
            sortBy(list(rec, "basePractices"), "bpID");
            sortBy(list(rec, "outcomes"), "outcome");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("project", project);
        out.put("coverage", coverage);
        out.put("processScores", processScores);
        out.put("basePracticeScores", bpScores);
        out.put("workProducts", new ArrayList<>(workProducts.values()));
        out.put("gaps", gaps);
        out.put("recommendations", new ArrayList<>(recs.values()));
        return out;
    }

    public Map<String, String> knowledgeBase() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, String> r : fuseki.select(SparqlTemplate.load("knowledge-base-check.rq"))) {
            out.put(r.get("graph"), r.get("triples"));
        }
        return out;
    }

    /**
     * Assessment coverage from the ratings of each work product (Pellet facts): "not_applicable" when a result is
     * Not applicable (R8), "assessed" when a result has a rating N/P/L/F (= has a percentage), else "not_assessed".
     * Sets "status" on each work product.
     */
    static Map<String, Object> coverage(java.util.Collection<Map<String, Object>> wps) {
        List<Map<String, String>> notApplicable = new ArrayList<>();
        List<Map<String, String>> notAssessed = new ArrayList<>();
        int assessed = 0, items = 0, itemsAssessed = 0, itemsNotApplicable = 0, itemsNotAssessed = 0;
        for (Map<String, Object> w : wps) {
            @SuppressWarnings("unchecked")
            Map<String, String> ratings = (Map<String, String>) w.get("ratings");
            int na = 0, rated = 0, none = 0;
            for (Map.Entry<String, String> e : ratings.entrySet()) {
                int n = Integer.parseInt(e.getValue());
                if ("Not_applicable".equals(e.getKey())) {
                    na += n;
                } else if ("Not_rated".equals(e.getKey())) {
                    none += n;
                } else {
                    rated += n;
                }
            }
            items += na + rated + none;
            itemsAssessed += rated;
            itemsNotApplicable += na;
            itemsNotAssessed += none;
            Map<String, String> id = Map.of("wpID", String.valueOf(w.get("wpID")), "wpName", String.valueOf(w.get("wpName")));
            if (na > 0) {
                w.put("status", "not_applicable");
                notApplicable.add(id);
            } else if (rated > 0) {
                w.put("status", "assessed");
                assessed++;
            } else {
                w.put("status", "not_assessed");
                notAssessed.add(id);
            }
        }
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("workProducts", wps.size());
        c.put("assessed", assessed);
        c.put("notApplicable", notApplicable);
        c.put("notAssessed", notAssessed);
        c.put("items", items);
        c.put("itemsAssessed", itemsAssessed);
        c.put("itemsNotApplicable", itemsNotApplicable);
        c.put("itemsNotAssessed", itemsNotAssessed);
        return c;
    }

    private List<Map<String, String>> select(String file, Map<String, String> iriBindings) {
        return fuseki.select(SparqlTemplate.load(file), iriBindings, Map.of());
    }

    private static Map<String, Object> recFor(Map<String, Map<String, Object>> recs, String ae, Map<String, String> wpOfAe,
                                              Map<String, Map<String, Object>> byWp, List<Map<String, String>> gaps) {
        Map<String, Object> w = byWp.get(wpOfAe.get(ae));
        String wpID = w == null ? local(ae) : String.valueOf(w.get("wpID"));
        return recs.computeIfAbsent(wpID, k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ae", local(ae));
            m.put("wpID", wpID);
            m.put("wpName", w == null ? "" : w.get("wpName"));
            List<Map<String, String>> own = new ArrayList<>();
            for (Map<String, String> g : gaps) {
                if (wpID.equals(g.get("wpID"))) {
                    own.add(g);
                }
            }
            m.put("gaps", own);
            m.put("tasks", new ArrayList<Map<String, String>>());
            m.put("basePractices", new ArrayList<Map<String, String>>());
            m.put("outcomes", new ArrayList<Map<String, String>>());
            return m;
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> list(Map<String, Object> m, String key) {
        return (List<Map<String, String>>) m.get(key);
    }

    private static void sortBy(List<Map<String, String>> xs, String key) {
        xs.sort(Comparator.comparing((Map<String, String> x) -> x.get(key).replaceAll("\\d+", ""))
                .thenComparing(x -> numericKey(x.get(key))));
    }

    /** "SI.1.10" -> "0000000001.0000000010" so numbers sort in order. */
    private static String numericKey(String s) {
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(s);
        while (m.find()) {
            sb.append(String.format("%010d.", Long.parseLong(m.group())));
        }
        return sb.toString();
    }

    private static int trailingNumber(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)$").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static String local(String iri) {
        if (iri == null) {
            return "";
        }
        int i = iri.lastIndexOf('#');
        return i >= 0 ? iri.substring(i + 1) : iri;
    }
}
