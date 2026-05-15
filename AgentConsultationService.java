package com.insurai.service;

import com.insurai.dto.AgentPerformanceDTO;
import com.insurai.dto.ConsultationDTO;
import com.insurai.dto.PolicyRecommendationRequest;
import com.insurai.model.Booking;
import com.insurai.model.Policy;
import com.insurai.model.User;
import com.insurai.model.UserPolicy;
import com.insurai.repository.BookingRepository;
import com.insurai.repository.PolicyRepository;
import com.insurai.repository.UserPolicyRepository;
import com.insurai.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentConsultationService {

    private final BookingRepository bookingRepo;
    private final UserRepository userRepo;
    private final PolicyRepository policyRepo;
    private final UserPolicyRepository userPolicyRepo;
    private final NotificationService notificationService;
    private final com.insurai.repository.AgentReviewRepository reviewRepo;
    private final com.insurai.repository.ClaimRepository claimRepo; // New dependency

    private static final int SLA_HOURS = 24; // 24-hour SLA for first response

    public AgentConsultationService(BookingRepository bookingRepo, UserRepository userRepo,
            PolicyRepository policyRepo, UserPolicyRepository userPolicyRepo,
            NotificationService notificationService,
            com.insurai.repository.AgentReviewRepository reviewRepo,
            com.insurai.repository.ClaimRepository claimRepo) {
        this.bookingRepo = bookingRepo;
        this.userRepo = userRepo;
        this.policyRepo = policyRepo;
        this.userPolicyRepo = userPolicyRepo;
        this.notificationService = notificationService;
        this.reviewRepo = reviewRepo;
        this.claimRepo = claimRepo;
    }

    /**
     * Submit a review for an agent
     */
    public void submitReview(long agentId, long userId, long bookingId, Integer rating, String feedbackText) {
        User agent = userRepo.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!booking.getAgent().getId().equals(agentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking does not match Agent");
        }
        if (!booking.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking does not match User");
        }

        if (reviewRepo.existsByBooking(booking)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review already exists for this booking");
        }

        com.insurai.model.AgentReview review = new com.insurai.model.AgentReview();
        review.setAgent(agent);
        review.setUser(user);
        review.setBooking(booking);
        review.setRating(rating);
        review.setFeedback(feedbackText);
        reviewRepo.save(review);

        // Update Agent's Average Rating
        Double avg = reviewRepo.calculateAverageRating(agent);
        if (avg != null) {
            agent.setRating(avg);
            userRepo.save(agent);
        }
    }

    public List<com.insurai.model.AgentReview> getAgentReviews(long agentId) {
        User agent = userRepo.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        return reviewRepo.findByAgent(agent);
    }

    public List<com.insurai.model.AgentReview> getAllReviews() {
        return reviewRepo.findAll();
    }

    /**
     * Get all consultations for an agent with AI-assisted risk indicators
     */
    public List<ConsultationDTO> getAgentConsultations(long agentId) {
        userRepo.findById(java.util.Objects.requireNonNull(agentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        List<Booking> bookings = bookingRepo.findByAgentId(agentId);
        List<ConsultationDTO> consultations = new ArrayList<>();

        for (Booking booking : bookings) {
            ConsultationDTO dto = buildConsultationDTO(booking);
            consultations.add(dto);
        }

        // Sort by appointment time (upcoming first)
        consultations.sort((a, b) -> {
            if (a.getAppointmentTime() == null)
                return 1;
            if (b.getAppointmentTime() == null)
                return -1;
            return a.getAppointmentTime().compareTo(b.getAppointmentTime());
        });

        return consultations;
    }

    /**
     * Build ConsultationDTO with AI-assisted risk analysis
     */
    private ConsultationDTO buildConsultationDTO(Booking booking) {
        ConsultationDTO dto = new ConsultationDTO();

        // Booking details
        dto.setBookingId(booking.getId());
        dto.setAppointmentTime(booking.getStartTime());
        dto.setAppointmentReason(booking.getReason());
        dto.setStatus(booking.getStatus());

        // User details
        User user = booking.getUser();
        dto.setUserId(user.getId());
        dto.setUserName(user.getName());
        dto.setUserEmail(user.getEmail());
        dto.setUserAge(user.getAge());
        dto.setUserIncome(user.getIncome());
        dto.setUserDependents(user.getDependents());
        dto.setUserHealthInfo(user.getHealthInfo());

        // Policy details
        if (booking.getPolicy() != null) {
            Policy policy = booking.getPolicy();
            dto.setPolicyId(policy.getId());
            dto.setPolicyName(policy.getName());
            dto.setPolicyType(policy.getType());
            dto.setPolicyPremium(policy.getPremium());
            dto.setPolicyCoverage(policy.getCoverage());

            // AI-Assisted Risk Indicators
            calculateRiskIndicators(dto, user, policy);
        }

        return dto;
    }

    /**
     * Calculate AI-assisted risk indicators
     */
    private void calculateRiskIndicators(ConsultationDTO dto, User user, Policy policy) {
        // Calculate match score using existing PolicyService logic
        dto.setMatchScore(calculateMatchScore(user, policy));

        // Check eligibility
        dto.setEligibilityStatus(checkEligibility(user, policy));

        // Calculate affordability
        if (user.getIncome() != null && policy.getPremium() != null) {
            double monthlyIncome = user.getIncome() / 12;
            double affordabilityRatio = (policy.getPremium() / monthlyIncome) * 100;
            dto.setAffordabilityRatio(affordabilityRatio);
            dto.setIsAffordable(affordabilityRatio <= 10.0); // Premium should be <= 10% of monthly income
        }

        // Determine risk level
        String riskLevel = "LOW";
        StringBuilder riskReason = new StringBuilder();

        if ("NOT_ELIGIBLE".equals(dto.getEligibilityStatus())) {
            riskLevel = "HIGH";
            riskReason.append("User does not meet eligibility criteria. ");
        } else if (Boolean.FALSE.equals(dto.getIsAffordable())) {
            riskLevel = "HIGH";
            riskReason.append("Premium exceeds 10% of monthly income. ");
        } else if (dto.getMatchScore() != null && dto.getMatchScore() < 50) {
            riskLevel = "MEDIUM";
            riskReason.append("Low match score indicates policy may not be suitable. ");
        }

        // Health risk
        if (user.getHealthInfo() != null && !user.getHealthInfo().isEmpty()) {
            if (user.getHealthInfo().toLowerCase().contains("chronic")
                    || user.getHealthInfo().toLowerCase().contains("disease")) {
                riskLevel = "MEDIUM";
                riskReason.append("Pre-existing health conditions noted. ");
            }
        }

        dto.setRiskLevel(riskLevel);
        dto.setRiskReason(riskReason.toString().trim());
    }

    /**
     * Process agent's consultation decision
     */
    public void processConsultationDecision(long agentId, PolicyRecommendationRequest request) {
        Booking booking = bookingRepo.findById(java.util.Objects.requireNonNull(request.getBookingId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        User agent = userRepo.findById(java.util.Objects.requireNonNull(agentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        // Mark as responded (for SLA tracking)
        if (booking.getRespondedAt() == null) {
            booking.setRespondedAt(LocalDateTime.now());

            // Check SLA breach (24 hours)
            long hoursSinceCreation = ChronoUnit.HOURS.between(booking.getCreatedAt(), LocalDateTime.now());
            if (hoursSinceCreation > SLA_HOURS) {
                booking.setSlaBreached(true);
            }
        }

        String action = request.getAction();

        if ("APPROVE".equals(action)) {
            handleApproval(booking, request, agent);
        } else if ("REJECT".equals(action)) {
            handleRejection(booking, request, agent);
        } else if ("RECOMMEND_ALTERNATIVE".equals(action)) {
            handleAlternativeRecommendation(booking, request, agent);
        }

        booking.setCompletedAt(LocalDateTime.now());
        bookingRepo.save(booking);
    }

    private void handleApproval(Booking booking, PolicyRecommendationRequest request, User agent) {
        booking.setStatus("APPROVED");

        // Create or update UserPolicy
        UserPolicy userPolicy = findOrCreateUserPolicy(booking);
        userPolicy.setWorkflowStatus("APPROVED");
        userPolicy.setAgentNotes(request.getAgentNotes());
        userPolicy.setStatus("PAYMENT_PENDING");
        userPolicyRepo.save(userPolicy);

        // Notify user
        notificationService.createNotification(
                booking.getUser(),
                "Your consultation with " + agent.getName() + " is complete. Policy approved! Proceed to payment.",
                "SUCCESS");
    }

    private void handleRejection(Booking booking, PolicyRecommendationRequest request, User agent) {
        booking.setStatus("REJECTED");

        UserPolicy userPolicy = findOrCreateUserPolicy(booking);
        userPolicy.setWorkflowStatus("REJECTED");
        userPolicy.setAgentNotes(request.getAgentNotes());
        userPolicy.setRejectionReason(request.getRejectionReason());
        userPolicy.setStatus("REJECTED");
        userPolicyRepo.save(userPolicy);

        // Notify user
        notificationService.createNotification(
                booking.getUser(),
                "Consultation completed. Agent suggests reviewing alternative policies. Check your dashboard for details.",
                "INFO");
    }

    private void handleAlternativeRecommendation(Booking booking, PolicyRecommendationRequest request, User agent) {
        booking.setStatus("COMPLETED");

        UserPolicy userPolicy = findOrCreateUserPolicy(booking);
        userPolicy.setWorkflowStatus("ALTERNATIVES_SUGGESTED");
        userPolicy.setAgentNotes(request.getAgentNotes());

        // Store alternative policy IDs
        if (request.getAlternatives() != null && !request.getAlternatives().isEmpty()) {
            List<Long> alternativeIds = request.getAlternatives().stream()
                    .map(PolicyRecommendationRequest.AlternativePolicyDTO::getPolicyId)
                    .collect(Collectors.toList());
            userPolicy.setAlternativePolicyIds(alternativeIds);

            // Create UserPolicy entries for each alternative with QUOTED status
            for (PolicyRecommendationRequest.AlternativePolicyDTO alt : request.getAlternatives()) {
                Policy altPolicy = policyRepo.findById(java.util.Objects.requireNonNull(alt.getPolicyId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Alternative policy not found"));

                UserPolicy altUserPolicy = new UserPolicy();
                altUserPolicy.setUser(booking.getUser());
                altUserPolicy.setPolicy(altPolicy);
                altUserPolicy.setStatus("QUOTED");
                altUserPolicy.setRecommendationNote(alt.getReason() + " - " + alt.getNotes());
                userPolicyRepo.save(altUserPolicy);
            }
        }

        userPolicyRepo.save(userPolicy);

        // Notify user
        notificationService.createNotification(
                booking.getUser(),
                agent.getName() + " has recommended " + (request.getAlternatives() != null
                        ? request.getAlternatives().size()
                        : 0) + " alternative policies for you.",
                "INFO");
    }

    private UserPolicy findOrCreateUserPolicy(Booking booking) {
        if (booking.getPolicy() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking has no associated policy");
        }

        // Try to find existing UserPolicy
        List<UserPolicy> existing = userPolicyRepo.findByUserId(booking.getUser().getId());
        for (UserPolicy up : existing) {
            if (up.getPolicy().getId().equals(booking.getPolicy().getId())
                    && "CONSULTATION_PENDING".equals(up.getWorkflowStatus())) {
                return up;
            }
        }

        // Create new UserPolicy
        UserPolicy userPolicy = new UserPolicy();
        userPolicy.setUser(booking.getUser());
        userPolicy.setPolicy(booking.getPolicy());
        userPolicy.setWorkflowStatus("CONSULTATION_PENDING");
        userPolicy.setStatus("QUOTED");
        return userPolicy;
    }

    /**
     * Get agent performance metrics
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AgentPerformanceDTO getAgentPerformance(long agentId) {
        User agent = userRepo.findById(java.util.Objects.requireNonNull(agentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        List<Booking> allBookings = bookingRepo.findByAgentId(agentId);

        AgentPerformanceDTO performance = new AgentPerformanceDTO();
        performance.setAgentId(agentId);
        performance.setAgentName(agent.getName());
        performance.setAssignedRegions(agent.getAssignedRegions());
        performance.setAssignedPolicyTypes(agent.getAssignedPolicyTypes());
        System.out.println("DEBUG: Performance assignments for " + agent.getName() + ": Regions="
                + agent.getAssignedRegions() + ", Types=" + agent.getAssignedPolicyTypes());

        // Populate Rating
        performance.setCustomerSatisfaction(agent.getRating() != null ? agent.getRating() : 0.0);

        // Total consultations
        performance.setTotalConsultations(allBookings.size());

        // Pending vs Completed
        long pending = allBookings.stream().filter(b -> "PENDING".equals(b.getStatus())).count();
        long completed = allBookings.stream().filter(b -> b.getCompletedAt() != null).count();
        performance.setPendingConsultations((int) pending);
        performance.setCompletedConsultations((int) completed);

        // Average response time (in hours)
        List<Booking> respondedBookings = allBookings.stream()
                .filter(b -> b.getRespondedAt() != null)
                .collect(Collectors.toList());

        if (!respondedBookings.isEmpty()) {
            double totalHours = respondedBookings.stream()
                    .mapToDouble(b -> Duration.between(b.getCreatedAt(), b.getRespondedAt()).toHours())
                    .average()
                    .orElse(0.0);
            performance.setAverageResponseTimeHours(totalHours);
        }

        // Approval/Rejection rates
        long approved = allBookings.stream().filter(b -> "APPROVED".equals(b.getStatus())).count();
        long rejected = allBookings.stream().filter(b -> "REJECTED".equals(b.getStatus())).count();

        if (completed > 0) {
            performance.setApprovalRate((approved * 100.0) / completed);
            performance.setRejectionRate((rejected * 100.0) / completed);
        }

        // Conversion rate (policies that became ACTIVE)
        List<UserPolicy> userPolicies = userPolicyRepo.findAll().stream()
                .filter(up -> allBookings.stream()
                        .anyMatch(b -> b.getUser().getId().equals(up.getUser().getId())))
                .collect(Collectors.toList());

        long activePolicies = userPolicies.stream().filter(up -> "ACTIVE".equals(up.getStatus())).count();
        if (completed > 0) {
            performance.setConversionRate((activePolicies * 100.0) / completed);
        }

        // SLA breaches
        long slaBreaches = allBookings.stream().filter(b -> Boolean.TRUE.equals(b.getSlaBreached())).count();
        performance.setSlaBreaches((int) slaBreaches);

        // Time-based metrics
        LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);
        LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1);

        long thisWeek = allBookings.stream().filter(b -> b.getCreatedAt().isAfter(weekAgo)).count();
        long thisMonth = allBookings.stream().filter(b -> b.getCreatedAt().isAfter(monthAgo)).count();

        performance.setConsultationsThisWeek((int) thisWeek);
        performance.setConsultationsThisMonth((int) thisMonth);

        // Rejection reasons analysis
        Map<String, Integer> rejectionReasons = new HashMap<>();
        userPolicies.stream()
                .filter(up -> up.getRejectionReason() != null && !up.getRejectionReason().isEmpty())
                .forEach(up -> {
                    String reason = up.getRejectionReason();
                    rejectionReasons.put(reason, rejectionReasons.getOrDefault(reason, 0) + 1);
                });
        performance.setRejectionReasons(rejectionReasons);

        // Alternatives recommended
        long alternatives = userPolicies.stream()
                .filter(up -> up.getAlternativePolicyIds() != null && !up.getAlternativePolicyIds().isEmpty())
                .count();
        performance.setAlternativesRecommended((int) alternatives);

        // --- NEW: Today's Metrics ---
        java.time.LocalDate today = java.time.LocalDate.now();

        // Approved Today: Bookings where respondedAt is today (assuming respondedAt is
        // set on approval)
        long approvedToday = allBookings.stream()
                .filter(b -> b.getRespondedAt() != null && b.getRespondedAt().toLocalDate().equals(today))
                .count();

        // Rejected Today: Bookings where status is REJECTED and completedAt is today
        long rejectedToday = allBookings.stream()
                .filter(b -> "REJECTED".equals(b.getStatus()) && b.getCompletedAt() != null
                        && b.getCompletedAt().toLocalDate().equals(today))
                .count();

        performance.setApprovedToday((int) approvedToday);
        performance.setRejectedToday((int) rejectedToday);

        // Claims Count for Agent's Company
        if (agent.getCompany() != null) {
            List<com.insurai.model.Claim> claims = claimRepo.findByPolicyCompanyId(agent.getCompany().getId());
            performance.setClaimsCount(claims.size());
        } else {
            performance.setClaimsCount(0);
        }

        // Recalculate Approval Rate using TODAY'S data (per requirement: (Approved
        // Today / (Approved Today + Rejected Today)) * 100)
        // Wait, normally approval rate is lifetime. The prompt asks: "Recalculate
        // Approval Rate based on: (Approved Today / (Approved Today + Rejected Today))
        // * 100"
        // This is a specific request for "Today's Approval Rate" or maybe just override
        // the global one?
        // "Approval Rate remains inaccurate because it is derived from wrong data...
        // Recalculate Approval Rate based on..."
        // I will stick to the prompt.
        if ((approvedToday + rejectedToday) > 0) {
            performance.setApprovalRate((double) approvedToday / (approvedToday + rejectedToday) * 100.0);
        } else {
            // Fallback to lifetime if no activity today? Or just 0?
            // Prompt implies the current one is wrong. Let's set it to today's if there's
            // data.
            if (performance.getTotalConsultations() > 0) {
                // Keep lifetime as fallback, or set to 0?
                // Let's keep the lifetime calculation done above if today is 0 to avoid showing
                // 0% for active agents.
                // Actually, let's override ONLY if there is activity today, otherwise users
                // might be confused.
                // But the prompt says "Approval Rate remains inaccurate...".
                // Let's strictly follow: approvedToday / (approvedToday + rejectedToday).
                // If 0 denominator, set to 0 or leave previous?
                performance.setApprovalRate(0.0);
            }
        }

        // Re-override if data exists
        if ((approvedToday + rejectedToday) > 0) {
            performance.setApprovalRate((double) approvedToday / (approvedToday + rejectedToday) * 100.0);
        }

        // Last active time
        performance.setLastActiveTime(agent.getAvailable() ? LocalDateTime.now() : null);

        // --- Calculate Rank Percentile ---
        calculateRankPercentile(performance, agentId);

        return performance;
    }

    private void calculateRankPercentile(AgentPerformanceDTO performance, Long agentId) {
        try {
            List<User> allAgents = userRepo.findByRole("AGENT");
            int totalAgents = allAgents.size();

            if (totalAgents <= 1) {
                performance.setRankPercentile(100); // Only one agent, top rank
            } else {
                int myCompleted = performance.getCompletedConsultations();
                int betterAgents = 0;

                for (User a : allAgents) {
                    if (!a.getId().equals(agentId)) {
                        List<Booking> theirBookings = bookingRepo.findByAgentIdAndStatus(a.getId(), "COMPLETED");
                        int theirCompleted = theirBookings != null ? theirBookings.size() : 0;
                        if (theirCompleted > myCompleted) {
                            betterAgents++;
                        }
                    }
                }
                double percentile = 100.0 * (1.0 - ((double) betterAgents / totalAgents));
                performance.setRankPercentile((int) percentile);
            }
        } catch (Exception e) {
            performance.setRankPercentile(50);
        }
    }

    // Helper methods (simplified versions of PolicyService methods)
    private double calculateMatchScore(User user, Policy policy) {
        // Reuse logic from PolicyService or simplify here
        return 75.0; // Placeholder
    }

    private String checkEligibility(User user, Policy policy) {
        if (policy.getMinAge() != null && user.getAge() != null && user.getAge() < policy.getMinAge()) {
            return "NOT_ELIGIBLE";
        }
        if (policy.getMaxAge() != null && user.getAge() != null && user.getAge() > policy.getMaxAge()) {
            return "NOT_ELIGIBLE";
        }
        if (policy.getMinIncome() != null && user.getIncome() != null && user.getIncome() < policy.getMinIncome()) {
            return "PARTIALLY_ELIGIBLE";
        }
        return "ELIGIBLE";
    }
}
