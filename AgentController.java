package com.insurai.controller;

import com.insurai.model.Booking;
import com.insurai.model.Policy;
import com.insurai.model.User;
import com.insurai.model.UserPolicy;
import com.insurai.repository.BookingRepository;
import com.insurai.repository.PolicyRepository;
import com.insurai.repository.UserPolicyRepository;
import com.insurai.repository.UserRepository;
import com.insurai.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@CrossOrigin(origins = "http://localhost:3000")
public class AgentController {

    private final UserRepository userRepo;
    private final BookingRepository bookingRepo;
    private final UserPolicyRepository userPolicyRepo;
    private final PolicyRepository policyRepo;
    private final NotificationService notificationService;
    private final com.insurai.service.AgentConsultationService agentConsultationService;
    private final com.insurai.service.GoogleCalendarService calendarService;
    private final com.insurai.service.AIService aiService;

    public AgentController(UserRepository userRepo, BookingRepository bookingRepo, UserPolicyRepository userPolicyRepo,
            PolicyRepository policyRepo, NotificationService notificationService,
            com.insurai.service.AgentConsultationService agentConsultationService,
            com.insurai.service.GoogleCalendarService calendarService,
            com.insurai.service.AIService aiService) {
        this.userRepo = userRepo;
        this.bookingRepo = bookingRepo;
        this.userPolicyRepo = userPolicyRepo;
        this.policyRepo = policyRepo;
        this.notificationService = notificationService;
        this.agentConsultationService = agentConsultationService;
        this.calendarService = calendarService;
        this.aiService = aiService;
    }

