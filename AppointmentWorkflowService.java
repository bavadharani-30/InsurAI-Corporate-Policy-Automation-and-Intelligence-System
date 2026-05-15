package com.insurai.service;

import com.insurai.dto.AppointmentDecisionResponse;
import com.insurai.model.*;
import com.insurai.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced Appointment & Policy Approval Workflow Service
 * Implements structured approval flow with AI-assisted decision making
 */
@Service
public class AppointmentWorkflowService {

        private final BookingRepository bookingRepository;
        private final UserRepository userRepository;
        private final PolicyRepository policyRepository;
        private final UserPolicyRepository userPolicyRepository;
        private final NotificationService notificationService;
        private final AIRiskAssessmentService aiRiskAssessmentService;
        private final AIRecommendationEngine aiRecommendationEngine;
        private final GoogleCalendarService calendarService;
        private final EmailService emailService;

        public AppointmentWorkflowService(
                        BookingRepository bookingRepository,
                        UserRepository userRepository,
                        PolicyRepository policyRepository,
                        UserPolicyRepository userPolicyRepository,
                        NotificationService notificationService,
                        AIRiskAssessmentService aiRiskAssessmentService,
                        AIRecommendationEngine aiRecommendationEngine,
                        GoogleCalendarService calendarService,
                        EmailService emailService) {
                this.bookingRepository = bookingRepository;
                this.userRepository = userRepository;
                this.policyRepository = policyRepository;
                this.userPolicyRepository = userPolicyRepository;
                this.notificationService = notificationService;
                this.aiRiskAssessmentService = aiRiskAssessmentService;
                this.aiRecommendationEngine = aiRecommendationEngine;
                this.calendarService = calendarService;
                this.emailService = emailService;
        }

