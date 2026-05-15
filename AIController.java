package com.insurai.controller;

import com.insurai.service.AIService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:3000")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        String response = aiService.getChatResponse(message);
        return Map.of("response", response);
    }

    @GetMapping("/recommend")
    public java.util.List<AIService.PolicyRecommendation> recommend(@RequestParam int age,
            @RequestParam(defaultValue = "50000") double income,
            @RequestParam(defaultValue = "Unknown") String occupation) {
        return aiService.recommendPolicies(age, income, occupation);
    }

    // Example endpoint to test fraud detection manually
    @PostMapping("/fraud-check")
    public Map<String, Object> checkFraud(@RequestBody com.insurai.model.Claim claim) {
        return Map.of("riskScore", aiService.calculateFraudRisk(claim));
    }
}
