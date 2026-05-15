package com.insurai.controller;

import com.insurai.dto.AppointmentDecisionResponse;
import com.insurai.model.Booking;
import com.insurai.service.AppointmentWorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST Controller for Appointment Management
 * Exposes appointment workflow endpoints
 */
@RestController
@RequestMapping("/api/appointments")
@CrossOrigin(origins = "*")
public class AppointmentController {

    private final AppointmentWorkflowService appointmentWorkflowService;

    public AppointmentController(AppointmentWorkflowService appointmentWorkflowService) {
        this.appointmentWorkflowService = appointmentWorkflowService;
    }

    /**
     * Book a new appointment
     * POST /api/appointments/book
     */
    @PostMapping("/book")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Booking> bookAppointment(@RequestBody BookingRequest request) {
        Booking booking = appointmentWorkflowService.createPolicyAppointment(
                request.getUserId(),
                request.getAgentId(),
                request.getPolicyId(),
                request.getStartTime(),
                request.getEndTime(),
                request.getReason());
        return ResponseEntity.ok(booking);
    }

    /**
     * Approve appointment and create meeting
     * PUT /api/appointments/{id}/approve
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<AppointmentDecisionResponse> approveAppointment(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request) {
        AppointmentDecisionResponse response = appointmentWorkflowService.approveMeeting(
                id,
                request.getAgentId(),
                request.getNotes());
        return ResponseEntity.ok(response);
    }

    /**
     * Reject appointment with AI recommendations
     * PUT /api/appointments/{id}/reject
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<AppointmentDecisionResponse> rejectAppointment(
            @PathVariable Long id,
            @RequestBody RejectionRequest request) {
        AppointmentDecisionResponse response = appointmentWorkflowService.rejectAppointment(
                id,
                request.getAgentId(),
                request.getRejectionReason(),
                request.isIncludeAIRecommendations());
        return ResponseEntity.ok(response);
    }

    /**
     * Mark appointment as completed
     * PUT /api/appointments/{id}/complete
     */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<AppointmentDecisionResponse> completeAppointment(
            @PathVariable Long id,
            @RequestBody CompletionRequest request) {
        AppointmentDecisionResponse response = appointmentWorkflowService.markAsCompleted(
                id,
                request.getAgentId(),
                request.getConsultationNotes());
        return ResponseEntity.ok(response);
    }

    /**
     * Get meeting link for appointment
     * GET /api/appointments/{id}/meeting-link
     */
    @GetMapping("/{id}/meeting-link")
    @PreAuthorize("hasAnyRole('USER', 'AGENT')")
    public ResponseEntity<MeetingLinkResponse> getMeetingLink(@PathVariable Long id) {
        AppointmentDecisionResponse appointment = appointmentWorkflowService.getAppointmentWithAIInsights(id);

        MeetingLinkResponse response = new MeetingLinkResponse();
        response.setAppointmentId(id);
        response.setMeetingLink(appointment.getMeetingLink());
        response.setStatus(appointment.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * Approve policy after consultation
     * PUT /api/appointments/{id}/approve-policy
     */
    @PutMapping("/{id}/approve-policy")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<AppointmentDecisionResponse> approvePolicy(
            @PathVariable Long id,
            @RequestBody PolicyApprovalRequest request) {
        AppointmentDecisionResponse response = appointmentWorkflowService.approvePolicy(
                id,
                request.getAgentId(),
                request.getApprovalNotes());
        return ResponseEntity.ok(response);
    }

    /**
     * Get appointment details with AI insights
     * GET /api/appointments/{id}/insights
     */
    @GetMapping("/{id}/insights")
    @PreAuthorize("hasAnyRole('USER', 'AGENT', 'ADMIN')")
    public ResponseEntity<AppointmentDecisionResponse> getAppointmentInsights(@PathVariable Long id) {
        AppointmentDecisionResponse response = appointmentWorkflowService.getAppointmentWithAIInsights(id);
        return ResponseEntity.ok(response);
    }

    // DTO Classes
    public static class BookingRequest {
        private Long userId;
        private Long agentId;
        private Long policyId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String reason;

        // Getters and Setters
        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getAgentId() {
            return agentId;
        }

        public void setAgentId(Long agentId) {
            this.agentId = agentId;
        }

        public Long getPolicyId() {
            return policyId;
        }

        public void setPolicyId(Long policyId) {
            this.policyId = policyId;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public void setEndTime(LocalDateTime endTime) {
            this.endTime = endTime;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class ApprovalRequest {
        private Long agentId;
        private String notes;

        public Long getAgentId() {
            return agentId;
        }

        public void setAgentId(Long agentId) {
            this.agentId = agentId;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    public static class RejectionRequest {
        private Long agentId;
        private String rejectionReason;
        private boolean includeAIRecommendations = true;

        public Long getAgentId() {
            return agentId;
        }

        public void setAgentId(Long agentId) {
            this.agentId = agentId;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }

        public void setRejectionReason(String rejectionReason) {
            this.rejectionReason = rejectionReason;
        }

        public boolean isIncludeAIRecommendations() {
            return includeAIRecommendations;
        }

        public void setIncludeAIRecommendations(boolean includeAIRecommendations) {
            this.includeAIRecommendations = includeAIRecommendations;
        }
    }

    public static class CompletionRequest {
        private Long agentId;
        private String consultationNotes;

        public Long getAgentId() {
            return agentId;
        }

        public void setAgentId(Long agentId) {
            this.agentId = agentId;
        }

        public String getConsultationNotes() {
            return consultationNotes;
        }

        public void setConsultationNotes(String consultationNotes) {
            this.consultationNotes = consultationNotes;
        }
    }

    public static class PolicyApprovalRequest {
        private Long agentId;
        private String approvalNotes;

        public Long getAgentId() {
            return agentId;
        }

        public void setAgentId(Long agentId) {
            this.agentId = agentId;
        }

        public String getApprovalNotes() {
            return approvalNotes;
        }

        public void setApprovalNotes(String approvalNotes) {
            this.approvalNotes = approvalNotes;
        }
    }

    public static class MeetingLinkResponse {
        private Long appointmentId;
        private String meetingLink;
        private String status;

        public Long getAppointmentId() {
            return appointmentId;
        }

        public void setAppointmentId(Long appointmentId) {
            this.appointmentId = appointmentId;
        }

        public String getMeetingLink() {
            return meetingLink;
        }

        public void setMeetingLink(String meetingLink) {
            this.meetingLink = meetingLink;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
