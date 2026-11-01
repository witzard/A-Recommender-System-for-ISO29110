package com.example.Backend.service;

import com.example.Backend.sparql.SparqlTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capability B - assessment-based QA practice recommendation.
 * Reads company/assessment data (asserted), ratings and recommend(?ae, ?rp) facts produced by Pellet SWRL
 * reasoning (named graph urn:iso29110:graph:inferred-swrl) and their explanation records.
 * This class contains NO recommendation logic: if the reasoner did not infer it, it is not shown.
 */
@Service
public class CompanyAssessmentService {

    public static final String ISO_NS = "http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#";
    private static final String ID_PATTERN = "[A-Za-z0-9._-]{1,64}";

    private final FusekiService fuseki;

    public CompanyAssessmentService(FusekiService fuseki) {
        this.fuseki = fuseki;
    }

    public List<Map<String, String>> companies() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> row : fuseki.select(SparqlTemplate.load("companies.rq"))) {
            Map<String, String> c = new LinkedHashMap<>();
            c.put("id", localName(row.get("enterprise")));
            c.put("name", row.get("name"));
            c.put("profile", row.getOrDefault("profile", ""));
            out.add(c);
        }
        return out;
    }

    public Map<String, Object> assessment(String companyId) {
        if (companyId == null || !companyId.matches(ID_PATTERN)
                || companies().stream().noneMatch(c -> companyId.equals(c.get("id")))) {
            throw new IllegalArgumentException("Unknown company.");
        }
        List<Map<String, String>> overview = fuseki.select(SparqlTemplate.load("company-overview.rq"),
                Map.of("pEnterprise", ISO_NS + companyId), Map.of());
        if (overview.isEmpty()) {
            throw new IllegalArgumentException("No assessment found for this company.");
        }
        Map<String, String> o = overview.get(0);
        String assessedEntity = o.get("assessedEntity");

        List<Map<String, String>> results = new ArrayList<>();
        Map<String, Integer> ratingCounts = new LinkedHashMap<>();
        for (Map<String, String> row : fuseki.select(SparqlTemplate.load("assessment-results.rq"),
                Map.of("pAssessedEntity", assessedEntity), Map.of())) {
            Map<String, String> r = new LinkedHashMap<>(row);
            r.put("bpID", row.get("bpID") == null ? null : row.get("bpID").trim());
            r.put("rating", row.containsKey("rating") ? localName(row.get("rating")) : "Not_rated");
            r.putIfAbsent("ratingSource", "none");
            ratingCounts.merge(r.get("rating"), 1, Integer::sum);
            results.add(r);
        }

        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (Map<String, String> row : fuseki.select(SparqlTemplate.load("recommendations.rq"),
                Map.of("pAssessedEntity", assessedEntity), Map.of())) {
            Map<String, Object> rec = new LinkedHashMap<>(row);
            rec.put("bpID", row.get("bpID").trim());
            rec.put("rating", localName(row.get("rating")));
            rec.put("necessaryRules", Arrays.asList(row.getOrDefault("necessaryRules", "").split("\\|")));
            recommendations.add(rec);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("company", Map.of("id", companyId, "name", o.get("name"), "profile", o.getOrDefault("profile", "")));
        response.put("project", pick(o, "project", "projectName", "projectDescription", "start", "end", "staff"));
        response.put("assessment", pick(o, "assessment", "assessmentDate", "consultantName", "assessedEntity"));
        response.put("ratingCounts", ratingCounts);
        response.put("results", results);
        response.put("recommendations", recommendations);
        return response;
    }

    public Map<String, String> reasoningStatus() {
        List<Map<String, String>> rows = fuseki.select(SparqlTemplate.load("reasoning-status.rq"));
        return rows.isEmpty() ? Map.of("status", "No reasoning run found in the dataset. Rebuild and reload kb/build/dataset.trig.")
                : rows.get(0);
    }

    private static Map<String, String> pick(Map<String, String> row, String... keys) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : keys) {
            out.put(k, row.get(k));
        }
        return out;
    }

    static String localName(String iri) {
        return iri == null ? null : iri.substring(iri.indexOf('#') + 1);
    }
}
