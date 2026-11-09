package com.example.Backend.sparql;

import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge-search queries, one per parameter group (08_Revisions/2026-11-08_QueryParameterGrouping):
 * G1 tasks (taskFinder), G2 work product status and criteria (workProductStatus, workProductCriteria),
 * G3 Base Practice - process outcome - work product (basePracticeOutcome). KnowledgeSearchService chooses which of
 * them run for the parameters the user selected. taskFinder / workProductStatus / workProductCriteria follow
 * 08_Revisions/_tools/templates.py (T1-T3 of Round 6, evaluated there). Differences: values chosen from a drop-down (role, activity, work product) are
 * compared with "=" because they are exact names read from the ontology, and each template is wrapped in an
 * outer query that only adds columns for display (IDs, roles, activity, work products). The outer query never
 * removes or adds a matching task.
 * Every value is written as an escaped SPARQL string literal; keywords become a case-insensitive regex in which
 * regex characters are escaped and a space matches any run of white space.
 */
public final class QueryTemplates {

    public static final String ISO = "http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#";
    public static final String PREFIX = "PREFIX iso: <" + ISO + ">\n";

    private QueryTemplates() {
    }

    /** T1: tasks filtered by role, activity, work product (input or output) and keywords in the task description. */
    public static String taskFinder(String role, String activity, String workProduct, List<String> kwAll, List<String> kwAny) {
        List<String> w = new ArrayList<>();
        w.add("?task a iso:Task ; iso:hasTaskDescription ?taskDesc .");
        if (present(role)) {
            w.add("?task iso:hasRole ?role . ?role iso:hasRoleName ?roleName . FILTER(str(?roleName) = " + lit(role) + ")");
        }
        if (present(activity)) {
            w.add("?task iso:isTaskOf ?act . ?act iso:hasActivityName ?actName . FILTER(str(?actName) = " + lit(activity) + ")");
        }
        if (present(workProduct)) {
            w.add("{ ?task iso:hasOutputWorkProduct ?x . } UNION { ?task iso:hasInputWorkProduct ?x . } "
                    + "?x iso:representsWorkProduct ?wp . ?wp iso:hasWorkProductName ?wpName . FILTER(str(?wpName) = " + lit(workProduct) + ")");
        }
        for (String k : clean(kwAll)) {
            w.add("FILTER(REGEX(str(?taskDesc), " + lit(keywordRegex(k)) + ", \"i\"))");
        }
        List<String> any = clean(kwAny);
        if (!any.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (String k : any) {
                parts.add("REGEX(str(?taskDesc), " + lit(keywordRegex(k)) + ", \"i\")");
            }
            w.add("FILTER(" + String.join(" || ", parts) + ")");
        }
        return PREFIX
                + "SELECT ?task ?taskID ?taskDesc ?activityName\n"
                + "       (GROUP_CONCAT(DISTINCT ?roleAbbr; separator=\", \") AS ?roles)\n"
                + "       (GROUP_CONCAT(DISTINCT ?outName; separator=\"; \") AS ?outputs)\n"
                + "WHERE {\n"
                + "  { SELECT DISTINCT ?task ?taskDesc WHERE {\n"
                + "      " + String.join("\n      ", w) + "\n"
                + "  } }\n"
                + "  OPTIONAL { ?task iso:hasTaskID ?taskID . }\n"
                + "  OPTIONAL { ?task iso:isTaskOf ?a . ?a iso:hasActivityName ?activityName . }\n"
                + "  OPTIONAL { ?task iso:hasRole ?r . ?r iso:hasRoleAbbreviation ?roleAbbr . }\n"
                + "  OPTIONAL { ?task iso:hasOutputWorkProduct ?o . ?o iso:representsWorkProduct ?ow . ?ow iso:hasWorkProductName ?outName . }\n"
                + "}\n"
                + "GROUP BY ?task ?taskID ?taskDesc ?activityName\n"
                + "ORDER BY ?taskID\n";
    }

    /** G2 (T2): tasks and Base Practices that output the work product (in the chosen final state, if any). */
    public static String workProductStatus(String workProduct, String state, List<String> kwAll, List<String> kwAny) {
        String f = keywordFilters("?producerDesc", kwAll, kwAny);
        String st = present(state) ? " FILTER(str(?stateName) = " + lit(state) + ")" : "";
        return PREFIX
                + "SELECT ?producer ?producerID ?producerDesc ?wpName ?stateName\n"
                + "       (GROUP_CONCAT(DISTINCT ?roleAbbr; separator=\", \") AS ?roles)\n"
                + "WHERE {\n"
                + "  { SELECT DISTINCT ?owp ?wpName ?stateName ?producer ?producerDesc WHERE {\n"
                + "      ?owp iso:representsWorkProduct ?wp ; iso:hasFinalState ?st . ?wp iso:hasWorkProductName ?wpName . ?st iso:hasWorkProductStateName ?stateName .\n"
                + "      FILTER(str(?wpName) = " + lit(workProduct) + ")" + st + "\n"
                + "      { ?producer a iso:Task ; iso:hasTaskDescription ?producerDesc ; iso:hasOutputWorkProduct ?owp . } UNION\n"
                + "      { ?producer a iso:BasePractice ; iso:hasBasePracticeDescription ?producerDesc ; iso:hasOutputWorkProduct ?owp . }" + f + "\n"
                + "  } }\n"
                + "  OPTIONAL { ?producer iso:hasTaskID ?taskID . }\n"
                + "  OPTIONAL { ?producer iso:hasBasePracticeID ?bpID . }\n"
                + "  BIND(COALESCE(?taskID, ?bpID) AS ?producerID)\n"
                + "  OPTIONAL { ?producer iso:hasRole ?r . ?r iso:hasRoleAbbreviation ?roleAbbr . }\n"
                + "}\n"
                + "GROUP BY ?producer ?producerID ?producerDesc ?wpName ?stateName\n"
                + "ORDER BY ?producerID\n";
    }

