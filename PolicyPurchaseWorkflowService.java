package com.insurai.service;

import com.insurai.dto.AgentReviewDecisionDTO;
import com.insurai.dto.PolicyPurchaseWorkflowDTO;
import com.insurai.model.*;
import com.insurai.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Policy Purchase Workflow Service
 * Manages the complete policy purchase flow with human-in-the-loop
 */
@Service
public class PolicyPurchaseWorkflowService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;
    private final UserPolicyRepository userPolicyRepository;
    private final NotificationService notificationService;

    public PolicyPurchaseWorkflowService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            PolicyRepository policyRepository,
            UserPolicyRepository userPolicyRepository,
            NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.policyRepository = policyRepository;
        this.userPolicyRepository = userPolicyRepository;
        this.notificationService = notificationService;
    }

    /**
     * Step 1: User requests consultation for a specific policy
     */
    @Transactional
    public PolicyPurchaseWorkflowDTO requestPolicyConsultation(Long userId, Long policyId, String reason) {
        User user = userRepository.findById(java.util.Objects.requireNonNull(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));
        Policy policy = policyRepository.findById(java.util.Objects.requireNonNull(policyId))
                .orElseThrow(() -> new RuntimeException("Policy not found"));

        // Create booking with policy context
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setPolicy(policy);
        booking.setReason(reason != null ? reason : "Policy consultation for " + policy.getName());
        booking.setStatus("PENDING");
        booking.setCreatedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);

        // Notify user
        notificationService.createNotification(
                user,
                "Consultation request submitted for " + policy.getName(),
                "INFO");

        return buildWorkflowDTO(saved);
    }

    /**
     * Step 2: Agent reviews the consultation request
     */
    public List<PolicyPurchaseWorkflowDTO> getAgentPendingReviews(Long agentId) {
        List<Booking> pendingBookings = bookingRepository.findByStatus("PENDING");

        return pendingBookings.stream()
                .filter(b -> b.getPolicy() != null) // Only policy-related bookings
                .map(this::buildWorkflowDTO)
                .collect(Collectors.toList());
    }

    /**
     * Step 3: Agent makes decision (Approve/Reject)
     */
    @Transactional
    public PolicyPurchaseWorkflowDTO agentReviewDecision(
            Long bookingId,
            Long agentId,
            AgentReviewDecisionDTO decision) {

        Booking booking = bookingRepository.findById(java.util.Objects.requireNonNull(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        User agent = userRepository.findById(java.util.Objects.requireNonNull(agentId))
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        booking.setAgent(agent);
        booking.setAgentNotes(decision.getAgentNotes());

        if ("APPROVE".equals(decision.getDecision())) {
            return handleApproval(booking, decision);
        } else if ("REJECT".equals(decision.getDecision())) {
            return handleRejection(booking, decision);
        } else {
            booking.setStatus("PENDING");
            bookingRepository.save(booking);
            return buildWorkflowDTO(booking);
        }
    }

    /**
     * Handle approval - trigger policy purchase
     */
    private PolicyPurchaseWorkflowDTO handleApproval(Booking booking, AgentReviewDecisionDTO decision) {
        booking.setStatus("APPROVED");
        booking.setReviewedAt(LocalDateTime.now());

        // Check if requires admin approval for high-risk cases
        if (Boolean.TRUE.equals(decision.getRequiresAdminApproval())) {
            booking.setStatus("PENDING_ADMIN_APPROVAL");
            bookingRepository.save(booking);

            notificationService.createNotification(
                    booking.getUser(),
                    "Your policy application is pending admin approval",
                    "INFO");

            return buildWorkflowDTO(booking);
        }

        // Automatically create UserPolicy (purchase)
        UserPolicy userPolicy = new UserPolicy();
        userPolicy.setUser(booking.getUser());
        userPolicy.setPolicy(booking.getPolicy());
        userPolicy.setStatus("ACTIVE");
        userPolicy.setPurchasedAt(LocalDateTime.now());
        userPolicy.setStartDate(java.time.LocalDate.now());
        userPolicy.setEndDate(java.time.LocalDate.now().plusYears(1));
        userPolicy.setPremium(booking.getPolicy() != null ? booking.getPolicy().getPremium() : 0.0);

        userPolicyRepository.save(userPolicy);
        bookingRepository.save(booking);

        // Notify user of approval
        notificationService.createNotification(
                booking.getUser(),
                "Congratulations! Your " + (booking.getPolicy() != null ? booking.getPolicy().getName() : "Policy")
                        + " policy has been approved and activated!",
                "SUCCESS");

        return buildWorkflowDTO(booking);
    }

    /**
     * Handle rejection - provide AI recommendations
     */
    private PolicyPurchaseWorkflowDTO handleRejection(Booking booking, AgentReviewDecisionDTO decision) {
        booking.setStatus("REJECTED");
        booking.setReviewedAt(LocalDateTime.now());
        booking.setRejectionReason(decision.getRejectionReason());
        bookingRepository.save(booking);

        // Get AI recommendations for alternative policies
        List<PolicyPurchaseWorkflowDTO.AlternativePolicyDTO> alternatives = getAIRecommendations(booking.getUser(),
                booking.getPolicy(), decision.getAlternativePolicyIds());

        // Notify user with alternatives
        String message = "Your application for "
                + (booking.getPolicy() != null ? booking.getPolicy().getName() : "Policy") +
                " was not approved. Reason: " + decision.getRejectionReason();

        if (!alternatives.isEmpty()) {
            message += ". We have recommended " + alternatives.size()
                    + " alternative policies that may suit you better.";
        }

        notificationService.createNotification(
                booking.getUser(),
                message,
                "WARNING");

        PolicyPurchaseWorkflowDTO workflow = buildWorkflowDTO(booking);
        workflow.setAiRecommendations(alternatives);
        return workflow;
    }

    /**
     * Get AI-powered alternative policy recommendations
     */
    private List<PolicyPurchaseWorkflowDTO.AlternativePolicyDTO> getAIRecommendations(
            User user, Policy rejectedPolicy, List<Long> agentSuggestedIds) {

        List<PolicyPurchaseWorkflowDTO.AlternativePolicyDTO> recommendations = new ArrayList<>();

        if (rejectedPolicy == null)
            return recommendations;

        // If agent provided specific alternatives
        if (agentSuggestedIds != null && !agentSuggestedIds.isEmpty()) {
            for (Long policyId : agentSuggestedIds) {
                policyRepository.findById(java.util.Objects.requireNonNull(policyId)).ifPresent(policy -> {
                    PolicyPurchaseWorkflowDTO.AlternativePolicyDTO alt = new PolicyPurchaseWorkflowDTO.AlternativePolicyDTO();
                    alt.setPolicyId(policy.getId());
                    alt.setPolicyName(policy.getName());
                    alt.setPolicyType(policy.getType());
                    alt.setPremium(policy.getPremium());
                    alt.setCoverage(policy.getCoverage());
                    alt.setRecommendationReason("Agent recommended as better fit");
                    alt.setMatchScore(0.85);
                    recommendations.add(alt);
                });
            }
        } else {
            // AI-based recommendations (simplified logic)
            List<Policy> allPolicies = policyRepository.findAll();

            for (Policy policy : allPolicies) {
                if (policy.getId().equals(rejectedPolicy.getId()))
                    continue;

                // Simple matching logic based on premium affordability
                if (policy.getPremium() < rejectedPolicy.getPremium() * 0.8) {
                    PolicyPurchaseWorkflowDTO.AlternativePolicyDTO alt = new PolicyPurchaseWorkflowDTO.AlternativePolicyDTO();
                    alt.setPolicyId(policy.getId());
                    alt.setPolicyName(policy.getName());
                    alt.setPolicyType(policy.getType());
                    alt.setPremium(policy.getPremium());
                    alt.setCoverage(policy.getCoverage());
                    alt.setRecommendationReason("More affordable premium with good coverage");
                    alt.setMatchScore(0.75);
                    recommendations.add(alt);

                    if (recommendations.size() >= 3)
                        break; // Limit to top 3
                }
            }
        }

        return recommendations;
    }

    @Transactional
    public PolicyPurchaseWorkflowDTO adminApproval(Long bookingId, Long adminId, String notes) {
        Booking booking = bookingRepository.findById(java.util.Objects.requireNonNull(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        booking.setStatus("APPROVED");
        booking.setAdminNotes(notes);

        // Create UserPolicy
        UserPolicy userPolicy = new UserPolicy();
        userPolicy.setUser(booking.getUser());
        userPolicy.setPolicy(booking.getPolicy());
        userPolicy.setStatus("ACTIVE");
        userPolicy.setPurchasedAt(LocalDateTime.now());
        userPolicy.setStartDate(java.time.LocalDate.now());
        userPolicy.setEndDate(java.time.LocalDate.now().plusYears(1));
        userPolicy.setPremium(booking.getPolicy() != null ? booking.getPolicy().getPremium() : 0.0);

        userPolicyRepository.save(userPolicy);
        bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getUser(),
                "Your policy has been approved by admin and is now active!",
                "SUCCESS");

        return buildWorkflowDTO(booking);
    }

    /**
     * Get workflow status for a user
     */
    public List<PolicyPurchaseWorkflowDTO> getUserWorkflows(Long userId) {
        List<Booking> userBookings = bookingRepository.findByUserId(userId);

        return userBookings.stream()
                .filter(b -> b.getPolicy() != null)
                .map(this::buildWorkflowDTO)
                .collect(Collectors.toList());
    }

    /**
     * Build workflow DTO from booking
     */
    private PolicyPurchaseWorkflowDTO buildWorkflowDTO(Booking booking) {
        PolicyPurchaseWorkflowDTO dto = new PolicyPurchaseWorkflowDTO();

        dto.setWorkflowId(booking.getId());
        dto.setBookingId(booking.getId());
        dto.setStatus(booking.getStatus());
        dto.setRequestedAt(booking.getCreatedAt());
        dto.setReviewedAt(booking.getReviewedAt());
        dto.setAppointmentTime(booking.getStartTime());
        dto.setAppointmentReason(booking.getReason());
        dto.setRejectionReason(booking.getRejectionReason());
        dto.setAgentNotes(booking.getAgentNotes());
        dto.setAdminNotes(booking.getAdminNotes());

        if (booking.getUser() != null) {
            dto.setUserId(booking.getUser().getId());
            dto.setUserName(booking.getUser().getName());
            dto.setUserEmail(booking.getUser().getEmail());
        }

        if (booking.getPolicy() != null) {
            dto.setPolicyId(booking.getPolicy().getId());
            dto.setPolicyName(booking.getPolicy().getName());
            dto.setPolicyType(booking.getPolicy().getType());
            dto.setPremium(booking.getPolicy().getPremium());
            dto.setCoverage(booking.getPolicy().getCoverage());
        }

        if (booking.getAgent() != null) {
            dto.setAgentId(booking.getAgent().getId());
            dto.setAgentName(booking.getAgent().getName());
        }

        return dto;
    }
}
