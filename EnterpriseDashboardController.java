package com.insurai.controller;

import com.insurai.dto.DashboardStats;
import com.insurai.model.User;
import com.insurai.repository.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class EnterpriseDashboardController {

    private final UserRepository userRepo;
    private final BookingRepository bookingRepo;
    private final PolicyRepository policyRepo;
    private final UserPolicyRepository userPolicyRepo;
    private final UserCompanyMapRepository userCompanyMapRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private CompanyRepository companyRepository;

    public EnterpriseDashboardController(UserRepository userRepo, BookingRepository bookingRepo,
            PolicyRepository policyRepo, UserPolicyRepository userPolicyRepo,
            UserCompanyMapRepository userCompanyMapRepo) {
        this.userRepo = userRepo;
        this.bookingRepo = bookingRepo;
        this.policyRepo = policyRepo;
        this.userPolicyRepo = userPolicyRepo;
        this.userCompanyMapRepo = userCompanyMapRepo;
    }

    // 🛡️ 1. SUPER ADMIN (Platform Owner)
    @GetMapping("/super-admin/dashboard")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public DashboardStats getSuperAdminStats() {
        DashboardStats stats = new DashboardStats();

        // Global Funnel
        stats.totalUsers = (int) userRepo.countByRole("USER");
        stats.funnelUsers = stats.totalUsers;
        stats.funnelAppointments = (int) bookingRepo.count();
        stats.funnelConsulted = (int) bookingRepo.countByStatus("COMPLETED"); // or COMPLETED + CONFIRMED?
        stats.funnelApproved = (int) bookingRepo.countByStatus("APPROVED");
        stats.funnelIssued = (int) policyRepo.count();
        stats.totalPolicies = stats.funnelIssued;

        stats.activeAgents = (int) userRepo.countByRole("AGENT");
        // stats.totalPlans = (int) planRepo.count(); // Placeholder
        stats.fraudAlerts = 12; // Placeholder

        stats.healthScore = calculateSystemHealth();

        return stats;
    }

    // 🏢 2. COMPANY ADMIN (Insurance Provider Admin)
    @GetMapping("/company-admin/dashboard")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public DashboardStats getCompanyAdminStats(Authentication auth) {
        String email = auth.getName();
        User admin = userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

        if (admin.getCompany() == null) {
            throw new RuntimeException("Admin is not assigned to any company");
        }

        Long companyId = admin.getCompany().getId();
        DashboardStats stats = new DashboardStats();

        // Scope: Company Only
        stats.totalUsers = (int) userCompanyMapRepo.countActiveUsersByCompany(companyId);
        stats.activeAgents = (int) userRepo.countByRoleAndCompanyId("AGENT", companyId);
        stats.totalPolicies = (int) policyRepo.countByCompanyId(companyId);

        // Company Funnel
        stats.funnelUsers = stats.totalUsers;
        stats.funnelAppointments = (int) bookingRepo.countByAgentCompanyId(companyId);
        stats.funnelConsulted = (int) bookingRepo.countByAgentCompanyIdAndStatus(companyId, "COMPLETED");
        stats.funnelApproved = (int) bookingRepo.countByAgentCompanyIdAndStatus(companyId, "APPROVED");
        stats.funnelIssued = stats.totalPolicies;

        // Company Fraud Alerts
        stats.fraudAlerts = 2; // Mock
        stats.healthScore = 98; // Mock

        return stats;
    }

    // 🧑‍💼 3. AGENT (Company Employee)
    @GetMapping("/agent/dashboard")
    @PreAuthorize("hasRole('AGENT')")
    public DashboardStats getAgentStats(Authentication auth) {
        String email = auth.getName();
        User agent = userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        Long agentId = agent.getId();

        DashboardStats stats = new DashboardStats();

        // Scope: Agent Only
        LocalDate today = LocalDate.now();

        java.util.List<com.insurai.model.Booking> appointments = bookingRepo.findByAgentId(agentId);

        long todayCount = appointments.stream()
                .filter(b -> b.getStartTime().toLocalDate().equals(today))
                .count();

        stats.appointmentsToday = (int) todayCount;
        stats.pendingRequests = (int) appointments.stream().filter(b -> "PENDING".equals(b.getStatus())).count();
        stats.completedConsultations = (int) appointments.stream().filter(b -> "COMPLETED".equals(b.getStatus()))
                .count();

        return stats;
    }

    // 👤 4. USER (Customer)
    @GetMapping("/user/dashboard")
    @PreAuthorize("hasRole('USER')")
    public DashboardStats getUserStats(Authentication auth) {
        String email = auth.getName();
        User user = userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

        DashboardStats stats = new DashboardStats();
        // User specific
        stats.activePolicies = userPolicyRepo.findByUserId(user.getId()).size();
        return stats;
    }

    @GetMapping("/company-admin/users-list")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'COMPANY')")
    public java.util.List<com.insurai.dto.CompanyUserDTO> getCompanyUsers(Authentication auth) {
        String email = auth.getName();
        Long companyId = null;

        var companyOpt = companyRepository.findByEmail(email);
        if (companyOpt.isPresent()) {
            companyId = companyOpt.get().getId();
        } else {
            User admin = userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
            if (admin.getCompany() == null) {
                throw new RuntimeException("Admin is not assigned to any company");
            }
            companyId = admin.getCompany().getId();
        }

        java.util.List<com.insurai.model.UserCompanyMap> mappings = userCompanyMapRepo.findByCompanyId(companyId);

        return mappings.stream().map(m -> new com.insurai.dto.CompanyUserDTO(
                m.getUser().getId(),
                m.getUser().getName(),
                m.getUser().getEmail(),
                m.getPolicy() != null ? m.getPolicy().getName() : "Unknown",
                m.getPolicy() != null ? m.getPolicy().getStatus() : "Unknown",
                m.getStatus(),
                m.getCreatedAt())).collect(java.util.stream.Collectors.toList());
    }

    private int calculateSystemHealth() {
        return 95; // Mock
    }
}
