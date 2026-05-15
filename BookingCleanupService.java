package com.insurai.service;

import com.insurai.model.Booking;
import com.insurai.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Booking Cleanup Service
 * Handles automated cleanup and expiry of bookings
 */
@Service
public class BookingCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(BookingCleanupService.class);

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    // SLA Configuration (in hours)
    private static final int PENDING_EXPIRY_HOURS = 48; // 2 days
    private static final int CONFIRMED_EXPIRY_HOURS = 72; // 3 days
    private static final int COMPLETED_AUTO_CLOSE_DAYS = 7; // 7 days

    public BookingCleanupService(
            BookingRepository bookingRepository,
            NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
    }

    /**
     * Run every hour to check for expired bookings
     * Cron: 0 0 * * * * = Every hour at minute 0
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireUnattendedBookings() {
        logger.info("Starting scheduled task: Expire unattended bookings");

        int expiredCount = 0;

        // Expire PENDING bookings older than 48 hours
        expiredCount += expirePendingBookings();

        // Expire CONFIRMED bookings with no completion after 72 hours
        expiredCount += expireConfirmedBookings();

        logger.info("Completed scheduled task: Expired {} bookings", expiredCount);
    }

    /**
     * Expire PENDING bookings that haven't been confirmed within SLA
     */
    private int expirePendingBookings() {
        LocalDateTime expiryThreshold = LocalDateTime.now().minusHours(PENDING_EXPIRY_HOURS);
        List<Booking> pendingBookings = bookingRepository.findByStatus("PENDING");

        int count = 0;
        for (Booking booking : pendingBookings) {
            if (booking.getCreatedAt().isBefore(expiryThreshold)) {
                booking.setStatus("EXPIRED");
                booking.setSlaBreached(true);
                bookingRepository.save(booking);

                // Notify user
                if (booking.getUser() != null) {
                    notificationService.createNotification(
                            booking.getUser(),
                            "Your consultation request has expired due to no agent response within 48 hours. Please submit a new request.",
                            "WARNING");
                }

                count++;
                logger.debug("Expired PENDING booking ID: {} (created: {})",
                        booking.getId(), booking.getCreatedAt());
            }
        }

        return count;
    }

    /**
     * Expire CONFIRMED bookings that weren't completed within SLA
     */
    private int expireConfirmedBookings() {
        LocalDateTime expiryThreshold = LocalDateTime.now().minusHours(CONFIRMED_EXPIRY_HOURS);
        List<Booking> confirmedBookings = bookingRepository.findByStatus("CONFIRMED");

        int count = 0;
        for (Booking booking : confirmedBookings) {
            // Check if appointment time has passed
            if (booking.getStartTime() != null && booking.getStartTime().isBefore(expiryThreshold)) {
                booking.setStatus("EXPIRED");
                booking.setSlaBreached(true);
                bookingRepository.save(booking);

                // Notify user and agent
                if (booking.getUser() != null) {
                    notificationService.createNotification(
                            booking.getUser(),
                            "Your scheduled consultation has expired. Please reschedule if still interested.",
                            "WARNING");
                }

                if (booking.getAgent() != null) {
                    notificationService.createNotification(
                            booking.getAgent(),
                            "Confirmed appointment ID " + booking.getId() + " has expired due to no completion.",
                            "INFO");
                }

                count++;
                logger.debug("Expired CONFIRMED booking ID: {} (scheduled: {})",
                        booking.getId(), booking.getStartTime());
            }
        }

        return count;
    }

    /**
     * Run daily to auto-close old completed bookings
     * Cron: 0 0 2 * * * = Every day at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void autoCloseCompletedBookings() {
        logger.info("Starting scheduled task: Auto-close completed bookings");

        LocalDateTime closeThreshold = LocalDateTime.now().minusDays(COMPLETED_AUTO_CLOSE_DAYS);
        List<Booking> completedBookings = bookingRepository.findByStatus("COMPLETED");

        int closedCount = 0;
        for (Booking booking : completedBookings) {
            if (booking.getCompletedAt() != null && booking.getCompletedAt().isBefore(closeThreshold)) {
                // Mark as archived (you could add an 'archived' flag to Booking model)
                // For now, we'll just log it
                logger.debug("Booking ID {} is eligible for archival (completed: {})",
                        booking.getId(), booking.getCompletedAt());
                closedCount++;
            }
        }

        logger.info("Completed scheduled task: {} bookings eligible for archival", closedCount);
    }

    /**
     * Run weekly to generate cleanup report
     * Cron: 0 0 0 * * MON = Every Monday at midnight
     */
    @Scheduled(cron = "0 0 0 * * MON")
    public void generateWeeklyCleanupReport() {
        logger.info("Starting scheduled task: Generate weekly cleanup report");

        LocalDateTime weekAgo = LocalDateTime.now().minusWeeks(1);

        // Count bookings by status in the past week
        long pendingCount = bookingRepository.findByStatus("PENDING").stream()
                .filter(b -> b.getCreatedAt().isAfter(weekAgo))
                .count();

        long expiredCount = bookingRepository.findByStatus("EXPIRED").stream()
                .filter(b -> b.getCreatedAt().isAfter(weekAgo))
                .count();

        long completedCount = bookingRepository.findByStatus("COMPLETED").stream()
                .filter(b -> b.getCompletedAt() != null && b.getCompletedAt().isAfter(weekAgo))
                .count();

        long policyIssuedCount = bookingRepository.findByStatus("POLICY_ISSUED").stream()
                .filter(b -> b.getCreatedAt().isAfter(weekAgo))
                .count();

        logger.info("Weekly Cleanup Report:");
        logger.info("  - Pending: {}", pendingCount);
        logger.info("  - Expired: {}", expiredCount);
        logger.info("  - Completed: {}", completedCount);
        logger.info("  - Policy Issued: {}", policyIssuedCount);
        logger.info("  - Expiry Rate: {}%",
                pendingCount > 0 ? (expiredCount * 100.0 / pendingCount) : 0);
    }

    /**
     * Manual cleanup trigger (for admin use)
     */
    @Transactional
    public void manualCleanup() {
        logger.info("Manual cleanup triggered");
        expireUnattendedBookings();
        autoCloseCompletedBookings();
    }

    /**
     * Get cleanup statistics
     */
    public CleanupStats getCleanupStats() {
        CleanupStats stats = new CleanupStats();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pendingThreshold = now.minusHours(PENDING_EXPIRY_HOURS);
        LocalDateTime confirmedThreshold = now.minusHours(CONFIRMED_EXPIRY_HOURS);

        // Count bookings at risk of expiry
        stats.pendingAtRisk = bookingRepository.findByStatus("PENDING").stream()
                .filter(b -> b.getCreatedAt().isBefore(pendingThreshold))
                .count();

        stats.confirmedAtRisk = bookingRepository.findByStatus("CONFIRMED").stream()
                .filter(b -> b.getStartTime() != null && b.getStartTime().isBefore(confirmedThreshold))
                .count();

        stats.totalExpired = bookingRepository.findByStatus("EXPIRED").size();
        stats.totalCompleted = bookingRepository.findByStatus("COMPLETED").size();
        stats.totalPolicyIssued = bookingRepository.findByStatus("POLICY_ISSUED").size();

        return stats;
    }

    /**
     * Cleanup statistics DTO
     */
    public static class CleanupStats {
        public long pendingAtRisk;
        public long confirmedAtRisk;
        public long totalExpired;
        public long totalCompleted;
        public long totalPolicyIssued;
    }
}
