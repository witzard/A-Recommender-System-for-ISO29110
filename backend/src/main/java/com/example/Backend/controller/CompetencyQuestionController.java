package com.example.Backend.controller;

import com.example.Backend.service.CompetencyQuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
@RequestMapping("/api/questions")
public class CompetencyQuestionController {
    private static final Logger logger = LoggerFactory.getLogger(CompetencyQuestionController.class);
    private final CompetencyQuestionService competencyQuestionService;

    public CompetencyQuestionController(CompetencyQuestionService competencyQuestionService) {
        this.competencyQuestionService = competencyQuestionService;
    }

    @GetMapping("/{questionId}/answer")
    public ResponseEntity<Map<String, Object>> getAnswer(@PathVariable int questionId) {
        try {
            return ResponseEntity.ok(competencyQuestionService.getAnswer(questionId));
        } catch (IllegalArgumentException exception) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("hasEvidence", false);
            error.put("message", exception.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception exception) {
            logger.error("Failed to answer competency question Q{}", questionId, exception);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("hasEvidence", false);
            error.put("message", "The ontology query failed. Check the backend logs for the underlying SPARQL or Fuseki error.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
