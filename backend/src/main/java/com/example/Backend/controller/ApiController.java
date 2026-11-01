package com.example.Backend.controller;

import com.example.Backend.service.CompanyAssessmentService;
import com.example.Backend.service.KnowledgeSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** All endpoints used by the single-page UI. Read-only: no company or assessment data entry. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private final CompanyAssessmentService companies;
    private final KnowledgeSearchService search;

    public ApiController(CompanyAssessmentService companies, KnowledgeSearchService search) {
        this.companies = companies;
        this.search = search;
    }

    @GetMapping("/companies")
    public List<Map<String, String>> listCompanies() {
        return companies.companies();
    }

    @GetMapping("/companies/{id}/assessment")
    public Map<String, Object> companyAssessment(@PathVariable String id) {
        return companies.assessment(id);
    }

    @GetMapping("/reasoning/status")
    public Map<String, String> reasoningStatus() {
        return companies.reasoningStatus();
    }

    @GetMapping("/search/options")
    public Map<String, List<Map<String, String>>> searchOptions() {
        return search.options();
    }

    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, String> request) {
        return search.search(request);
    }

    @GetMapping("/search/task")
    public Map<String, Object> taskDetails(@RequestParam String iri) {
        return search.taskDetails(iri);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> failure(Exception e) {
        log.error("Knowledge base request failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error",
                "The knowledge base could not be queried. Check that Fuseki is running and dataset.trig is loaded."));
    }
}
