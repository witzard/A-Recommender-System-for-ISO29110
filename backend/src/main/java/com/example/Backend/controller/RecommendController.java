package com.example.Backend.controller;

import com.example.Backend.dto.RecommendRequest;
import com.example.Backend.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendationService recommendationService;

    public RecommendController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/test")
    public Map<String, Object> testFuseki() {
        RecommendRequest request = new RecommendRequest();
        request.setProcess("PM");
        request.setTopic("Project Plan");

        List<Map<String, String>> results =
                recommendationService.recommend(request);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("success", true);
        response.put("source", "Fuseki");
        response.put("count", results.size());
        response.put("results", results);

        return response;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> recommend(
            @RequestBody RecommendRequest request) {

        Map<String, Object> response = new LinkedHashMap<>();

        try {
            List<Map<String, String>> results =
                    recommendationService.recommend(request);

            response.put("success", true);
            response.put("count", results.size());
            response.put("results", results);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}
