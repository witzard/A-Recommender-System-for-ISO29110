package com.example.Backend.controller;

import com.example.Backend.service.OptionsService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
@RequestMapping("/api/options")
public class OptionsController {

    private final OptionsService optionsService;

    public OptionsController(OptionsService optionsService) {
        this.optionsService = optionsService;
    }

    @GetMapping
    public Map<String, Object> getOptions() {
        return optionsService.getOptions();
    }
}
