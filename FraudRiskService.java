package com.insurai.service;

import com.insurai.model.Booking;
import com.insurai.model.User;
import com.insurai.model.UserPolicy;
import com.insurai.repository.BookingRepository;
import com.insurai.repository.UserPolicyRepository;
import com.insurai.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Fraud Risk Analysis Service
 * Calculates fraud risk scores and generates heatmap data
 */
@Service
public class FraudRiskService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final UserPolicyRepository userPolicyRepository;

    public FraudRiskService(
            UserRepository userRepository,
            BookingRepository bookingRepository,
            UserPolicyRepository userPolicyRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.userPolicyRepository = userPolicyRepository;
    }

    /**
     * Get fraud risk heatmap for users (filtered by company if provided)
     */
    public FraudHeatmap getFraudHeatmap(Long companyId) {
        List<User> users;
        if (companyId != null) {
            // In a large system, this should be a DB join.
            // For now, we filter high-level to users who have policies or bookings with
            // this company.
            users = userRepository.findAll().stream()
                    .filter(u -> hasCompanyInteraction(u, companyId))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            users = userRepository.findAll();
        }

        FraudHeatmap heatmap = new FraudHeatmap();
        List<UserRiskScore> riskScores = new ArrayList<>();
        int greenCount = 0, yellowCount = 0, redCount = 0;

        for (User user : users) {
            UserRiskScore score = calculateUserRiskScore(user);
            riskScores.add(score);

            if ("GREEN".equals(score.getRiskLevel()))
                greenCount++;
            else if ("YELLOW".equals(score.getRiskLevel()))
                yellowCount++;
            else if ("RED".equals(score.getRiskLevel()))
                redCount++;
        }

        heatmap.setUserRiskScores(riskScores);
        heatmap.setTotalUsers(users.size());
        heatmap.setGreenCount(greenCount);
        heatmap.setYellowCount(yellowCount);
        heatmap.setRedCount(redCount);
        heatmap.setGeneratedAt(LocalDateTime.now());

        return heatmap;
    }

    private boolean hasCompanyInteraction(User user, Long companyId) {
        // Find if user has bookings with company's agents
        boolean hasBooking = bookingRepository.findByUserId(user.getId()).stream()
                .anyMatch(b -> b.getAgent() != null && b.getAgent().getCompany() != null
                        && b.getAgent().getCompany().getId().equals(companyId));
        if (hasBooking)
            return true;

        // Find if user has policies from this company
        boolean hasPolicy = userPolicyRepository.findByUserId(user.getId()).stream()
                .anyMatch(up -> up.getPolicy() != null && up.getPolicy().getCompany() != null
                        && up.getPolicy().getCompany().getId().equals(companyId));
        return hasPolicy;
    }

    /**
     * Calculate fraud risk score for a specific user
     */
    public UserRiskScore calculateUserRiskScore(User user) {
        UserRiskScore score = new UserRiskScore();
        score.setUserId(user.getId());
        score.setUserName(user.getName());
        score.setUserEmail(user.getEmail());

        // Calculate individual risk factors
        double profileCompletenessScore = calculateProfileCompleteness(user);
        double activityPatternScore = calculateActivityPattern(user);
        double policyClaimRatioScore = calculatePolicyClaimRatio(user);
        double rapidApplicationScore = calculateRapidApplicationScore(user);
        double incomeVerificationScore = calculateIncomeVerificationScore(user);

        // Weighted overall risk score (0-100, higher = more risky)
        double overallRisk = (profileCompletenessScore * 0.15) + // 15% weight
                (activityPatternScore * 0.25) + // 25% weight
                (policyClaimRatioScore * 0.30) + // 30% weight
                (rapidApplicationScore * 0.20) + // 20% weight
                (incomeVerificationScore * 0.10); // 10% weight

        score.setRiskScore(overallRisk);
        score.setRiskLevel(getRiskLevel(overallRisk));
        score.setRiskFactors(identifyRiskFactors(user, profileCompletenessScore, activityPatternScore,
                policyClaimRatioScore, rapidApplicationScore, incomeVerificationScore));

        return score;
    }

    /**
     * Profile completeness (incomplete = higher risk)
     * Returns 0-100 (higher = more risky)
     */
    private double calculateProfileCompleteness(User user) {
        int missingFields = 0;

        if (user.getEmail() == null || user.getEmail().isEmpty())
            missingFields++;
        if (user.getPhone() == null || user.getPhone().isEmpty())
            missingFields++;
        if (user.getAge() == null || user.getAge() <= 0)
            missingFields++;
        if (user.getIncome() == null || user.getIncome() <= 0)
            missingFields++;
        if (user.getAddress() == null || user.getAddress().isEmpty())
            missingFields++;

        // More missing fields = higher risk
        return missingFields * 20.0; // 0, 20, 40, 60, 80, 100
    }

    /**
     * Activity pattern analysis (suspicious patterns = higher risk)
     * Returns 0-100 (higher = more risky)
     */
    private double calculateActivityPattern(User user) {
        List<Booking> bookings = bookingRepository.findByUserId(user.getId());

        if (bookings.isEmpty()) {
            return 10.0; // New user, slight risk
        }

        // Check for suspicious patterns
        double riskScore = 0.0;

        // Pattern 1: Too many bookings in short time
        long recentBookings = bookings.stream()
                .filter(b -> b.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7)))
                .count();

        if (recentBookings > 5)
            riskScore += 30.0; // Very suspicious
        else if (recentBookings > 3)
            riskScore += 15.0;

        // Pattern 2: High rejection rate
        long rejectedCount = bookings.stream()
                .filter(b -> "REJECTED".equals(b.getStatus()))
                .count();

        double rejectionRate = bookings.size() > 0 ? (rejectedCount * 100.0 / bookings.size()) : 0;
        if (rejectionRate > 70)
            riskScore += 25.0;
        else if (rejectionRate > 50)
            riskScore += 10.0;

        // Pattern 3: Many cancelled bookings
        long cancelledCount = bookings.stream()
                .filter(b -> "CANCELLED".equals(b.getStatus()))
                .count();

        if (cancelledCount > 3)
            riskScore += 20.0;

        return Math.min(riskScore, 100.0);
    }

    /**
     * Policy to claim ratio (high claims = higher risk)
     * Returns 0-100 (higher = more risky)
     */
    private double calculatePolicyClaimRatio(User user) {
        List<UserPolicy> policies = userPolicyRepository.findByUserId(user.getId());

        if (policies.isEmpty()) {
            return 0.0; // No policies, no risk from this factor
        }

        // In a real system, you'd check claims data
        // For now, we'll use a simplified approach

        // Check for multiple policies purchased in short time (potential fraud)
        long recentPolicies = policies.stream()
                .filter(p -> p.getPurchasedAt() != null &&
                        p.getPurchasedAt().isAfter(LocalDateTime.now().minusMonths(1)))
                .count();

        if (recentPolicies > 3)
            return 60.0; // High risk
        if (recentPolicies > 2)
            return 30.0; // Medium risk

        return 10.0; // Low risk
    }

    /**
     * Rapid application score (too fast = bot/fraud)
     * Returns 0-100 (higher = more risky)
     */
    private double calculateRapidApplicationScore(User user) {
        List<Booking> bookings = bookingRepository.findByUserId(user.getId());

        if (bookings.size() < 2) {
            return 0.0; // Not enough data
        }

        // Check time between consecutive bookings
        double minMinutesBetween = Double.MAX_VALUE;

        for (int i = 1; i < bookings.size(); i++) {
            LocalDateTime prev = bookings.get(i - 1).getCreatedAt();
            LocalDateTime curr = bookings.get(i).getCreatedAt();
            long minutesBetween = ChronoUnit.MINUTES.between(prev, curr);

            if (minutesBetween < minMinutesBetween) {
                minMinutesBetween = minutesBetween;
            }
        }

        // Very rapid applications = suspicious
        if (minMinutesBetween < 5)
            return 80.0; // Less than 5 minutes = bot
        if (minMinutesBetween < 30)
            return 50.0; // Less than 30 minutes = suspicious
        if (minMinutesBetween < 120)
            return 20.0; // Less than 2 hours = slightly suspicious

        return 0.0;
    }

    /**
     * Income verification score (unverified = higher risk)
     * Returns 0-100 (higher = more risky)
     */
    private double calculateIncomeVerificationScore(User user) {
        // In a real system, you'd check if income is verified with documents
        // For now, we'll use heuristics

        if (user.getIncome() == null || user.getIncome() <= 0) {
            return 80.0; // No income declared = high risk
        }

        // Check if income seems unrealistic
        if (user.getIncome() > 10000000) { // > 1 crore
            return 40.0; // Very high income, needs verification
        }

        if (user.getIncome() < 100000) { // < 1 lakh
            return 30.0; // Very low income, may not afford policies
        }

        return 10.0; // Reasonable income range
    }

    /**
     * Determine risk level from score
     */
    private String getRiskLevel(double score) {
        if (score < 30)
            return "GREEN"; // Low risk
        if (score < 60)
            return "YELLOW"; // Medium risk
        return "RED"; // High risk
    }

    /**
     * Identify specific risk factors
     */
    private List<String> identifyRiskFactors(User user, double profileScore, double activityScore,
            double claimScore, double rapidScore, double incomeScore) {

        List<String> factors = new ArrayList<>();

        if (profileScore > 40) {
            factors.add("Incomplete profile - missing critical information");
        }

        if (activityScore > 40) {
            factors.add("Suspicious activity pattern - multiple rapid bookings or high rejection rate");
        }

        if (claimScore > 40) {
            factors.add("Multiple policies purchased in short timeframe");
        }

        if (rapidScore > 40) {
            factors.add("Rapid-fire applications - possible bot activity");
        }

        if (incomeScore > 40) {
            factors.add("Income verification needed - declared income seems unusual");
        }

        if (factors.isEmpty()) {
            factors.add("No significant risk factors detected");
        }

        return factors;
    }

    /**
     * Get high-risk users for admin review
     */
    public List<UserRiskScore> getHighRiskUsers(Long companyId) {
        FraudHeatmap heatmap = getFraudHeatmap(companyId);

        return heatmap.getUserRiskScores().stream()
                .filter(score -> "RED".equals(score.getRiskLevel()))
                .sorted((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Fraud Heatmap DTO
     */
    public static class FraudHeatmap {
        private List<UserRiskScore> userRiskScores;
        private int totalUsers;
        private int greenCount;
        private int yellowCount;
        private int redCount;
        private LocalDateTime generatedAt;

        // Getters and Setters
        public List<UserRiskScore> getUserRiskScores() {
            return userRiskScores;
        }

        public void setUserRiskScores(List<UserRiskScore> userRiskScores) {
            this.userRiskScores = userRiskScores;
        }

        public int getTotalUsers() {
            return totalUsers;
        }

        public void setTotalUsers(int totalUsers) {
            this.totalUsers = totalUsers;
        }

        public int getGreenCount() {
            return greenCount;
        }

        public void setGreenCount(int greenCount) {
            this.greenCount = greenCount;
        }

        public int getYellowCount() {
            return yellowCount;
        }

        public void setYellowCount(int yellowCount) {
            this.yellowCount = yellowCount;
        }

        public int getRedCount() {
            return redCount;
        }

        public void setRedCount(int redCount) {
            this.redCount = redCount;
        }

        public LocalDateTime getGeneratedAt() {
            return generatedAt;
        }

        public void setGeneratedAt(LocalDateTime generatedAt) {
            this.generatedAt = generatedAt;
        }
    }

    /**
     * User Risk Score DTO
     */
    public static class UserRiskScore {
        private Long userId;
        private String userName;
        private String userEmail;
        private double riskScore; // 0-100
        private String riskLevel; // GREEN, YELLOW, RED
        private List<String> riskFactors;

        // Getters and Setters
        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getUserEmail() {
            return userEmail;
        }

        public void setUserEmail(String userEmail) {
            this.userEmail = userEmail;
        }

        public double getRiskScore() {
            return riskScore;
        }

        public void setRiskScore(double riskScore) {
            this.riskScore = riskScore;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public List<String> getRiskFactors() {
            return riskFactors;
        }

        public void setRiskFactors(List<String> riskFactors) {
            this.riskFactors = riskFactors;
        }
    }
}
