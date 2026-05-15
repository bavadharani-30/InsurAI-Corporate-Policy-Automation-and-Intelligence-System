package com.insurai.service;

import com.insurai.dto.*;
import com.insurai.model.*;
import com.insurai.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin Governance Service
 * Handles full lifecycle visibility, agent governance, and exception handling
 */
@Service
@Transactional
public class AdminGovernanceService {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private CompanyRepository companyRepository;

        @Autowired
        private PolicyRepository policyRepository;

        @Autowired
        private BookingRepository bookingRepository;

        @Autowired
        private UserPolicyRepository userPolicyRepository;

        @Autowired
        private ExceptionCaseRepository exceptionCaseRepository;

        @Autowired
        private NotificationService notificationService;

        // ===== FULL LIFECYCLE VISIBILITY =====

        /**
         * Get comprehensive admin analytics with funnel metrics and drop-off analysis
         */
        public AdminAnalyticsDTO getAdminAnalytics(String email) {
                Long companyId = getCompanyIdFromEmail(email);
                AdminAnalyticsDTO analytics = new AdminAnalyticsDTO();

                // Funnel Metrics
                analytics.setFunnelMetrics(calculateFunnelMetrics(companyId));

                // Agent Performance Summary
                analytics.setAgentPerformance(calculateAgentPerformanceSummary(companyId));

                // Policy Metrics
                analytics.setPolicyMetrics(calculatePolicyMetrics(companyId));

                // Financial Metrics
                analytics.setFinancialMetrics(calculateFinancialMetrics(companyId));

                // Drop-off Analysis
                analytics.setDropOffAnalysis(calculateDropOffAnalysis(companyId));

                return analytics;
        }

        private AdminAnalyticsDTO.FunnelMetrics calculateFunnelMetrics(Long companyId) {
                AdminAnalyticsDTO.FunnelMetrics funnel = new AdminAnalyticsDTO.FunnelMetrics();

                // Count policies (views proxy)
                long totalPolicies = companyId != null ? policyRepository.countByCompanyId(companyId)
                                : policyRepository.count();
                funnel.setTotalPolicyViews(totalPolicies * 10); // Estimate

                // Count appointments
                long totalAppointments = companyId != null ? bookingRepository.countByAgentCompanyId(companyId)
                                : bookingRepository.count();
                funnel.setTotalAppointmentsBooked(totalAppointments);

                // Count completed consultations
                long completedConsultations = companyId != null
                                ? bookingRepository.countByAgentCompanyIdAndCompletedAtIsNotNull(companyId)
                                : bookingRepository.countByCompletedAtIsNotNull();
                funnel.setTotalConsultationsCompleted(completedConsultations);

                // Count approvals
                long approvals = companyId != null
                                ? userPolicyRepository.countByPolicyCompanyIdAndWorkflowStatus(companyId, "APPROVED")
                                : userPolicyRepository.countByWorkflowStatus("APPROVED");
                funnel.setTotalApprovalsGiven(approvals);

                // Count purchases (active policies)
                long purchases = companyId != null
                                ? userPolicyRepository.countByPolicyCompanyIdAndStatus(companyId, "ACTIVE")
                                : userPolicyRepository.countByStatus("ACTIVE");
                funnel.setTotalPurchasesCompleted(purchases);

                // Calculate conversion rates
                if (funnel.getTotalPolicyViews() > 0) {
                        funnel.setViewToAppointmentRate(
                                        (double) totalAppointments / funnel.getTotalPolicyViews() * 100);
                }
                if (totalAppointments > 0) {
                        funnel.setAppointmentToConsultationRate(
                                        (double) completedConsultations / totalAppointments * 100);
                }
                if (completedConsultations > 0) {
                        funnel.setConsultationToApprovalRate((double) approvals / completedConsultations * 100);
                }
                if (approvals > 0) {
                        funnel.setApprovalToPurchaseRate((double) purchases / approvals * 100);
                }
                if (funnel.getTotalPolicyViews() > 0) {
                        funnel.setOverallConversionRate((double) purchases / funnel.getTotalPolicyViews() * 100);
                }

                return funnel;
        }

