package com.insurai.controller;

import com.insurai.model.Policy;
import com.insurai.model.User;
import com.insurai.repository.PolicyRepository;
import com.insurai.repository.UserRepository;
import com.insurai.service.AIAssistantService;
import com.insurai.service.AIRecommendationEngine;
import com.insurai.service.FraudRiskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI Features Controller
 * Exposes AI-powered features via REST API
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:3000")
public class AIFeaturesController {

        private final AIRecommendationEngine recommendationEngine;
        private final AIAssistantService assistantService;
        private final FraudRiskService fraudRiskService;
        private final UserRepository userRepository;
        private final PolicyRepository policyRepository;

        public AIFeaturesController(
                        AIRecommendationEngine recommendationEngine,
                        AIAssistantService assistantService,
                        FraudRiskService fraudRiskService,
                        UserRepository userRepository,
                        PolicyRepository policyRepository) {
                this.recommendationEngine = recommendationEngine;
                this.assistantService = assistantService;
                this.fraudRiskService = fraudRiskService;
                this.userRepository = userRepository;
                this.policyRepository = policyRepository;
        }

        /**
         * Get AI policy recommendations with explanations
         * POST /api/ai/recommendations
         */
        @PostMapping("/recommendations")
        @PreAuthorize("hasRole('USER')")
        public ResponseEntity<List<AIRecommendationEngine.PolicyRecommendation>> getRecommendations(
                        @RequestBody Map<String, Object> request) {

                Object userIdObj = request.get("userId");
                if (userIdObj == null) {
                        throw new IllegalArgumentException("userId is required");
                }
                Long userId = Long.valueOf(userIdObj.toString());

                Long rejectedPolicyId = request.get("rejectedPolicyId") != null
                                ? Long.valueOf(request.get("rejectedPolicyId").toString())
                                : null;
                int limit = request.get("limit") != null
                                ? Integer.parseInt(request.get("limit").toString())
                                : 3;

                User user = userRepository.findById(java.util.Objects.requireNonNull(userId))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Policy rejectedPolicy = null;
                if (rejectedPolicyId != null) {
                        rejectedPolicy = policyRepository.findById(rejectedPolicyId)
                                        .orElseThrow(() -> new RuntimeException("Policy not found"));
                }

                List<AIRecommendationEngine.PolicyRecommendation> recommendations = recommendationEngine
                                .getRecommendations(user, rejectedPolicy, limit);

                return ResponseEntity.ok(recommendations);
        }

        /**
         * Get personalized recommendations for user
         * GET /api/ai/recommendations/{userId}
         */
        @GetMapping("/recommendations/{userId}")
        @PreAuthorize("hasAnyRole('USER', 'AGENT')")
        public ResponseEntity<List<AIRecommendationEngine.PolicyRecommendation>> getUserRecommendations(
                        @PathVariable Long userId,
                        @RequestParam(defaultValue = "5") int limit) {

                User user = userRepository.findById(java.util.Objects.requireNonNull(userId))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                List<AIRecommendationEngine.PolicyRecommendation> recommendations = recommendationEngine
                                .getRecommendations(user, null, limit);

                return ResponseEntity.ok(recommendations);
        }

        /**
         * Chat with AI Assistant
         * POST /api/ai/assistant/chat
         */
        @PostMapping("/assistant/chat")
        @PreAuthorize("hasRole('USER')")
        public ResponseEntity<AIAssistantService.AssistantResponse> chatWithAssistant(
                        @RequestBody Map<String, Object> request) {

                Object userIdObj = request.get("userId");
                if (userIdObj == null) {
                        throw new IllegalArgumentException("userId is required");
                }
                Long userId = Long.valueOf(userIdObj.toString());

                Object queryObj = request.get("query");
                if (queryObj == null) {
                        throw new IllegalArgumentException("query is required");
                }
                String query = queryObj.toString();

                User user = userRepository.findById(java.util.Objects.requireNonNull(userId))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                AIAssistantService.AssistantResponse response = assistantService.getResponse(user, query);

                return ResponseEntity.ok(response);
        }

        /**
         * Get fraud risk heatmap (Admin only)
         * GET /api/ai/fraud/heatmap
         */
        @GetMapping("/fraud/heatmap")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN')")
        public ResponseEntity<FraudRiskService.FraudHeatmap> getFraudHeatmap(
                        org.springframework.security.core.Authentication auth) {
                Long companyId = getCompanyIdFromEmail(auth.getName());
                FraudRiskService.FraudHeatmap heatmap = fraudRiskService.getFraudHeatmap(companyId);
                return ResponseEntity.ok(heatmap);
        }

        /**
         * Get high-risk users (Admin only)
         * GET /api/ai/fraud/high-risk
         */
        @GetMapping("/fraud/high-risk")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN')")
        public ResponseEntity<List<FraudRiskService.UserRiskScore>> getHighRiskUsers(
                        org.springframework.security.core.Authentication auth) {
                Long companyId = getCompanyIdFromEmail(auth.getName());
                List<FraudRiskService.UserRiskScore> highRiskUsers = fraudRiskService.getHighRiskUsers(companyId);
                return ResponseEntity.ok(highRiskUsers);
        }

        private Long getCompanyIdFromEmail(String email) {
                // Find the company of the admin by their email.
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null && ("COMPANY_ADMIN".equals(user.getRole()) || "COMPANY".equals(user.getRole()))
                                && user.getCompany() != null) {
                        return user.getCompany().getId();
                }
                return null;
        }

        /**
         * Get fraud risk score for specific user (Admin/Agent)
         * GET /api/ai/fraud/user/{userId}
         */
        @GetMapping("/fraud/user/{userId}")
        @PreAuthorize("hasAnyRole('AGENT', 'SUPER_ADMIN', 'COMPANY_ADMIN')")
        public ResponseEntity<FraudRiskService.UserRiskScore> getUserRiskScore(
                        @PathVariable Long userId) {

                User user = userRepository.findById(java.util.Objects.requireNonNull(userId))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                FraudRiskService.UserRiskScore riskScore = fraudRiskService.calculateUserRiskScore(user);

                return ResponseEntity.ok(riskScore);
        }
}
