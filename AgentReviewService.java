package com.insurai.service;

import com.insurai.model.AgentReview;
import com.insurai.model.Booking;
import com.insurai.model.User;
import com.insurai.repository.AgentReviewRepository;
import com.insurai.repository.BookingRepository;
import com.insurai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentReviewService {

    @Autowired
    private AgentReviewRepository reviewRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Submit a review for an agent after consultation
     */
    public AgentReview submitReview(long bookingId, long userId, Integer rating, String feedback) {
        // Validate booking exists
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        // Validate user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Verify booking belongs to user
        if (!booking.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only review your own appointments");
        }

        // Verify consultation is completed
        if (!"CONSULTED".equals(booking.getStatus()) && !"POLICY_APPROVED".equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only review completed consultations");
        }

        // Check if review already exists
        if (reviewRepository.existsByBooking(booking)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review already submitted for this appointment");
        }

        // Validate rating
        if (rating < 1 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
        }

        // Create review
        AgentReview review = new AgentReview();
        review.setBooking(booking);
        review.setAgent(booking.getAgent());
        review.setUser(user);
        review.setRating(rating);
        review.setFeedback(feedback);
        review.setCreatedAt(LocalDateTime.now());

        AgentReview savedReview = reviewRepository.save(review);

        // Update agent's average rating
        updateAgentRating(booking.getAgent());

        return savedReview;
    }

    /**
     * Update agent's average rating
     */
    public void updateAgentRating(User agent) {
        Double averageRating = reviewRepository.calculateAverageRating(agent);
        if (averageRating != null) {
            agent.setRating(Math.round(averageRating * 10.0) / 10.0); // Round to 1 decimal
            userRepository.save(agent);
        }
    }

    /**
     * Get all reviews for an agent
     */
    public List<AgentReview> getAgentReviews(long agentId) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        return reviewRepository.findByAgent(agent);
    }

    /**
     * Get review for a specific booking
     */
    public AgentReview getReviewByBooking(long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        return reviewRepository.findByBooking(booking).orElse(null);
    }

    /**
     * Check if user can review a booking
     */
    public boolean canReview(long bookingId, long userId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null)
            return false;

        // Check if booking belongs to user
        if (!booking.getUser().getId().equals(userId))
            return false;

        // Check if consultation is completed
        if (!"CONSULTED".equals(booking.getStatus()) && !"POLICY_APPROVED".equals(booking.getStatus())) {
            return false;
        }

        // Check if review already exists
        return !reviewRepository.existsByBooking(booking);
    }

    /**
     * Get agent statistics
     */
    public AgentStats getAgentStats(long agentId) {
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));

        Double averageRating = reviewRepository.calculateAverageRating(agent);
        long totalReviews = reviewRepository.countByAgent(agent);

        AgentStats stats = new AgentStats();
        stats.setAverageRating(averageRating != null ? averageRating : 0.0);
        stats.setTotalReviews(totalReviews);

        return stats;
    }

    // Inner class for agent statistics
    public static class AgentStats {
        private Double averageRating;
        private Long totalReviews;

        public Double getAverageRating() {
            return averageRating;
        }

        public void setAverageRating(Double averageRating) {
            this.averageRating = averageRating;
        }

        public Long getTotalReviews() {
            return totalReviews;
        }

        public void setTotalReviews(Long totalReviews) {
            this.totalReviews = totalReviews;
        }
    }
}
