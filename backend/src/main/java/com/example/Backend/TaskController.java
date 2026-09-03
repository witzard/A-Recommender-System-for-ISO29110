package com.example.Backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TaskController {

    @GetMapping("/tasks")
    public Map<String, Object> getTasks() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Connect Spring Boot and React Successfully!");
        response.put("system", "Recommender System");
        return response;
    }
}