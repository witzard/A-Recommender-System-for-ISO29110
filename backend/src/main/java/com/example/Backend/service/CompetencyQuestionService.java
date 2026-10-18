package com.example.Backend.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CompetencyQuestionService {
    private static final String ISO_PREFIX =
            "PREFIX iso: <http://www.semanticweb.org/mrwit/ontologies/2026/3/ISO29110V2#>\n";

    private static final Map<Integer, List<String>> SEARCH_TERMS = Map.ofEntries(
            Map.entry(1, List.of("risk", "risk analysis")),
            Map.entry(2, List.of("deliverable")),
            Map.entry(3, List.of("customer acceptance", "closure", "delivery")),
            Map.entry(4, List.of("project plan", "review")),
            Map.entry(5, List.of("development environment", "environment")),
            Map.entry(6, List.of("software requirements specification", "requirements")),
            Map.entry(7, List.of("requirements", "customer")),
            Map.entry(8, List.of("baseline", "design")),
            Map.entry(9, List.of("system design", "review")),
            Map.entry(10, List.of("test plan", "requirements", "software design")),
            Map.entry(11, List.of("traceability", "requirements")),
            Map.entry(12, List.of("system design", "requirements")),
            Map.entry(13, List.of("system design", "requirements")),
            Map.entry(14, List.of("software construction", "program")),
            Map.entry(15, List.of("test", "software")),
            Map.entry(16, List.of("system test", "test")),
            Map.entry(17, List.of("test", "software")),
            Map.entry(18, List.of("defect", "problem", "software")),
            Map.entry(19, List.of("schedule", "project plan")),
            Map.entry(20, List.of("system test", "test")),
            Map.entry(21, List.of("test", "requirements")),
            Map.entry(22, List.of("test report", "test")),
            Map.entry(23, List.of("defect", "software")),
            Map.entry(24, List.of("test case", "software design", "requirements")),
            Map.entry(25, List.of("operation", "documentation")),
            Map.entry(26, List.of("operation", "test")),
            Map.entry(27, List.of("user documentation", "user")),
            Map.entry(28, List.of("user documentation", "software")),
            Map.entry(29, List.of("delivery", "customer")),
            Map.entry(30, List.of("delivery", "customer")),
            Map.entry(31, List.of("configuration", "repository", "software")),
            Map.entry(32, List.of("maintenance", "documentation")),
            Map.entry(33, List.of("work product", "evaluation", "review")),
            Map.entry(34, List.of("baseline", "configuration"))
    );

    private final FusekiService fusekiService;

    public CompetencyQuestionService(FusekiService fusekiService) {
        this.fusekiService = fusekiService;
    }

    public Map<String, Object> getAnswer(int questionId) {
        List<String> terms = SEARCH_TERMS.get(questionId);
        if (terms == null) {
            throw new IllegalArgumentException("Unknown competency question ID: Q" + questionId);
        }

        String filter = terms.stream()
                .map(this::escapeSparqlRegex)
                .map(term -> "CONTAINS(LCASE(STR(?searchText)), LCASE(\"" + term + "\"))")
                .reduce((left, right) -> left + " || " + right)
                .orElse("false");

        String taskQuery = ISO_PREFIX + """
            SELECT DISTINCT ?taskID ?taskDescription ?roleName ?inputName ?outputName
            WHERE {
                ?task a iso:Task ;
                      iso:hasTaskID ?taskID ;
                      iso:hasTaskDescription ?taskDescription .
                OPTIONAL { ?task iso:hasRole ?role . ?role iso:hasRoleName ?roleName . }
                OPTIONAL { ?task iso:hasInputWorkProduct ?input . ?input iso:hasInputWorkProductName ?inputName . }
                OPTIONAL { ?task iso:hasOutputWorkProduct ?output . ?output iso:hasOutputWorkProductName ?outputName . }
                BIND(CONCAT(STR(?taskDescription), " ", COALESCE(STR(?roleName), ""), " ",
                    COALESCE(STR(?inputName), ""), " ", COALESCE(STR(?outputName), "")) AS ?searchText)
                FILTER(%s)
            }
            ORDER BY ?taskID
            LIMIT 30
            """.formatted(filter);

        String basePracticeQuery = ISO_PREFIX + """
            SELECT DISTINCT ?bpID ?bpDescription
            WHERE {
                ?bp a iso:BasePractice ;
                    iso:hasBasePracticeID ?bpID ;
                    iso:hasBasePracticeDescription ?bpDescription .
                BIND(STR(?bpDescription) AS ?searchText)
                FILTER(%s)
            }
            ORDER BY ?bpID
            LIMIT 20
            """.formatted(filter);

        List<Map<String, String>> tasks = fusekiService.executeQuery(taskQuery);
        List<Map<String, String>> basePractices = fusekiService.executeQuery(basePracticeQuery);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("questionId", "Q" + questionId);
        response.put("searchTerms", terms);
        response.put("answer", questionId == 2
                ? "The team identifies the deliverables specified in the Agreement, records them in the Project Plan, and identifies the tasks needed to produce them. The Project Plan is then verified and approved, including customer acceptance against the Statement of Work."
                : "The ontology evidence below identifies relevant ISO/IEC 29110 Base Practices and Tasks for this question. Use the most directly related records to determine the process; these records do not establish whether a company has followed it.");
        response.put("tasks", questionId == 2
                ? prioritizeTasks(tasks, List.of("PM.1.12", "PM.1.3", "PM.1.13", "PM.1.14"))
                : tasks);
        response.put("basePractices", questionId == 2
                ? prioritizeBasePractices(basePractices, List.of("PM.BP12", "PM.BP3", "PM.BP13", "PM.BP14"))
                : basePractices);
        response.put("hasEvidence", !tasks.isEmpty() || !basePractices.isEmpty());
        response.put("message", tasks.isEmpty() && basePractices.isEmpty()
                ? "No matching Tasks or Base Practices were found in the connected ontology. This may mean the search terms need refinement; it does not necessarily mean the ontology has no relevant information."
                : "These are matching records retrieved from the ontology. Use them as supporting evidence; they do not prove that the company follows the practice.");
        return response;
    }

    private List<Map<String, String>> prioritizeBasePractices(List<Map<String, String>> records, List<String> preferredIds) {
        return records.stream()
                .filter(record -> preferredIds.contains(record.get("bpID")))
                .sorted(java.util.Comparator.comparingInt(record -> preferredIds.indexOf(record.get("bpID"))))
                .toList();
    }

    private List<Map<String, String>> prioritizeTasks(List<Map<String, String>> records, List<String> preferredIds) {
        Map<String, Map<String, String>> unique = new LinkedHashMap<>();
        for (Map<String, String> record : records) {
            String id = record.get("taskID");
            if (preferredIds.contains(id)) {
                unique.putIfAbsent(id, record);
            }
        }
        return unique.values().stream()
                .sorted(java.util.Comparator.comparingInt(record -> preferredIds.indexOf(record.get("taskID"))))
                .toList();
    }

    private String escapeSparqlRegex(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }
}
