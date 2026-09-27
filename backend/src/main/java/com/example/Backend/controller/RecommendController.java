package com.example.Backend.controller;

import com.example.Backend.dto.RecommendRequest;
import com.example.Backend.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendationService recommendationService;

    public RecommendController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> recommend(
            @RequestBody RecommendRequest request) {

        Map<String, Object> response = new LinkedHashMap<>();

        try {
            Map<String, Object> results =
                    recommendationService.recommend(request);

            response.put("success", true);
            response.put("results", results);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}