        private AdminAnalyticsDTO.AgentPerformanceSummary calculateAgentPerformanceSummary(Long companyId) {
                AdminAnalyticsDTO.AgentPerformanceSummary summary = new AdminAnalyticsDTO.AgentPerformanceSummary();

                List<User> agents = companyId != null ? userRepository.findByCompanyIdAndRole(companyId, "AGENT")
                                : userRepository.findByRole("AGENT");
                summary.setTotalAgents(agents.size());
                summary.setActiveAgents((int) agents.stream().filter(User::getIsActive).count());
                summary.setInactiveAgents((int) agents.stream().filter(a -> !a.getIsActive()).count());

                // Calculate average metrics across agents
                List<Booking> allBookings = companyId != null ? bookingRepository.findByAgentCompanyId(companyId)
                                : bookingRepository.findAll();

                // Average response time
                double avgResponseTime = allBookings.stream()
                                .filter(b -> b.getRespondedAt() != null)
                                .mapToDouble(b -> ChronoUnit.HOURS.between(b.getCreatedAt(), b.getRespondedAt()))
                                .average()
                                .orElse(0.0);
                summary.setAverageResponseTime(avgResponseTime);

                // SLA breaches
                int slaBreaches = (int) allBookings.stream()
                                .filter(b -> Boolean.TRUE.equals(b.getSlaBreached()))
                                .count();
                summary.setTotalSLABreaches(slaBreaches);

                int agentsWithBreaches = (int) agents.stream()
                                .filter(agent -> allBookings.stream()
                                                .anyMatch(b -> b.getAgent() != null &&
                                                                b.getAgent().getId().equals(agent.getId()) &&
                                                                Boolean.TRUE.equals(b.getSlaBreached())))
                                .count();
                summary.setAgentsWithSLABreaches(agentsWithBreaches);

                // Average approval and conversion rates
                List<UserPolicy> allPolicies = companyId != null ? userPolicyRepository.findByPolicyCompanyId(companyId)
                                : userPolicyRepository.findAll();
                if (!allPolicies.isEmpty()) {
                        double approvalRate = (double) allPolicies.stream()
                                        .filter(up -> "APPROVED".equals(up.getWorkflowStatus()))
                                        .count() / allPolicies.size() * 100;
                        summary.setAverageApprovalRate(approvalRate);

                        double conversionRate = (double) allPolicies.stream()
                                        .filter(up -> "ACTIVE".equals(up.getStatus()))
                                        .count() / allPolicies.size() * 100;
                        summary.setAverageConversionRate(conversionRate);
                }

                return summary;
        }