    // Public/User: Find agents
    @GetMapping
    public List<User> getAllAgents(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) {
            User currentUser = userRepo.findByEmail(auth.getName()).orElse(null);
            if (currentUser != null && "COMPANY_ADMIN".equals(currentUser.getRole())) {
                if (currentUser.getCompany() != null) {
                    return userRepo.findByCompanyIdAndRole(currentUser.getCompany().getId(), "AGENT");
                }
            }
        }
        return userRepo.findByRole("AGENT");
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<?> toggleAvailability(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        User agent = userRepo.findById(java.util.Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        Boolean available = body.get("available");
        agent.setAvailable(Boolean.TRUE.equals(available));
        userRepo.save(agent);
        return ResponseEntity.ok(Map.of("message", "Updated", "available", Boolean.TRUE.equals(available)));
    }

    // --- New Agent Features ---

    @PatchMapping("/{id}/activation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleActivation(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        User agent = userRepo.findById(java.util.Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        Boolean active = body.get("active");
        agent.setIsActive(Boolean.TRUE.equals(active));

        // Sync availability with activation
        if (Boolean.TRUE.equals(active)) {
            agent.setAvailable(true); // Activating -> Online
        } else {
            agent.setAvailable(false); // Deactivating -> Offline
        }

        userRepo.save(agent);
        return ResponseEntity.ok(
                Map.of("message", "Updated", "active", Boolean.TRUE.equals(active), "available", agent.getAvailable()));
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasRole('AGENT')")
    public List<Booking> getMyAppointments(Authentication auth) {
        User agent = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (Boolean.FALSE.equals(agent.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated. Contact Admin.");
        }

        if (Boolean.FALSE.equals(agent.getAvailable())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are currently offline. Go Online to view tasks.");
        }

        return bookingRepo.findByAgentId(java.util.Objects.requireNonNull(agent.getId()));
    }

    @PutMapping("/appointments/{id}/status")
    @PreAuthorize("hasRole('AGENT')")
    public Booking updateAppointmentStatus(@PathVariable Long id, @RequestBody Map<String, String> body,
            Authentication auth) {
        User agent = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (Boolean.FALSE.equals(agent.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated. Contact Admin.");
        }

        if (Boolean.FALSE.equals(agent.getAvailable())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are currently offline. Go Online to perform actions.");
        }

        Booking booking = bookingRepo.findById(java.util.Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String newStatus = body.get("status");
        if (newStatus == null)
            return booking;

        if ("EXPIRED".equals(newStatus)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Status option for expiry is now disabled and system-managed.");
        }

        // Allow transitions from PENDING or APPROVED or CONSULTED
        if (!"PENDING".equals(booking.getStatus()) && !"APPROVED".equals(booking.getStatus())
                && !"CONSULTED".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Booking is already processed (Current status: " + booking.getStatus() + ")");
        }

        if ("APPROVED".equals(newStatus)) {
            // Task 2: Approve Meeting (Scenario 1)
            if (booking.getRespondedAt() == null) {
                booking.setRespondedAt(java.time.LocalDateTime.now());
            }

            // Just generate link, DO NOT create policy yet.
            if (booking.getMeetingLink() == null) {
                String link = calendarService.createMeeting(
                        "Consultation: " + booking.getUser().getName(),
                        booking.getPolicy() != null ? "Policy Purchase Discussion: " + booking.getPolicy().getName()
                                : "General Consultation",
                        booking.getStartTime().toString(),
                        booking.getEndTime().toString(),
                        booking.getUser().getEmail(),
                        booking.getAgent().getEmail());
                booking.setMeetingLink(link);
            }
        } else if ("COMPLETED".equals(newStatus)) {
            booking.setCompletedAt(java.time.LocalDateTime.now());

            // Task 5: Policy Issuance (After Consultation)
            if (booking.getPolicy() != null && "PURCHASE".equalsIgnoreCase(booking.getBookingType())) {
                // Check duplicate
                List<UserPolicy> existing = userPolicyRepo.findByUserIdAndPolicyId(booking.getUser().getId(),
                        booking.getPolicy().getId());
                boolean hasActive = existing.stream().anyMatch(
                        p -> "ACTIVE".equalsIgnoreCase(p.getStatus()) || "PENDING".equalsIgnoreCase(p.getStatus())
                                || "PAYMENT_PENDING".equalsIgnoreCase(p.getStatus()));

                if (hasActive) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "User already has an active or pending policy of this type.");
                }

                UserPolicy up = new UserPolicy();
                up.setUser(booking.getUser());
                up.setPolicy(booking.getPolicy());
                up.setStatus("PAYMENT_PENDING"); // ISSUED
                up.setStartDate(java.time.LocalDate.now());
                up.setEndDate(java.time.LocalDate.now().plusYears(1));
                userPolicyRepo.save(up);

                notificationService.createNotification(
                        booking.getUser(),
                        "Policy Issued! Please complete payment for " + booking.getPolicy().getName(),
                        "SUCCESS");
            } else if (booking.getPolicy() != null && "ENQUIRY".equalsIgnoreCase(booking.getBookingType())) {
                // Just notify that enquiry is resolved
                notificationService.createNotification(
                        booking.getUser(),
                        "Enquiry for " + booking.getPolicy().getName() + " has been resolved mark as satisfied.",
                        "SUCCESS");
            }
        } else if ("REJECTED".equals(newStatus)) {
            booking.setCompletedAt(java.time.LocalDateTime.now());
            // Phase 3: Rejection with AI
            String reason = body.get("reason");
            if (reason == null || reason.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is mandatory.");
            }
            booking.setRejectionReason(reason);

            // AI Analysis
            if (booking.getPolicy() != null) {
                var analysis = aiService.analyzeRejection(reason, booking.getUser(), booking.getPolicy());
                booking.setRiskScore(analysis.riskScore());
                // Simple JSON-like string for now, or just explanation
                booking.setAiAnalysis(
                        "Explanation: " + analysis.explanation() + " | Recommendations: " + analysis.recommendations()
                                .stream().map(r -> r.policyName()).collect(java.util.stream.Collectors.joining(", ")));
            }
        }

        booking.setStatus(newStatus);

        // Notify User of general status change
        notificationService.createNotification(
                booking.getUser(),
                "Appointment status updated to: " + newStatus,
                "APPROVED".equals(newStatus) ? "SUCCESS" : "INFO");

        return bookingRepo.save(booking);
    }

    @PostMapping("/recommendations")
    @PreAuthorize("hasRole('AGENT')")
    public UserPolicy recommendPolicy(@RequestBody Map<String, Object> payload, Authentication auth) {
        User agent = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (Boolean.FALSE.equals(agent.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated.");
        }

        if (Boolean.FALSE.equals(agent.getAvailable())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are currently offline. Go Online to make recommendations.");
        }

        Object uIdObj = payload.get("userId");
        Object pIdObj = payload.get("policyId");

        if (uIdObj == null || pIdObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing userId or policyId");
        }

        Long userId = Long.valueOf(uIdObj.toString());
        Long policyId = Long.valueOf(pIdObj.toString());
        String note = (String) payload.get("note");

        User user = userRepo.findById(java.util.Objects.requireNonNull(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Policy policy = policyRepo.findById(java.util.Objects.requireNonNull(policyId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));

        // Check for existing active/pending policy
        List<UserPolicy> existing = userPolicyRepo.findByUserIdAndPolicyId(userId, policyId);
        boolean hasActive = existing.stream().anyMatch(
                p -> "ACTIVE".equalsIgnoreCase(p.getStatus()) || "PENDING".equalsIgnoreCase(p.getStatus()));

        if (hasActive) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User already has an active or pending policy of this type.");
        }

        UserPolicy up = new UserPolicy();
        up.setUser(user);
        up.setPolicy(policy);
        up.setStatus("QUOTED");
        up.setRecommendationNote(note);

        UserPolicy saved = userPolicyRepo.save(up);

        // Notify User
        notificationService.createNotification(
                user,
                "New Policy Recommendation from " + agent.getName() + ": " + policy.getName(),
                "INFO");

        return saved;
    }

    // NEW: Get Agent's Consultations with AI-Assisted Risk Indicators
    @GetMapping("/consultations")
    @PreAuthorize("hasRole('AGENT')")
    public List<com.insurai.dto.ConsultationDTO> getMyConsultations(Authentication auth) {
        String email = auth.getName();
        User agent = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        return agentConsultationService.getAgentConsultations(agent.getId());
    }

    // NEW: Process Consultation Decision (Approve/Reject/Recommend Alternative)
    @PostMapping("/consultations/decision")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<String> processConsultationDecision(
            Authentication auth,
            @RequestBody com.insurai.dto.PolicyRecommendationRequest request) {

        String email = auth.getName();
        User agent = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        agentConsultationService.processConsultationDecision(agent.getId(), request);

        return ResponseEntity.ok("Consultation decision processed successfully");
    }

    // NEW: Get Agent Performance Metrics
    @GetMapping("/performance")
    @PreAuthorize("hasRole('AGENT')")
    public com.insurai.dto.AgentPerformanceDTO getMyPerformance(Authentication auth) {
        String email = auth.getName();
        User agent = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        return agentConsultationService.getAgentPerformance(agent.getId());
    }

    // NEW: Admin - Get Any Agent's Performance
    @GetMapping("/{agentId}/performance")
    @PreAuthorize("hasRole('ADMIN')")
    public com.insurai.dto.AgentPerformanceDTO getAgentPerformance(@PathVariable Long agentId) {
        return agentConsultationService.getAgentPerformance(agentId);
    }

    // NEW: Submit Review
    @PostMapping("/{agentId}/reviews")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> submitReview(@PathVariable Long agentId, @RequestBody Map<String, Object> body,
            Authentication auth) {
        String email = auth.getName();
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Long bookingId = Long.valueOf(body.get("bookingId").toString());
        Integer rating = Integer.valueOf(body.get("rating").toString());
        String feedback = (String) body.get("feedback");

        agentConsultationService.submitReview(agentId, user.getId(), bookingId, rating, feedback);
        return ResponseEntity.ok("Review submitted successfully");
    }

    // NEW: Get Reviews
    @GetMapping("/{agentId}/reviews")
    public List<com.insurai.model.AgentReview> getAgentReviews(@PathVariable Long agentId) {
        return agentConsultationService.getAgentReviews(agentId);
    }

    // NEW: Admin - Get All Reviews
    @GetMapping("/reviews/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<com.insurai.model.AgentReview> getAllReviews() {
        return agentConsultationService.getAllReviews();
    }

    @GetMapping("/dashboard/today-metrics")
    public ResponseEntity<Map<String, Object>> getTodayMetrics(Authentication auth) {
        User agent = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        if (!"AGENT".equals(agent.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        List<Booking> appts = bookingRepo.findByAgentId(agent.getId());

        long approvedToday = appts.stream()
                .filter(b -> ("APPROVED".equals(b.getStatus()) || "CONFIRMED".equals(b.getStatus()))
                        && b.getRespondedAt() != null
                        && b.getRespondedAt().toLocalDate().equals(today))
                .count();

        long rejectedToday = appts.stream()
                .filter(b -> "REJECTED".equals(b.getStatus())
                        && b.getCompletedAt() != null
                        && b.getCompletedAt().toLocalDate().equals(today))
                .count();

        // Recalculate daily approval rate
        double approvalRate = 0.0;
        if ((approvedToday + rejectedToday) > 0) {
            approvalRate = ((double) approvedToday / (approvedToday + rejectedToday)) * 100;
        }

        return ResponseEntity.ok(Map.of(
                "approvedToday", approvedToday,
                "rejectedToday", rejectedToday,
                "approvalRate", Math.round(approvalRate)));
    }
}
