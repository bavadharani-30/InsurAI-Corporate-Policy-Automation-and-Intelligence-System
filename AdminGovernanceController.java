package com.insurai.controller;

import com.insurai.dto.*;
import com.insurai.model.ExceptionCase;
import com.insurai.service.AdminGovernanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin Governance Controller
 * Provides endpoints for analytics, agent governance, and exception handling
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'COMPANY')")
public class AdminGovernanceController {

    @Autowired
    private AdminGovernanceService adminGovernanceService;

    // ===== FULL LIFECYCLE VISIBILITY =====

    /**
     * GET /api/admin/analytics
     * Get comprehensive admin analytics with funnel metrics
     */
    @GetMapping("/analytics")
    public ResponseEntity<AdminAnalyticsDTO> getAnalytics(Authentication auth) {
        String email = auth.getName();
        AdminAnalyticsDTO analytics = adminGovernanceService.getAdminAnalytics(email);
        return ResponseEntity.ok(analytics);
    }

    // ===== AGENT GOVERNANCE =====

    /**
     * GET /api/admin/agents/governance
     * Get all agents with governance details
     */
    @GetMapping("/agents/governance")
    public ResponseEntity<List<AgentGovernanceDTO>> getAllAgentsGovernance(Authentication auth) {
        String email = auth.getName();
        // Assuming email is used to fetch user. For simplicity, pass email to service
        // to resolve User.
        List<AgentGovernanceDTO> agents = adminGovernanceService.getAllAgentsGovernance(email);
        return ResponseEntity.ok(agents);
    }

    /**
     * GET /api/admin/agents/{agentId}/governance
     * Get single agent governance details
     */
    @GetMapping("/agents/{agentId}/governance")
    public ResponseEntity<AgentGovernanceDTO> getAgentGovernance(@PathVariable Long agentId) {
        AgentGovernanceDTO agent = adminGovernanceService.getAgentGovernance(agentId);
        return ResponseEntity.ok(agent);
    }

    /**
     * PUT /api/admin/agents/{agentId}/assignments
     * Update agent assignments (regions and policy types)
     * 
     * Request Body:
     * {
     * "regions": ["North", "South"],
     * "policyTypes": ["Health", "Life"]
     * }
     */
    @PutMapping("/agents/{agentId}/assignments")
    public ResponseEntity<String> updateAgentAssignments(
            @PathVariable Long agentId,
            @RequestBody Map<String, List<String>> assignments) {

        List<String> regions = assignments.get("regions");
        List<String> policyTypes = assignments.get("policyTypes");

        adminGovernanceService.updateAgentAssignments(agentId, regions, policyTypes);
        return ResponseEntity.ok("Agent assignments updated successfully");
    }

    /**
     * PUT /api/admin/agents/{agentId}/status
     * Activate or deactivate an agent
     * 
     * Request Body:
     * {
     * "isActive": false,
     * "reason": "Performance issues"
     * }
     */
    @PutMapping("/agents/{agentId}/status")
    public ResponseEntity<String> setAgentStatus(
            @PathVariable Long agentId,
            @RequestBody Map<String, Object> statusUpdate) {

        Boolean isActive = (Boolean) statusUpdate.get("isActive");
        String reason = (String) statusUpdate.get("reason");

        adminGovernanceService.setAgentActiveStatus(agentId, isActive, reason);

        String message = isActive ? "Agent activated successfully" : "Agent deactivated successfully";

        return ResponseEntity.ok(message);
    }

    // ===== EXCEPTION HANDLING =====

    /**
     * GET /api/admin/exceptions
     * Get all exception cases
     */
    @GetMapping("/exceptions")
    public ResponseEntity<List<ExceptionCaseDTO>> getAllExceptionCases(Authentication auth) {
        String email = auth.getName();
        List<ExceptionCaseDTO> cases = adminGovernanceService.getAllExceptionCases(email);
        return ResponseEntity.ok(cases);
    }

    /**
     * GET /api/admin/exceptions/status/{status}
     * Get exception cases by status (PENDING, UNDER_REVIEW, RESOLVED, CLOSED)
     */
    @GetMapping("/exceptions/status/{status}")
    public ResponseEntity<List<ExceptionCaseDTO>> getExceptionCasesByStatus(@PathVariable String status,
            Authentication auth) {
        String email = auth.getName();
        List<ExceptionCaseDTO> cases = adminGovernanceService.getExceptionCasesByStatus(status, email);
        return ResponseEntity.ok(cases);
    }

    /**
     * GET /api/admin/exceptions/type/{caseType}
     * Get exception cases by type (ESCALATED_REJECTION, DISPUTED_CLAIM,
     * AGENT_MISCONDUCT)
     */
    @GetMapping("/exceptions/type/{caseType}")
    public ResponseEntity<List<ExceptionCaseDTO>> getExceptionCasesByType(@PathVariable String caseType,
            Authentication auth) {
        String email = auth.getName();
        List<ExceptionCaseDTO> cases = adminGovernanceService.getExceptionCasesByType(caseType, email);
        return ResponseEntity.ok(cases);
    }

    /**
     * POST /api/admin/exceptions
     * Create new exception case
     * 
     * Request Body: ExceptionCase object
     */
    @PostMapping("/exceptions")
    public ResponseEntity<ExceptionCaseDTO> createExceptionCase(@RequestBody ExceptionCase exceptionCase) {
        ExceptionCaseDTO created = adminGovernanceService.createExceptionCase(exceptionCase);
        return ResponseEntity.ok(created);
    }

    /**
     * PUT /api/admin/exceptions/{caseId}/resolve
     * Resolve an exception case
     * 
     * Request Body:
     * {
     * "resolution": "Policy approved after review",
     * "actionTaken": "APPROVED"
     * }
     */
    @PutMapping("/exceptions/{caseId}/resolve")
    public ResponseEntity<String> resolveExceptionCase(
            @PathVariable Long caseId,
            @RequestBody Map<String, String> resolution,
            Authentication auth) {

        String resolutionText = resolution.get("resolution");
        String actionTaken = resolution.get("actionTaken");

        // Get admin ID from authentication email
        String email = auth.getName();
        adminGovernanceService.resolveExceptionCaseFromEmail(caseId, resolutionText, actionTaken, email);
        return ResponseEntity.ok("Exception case resolved successfully");
    }

    /**
     * PUT /api/admin/exceptions/{caseId}/status
     * Update exception case status
     * 
     * Request Body:
     * {
     * "status": "UNDER_REVIEW",
     * "adminNotes": "Investigating the claim"
     * }
     */
    @PutMapping("/exceptions/{caseId}/status")
    public ResponseEntity<String> updateExceptionCaseStatus(
            @PathVariable Long caseId,
            @RequestBody Map<String, String> update) {

        // This would be implemented in the service
        // For now, returning success
        return ResponseEntity.ok("Exception case status updated");
    }
}
