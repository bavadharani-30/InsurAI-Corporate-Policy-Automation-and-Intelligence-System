package com.insurai.controller;

import com.insurai.model.Company;
import com.insurai.model.Policy;
import com.insurai.model.User;
import com.insurai.service.CompanyService;
import com.insurai.repository.UserRepository;
import com.insurai.repository.AuditLogRepository;
import com.insurai.repository.ClaimRepository;
import com.insurai.repository.UserPolicyRepository;
import com.insurai.repository.BookingRepository;
import com.insurai.repository.UserCompanyMapRepository;

import com.insurai.repository.CompanyRepository;
import com.insurai.repository.AgentReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/company")
@CrossOrigin(origins = "http://localhost:3000")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private UserPolicyRepository userPolicyRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserCompanyMapRepository userCompanyMapRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AgentReviewRepository agentReviewRepository;

    @Autowired
    private com.insurai.service.NotificationService notificationService;

    @Autowired
    private com.insurai.repository.ExceptionCaseRepository exceptionCaseRepository;

    /**
     * Resolves the authenticated company from JWT.
     * Company admins log in with a Company email (stored in company table).
     * Falls back to User→Company for agent/user accounts linked to a company.
     */
    private Company getAuthenticatedCompany(Authentication auth) {
        String email = auth.getName();

        // Primary: Company logged in directly
        var companyByEmail = companyRepository.findByEmail(email);
        if (companyByEmail.isPresent()) {
            Company c = companyByEmail.get();
            if (Boolean.FALSE.equals(c.getIsActive())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company account is deactivated");
            }
            return c;
        }

        // Fallback: agent/user account linked to a company
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No company or user found for email: " + email));
        Company company = user.getCompany();
        if (company == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User '" + email + "' is not associated with any company");
        }
        return company;
    }

    /**
     * Get Company Audit Logs
     */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<?> getCompanyAuditLogs(Authentication auth) {
        Company company = getAuthenticatedCompany(auth);
        List<com.insurai.model.AuditLog> logs = auditLogRepository.findByCompanyId(company.getId());
        return ResponseEntity.ok(logs);
    }

    /**
     * Register a new company (Public Endpoint)
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerCompany(@RequestBody Company company) {
        try {
            Company registered = companyService.registerCompany(company);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Company registered successfully. Awaiting approval.",
                    "company", registered));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get company profile
     */
    @GetMapping("/profile")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<Company> getProfile(Authentication auth) {
        Company company = getAuthenticatedCompany(auth);
        return ResponseEntity.ok(company);
    }

    /**
     * Update company profile
     */
    @PutMapping("/profile")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<Company> updateProfile(@RequestBody Company updatedCompany, Authentication auth) {
        Company company = getAuthenticatedCompany(auth);
        Company updated = companyService.updateCompany(company.getId(), updatedCompany);
        return ResponseEntity.ok(updated);
    }

    /**
     * Get all policies for the authenticated company
     */
    @GetMapping("/policies")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<List<Policy>> getCompanyPolicies(Authentication auth) {
        Company company = getAuthenticatedCompany(auth);
        List<Policy> policies = companyService.getCompanyPolicies(company.getId());
        return ResponseEntity.ok(policies);
    }

    /**
     * Add a new policy
     */
    @PostMapping("/policies")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<?> addPolicy(@RequestBody Policy policy, Authentication auth) {
        try {
            Company company = getAuthenticatedCompany(auth);
            Policy created = companyService.addPolicy(company.getId(), policy);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update a policy
     */
    @PutMapping("/policies/{policyId}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<?> updatePolicy(
            @PathVariable Long policyId,
            @RequestBody Policy updatedPolicy,
            Authentication auth) {
        try {
            Company company = getAuthenticatedCompany(auth);
            Policy updated = companyService.updatePolicy(company.getId(), policyId, updatedPolicy);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a policy
     */
    @DeleteMapping("/policies/{policyId}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<?> deletePolicy(@PathVariable Long policyId, Authentication auth) {
        try {
            Company company = getAuthenticatedCompany(auth);
            companyService.deletePolicy(company.getId(), policyId);
            return ResponseEntity.ok(Map.of("message", "Policy deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get active companies (public endpoint)
     */
    @GetMapping("/active")
    public ResponseEntity<List<Company>> getActiveCompanies() {
        List<Company> companies = companyService.getActiveCompanies();
        return ResponseEntity.ok(companies);
    }

    /**
     * Get Company Dashboard KPIs
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<Map<String, Object>> getDashboardStats(Authentication auth) {
        Company company = getAuthenticatedCompany(auth);
        Long companyId = company.getId();

        List<Policy> allPolicies = companyService.getCompanyPolicies(companyId);
        long totalPolicies = allPolicies.size();
        long activePolicies = allPolicies.stream().filter(p -> "ACTIVE".equals(p.getStatus())).count();

        // Get total unique users who have purchased policies from this company
        long totalCompanyUsers = userCompanyMapRepository.countActiveUsersByCompany(companyId);

        List<com.insurai.model.UserPolicy> soldPolicies = userPolicyRepository.findByPolicyCompanyId(companyId);
        long policiesSold = soldPolicies.size();
        double revenue = soldPolicies.stream()
                .mapToDouble(up -> up.getPremium() != null ? up.getPremium() : 0.0)
                .sum();

        // Calculate Conversion Rate: Policies Sold / Total Bookings * 100
        List<com.insurai.model.Booking> bookings = bookingRepository.findByPolicyCompanyId(companyId);
        long totalBookings = bookings.size();
        double conversionRate = totalBookings > 0 ? (double) policiesSold / totalBookings * 100 : 0;

        // Sales Data (Last 6 Months)
        Map<java.time.Month, Double> monthlySales = new java.util.TreeMap<>();
        java.time.LocalDateTime sixMonthsAgo = java.time.LocalDateTime.now().minusMonths(6);

        soldPolicies.stream()
                .filter(up -> up.getCreatedAt() != null && up.getCreatedAt().isAfter(sixMonthsAgo))
                .forEach(up -> {
                    java.time.Month month = up.getCreatedAt().getMonth();
                    monthlySales.put(month,
                            monthlySales.getOrDefault(month, 0.0) + (up.getPremium() != null ? up.getPremium() : 0));
                });

        List<Map<String, Object>> salesData = monthlySales.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("name",
                            e.getKey().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH));
                    m.put("profit", e.getValue());
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());

        // AI Insights
        List<Map<String, String>> aiInsights = new java.util.ArrayList<>();

        // Insight 1: Conversion Rate
        if (conversionRate < 10) {
            aiInsights.add(Map.of("type", "warning", "title", "Low Conversion", "text",
                    "Conversion rate is below 10%. Consider revising policy premiums."));
        } else if (conversionRate > 30) {
            aiInsights.add(Map.of("type", "success", "title", "Strong Conversion", "text",
                    "Excellent conversion rate exceeding 30%."));
        }

        // Insight 2: Top Selling Policy
        if (!soldPolicies.isEmpty()) {
            Map<String, Long> policyCounts = soldPolicies.stream()
                    .filter(up -> up.getPolicy() != null)
                    .collect(java.util.stream.Collectors.groupingBy(up -> up.getPolicy().getName(),
                            java.util.stream.Collectors.counting()));

            policyCounts.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(best -> {
                aiInsights.add(Map.of("type", "suggestion", "title", "Top Performer", "text",
                        best.getKey() + " is your best seller with " + best.getValue() + " units sold."));
            });
        } else {
            aiInsights.add(Map.of("type", "suggestion", "title", "No Sales Yet", "text",
                    "Promote your policies to attract customers."));
        }

        long totalAgents = userRepository.findAll().stream()
                .filter(u -> "AGENT".equals(u.getRole()) && u.getCompany() != null
                        && u.getCompany().getId().equals(companyId))
                .count();

        // Fraud Alerts: count ExceptionCase entities for this company
        long fraudAlerts = exceptionCaseRepository.countByCompanyId(companyId);

        // Feedback scoped to users of this company
        long totalFeedback = agentReviewRepository.findAll().stream()
                .filter(r -> r.getAgent() != null && r.getAgent().getCompany() != null
                        && companyId.equals(r.getAgent().getCompany().getId()))
                .count();

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("metrics", Map.of(
                "name", company.getName(),
                "totalPolicies", totalPolicies,
                "activePolicies", activePolicies,
                "policiesSold", policiesSold,
                "revenue", revenue,
                "totalUsers", totalCompanyUsers,
                "totalAgents", totalAgents,
                "fraudAlerts", fraudAlerts,
                "totalFeedback", totalFeedback,
                "conversionRate", String.format("%.1f", conversionRate)));
        response.put("recentPolicies", allPolicies.stream().limit(5).collect(java.util.stream.Collectors.toList()));
        response.put("salesData", salesData);
        response.put("aiInsights", aiInsights);

        return ResponseEntity.ok(response);

    }

    /**
     * Get Agent Performance for this Company
     */
    @GetMapping("/agents")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<?> getCompanyAgents(Authentication auth) {
        Company company = getAuthenticatedCompany(auth);

        List<com.insurai.model.Booking> bookings = bookingRepository.findByPolicyCompanyId(company.getId());

        // Group by Agent
        Map<com.insurai.model.User, List<com.insurai.model.Booking>> agentBookings = bookings.stream()
                .filter(b -> b.getAgent() != null)
                .collect(java.util.stream.Collectors.groupingBy(com.insurai.model.Booking::getAgent));

        List<Map<String, Object>> agentPerformance = agentBookings.entrySet().stream().map(entry -> {
            com.insurai.model.User agent = entry.getKey();
            List<com.insurai.model.Booking> bList = entry.getValue();

            long approvals = bList.stream()
                    .filter(b -> "APPROVED".equals(b.getStatus()) || "COMPLETED".equals(b.getStatus())).count();
            long rejections = bList.stream().filter(b -> "REJECTED".equals(b.getStatus())).count();

            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", agent.getId());
            map.put("name", agent.getName());
            map.put("rating", agent.getRating());
            map.put("approvals", approvals);
            map.put("rejections", rejections);
            map.put("status", Boolean.TRUE.equals(agent.getAvailable()) ? "Online" : "Offline");
            map.put("avgTime", "15 mins");
            return map;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(agentPerformance);
    }

    /**
     * Add a new Agent to the Company
     */
    @PostMapping("/agents")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<?> addAgent(@RequestBody Map<String, String> payload, Authentication auth) {
        try {
            Company company = getAuthenticatedCompany(auth);

            String agentName = payload.get("name");
            String agentEmail = payload.get("email");
            String agentPassword = payload.get("password");

            if (agentName == null || agentEmail == null || agentPassword == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            if (userRepository.findByEmail(agentEmail).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
            }

            com.insurai.model.User agent = new com.insurai.model.User();
            agent.setName(agentName);
            agent.setEmail(agentEmail);
            agent.setPassword(passwordEncoder.encode(agentPassword));
            agent.setRole("AGENT");
            agent.setCompany(company);
            agent.setIsActive(true);
            agent.setVerified(true);
            agent.setAvailable(true); // Default to online
            agent.setRating(4.5); // Default rating

            userRepository.save(agent);

            // Notify company dashboard for real-time update
            notificationService.broadcastUpdate("company/" + company.getId(), "AGENT_ADDED");

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Agent added successfully",
                    "agent", agent));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get Company Claims
     */
    @GetMapping("/claims")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<?> getCompanyClaims(Authentication auth) {
        Company company = getAuthenticatedCompany(auth);
        Long companyId = company.getId();

        List<Policy> companyPolicies = companyService.getCompanyPolicies(companyId);
        List<String> policyNames = companyPolicies.stream()
                .map(Policy::getName)
                .collect(java.util.stream.Collectors.toList());

        if (policyNames.isEmpty()) {
            return ResponseEntity.ok(new java.util.ArrayList<>());
        }

        List<com.insurai.model.Claim> claims = claimRepository.findByPolicyNameIn(policyNames);
        return ResponseEntity.ok(claims);
    }

    /**
     * Get Agent Reviews for the Company
     */
    @GetMapping("/agent-reviews")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<List<com.insurai.model.AgentReview>> getCompanyAgentReviews(Authentication auth) {
        Company company = getAuthenticatedCompany(auth);
        List<com.insurai.model.AgentReview> reviews = agentReviewRepository.findAll().stream()
                .filter(r -> r.getAgent() != null && r.getAgent().getCompany() != null
                        && company.getId().equals(r.getAgent().getCompany().getId()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(reviews);
    }
}