    /**
     * G3: Base Practices with their process outcomes and the work products of the advisor's BP-WP table (rev09: the work
     * products assessed in the pre-assessment of the Base Practice, the same set as RecommendBasePracticeRule uses; the
     * asserted BP hasOutputWorkProduct links over-link and are no longer used here).
     * process / basePractice / outcome are local names already checked against the ontology values; keywords are
     * matched in the Base Practice or the outcome description.
     */
    public static String basePracticeOutcome(String process, String basePractice, String outcome, String workProduct,
                                             List<String> kwAll, List<String> kwAny) {
        List<String> w = new ArrayList<>();
        w.add("?bp a iso:BasePractice ; iso:hasBasePracticeDescription ?bpDesc ; iso:hasProcessOutcome ?po .");
        w.add("?po iso:hasProcessOutcomeDescription ?poDesc .");
        w.add("BIND(CONCAT(str(?bpDesc), \" \", str(?poDesc)) AS ?text)");
        if (present(process)) {
            w.add("<" + ISO + process + "> iso:hasBasePractice ?bp .");
        }
        if (present(basePractice)) {
            w.add("FILTER(?bp = <" + ISO + basePractice + ">)");
        }
        if (present(outcome)) {
            w.add("FILTER(?po = <" + ISO + outcome + ">)");
        }
        if (present(workProduct)) {
            w.add("?fpas a iso:PreAssessment ; iso:hasBasePractice ?bp ; iso:hasAssessedEntity ?fae . ?fae iso:hasEntity ?fw . ?fw iso:hasWorkProductName ?fwName . "
                    + "FILTER(str(?fwName) = " + lit(workProduct) + ")");
        }
        String f = keywordFilters("?text", kwAll, kwAny);
        if (!f.isEmpty()) {
            w.add(f.trim());
        }
        return PREFIX
                + "SELECT ?bp ?bpID ?bpDesc ?po ?process ?poID ?poDesc\n"
                + "       (GROUP_CONCAT(DISTINCT ?outName; separator=\"; \") AS ?outputs)\n"
                + "WHERE {\n"
                + "  { SELECT DISTINCT ?bp ?bpDesc ?po ?poDesc WHERE {\n"
                + "      " + String.join("\n      ", w) + "\n"
                + "  } }\n"
                + "  OPTIONAL { ?bp iso:hasBasePracticeID ?bpID . }\n"
                + "  OPTIONAL { ?po iso:hasProcessOutcomeID ?poID . }\n"
                + "  OPTIONAL { ?proc a iso:Process ; iso:hasBasePractice ?bp . BIND(STRAFTER(str(?proc), \"#\") AS ?process) }\n"
                + "  OPTIONAL { ?pas a iso:PreAssessment ; iso:hasBasePractice ?bp ; iso:hasAssessedEntity ?ae . ?ae iso:hasEntity ?ow . ?ow iso:hasWorkProductName ?outName . }\n"
                + "}\n"
                + "GROUP BY ?bp ?bpID ?bpDesc ?po ?process ?poID ?poDesc\n"
                + "ORDER BY ?bp ?po\n";
    }

    /** T3: assessment characteristics of one work product (or of all when none is chosen). */
    public static String workProductCriteria(String workProduct) {
        String f = present(workProduct) ? " FILTER(str(?wpName) = " + lit(workProduct) + ")" : "";
        return PREFIX
                + "SELECT ?wp ?wpID ?wpName ?wpc ?criterion\n"
                + "WHERE {\n"
                + "  { SELECT DISTINCT ?wp ?wpc ?wpName ?criterion WHERE {\n"
                + "      ?wp a iso:WorkProduct ; iso:hasWorkProductName ?wpName ; iso:hasCharacteristics ?wpc .\n"
                + "      OPTIONAL { ?wpc iso:hasWorkProductCharacteristicList ?criterion . }" + f + "\n"
                + "  } }\n"
                + "  OPTIONAL { ?wp iso:hasWorkProductID ?wpID . }\n"
                + "}\n"
                + "ORDER BY ?wpID ?wpc\n";
    }

    /** FILTERs for "contains all of" / "contains any of" on one variable (empty string when there is no keyword). */
    static String keywordFilters(String var, List<String> kwAll, List<String> kwAny) {
        StringBuilder sb = new StringBuilder();
        for (String k : clean(kwAll)) {
            sb.append(" FILTER(REGEX(str(").append(var).append("), ").append(lit(keywordRegex(k))).append(", \"i\"))");
        }
        List<String> any = clean(kwAny);
        if (!any.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (String k : any) {
                parts.add("REGEX(str(" + var + "), " + lit(keywordRegex(k)) + ", \"i\")");
            }
            sb.append(" FILTER(").append(String.join(" || ", parts)).append(")");
        }
        return sb.toString();
    }

    /** Keyword to regex: regex characters escaped, any white space matches one or more white-space characters. */
    static String keywordRegex(String keyword) {
        StringBuilder sb = new StringBuilder();
        boolean space = false;
        for (char c : keyword.trim().toCharArray()) {
            if (Character.isWhitespace(c)) {
                space = true;
                continue;
            }
            if (space) {
                sb.append("\\s+");
                space = false;
            }
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** SPARQL string literal with escaping. */
    static String lit(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static List<String> clean(List<String> xs) {
        List<String> out = new ArrayList<>();
        if (xs != null) {
            for (String x : xs) {
                if (present(x)) {
                    out.add(x.trim());
                }
            }
        }
        return out;
    }
}
