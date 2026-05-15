package com.insurai.controller;

import com.insurai.model.Booking;
import com.insurai.service.BookingCleanupService;
import com.insurai.service.BookingLifecycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Booking Lifecycle Controller
 * Manages booking state transitions and lifecycle operations
 */
@RestController
@RequestMapping("/api/booking-lifecycle")
@CrossOrigin(origins = "http://localhost:3000")
public class BookingLifecycleController {

    private final BookingLifecycleService lifecycleService;
    private final BookingCleanupService cleanupService;

    public BookingLifecycleController(
            BookingLifecycleService lifecycleService,
            BookingCleanupService cleanupService) {
        this.lifecycleService = lifecycleService;
        this.cleanupService = cleanupService;
    }

    /**
     * Agent confirms booking
     * POST /api/booking-lifecycle/{bookingId}/confirm
     */
    @PostMapping("/{bookingId}/confirm")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<Booking> confirmBooking(
            @PathVariable Long bookingId,
            @RequestBody Map<String, Object> request) {

        Long agentId = Long.valueOf(request.get("agentId").toString());
        LocalDateTime appointmentTime = LocalDateTime.parse(request.get("appointmentTime").toString());

        Booking booking = lifecycleService.confirmBooking(bookingId, agentId, appointmentTime);
        return ResponseEntity.ok(booking);
    }

    /**
     * Agent marks booking as completed
     * POST /api/booking-lifecycle/{bookingId}/complete
     */
    @PostMapping("/{bookingId}/complete")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<Booking> completeBooking(
            @PathVariable Long bookingId,
            @RequestBody Map<String, String> request) {

        String agentNotes = request.get("agentNotes");
        Booking booking = lifecycleService.completeBooking(bookingId, agentNotes);
        return ResponseEntity.ok(booking);
    }

    /**
     * User cancels booking
     * POST /api/booking-lifecycle/{bookingId}/cancel
     */
    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Booking> cancelBooking(
            @PathVariable Long bookingId,
            @RequestBody Map<String, Long> request) {

        Long userId = request.get("userId");
        Booking booking = lifecycleService.cancelBooking(bookingId, userId);
        return ResponseEntity.ok(booking);
    }

    /**
     * Get booking timeline
     * GET /api/booking-lifecycle/{bookingId}/timeline
     */
    @GetMapping("/{bookingId}/timeline")
    @PreAuthorize("hasAnyRole('USER', 'AGENT', 'ADMIN')")
    public ResponseEntity<Map<String, LocalDateTime>> getTimeline(
            @PathVariable Long bookingId) {

        Map<String, LocalDateTime> timeline = lifecycleService.getBookingTimeline(bookingId);
        return ResponseEntity.ok(timeline);
    }

    /**
     * Get booking statistics by status
     * GET /api/booking-lifecycle/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('AGENT', 'ADMIN')")
    public ResponseEntity<Map<String, Long>> getStats() {
        Map<String, Long> stats = lifecycleService.getBookingStatsByStatus();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get conversion funnel metrics
     * GET /api/booking-lifecycle/funnel
     */
    @GetMapping("/funnel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingLifecycleService.FunnelMetrics> getFunnelMetrics() {
        BookingLifecycleService.FunnelMetrics metrics = lifecycleService.getFunnelMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get cleanup statistics
     * GET /api/booking-lifecycle/cleanup-stats
     */
    @GetMapping("/cleanup-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingCleanupService.CleanupStats> getCleanupStats() {
        BookingCleanupService.CleanupStats stats = cleanupService.getCleanupStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Trigger manual cleanup (admin only)
     * POST /api/booking-lifecycle/cleanup
     */
    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerCleanup() {
        cleanupService.manualCleanup();
        return ResponseEntity.ok("Cleanup triggered successfully");
    }
}
