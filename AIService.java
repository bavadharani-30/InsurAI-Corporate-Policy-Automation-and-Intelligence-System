package com.insurai.service;

import com.insurai.model.Booking;
import com.insurai.model.Claim;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class AIService {

    private final Random random = new Random();

    // 1. Policy Recommendation
    // 1. Policy Recommendation
    public record PolicyRecommendation(String policyName, String reason, String matchScore) {
    }

    public java.util.List<PolicyRecommendation> recommendPolicies(int age, double income, String occupation) {
        java.util.List<PolicyRecommendation> recs = new java.util.ArrayList<>();

        // Logic 1: High Income / Corporate
        if (income > 80000) {
            recs.add(new PolicyRecommendation("Platinum Corporate Cover",
                    "Given your high income bracket, this plan offers tax-efficient protection.", "95% Match"));
        } else if (income < 30000) {
            recs.add(new PolicyRecommendation("Micro-Insurance Starter",
                    "A perfect budget-friendly option to start your protection journey.", "90% Match"));
        }

        // Logic 2: Age Demographics
        if (age < 30) {
            recs.add(new PolicyRecommendation("Digital Nomad Health",
                    "Designed for young professionals who travel frequently.", "88% Match"));
        } else if (age > 50) {
            recs.add(new PolicyRecommendation("Senior Life Secure",
                    "Maximizes retirement benefits and critical illness cover.", "92% Match"));
        } else {
            recs.add(new PolicyRecommendation("Family Comprehensive",
                    "Balanced coverage for you and your dependents.", "85% Match"));
        }

        // Logic 3: Occupation Risks (Mock)
        if ("Remote".equalsIgnoreCase(occupation)) {
            recs.add(new PolicyRecommendation("Home Office Property",
                    "Protects your work-from-home equipment.", "High Relevance"));
        }

        return recs;
    }

    // 2. Fraud Detection
    // Logic: High amount (>50000) or description contains "suspicious" keywords
    public double calculateFraudRisk(Claim claim) {
        double score = 0.05; // Base risk

        if (claim.getAmount() > 50000)
            score += 0.4;
        if (claim.getDescription().toLowerCase().contains("lost")
                && claim.getDescription().toLowerCase().contains("cash")) {
            score += 0.3;
        }

        // Random fluctuation to simulate AI model variance
        score += (random.nextDouble() * 0.1);

        return Math.min(score, 0.99);
    }

    // 3. Chatbot Logic (Advanced)
    // 3. Chatbot Logic (Advanced)
    private final com.insurai.config.GroqProperties groqProperties;

    public AIService(com.insurai.config.GroqProperties groqProperties) {
        this.groqProperties = groqProperties;
    }

    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    // 3. Chatbot Logic (Advanced)
    // 3. Chatbot Logic (Advanced)
    // 3. Chatbot Logic (Advanced)
    // 3. Chatbot Logic (Advanced)
    @SuppressWarnings({ "null", "unchecked" })
    public String getChatResponse(String message) {
        String apiKey = groqProperties.getKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            return getManualResponse(message);
        }

        try {
            String url = "https://api.groq.com/openai/v1/chat/completions";

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", "llama-3.3-70b-versatile");
            body.put("messages", java.util.List.of(
                    java.util.Map.of("role", "system", "content",
                            "You are InsurAI, an intelligent, professional, and friendly insurance assistant. Your goal is to help users with their insurance needs. Keep answers concise (under 3 lines) unless asked for detail. Use emoji sparingly."),
                    java.util.Map.of("role", "user", "content", message)));
            body.put("stream", false);

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(
                    body, headers);

            org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>> responseType = new org.springframework.core.ParameterizedTypeReference<>() {
            };

            org.springframework.http.ResponseEntity<java.util.Map<String, Object>> response = restTemplate.exchange(
                    url,
                    java.util.Objects.requireNonNull(org.springframework.http.HttpMethod.POST),
                    entity,
                    responseType);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                java.util.List<java.util.Map<String, Object>> choices = (java.util.List<java.util.Map<String, Object>>) response
                        .getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    java.util.Map<String, Object> messageObj = (java.util.Map<String, Object>) choices.get(0)
                            .get("message");
                    return (String) messageObj.get("content");
                }
            }
        } catch (Exception e) {
            // Silently fall back to manual response
        }

        return getManualResponse(message);
    }

    private String getManualResponse(String message) {
        String msg = message.toLowerCase();

        // 1. Identity & Greeting
        if (msg.contains("hello") || msg.contains("hi") || msg.contains("hey"))
            return "Hello! I am InsurAI, your 24/7 intelligent assistant. I can help with policies, claims, or connecting you to an agent.";
        if (msg.contains("who are you") || msg.contains("your name"))
            return "I'm InsurAI, a virtual agent designed to make insurance simple and transparent.";

        // 2. Policy Definitions (Explainability)
        if (msg.contains("what is premium") || msg.contains("define premium"))
            return "A 'Premium' is the amount you pay for your insurance policy, usually monthly or annually.";
        if (msg.contains("what is deductible") || msg.contains("deductible mean"))
            return "A 'Deductible' is the amount you pay out-of-pocket before your insurance covers the rest.";
        if (msg.contains("coverage") && msg.contains("mean"))
            return "'Coverage' refers to the specific risks or losses that your insurance policy protects you against.";

        // 3. Claims Guidance
        if (msg.contains("file") && (msg.contains("claim") || msg.contains("complaint")))
            return "To file a claim: Go to the 'Claims' tab, click 'File New Claim', and fill in the incident details. You'll need to upload proof documents.";
        if (msg.contains("status") && msg.contains("claim"))
            return "You can check your claim status in the 'My Claims' dashboard. Updates like 'Approved' or 'Reviewing' happen in real-time.";
        if (msg.contains("rejected") && msg.contains("why"))
            return "Claims are usually rejected due to incomplete documentation or policy exclusions. Check the 'My Claims' detail view for the specific reason.";

        // 4. Renewal & Updates
        if (msg.contains("renew") || msg.contains("expire"))
            return "Policies can be renewed in the 'My Policies' section. We recommend renewing 30 days before expiration to avoid coverage gaps.";
        if (msg.contains("upgrade") || msg.contains("plan"))
            return "Looking for more coverage? Visit the 'Plans' page to compare our Platinum and Gold tiers.";

        // 5. Agent Escalation (The "Win" Feature)
        if (msg.contains("human") || msg.contains("person") || msg.contains("talk to agent") || msg.contains("complex"))
            return "I understand some things need a human touch. Please use the 'Schedule' page to book a video call with our top agents.";

        // 6. Fallback
        return "I'm not sure I understand. Try asking about 'filing a claim', 'renewing', or 'what is a deductible'. Or say 'agent' to book a call.";
    }

    // 4. Appointment Success Prediction
    // Logic: Weekends have higher success, Agent experience (mocked)
    public double predictAppointmentSuccess(Booking booking) {
        double chance = 0.5; // Base

        // Weekends better
        var day = booking.getStartTime().getDayOfWeek();
        if (day.getValue() >= 6)
            chance += 0.2;

        // Morning appointments better
        if (booking.getStartTime().getHour() < 12)
            chance += 0.1;

        return Math.min(chance, 0.95);
    }

    // 5. Rejection Analysis
    public RejectionAnalysis analyzeRejection(String reason, com.insurai.model.User user,
            com.insurai.model.Policy policy) {
        // Mock AI Logic
        double riskScore = 0.3; // Low risk default
        StringBuilder explanation = new StringBuilder();
        java.util.List<PolicyRecommendation> alternatives = new java.util.ArrayList<>();

        // Logic based on reason keywords
        if (reason.toLowerCase().contains("income")) {
            riskScore = 0.8;
            explanation.append("User income (").append(user.getIncome())
                    .append(") does not meet the policy requirement. ");
            // Recommend lower premium
            alternatives
                    .add(new PolicyRecommendation("Micro-Insurance Starter", "Fits lower income bracket", "95% Match"));
        } else if (reason.toLowerCase().contains("age")) {
            riskScore = 0.7;
            explanation.append("User age (").append(user.getAge()).append(") is outside the eligible range. ");
            // Recommend age appropriate
            if (user.getAge() > 50) {
                alternatives.add(new PolicyRecommendation("Senior Life Secure", "Optimized for seniors", "98% Match"));
            } else {
                alternatives.add(new PolicyRecommendation("Digital Nomad Health", "Optimized for youth", "90% Match"));
            }
        } else if (reason.toLowerCase().contains("document")) {
            riskScore = 0.5;
            explanation.append("Incomplete or unverifiable documentation provided. ");
            explanation.append("Risk factor: Identity verification failure. ");
        } else {
            // Generic
            explanation.append("Policy criteria not met based on agent assessment. ");
            alternatives.addAll(recommendPolicies(user.getAge() != null ? user.getAge() : 30,
                    user.getIncome() != null ? user.getIncome() : 50000, "Unknown"));
        }

        return new RejectionAnalysis(riskScore, explanation.toString(), alternatives);
    }

    public record RejectionAnalysis(double riskScore, String explanation,
            java.util.List<PolicyRecommendation> recommendations) {
    }
}
