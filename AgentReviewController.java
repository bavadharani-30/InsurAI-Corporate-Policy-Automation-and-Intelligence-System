package com.insurai.controller;

import com.insurai.model.AgentReview;
import com.insurai.service.AgentReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "http://localhost:3000")
public class AgentReviewController {

    @Autowired
    private AgentReviewService reviewService;

    /**
     * Submit a review for an agent (USER only)
     */
    @PostMapping("/submit")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> submitReview(
            @RequestBody ReviewRequest request,
            Principal principal) {
        try {
            Long userId = Long.parseLong(principal.getName());

            AgentReview review = reviewService.submitReview(
                    request.getBookingId(),
                    userId,
                    request.getRating(),
                    request.getFeedback());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Review submitted successfully!");
            response.put("review", review);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get all reviews for an agent (PUBLIC)
     */
    @GetMapping("/agent/{agentId}")
    public ResponseEntity<?> getAgentReviews(@PathVariable Long agentId) {
        try {
            List<AgentReview> reviews = reviewService.getAgentReviews(agentId);
            return ResponseEntity.ok(reviews);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get review for a specific booking
     */
    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasAnyRole('USER', 'AGENT', 'ADMIN')")
    public ResponseEntity<?> getReviewByBooking(@PathVariable Long bookingId) {
        try {
            AgentReview review = reviewService.getReviewByBooking(bookingId);
            if (review == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("exists", false);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.ok(review);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Check if user can review a booking
     */
    @GetMapping("/can-review/{bookingId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> canReview(@PathVariable Long bookingId, Principal principal) {
        try {
            Long userId = Long.parseLong(principal.getName());
            boolean canReview = reviewService.canReview(bookingId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("canReview", canReview);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get agent statistics (rating, review count)
     */
    @GetMapping("/agent/{agentId}/stats")
    public ResponseEntity<?> getAgentStats(@PathVariable Long agentId) {
        try {
            AgentReviewService.AgentStats stats = reviewService.getAgentStats(agentId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Request DTO
    public static class ReviewRequest {
        private Long bookingId;
        private Integer rating;
        private String feedback;

        public Long getBookingId() {
            return bookingId;
        }

        public void setBookingId(Long bookingId) {
            this.bookingId = bookingId;
        }

        public Integer getRating() {
            return rating;
        }

        public void setRating(Integer rating) {
            this.rating = rating;
        }

        public String getFeedback() {
            return feedback;
        }

        public void setFeedback(String feedback) {
            this.feedback = feedback;
        }
    }
}