        /**
         * PHASE 1: Create policy-based appointment booking
         * Status: REQUESTED
         */
        @Transactional
        public Booking createPolicyAppointment(long userId, long agentId, long policyId,
                        LocalDateTime startTime, LocalDateTime endTime, String reason) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
                User agent = userRepository.findById(agentId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Agent not found"));
                Policy policy = policyRepository.findById(policyId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Policy not found"));

                Booking booking = new Booking();
                booking.setUser(user);
                booking.setAgent(agent);
                booking.setPolicy(policy);
                booking.setStartTime(startTime);
                booking.setEndTime(endTime);
                booking.setReason(reason != null ? reason : "Policy consultation for " + policy.getName());
                booking.setStatus("REQUESTED");
                booking.setCreatedAt(LocalDateTime.now());

                Booking saved = bookingRepository.save(booking);

                // Notify agent
                notificationService.createNotification(
                                agent,
                                "New appointment request from " + user.getName() + " for " + policy.getName(),
                                "INFO");

                // Notify user
                notificationService.createNotification(
                                user,
                                "Appointment request submitted. Waiting for agent approval.",
                                "INFO");

                // Send Confirmation Email
                emailService.sendAppointmentRequested(booking);

                return saved;
        }

        /**
         * PHASE 2 - SCENARIO 1: Agent approves meeting
         * Status: REQUESTED -> MEETING_APPROVED
         */
        @Transactional
        public AppointmentDecisionResponse approveMeeting(long bookingId, long agentId, String notes) {
                Booking booking = bookingRepository.findById(bookingId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Booking not found"));
                User agent = userRepository.findById(agentId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Agent not found"));

                if (!"REQUESTED".equals(booking.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Can only approve meetings with REQUESTED status");
                }

                // Create Google Meet link
                String meetingLink = calendarService.createMeeting(
                                "Insurance Consultation: " + booking.getUser().getName(),
                                "Policy Discussion for " + (booking.getPolicy() != null ? booking.getPolicy().getName()
                                                : "General"),
                                booking.getStartTime().toString(),
                                booking.getEndTime().toString(),
                                booking.getUser().getEmail(),
                                agent.getEmail());

                booking.setStatus("MEETING_APPROVED");
                booking.setMeetingLink(meetingLink);
                booking.setAgentNotes(notes);
                booking.setRespondedAt(LocalDateTime.now());
                bookingRepository.save(booking);

                // Notify user
                notificationService.createNotification(
                                booking.getUser(),
                                "Your appointment has been approved! Meeting link: " + meetingLink,
                                "SUCCESS");

                // Send email notification
                emailService.sendAppointmentApproved(booking);

                AppointmentDecisionResponse response = new AppointmentDecisionResponse();
                response.setBookingId(booking.getId());
                response.setStatus("MEETING_APPROVED");
                response.setMeetingLink(meetingLink);
                response.setMessage("Meeting approved successfully");

                return response;
        }

        /**
         * PHASE 2 - SCENARIO 2: Agent marks as completed (early consultation)
         * Status: REQUESTED/MEETING_APPROVED -> CONSULTED
         */
        @Transactional
        public AppointmentDecisionResponse markAsCompleted(long bookingId, long agentId, String consultationNotes) {
                Booking booking = bookingRepository.findById(bookingId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Booking not found"));

                if (!List.of("REQUESTED", "MEETING_APPROVED").contains(booking.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Can only complete bookings with REQUESTED or MEETING_APPROVED status");
                }

                booking.setStatus("CONSULTED");
                booking.setCompletedAt(LocalDateTime.now());
                booking.setAgentNotes(consultationNotes);
                bookingRepository.save(booking);

                // Notify user
                notificationService.createNotification(
                                booking.getUser(),
                                "Consultation completed. Awaiting policy approval decision.",
                                "INFO");

                AppointmentDecisionResponse response = new AppointmentDecisionResponse();
                response.setBookingId(booking.getId());
                response.setStatus("CONSULTED");
                response.setMessage("Consultation marked as completed");

                return response;
        }

        /**
         * PHASE 2: Agent approves policy after consultation
         * Status: CONSULTED -> POLICY_APPROVED -> ISSUED
         */
        @Transactional
        public AppointmentDecisionResponse approvePolicy(long bookingId, long agentId, String approvalNotes) {
                Booking booking = bookingRepository.findById(bookingId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Booking not found"));

                if (!"CONSULTED".equals(booking.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Can only approve policy for CONSULTED appointments");
                }

                if (booking.getPolicy() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "No policy associated with this booking");
                }

                // Update booking status
                booking.setStatus("POLICY_APPROVED");
                booking.setAgentNotes(approvalNotes);
                bookingRepository.save(booking);

                // Issue policy (PAYMENT_PENDING state)
                UserPolicy userPolicy = new UserPolicy();
                userPolicy.setUser(booking.getUser());
                userPolicy.setPolicy(booking.getPolicy());
                userPolicy.setStatus("ISSUED");
                userPolicy.setWorkflowStatus("PAYMENT_PENDING");
                userPolicy.setAgentNotes(approvalNotes);
                userPolicy.setStartDate(java.time.LocalDate.now());
                userPolicy.setEndDate(java.time.LocalDate.now().plusYears(1));
                userPolicy.setPremium(booking.getPolicy().getPremium());
                userPolicyRepository.save(userPolicy);

                // Notify user
                notificationService.createNotification(
                                booking.getUser(),
                                "🎉 Policy approved! " + booking.getPolicy().getName() +
                                                " has been issued. Please complete payment to activate.",
                                "SUCCESS");

                // Send Policy Approved Email
                emailService.sendPolicyApproved(userPolicy);

                AppointmentDecisionResponse response = new AppointmentDecisionResponse();
                response.setBookingId(booking.getId());
                response.setStatus("POLICY_APPROVED");
                response.setUserPolicyId(userPolicy.getId());
                response.setMessage("Policy approved and issued successfully");

                return response;
        }

        /**
         * PHASE 3: Agent rejects appointment with AI assistance
         * Status: REQUESTED/CONSULTED -> REJECTED
         */
        @Transactional
        public AppointmentDecisionResponse rejectAppointment(long bookingId, long agentId,
                        String rejectionReason, boolean includeAIRecommendations) {
                Booking booking = bookingRepository.findById(bookingId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Booking not found"));

                if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Rejection reason is mandatory");
                }

                booking.setStatus("REJECTED");
                booking.setRejectionReason(rejectionReason);
                booking.setCompletedAt(LocalDateTime.now());
                bookingRepository.save(booking);

                AppointmentDecisionResponse response = new AppointmentDecisionResponse();
                response.setBookingId(booking.getId());
                response.setStatus("REJECTED");
                response.setRejectionReason(rejectionReason);

                // Get AI assistance if requested and policy exists
                if (includeAIRecommendations && booking.getPolicy() != null) {
                        // Get risk assessment
                        com.insurai.dto.RiskAssessmentDTO riskAssessment = aiRiskAssessmentService
                                        .assessRisk(booking.getUser().getId(), booking.getPolicy().getId());

                        response.setAiRiskScore(riskAssessment.getRiskScore() != null
                                        ? riskAssessment.getRiskScore().doubleValue()
                                        : null);
                        response.setAiRiskFactors(riskAssessment.getRiskFactors().stream()
                                        .map(com.insurai.dto.RiskAssessmentDTO.RiskFactor::getFactor)
                                        .collect(Collectors.toList()));

                        // Get alternative policy recommendations
                        List<AIRecommendationEngine.PolicyRecommendation> aiRecommendations = aiRecommendationEngine
                                        .getRecommendations(booking.getUser(), booking.getPolicy(), 5);

                        List<AppointmentDecisionResponse.AlternativePolicy> alternatives = new ArrayList<>();
                        for (AIRecommendationEngine.PolicyRecommendation rec : aiRecommendations) {
                                Policy altPolicy = rec.getPolicy();
                                if (altPolicy != null) {
                                        AppointmentDecisionResponse.AlternativePolicy alt = new AppointmentDecisionResponse.AlternativePolicy();
                                        alt.setPolicyId(altPolicy.getId());
                                        alt.setPolicyName(altPolicy.getName());
                                        alt.setPolicyType(altPolicy.getType());
                                        alt.setPremium(altPolicy.getPremium());
                                        alt.setCoverage(altPolicy.getCoverage());
                                        // Get first reason if available, otherwise use a default message
                                        String reason = (rec.getReasons() != null && !rec.getReasons().isEmpty())
                                                        ? rec.getReasons().get(0)
                                                        : "Better suited to your profile";
                                        alt.setRecommendationReason(reason);
                                        alt.setMatchScore(rec.getMatchScore());
                                        alternatives.add(alt);
                                }
                        }
                        response.setAlternativePolicies(alternatives);

                        // Generate AI explanation
                        String aiExplanation = generateRejectionExplanation(riskAssessment, rejectionReason);
                        response.setAiExplanation(aiExplanation);
                }

                // Notify user with alternatives
                String message = "Your appointment for " +
                                (booking.getPolicy() != null ? booking.getPolicy().getName() : "consultation") +
                                " was not approved. Reason: " + rejectionReason;

                if (response.getAlternativePolicies() != null && !response.getAlternativePolicies().isEmpty()) {
                        message += ". We have " + response.getAlternativePolicies().size() +
                                        " alternative policies that may better suit your profile.";
                }

                notificationService.createNotification(
                                booking.getUser(),
                                message,
                                "WARNING");

                // Send Rejection Email
                emailService.sendAppointmentRejected(booking,
                                (response.getAlternativePolicies() != null
                                                && !response.getAlternativePolicies().isEmpty())
                                                                ? aiRecommendationEngine
                                                                                .getRecommendations(booking.getUser(),
                                                                                                booking.getPolicy(), 3)
                                                                                .stream()
                                                                                .map(AIRecommendationEngine.PolicyRecommendation::getPolicy)
                                                                                .collect(Collectors.toList())
                                                                : null,
                                rejectionReason);

                response.setMessage("Appointment rejected with AI recommendations");
                return response;
        }

        /**
         * User activates policy by paying premium
         * Status: ISSUED -> ACTIVE
         */
        @Transactional
        public UserPolicy activatePolicy(long userPolicyId, long userId) {
                UserPolicy userPolicy = userPolicyRepository.findById(userPolicyId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Policy not found"));

                if (!userPolicy.getUser().getId().equals(userId)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");
                }

                if (!"ISSUED".equals(userPolicy.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Can only activate policies with ISSUED status");
                }

                userPolicy.setStatus("ACTIVE");
                userPolicy.setWorkflowStatus("ACTIVE");
                userPolicy.setPurchasedAt(LocalDateTime.now());
                UserPolicy saved = userPolicyRepository.save(userPolicy);

                notificationService.createNotification(
                                userPolicy.getUser(),
                                "🎉 Payment successful! Your " + userPolicy.getPolicy().getName()
                                                + " policy is now ACTIVE.",
                                "SUCCESS");

                return saved;
        }

        /**
         * Generate AI-powered rejection explanation
         */
        private String generateRejectionExplanation(com.insurai.dto.RiskAssessmentDTO riskAssessment,
                        String agentReason) {
                StringBuilder explanation = new StringBuilder();
                explanation.append("Based on AI analysis:\n\n");

                explanation.append("Risk Score: ").append(String.format("%.1f", riskAssessment.getRiskScore()))
                                .append("/10\n");

                if (riskAssessment.getRiskFactors() != null && !riskAssessment.getRiskFactors().isEmpty()) {
                        explanation.append("\nIdentified Risk Factors:\n");
                        for (com.insurai.dto.RiskAssessmentDTO.RiskFactor factor : riskAssessment.getRiskFactors()) {
                                explanation.append("• ").append(factor.getFactor())
                                                .append(" (Impact: ").append(String.format("%.1f", factor.getImpact()))
                                                .append(")\n");
                        }
                }

                explanation.append("\nAgent's Decision: ").append(agentReason);
                explanation.append("\n\nWe recommend reviewing the alternative policies suggested below, ");
                explanation.append("which are better aligned with your current profile.");

                return explanation.toString();
        }

        /**
         * Get appointment details with AI insights
         */
        public AppointmentDecisionResponse getAppointmentWithAIInsights(long bookingId) {
                Booking booking = bookingRepository.findById(bookingId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Booking not found"));

                AppointmentDecisionResponse response = new AppointmentDecisionResponse();
                response.setBookingId(booking.getId());
                response.setStatus(booking.getStatus());
                response.setRejectionReason(booking.getRejectionReason());
                response.setMeetingLink(booking.getMeetingLink());

                // If policy exists, get AI insights
                if (booking.getPolicy() != null) {
                        com.insurai.dto.RiskAssessmentDTO riskAssessment = aiRiskAssessmentService
                                        .assessRisk(booking.getUser().getId(), booking.getPolicy().getId());

                        response.setAiRiskScore(riskAssessment.getRiskScore() != null
                                        ? riskAssessment.getRiskScore().doubleValue()
                                        : null);
                        response.setAiRiskFactors(riskAssessment.getRiskFactors().stream()
                                        .map(com.insurai.dto.RiskAssessmentDTO.RiskFactor::getFactor)
                                        .collect(Collectors.toList()));
                }

                return response;
        }
}
