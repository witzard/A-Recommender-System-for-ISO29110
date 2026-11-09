package com.example.Backend.controller;

import com.example.Backend.service.AssessmentService;
import com.example.Backend.service.KnowledgeSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Endpoints used by the web page. Read-only. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private final AssessmentService assessments;
    private final KnowledgeSearchService search;

    @Value("${app.reasoning-report:../kb/reasoning-report.json}")
    private String reasoningReport;

    public ApiController(AssessmentService assessments, KnowledgeSearchService search) {
        this.assessments = assessments;
        this.search = search;
    }

    @GetMapping("/projects")
    public List<Map<String, Object>> projects() {
        return assessments.projects();
    }

    @GetMapping("/projects/{id}/assessment")
    public Map<String, Object> assessment(@PathVariable String id) {
        return assessments.assessment(id);
    }

    @GetMapping("/search/options")
    public Map<String, List<Map<String, String>>> searchOptions() {
        return search.options();
    }

    /** Knowledge search by parameters: the service chooses the queries and returns their results together. */
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, Object> request) {
        return search.search(request);
    }

    /** Triples per graph in Fuseki. */
    @GetMapping("/status")
    public Map<String, String> status() {
        return assessments.knowledgeBase();
    }

    /** The report written by the reasoner run (scripts/reason.cmd). */
    @GetMapping(value = "/reasoning-report", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reasoningReport() throws IOException {
        Path p = Path.of(reasoningReport);
        if (!Files.exists(p)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Reasoning report not found. Run scripts\\\\reason.cmd.\"}");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Files.readString(p, StandardCharsets.UTF_8));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> failure(Exception e) {
        log.error("Knowledge base request failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error",
                "The knowledge base could not be queried. Check that Fuseki is running and the data is loaded (scripts\\load-fuseki.cmd)."));
    }
}
