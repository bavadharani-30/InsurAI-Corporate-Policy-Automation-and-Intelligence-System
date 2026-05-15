package com.insurai.controller;

import com.insurai.dto.BookingRequest;
import com.insurai.model.Booking;
import com.insurai.service.BookingService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "http://localhost:3000")
public class BookingController {

    private final BookingService bookingService;
    private final com.insurai.repository.UserRepository userRepo;

    public BookingController(BookingService bookingService, com.insurai.repository.UserRepository userRepo) {
        this.bookingService = bookingService;
        this.userRepo = userRepo;
    }

    @GetMapping
    public List<Booking> getAll(org.springframework.security.core.Authentication auth) {
        com.insurai.model.User user = null;
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            user = userRepo.findByEmail(email).orElse(null);
        }
        return bookingService.getAllBookings(user);
    }

    @PostMapping
    public Booking create(@RequestBody BookingRequest request, org.springframework.security.core.Authentication auth) {
        Long userId = request.getUserId();
        Long agentId = request.getAgentId();

        if (userId == null || agentId == null) {
            throw new IllegalArgumentException("User ID and Agent ID are required");
        }

        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            com.insurai.model.User currentUser = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            if (!currentUser.getId().equals(userId)
                    && !"SUPER_ADMIN".equals(currentUser.getRole())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Cannot book for another user");
            }
        }

        return bookingService.createBooking(
                userId,
                agentId,
                request.getStart(),
                request.getEnd(),
                request.getPolicyId(),
                request.getReason(),
                request.getBookingType());
    }

    @GetMapping("/user/{id}")
    public List<Booking> userBookings(@PathVariable Long id, org.springframework.security.core.Authentication auth) {
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            com.insurai.model.User currentUser = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            boolean isAdmin = "SUPER_ADMIN".equals(currentUser.getRole())
                    || "COMPANY_ADMIN".equals(currentUser.getRole());
            if (!currentUser.getId().equals(id) && !isAdmin) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Access denied");
            }
        }
        return bookingService.getUserBookings(java.util.Objects.requireNonNull(id));
    }

    @GetMapping("/agent/{id}")
    public List<Booking> agentBookings(@PathVariable Long id, org.springframework.security.core.Authentication auth) {
        if (auth != null && auth.isAuthenticated()) {
            String email = auth.getName();
            com.insurai.model.User currentUser = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            // Agents can only see their own. Admins can see any.
            boolean isAdmin = "SUPER_ADMIN".equals(currentUser.getRole())
                    || "COMPANY_ADMIN".equals(currentUser.getRole());
            if (!currentUser.getId().equals(id) && !isAdmin) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Access denied");
            }
        }
        return bookingService.getAgentBookings(java.util.Objects.requireNonNull(id));
    }

    @PutMapping("/{id}/status")
    public Booking updateStatus(@PathVariable Long id,
            @RequestParam String status) {
        return bookingService.updateStatus(java.util.Objects.requireNonNull(id), status);
    }

    @PatchMapping("/{id}/status")
    public Booking updateStatusPatch(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null)
            throw new IllegalArgumentException("Status is required in body");
        return bookingService.updateStatus(java.util.Objects.requireNonNull(id), status);
    }

    @PostMapping("/block")
    public void blockSlot(@RequestBody BookingRequest request) {
        Long agentId = request.getAgentId();
        if (agentId == null) {
            throw new IllegalArgumentException("Agent ID is required");
        }
        bookingService.blockSlot(agentId, request.getStart(), request.getEnd());
    }

    @PutMapping("/{id}/reschedule")
    public Booking reschedule(@PathVariable Long id, @RequestBody BookingRequest request) {
        return bookingService.rescheduleBooking(id, request.getStart(), request.getEnd());
    }

    @GetMapping("/availability")
    public List<String> getAvailability(@RequestParam String date, @RequestParam Long agentId) {
        return bookingService.getAvailableSlots(date, agentId);
    }
}
