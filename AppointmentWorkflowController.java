package com.insurai.controller;

import com.insurai.dto.AppointmentDecisionRequest;
import com.insurai.dto.AppointmentDecisionResponse;
import com.insurai.model.Booking;
import com.insurai.model.UserPolicy;
import com.insurai.service.AppointmentWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Enhanced Appointment Workflow Controller
 * Implements structured approval-based insurance workflow
 */
@RestController
@RequestMapping("/api/appointment-workflow")
@CrossOrigin(origins = "http://localhost:3000")
public class AppointmentWorkflowController {

    private final AppointmentWorkflowService workflowService;
    private final com.insurai.repository.UserRepository userRepository;

    public AppointmentWorkflowController(AppointmentWorkflowService workflowService,
            com.insurai.repository.UserRepository userRepository) {
        this.workflowService = workflowService;
        this.userRepository = userRepository;
    }

    /**
     * PHASE 1: User creates policy-based appointment
     * POST /api/appointment-workflow/book
     */
    @PostMapping("/book")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Booking> bookPolicyAppointment(
            @RequestBody Map<String, Object> request,
            Authentication auth) {

        Long userId = Long.valueOf(request.get("userId").toString());
        Long agentId = Long.valueOf(request.get("agentId").toString());
        Long policyId = Long.valueOf(request.get("policyId").toString());
        String startTimeStr = request.get("startTime").toString();
        String endTimeStr = request.get("endTime").toString();
        String reason = request.get("reason") != null ? request.get("reason").toString() : null;

        LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

        Booking booking = workflowService.createPolicyAppointment(
                userId, agentId, policyId, startTime, endTime, reason);

        return ResponseEntity.ok(booking);
    }

    /**
     * PHASE 2: Agent processes appointment decision
     * POST /api/appointment-workflow/agent/decision
     */
    @PostMapping("/agent/decision")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<AppointmentDecisionResponse> processAgentDecision(
            @RequestBody AppointmentDecisionRequest request,
            Authentication auth) {

        com.insurai.model.User agent = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Agent not found"));

        AppointmentDecisionResponse response;

        switch (request.getAction()) {
            case "APPROVE_MEETING":
                response = workflowService.approveMeeting(
                        request.getBookingId(), agent.getId(), request.getNotes());
                break;

            case "MARK_COMPLETED":
                response = workflowService.markAsCompleted(
                        request.getBookingId(), agent.getId(), request.getNotes());
                break;

            case "APPROVE_POLICY":
                response = workflowService.approvePolicy(
                        request.getBookingId(), agent.getId(), request.getNotes());
                break;

            case "REJECT":
                if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Rejection reason is mandatory");
                }
                response = workflowService.rejectAppointment(
                        request.getBookingId(),
                        agent.getId(),
                        request.getRejectionReason(),
                        request.getIncludeAIRecommendations() != null && request.getIncludeAIRecommendations());
                break;

            default:
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Invalid action: " + request.getAction());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get appointment with AI insights
     * GET /api/appointment-workflow/{bookingId}/insights
     */
    @GetMapping("/{bookingId}/insights")
    @PreAuthorize("hasAnyRole('USER', 'AGENT', 'ADMIN')")
    public ResponseEntity<AppointmentDecisionResponse> getAppointmentInsights(
            @PathVariable Long bookingId) {

        AppointmentDecisionResponse response = workflowService.getAppointmentWithAIInsights(bookingId);
        return ResponseEntity.ok(response);
    }

    /**
     * PHASE 3: User activates policy by paying premium
     * POST /api/appointment-workflow/activate-policy/{userPolicyId}
     */
    @PostMapping("/activate-policy/{userPolicyId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserPolicy> activatePolicy(
            @PathVariable Long userPolicyId,
            Authentication auth) {

        com.insurai.model.User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));

        UserPolicy activatedPolicy = workflowService.activatePolicy(userPolicyId, user.getId());
        return ResponseEntity.ok(activatedPolicy);
    }
}
