package com.insurai.service;

import com.insurai.model.Booking;
import com.insurai.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Booking Lifecycle Service
 * Manages state transitions and lifecycle events for bookings
 */
@Service
public class BookingLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(BookingLifecycleService.class);

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public BookingLifecycleService(
            BookingRepository bookingRepository,
            NotificationService notificationService,
            EmailService emailService) {
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    /**
     * Transition booking to CONFIRMED state
     * PENDING → CONFIRMED
     */
    @Transactional
    public Booking confirmBooking(Long bookingId, Long agentId, LocalDateTime appointmentTime) {
        Booking booking = bookingRepository.findById(java.util.Objects.requireNonNull(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        validateTransition(booking, "PENDING", "CONFIRMED");

        booking.setStatus("CONFIRMED");
        booking.setStartTime(appointmentTime);
        booking.setRespondedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);

        // Notify user
        String message = "Your consultation has been confirmed for " + appointmentTime.toString();
        notificationService.createNotification(
                booking.getUser(),
                message,
                "SUCCESS");

        // Send Email
        try {
            emailService.send(booking.getUser().getEmail(), "Booking Confirmed - InsurAI", message);
        } catch (Exception e) {
            logger.error("Failed to send email", e);
        }

        logger.info("Booking {} transitioned: PENDING → CONFIRMED by agent {}", bookingId, agentId);
        return saved;
    }

    /**
     * Transition booking to COMPLETED state
     * CONFIRMED → COMPLETED
     */
    @Transactional
    public Booking completeBooking(Long bookingId, String agentNotes) {
        Booking booking = bookingRepository.findById(java.util.Objects.requireNonNull(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        validateTransition(booking, "CONFIRMED", "COMPLETED");

        booking.setStatus("COMPLETED");
        booking.setCompletedAt(LocalDateTime.now());
        if (agentNotes != null) {
            booking.setAgentNotes(agentNotes);
        }

        Booking saved = bookingRepository.save(booking);

        // Notify user
        String message = "Your consultation has been completed. The agent will review your application.";
        notificationService.createNotification(
                booking.getUser(),
                message,
                "INFO");

        // Send Email
        try {
            emailService.send(booking.getUser().getEmail(), "Consultation Completed - InsurAI", message);
        } catch (Exception e) {
            logger.error("Failed to send email", e);
        }

        logger.info("Booking {} transitioned: CONFIRMED → COMPLETED", bookingId);
        return saved;
    }

    /**
     * Transition booking to POLICY_ISSUED state
     * COMPLETED → POLICY_ISSUED (after approval)
     */
    @Transactional
    public Booking issuePolicyForBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(java.util.Objects.requireNonNull(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Can transition from COMPLETED or PENDING_ADMIN_APPROVAL
        if (!"COMPLETED".equals(booking.getStatus()) &&
                !"PENDING_ADMIN_APPROVAL".equals(booking.getStatus())) {
            throw new IllegalStateException(
                    "Cannot issue policy from status: " + booking.getStatus());
        }

        booking.setStatus("POLICY_ISSUED");
        Booking saved = bookingRepository.save(booking);

        // Notify user
        String message = "Congratulations! Your policy has been issued and is now active.";
        notificationService.createNotification(
                booking.getUser(),
                message,
                "SUCCESS");

        // Send Email
        try {
            emailService.send(booking.getUser().getEmail(), "Policy Issued - InsurAI", message);
        } catch (Exception e) {
            logger.error("Failed to send email", e);
        }

        logger.info("Booking {} transitioned: {} → POLICY_ISSUED",
                bookingId, booking.getStatus());
        return saved;
    }

    /**
     * Transition booking to REJECTED state
     * PENDING/CONFIRMED/COMPLETED → REJECTED
     */
    @Transactional
    public Booking rejectBooking(Long bookingId, String rejectionReason) {
        Booking booking = bookingRepository.findById(java.util.Objects.requireNonNull(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        String previousStatus = booking.getStatus();
        booking.setStatus("REJECTED");
        booking.setRejectionReason(rejectionReason);
        booking.setReviewedAt(LocalDateTime.now());

        Booking saved = bookingRepository.save(booking);

        // Notify user
        String message = "Your application has been rejected. Reason: " + rejectionReason;
        notificationService.createNotification(
                booking.getUser(),
                message,
                "WARNING");

        // Send Email
        try {
            emailService.send(booking.getUser().getEmail(), "Application Update - InsurAI", message);
        } catch (Exception e) {
            logger.error("Failed to send email", e);
        }

        logger.info("Booking {} transitioned: {} → REJECTED", bookingId, previousStatus);
        return saved;
    }

    /**
     * Transition booking to CANCELLED state
     * User cancellation
     */
    @Transactional
    public Booking cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(java.util.Objects.requireNonNull(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Verify user owns this booking
        if (!booking.getUser().getId().equals(userId)) {
            throw new SecurityException("User does not own this booking");
        }

        // Can only cancel if not already in terminal state
        if (isTerminalStatus(booking.getStatus())) {
            throw new IllegalStateException(
                    "Cannot cancel booking in status: " + booking.getStatus());
        }

        String previousStatus = booking.getStatus();
        booking.setStatus("CANCELLED");

        Booking saved = bookingRepository.save(booking);

        // Notify agent if assigned
        if (booking.getAgent() != null) {
            notificationService.createNotification(
                    booking.getAgent(),
                    "Booking #" + bookingId + " has been cancelled by the user",
                    "INFO");
        }

        logger.info("Booking {} transitioned: {} → CANCELLED by user {}",
                bookingId, previousStatus, userId);
        return saved;
    }

    /**
     * Get booking timeline (all state transitions)
     */
    public Map<String, LocalDateTime> getBookingTimeline(Long bookingId) {
        Booking booking = bookingRepository.findById(java.util.Objects.requireNonNull(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        Map<String, LocalDateTime> timeline = new HashMap<>();

        timeline.put("CREATED", booking.getCreatedAt());

        if (booking.getRespondedAt() != null) {
            timeline.put("CONFIRMED", booking.getRespondedAt());
        }

        if (booking.getCompletedAt() != null) {
            timeline.put("COMPLETED", booking.getCompletedAt());
        }

        if (booking.getReviewedAt() != null) {
            if ("REJECTED".equals(booking.getStatus())) {
                timeline.put("REJECTED", booking.getReviewedAt());
            } else if ("POLICY_ISSUED".equals(booking.getStatus())) {
                timeline.put("POLICY_ISSUED", booking.getReviewedAt());
            }
        }

        return timeline;
    }

    /**
     * Get booking statistics by status
     */
    public Map<String, Long> getBookingStatsByStatus() {
        Map<String, Long> stats = new HashMap<>();

        stats.put("PENDING", (long) bookingRepository.findByStatus("PENDING").size());
        stats.put("CONFIRMED", (long) bookingRepository.findByStatus("CONFIRMED").size());
        stats.put("COMPLETED", (long) bookingRepository.findByStatus("COMPLETED").size());
        stats.put("POLICY_ISSUED", (long) bookingRepository.findByStatus("POLICY_ISSUED").size());
        stats.put("REJECTED", (long) bookingRepository.findByStatus("REJECTED").size());
        stats.put("EXPIRED", (long) bookingRepository.findByStatus("EXPIRED").size());
        stats.put("CANCELLED", (long) bookingRepository.findByStatus("CANCELLED").size());
        stats.put("PENDING_ADMIN_APPROVAL", (long) bookingRepository.findByStatus("PENDING_ADMIN_APPROVAL").size());

        return stats;
    }

    /**
     * Get conversion funnel metrics
     */
    public FunnelMetrics getFunnelMetrics() {
        FunnelMetrics metrics = new FunnelMetrics();

        List<Booking> allBookings = bookingRepository.findAll();

        metrics.totalRequests = allBookings.size();
        metrics.confirmed = bookingRepository.findByStatus("CONFIRMED").size();
        metrics.completed = bookingRepository.findByStatus("COMPLETED").size();
        metrics.policyIssued = bookingRepository.findByStatus("POLICY_ISSUED").size();
        metrics.rejected = bookingRepository.findByStatus("REJECTED").size();
        metrics.expired = bookingRepository.findByStatus("EXPIRED").size();
        metrics.cancelled = bookingRepository.findByStatus("CANCELLED").size();

        // Calculate conversion rates
        if (metrics.totalRequests > 0) {
            metrics.confirmationRate = (metrics.confirmed * 100.0) / metrics.totalRequests;
            metrics.completionRate = (metrics.completed * 100.0) / metrics.totalRequests;
            metrics.conversionRate = (metrics.policyIssued * 100.0) / metrics.totalRequests;
            metrics.rejectionRate = (metrics.rejected * 100.0) / metrics.totalRequests;
            metrics.expiryRate = (metrics.expired * 100.0) / metrics.totalRequests;
        }

        return metrics;
    }

    /**
     * Validate state transition
     */
    private void validateTransition(Booking booking, String expectedCurrent, String newStatus) {
        if (!expectedCurrent.equals(booking.getStatus())) {
            throw new IllegalStateException(
                    String.format("Invalid state transition: Expected %s but found %s",
                            expectedCurrent, booking.getStatus()));
        }
    }

    /**
     * Check if status is terminal (no further transitions)
     */
    private boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status) ||
                "POLICY_ISSUED".equals(status) ||
                "REJECTED".equals(status) ||
                "EXPIRED".equals(status) ||
                "CANCELLED".equals(status);
    }

    /**
     * Funnel metrics DTO
     */
    public static class FunnelMetrics {
        public long totalRequests;
        public long confirmed;
        public long completed;
        public long policyIssued;
        public long rejected;
        public long expired;
        public long cancelled;

        public double confirmationRate;
        public double completionRate;
        public double conversionRate;
        public double rejectionRate;
        public double expiryRate;
    }
}