        private AdminAnalyticsDTO.PolicyMetrics calculatePolicyMetrics(Long companyId) {
                AdminAnalyticsDTO.PolicyMetrics metrics = new AdminAnalyticsDTO.PolicyMetrics();

                List<Policy> allPolicies = companyId != null ? policyRepository.findByCompanyId(companyId)
                                : policyRepository.findAll();
                metrics.setTotalPolicies(allPolicies.size());

                List<UserPolicy> userPolicies = companyId != null
                                ? userPolicyRepository.findByPolicyCompanyId(companyId)
                                : userPolicyRepository.findAll();
                metrics.setActivePolicies((int) userPolicies.stream()
                                .filter(up -> "ACTIVE".equals(up.getStatus()))
                                .count());
                metrics.setQuotedPolicies((int) userPolicies.stream()
                                .filter(up -> "QUOTED".equals(up.getStatus()))
                                .count());
                metrics.setRejectedPolicies((int) userPolicies.stream()
                                .filter(up -> "REJECTED".equals(up.getWorkflowStatus()))
                                .count());

                // Policies by type
                Map<String, Integer> byType = allPolicies.stream()
                                .collect(Collectors.groupingBy(
                                                p -> p.getType() != null ? p.getType() : "Unknown",
                                                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
                metrics.setPoliciesByType(byType);

                // Policies by category
                Map<String, Integer> byCategory = allPolicies.stream()
                                .collect(Collectors.groupingBy(
                                                p -> p.getCategory() != null ? p.getCategory() : "Unknown",
                                                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
                metrics.setPoliciesByCategory(byCategory);

                return metrics;
        }

        private AdminAnalyticsDTO.FinancialMetrics calculateFinancialMetrics(Long companyId) {
                AdminAnalyticsDTO.FinancialMetrics metrics = new AdminAnalyticsDTO.FinancialMetrics();

                List<UserPolicy> activePolicies = (companyId != null
                                ? userPolicyRepository.findByPolicyCompanyId(companyId)
                                : userPolicyRepository.findAll())
                                .stream()
                                .filter(up -> "ACTIVE".equals(up.getStatus()))
                                .collect(Collectors.toList());

                double totalRevenue = activePolicies.stream()
                                .mapToDouble(up -> up.getPolicy().getPremium() != null ? up.getPolicy().getPremium()
                                                : 0.0)
                                .sum();
                metrics.setTotalRevenue(totalRevenue);

                // Monthly revenue (policies purchased this month)
                LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
                double monthlyRevenue = activePolicies.stream()
                                .filter(up -> up.getCreatedAt() != null && up.getCreatedAt().isAfter(monthStart))
                                .mapToDouble(up -> up.getPolicy().getPremium() != null ? up.getPolicy().getPremium()
                                                : 0.0)
                                .sum();
                metrics.setMonthlyRevenue(monthlyRevenue);

                // Average premium
                if (!activePolicies.isEmpty()) {
                        metrics.setAveragePremium(totalRevenue / activePolicies.size());
                }

                // Total coverage issued
                double totalCoverage = activePolicies.stream()
                                .mapToDouble(up -> up.getPolicy().getCoverage() != null ? up.getPolicy().getCoverage()
                                                : 0.0)
                                .sum();
                metrics.setTotalCoverageIssued(totalCoverage);

                // User metrics (simplified to all users for platform context, or could be
                // linked to purchases)
                List<User> users = userRepository.findByRole("USER");
                metrics.setTotalUsers(users.size());
                metrics.setActiveUsers((int) users.stream()
                                .filter(u -> activePolicies.stream()
                                                .anyMatch(up -> up.getUser().getId().equals(u.getId())))
                                .count());

                return metrics;
        }

        private AdminAnalyticsDTO.DropOffAnalysis calculateDropOffAnalysis(Long companyId) {
                AdminAnalyticsDTO.DropOffAnalysis analysis = new AdminAnalyticsDTO.DropOffAnalysis();

                long totalViews = (companyId != null ? policyRepository.countByCompanyId(companyId)
                                : policyRepository.count()) * 10;
                long totalAppointments = companyId != null ? bookingRepository.countByAgentCompanyId(companyId)
                                : bookingRepository.count();
                long completedConsultations = companyId != null
                                ? bookingRepository.countByAgentCompanyIdAndCompletedAtIsNotNull(companyId)
                                : bookingRepository.countByCompletedAtIsNotNull();
                long approvals = companyId != null
                                ? userPolicyRepository.countByPolicyCompanyIdAndWorkflowStatus(companyId, "APPROVED")
                                : userPolicyRepository.countByWorkflowStatus("APPROVED");
                long purchases = companyId != null
                                ? userPolicyRepository.countByPolicyCompanyIdAndStatus(companyId, "ACTIVE")
                                : userPolicyRepository.countByStatus("ACTIVE");

                // View to Appointment
                AdminAnalyticsDTO.DropOffAnalysis.DropOffPoint viewToAppt = new AdminAnalyticsDTO.DropOffAnalysis.DropOffPoint();
                viewToAppt.setStage("View to Appointment");
                viewToAppt.setEntered(totalViews);
                viewToAppt.setExited(totalAppointments);
                viewToAppt.setDropped(totalViews - totalAppointments);
                if (totalViews > 0) {
                        viewToAppt.setDropOffRate((double) (totalViews - totalAppointments) / totalViews * 100);
                }
                viewToAppt.setPrimaryReason("User browsing, not ready to commit");
                analysis.setViewToAppointment(viewToAppt);

                // Appointment to Consultation
                AdminAnalyticsDTO.DropOffAnalysis.DropOffPoint apptToConsult = new AdminAnalyticsDTO.DropOffAnalysis.DropOffPoint();
                apptToConsult.setStage("Appointment to Consultation");
                apptToConsult.setEntered(totalAppointments);
                apptToConsult.setExited(completedConsultations);
                apptToConsult.setDropped(totalAppointments - completedConsultations);
                if (totalAppointments > 0) {
                        apptToConsult
                                        .setDropOffRate((double) (totalAppointments - completedConsultations)
                                                        / totalAppointments * 100);
                }
                apptToConsult.setPrimaryReason("Agent no-show or user cancellation");
                analysis.setAppointmentToConsultation(apptToConsult);

                // Consultation to Approval
                AdminAnalyticsDTO.DropOffAnalysis.DropOffPoint consultToApproval = new AdminAnalyticsDTO.DropOffAnalysis.DropOffPoint();
                consultToApproval.setStage("Consultation to Approval");
                consultToApproval.setEntered(completedConsultations);
                consultToApproval.setExited(approvals);
                consultToApproval.setDropped(completedConsultations - approvals);
                if (completedConsultations > 0) {
                        consultToApproval
                                        .setDropOffRate((double) (completedConsultations - approvals)
                                                        / completedConsultations * 100);
                }
                consultToApproval.setPrimaryReason("Policy rejection due to eligibility");
                analysis.setConsultationToApproval(consultToApproval);

                // Approval to Purchase
                AdminAnalyticsDTO.DropOffAnalysis.DropOffPoint approvalToPurchase = new AdminAnalyticsDTO.DropOffAnalysis.DropOffPoint();
                approvalToPurchase.setStage("Approval to Purchase");
                approvalToPurchase.setEntered(approvals);
                approvalToPurchase.setExited(purchases);
                approvalToPurchase.setDropped(approvals - purchases);
                if (approvals > 0) {
                        approvalToPurchase.setDropOffRate((double) (approvals - purchases) / approvals * 100);
                }
                approvalToPurchase.setPrimaryReason("Payment issues or user changed mind");
                analysis.setApprovalToPurchase(approvalToPurchase);

                return analysis;
        }

        // ===== AGENT GOVERNANCE =====

        /**
         * Get all agents with governance details
         */
        public List<AgentGovernanceDTO> getAllAgentsGovernance(String email) {
                Long companyId = getCompanyIdFromEmail(email);
                List<User> agents = companyId != null ? userRepository.findByCompanyIdAndRole(companyId, "AGENT")
                                : userRepository.findByRole("AGENT");

                return agents.stream()
                                .map(this::mapToAgentGovernanceDTO)
                                .collect(Collectors.toList());
        }

        /**
         * Get single agent governance details
         */
        public AgentGovernanceDTO getAgentGovernance(Long agentId) {
                User agent = userRepository.findById(java.util.Objects.requireNonNull(agentId))
                                .orElseThrow(() -> new RuntimeException("Agent not found"));

                if (!"AGENT".equals(agent.getRole())) {
                        throw new RuntimeException("User is not an agent");
                }

                return mapToAgentGovernanceDTO(agent);
        }

        private AgentGovernanceDTO mapToAgentGovernanceDTO(User agent) {
                AgentGovernanceDTO dto = new AgentGovernanceDTO();

                dto.setAgentId(agent.getId());
                dto.setAgentName(agent.getName());
                dto.setAgentEmail(agent.getEmail());
                dto.setIsActive(agent.getIsActive());
                dto.setIsAvailable(agent.getAvailable());

                dto.setAssignedRegions(agent.getAssignedRegions());
                dto.setAssignedPolicyTypes(agent.getAssignedPolicyTypes());
                dto.setSpecializations(
                                agent.getSpecialization() != null ? Arrays.asList(agent.getSpecialization().split(","))
                                                : new ArrayList<>());

                dto.setLastActive(LocalDateTime.now());
                dto.setDeactivatedAt(agent.getDeactivatedAt());
                dto.setDeactivationReason(agent.getDeactivationReason());

                // Performance metrics
                List<Booking> agentBookings = bookingRepository.findByAgentId(agent.getId());
                dto.setTotalConsultations(agentBookings.size());
                dto.setPendingConsultations((int) agentBookings.stream()
                                .filter(b -> "PENDING".equals(b.getStatus()))
                                .count());

                List<UserPolicy> agentPolicies = userPolicyRepository.findAll().stream()
                                .filter(up -> up.getUser() != null &&
                                                agentBookings.stream().anyMatch(b -> b.getUser() != null &&
                                                                b.getUser().getId().equals(up.getUser().getId())))
                                .collect(Collectors.toList());

                if (!agentPolicies.isEmpty()) {
                        double approvalRate = (double) agentPolicies.stream()
                                        .filter(up -> "APPROVED".equals(up.getWorkflowStatus()))
                                        .count() / agentPolicies.size() * 100;
                        dto.setApprovalRate(approvalRate);

                        double conversionRate = (double) agentPolicies.stream()
                                        .filter(up -> "ACTIVE".equals(up.getStatus()))
                                        .count() / agentPolicies.size() * 100;
                        dto.setConversionRate(conversionRate);
                }

                dto.setSlaBreaches((int) agentBookings.stream()
                                .filter(b -> Boolean.TRUE.equals(b.getSlaBreached()))
                                .count());

                // Exception flags
                dto.setMisconductFlags((int) exceptionCaseRepository.findByAgentId(agent.getId()).stream()
                                .filter(ec -> "AGENT_MISCONDUCT".equals(ec.getCaseType()))
                                .count());
                dto.setEscalatedCases((int) exceptionCaseRepository.findByAgentId(agent.getId()).stream()
                                .filter(ec -> "ESCALATED_REJECTION".equals(ec.getCaseType()))
                                .count());
                dto.setDisputedClaims((int) exceptionCaseRepository.findByAgentId(agent.getId()).stream()
                                .filter(ec -> "DISPUTED_CLAIM".equals(ec.getCaseType()))
                                .count());

                // Compliance metrics (placeholder)
                AgentGovernanceDTO.ComplianceMetrics compliance = new AgentGovernanceDTO.ComplianceMetrics();
                compliance.setHasValidLicense(true);
                compliance.setHasCompletedTraining(true);
                compliance.setComplianceScore(85);
                compliance.setViolations(new ArrayList<>());
                dto.setCompliance(compliance);

                return dto;
        }

        /**
         * Update agent assignments (regions and policy types)
         */
        public void updateAgentAssignments(Long agentId, List<String> regions, List<String> policyTypes) {
                User agent = userRepository.findById(java.util.Objects.requireNonNull(agentId))
                                .orElseThrow(() -> new RuntimeException("Agent not found"));

                agent.setAssignedRegions(regions);
                agent.setAssignedPolicyTypes(policyTypes);

                // Sync specialization field for public display
                if (policyTypes != null && !policyTypes.isEmpty()) {
                        agent.setSpecialization(String.join(", ", policyTypes));
                }

                userRepository.save(agent);

                notificationService.createNotification(
                                agent,
                                "Your assignments have been updated by admin",
                                "INFO");

                // Notify company dashboard
                if (agent.getCompany() != null) {
                        notificationService.broadcastUpdate("company/" + agent.getCompany().getId(),
                                        "ASSIGNMENT_UPDATED");
                }
        }

        /**
         * Activate/Deactivate agent
         */
        public void setAgentActiveStatus(Long agentId, Boolean isActive, String reason) {
                User agent = userRepository.findById(java.util.Objects.requireNonNull(agentId))
                                .orElseThrow(() -> new RuntimeException("Agent not found"));

                agent.setIsActive(isActive);

                if (!isActive) {
                        agent.setDeactivatedAt(LocalDateTime.now());
                        agent.setDeactivationReason(reason);
                        agent.setAvailable(false); // Also set offline
                } else {
                        agent.setDeactivatedAt(null);
                        agent.setDeactivationReason(null);
                }

                userRepository.save(agent);

                notificationService.createNotification(
                                agent,
                                isActive ? "Your account has been activated"
                                                : "Your account has been deactivated: " + reason,
                                isActive ? "SUCCESS" : "WARNING");
        }

        // ===== EXCEPTION HANDLING =====

        private Long getCompanyIdFromEmail(String email) {
                var company = companyRepository.findByEmail(email);
                if (company.isPresent()) {
                        return company.get().getId();
                }
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null && ("COMPANY_ADMIN".equals(user.getRole()) || "COMPANY".equals(user.getRole()))
                                && user.getCompany() != null) {
                        return user.getCompany().getId();
                }
                return null;
        }

        /**
         * Get all exception cases
         */
        public List<ExceptionCaseDTO> getAllExceptionCases(String email) {
                Long companyId = getCompanyIdFromEmail(email);
                List<ExceptionCase> cases = companyId != null ? exceptionCaseRepository.findByCompanyId(companyId)
                                : exceptionCaseRepository.findAll();

                return cases.stream()
                                .map(this::mapToExceptionCaseDTO)
                                .collect(Collectors.toList());
        }

        /**
         * Get exception cases by status
         */
        public List<ExceptionCaseDTO> getExceptionCasesByStatus(String status, String email) {
                Long companyId = getCompanyIdFromEmail(email);
                List<ExceptionCase> cases = companyId != null
                                ? exceptionCaseRepository.findByStatusAndCompanyId(status, companyId)
                                : exceptionCaseRepository.findByStatus(status);

                return cases.stream()
                                .map(this::mapToExceptionCaseDTO)
                                .collect(Collectors.toList());
        }

        /**
         * Get exception cases by type
         */
        public List<ExceptionCaseDTO> getExceptionCasesByType(String caseType, String email) {
                Long companyId = getCompanyIdFromEmail(email);
                List<ExceptionCase> cases = companyId != null
                                ? exceptionCaseRepository.findByCaseTypeAndCompanyId(caseType, companyId)
                                : exceptionCaseRepository.findByCaseType(caseType);

                return cases.stream()
                                .map(this::mapToExceptionCaseDTO)
                                .collect(Collectors.toList());
        }

        private ExceptionCaseDTO mapToExceptionCaseDTO(ExceptionCase ec) {
                ExceptionCaseDTO dto = new ExceptionCaseDTO();

                dto.setCaseId(ec.getId());
                dto.setCaseType(ec.getCaseType());
                dto.setStatus(ec.getStatus());
                dto.setPriority(ec.getPriority());

                if (ec.getUser() != null) {
                        dto.setUserId(ec.getUser().getId());
                        dto.setUserName(ec.getUser().getName());
                        dto.setUserEmail(ec.getUser().getEmail());
                }

                if (ec.getAgent() != null) {
                        dto.setAgentId(ec.getAgent().getId());
                        dto.setAgentName(ec.getAgent().getName());
                        dto.setAgentEmail(ec.getAgent().getEmail());
                }

                if (ec.getPolicy() != null) {
                        dto.setPolicyId(ec.getPolicy().getId());
                        dto.setPolicyName(ec.getPolicy().getName());
                }

                if (ec.getBooking() != null) {
                        dto.setBookingId(ec.getBooking().getId());
                }

                dto.setClaimId(ec.getClaimId());
                dto.setTitle(ec.getTitle());
                dto.setDescription(ec.getDescription());
                dto.setUserComplaint(ec.getUserComplaint());
                dto.setAgentResponse(ec.getAgentResponse());
                dto.setEvidence(ec.getEvidence());
                dto.setAdminNotes(ec.getAdminNotes());
                dto.setResolution(ec.getResolution());
                dto.setActionTaken(ec.getActionTaken());
                dto.setResolvedAt(ec.getResolvedAt());

                if (ec.getResolvedBy() != null) {
                        dto.setResolvedBy(ec.getResolvedBy().getId());
                        dto.setResolvedByName(ec.getResolvedBy().getName());
                }

                dto.setCreatedAt(ec.getCreatedAt());
                dto.setUpdatedAt(ec.getUpdatedAt());
                dto.setEscalatedAt(ec.getEscalatedAt());
                dto.setRequiresLegalReview(ec.getRequiresLegalReview());
                dto.setRequiresComplianceReview(ec.getRequiresComplianceReview());
                dto.setIsUrgent(ec.getIsUrgent());

                return dto;
        }

        /**
         * Create new exception case
         */
        public ExceptionCaseDTO createExceptionCase(ExceptionCase exceptionCase) {
                exceptionCase.setCreatedAt(LocalDateTime.now());
                exceptionCase.setUpdatedAt(LocalDateTime.now());
                exceptionCase.setEscalatedAt(LocalDateTime.now());

                ExceptionCase saved = exceptionCaseRepository.save(exceptionCase);

                // Notify admin
                List<User> admins = userRepository.findByRole("SUPER_ADMIN");
                for (User admin : admins) {
                        notificationService.createNotification(
                                        admin,
                                        "New exception case: " + exceptionCase.getTitle(),
                                        "WARNING");
                }

                return mapToExceptionCaseDTO(saved);
        }

        public void resolveExceptionCaseFromEmail(Long caseId, String resolution, String actionTaken, String email) {
                User admin = userRepository.findByEmail(email).orElse(null);
                Long adminId = admin != null ? admin.getId() : null;
                resolveExceptionCase(caseId, resolution, actionTaken, adminId);
        }

        /**
         * Resolve exception case
         */
        public void resolveExceptionCase(Long caseId, String resolution, String actionTaken, Long adminId) {
                ExceptionCase ec = exceptionCaseRepository.findById(java.util.Objects.requireNonNull(caseId))
                                .orElseThrow(() -> new RuntimeException("Exception case not found"));

                ec.setStatus("RESOLVED");
                ec.setResolution(resolution);
                ec.setActionTaken(actionTaken);
                ec.setResolvedAt(LocalDateTime.now());
                ec.setUpdatedAt(LocalDateTime.now());

                User admin = null;
                if (adminId != null) {
                        admin = userRepository.findById(adminId).orElse(null);
                }
                ec.setResolvedBy(admin);

                exceptionCaseRepository.save(ec);

                // Notify user
                if (ec.getUser() != null) {
                        notificationService.createNotification(
                                        ec.getUser(),
                                        "Your case has been resolved: " + ec.getTitle(),
                                        "SUCCESS");
                }

                // Notify agent if misconduct
                if (ec.getAgent() != null && "AGENT_MISCONDUCT".equals(ec.getCaseType())) {
                        notificationService.createNotification(
                                        ec.getAgent(),
                                        "Misconduct case resolved: " + actionTaken,
                                        "WARNING");
                }
        }
}
