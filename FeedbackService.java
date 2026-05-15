package com.insurai.service;

import com.insurai.model.Feedback;
import com.insurai.model.User;
import com.insurai.repository.FeedbackRepository;
import com.insurai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeedbackService {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Submit new feedback
     */
    public Feedback submitFeedback(long userId, String category, String subject, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Validate category
        if (!isValidCategory(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid category. Must be: BUG, QUERY, SUGGESTION, or COMPLAINT");
        }

        Feedback feedback = new Feedback();
        feedback.setUser(user);
        feedback.setCategory(category);
        feedback.setSubject(subject);
        feedback.setDescription(description);
        feedback.setStatus("OPEN");
        feedback.setCreatedAt(LocalDateTime.now());

        return feedbackRepository.save(feedback);
    }

    /**
     * Get all feedback (admin)
     */
    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAll();
    }

    /**
     * Get feedback by user
     */
    public List<Feedback> getUserFeedback(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return feedbackRepository.findByUser(user);
    }

    /**
     * Get feedback by status
     */
    public List<Feedback> getFeedbackByStatus(String status) {
        return feedbackRepository.findByStatus(status);
    }

    /**
     * Get feedback by category
     */
    public List<Feedback> getFeedbackByCategory(String category) {
        return feedbackRepository.findByCategory(category);
    }

    /**
     * Assign feedback to admin/agent
     */
    public Feedback assignFeedback(long feedbackId, long assigneeId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));

        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignee not found"));

        feedback.setAssignedTo(assignee);
        if ("OPEN".equals(feedback.getStatus())) {
            feedback.setStatus("IN_PROGRESS");
        }

        return feedbackRepository.save(feedback);
    }

    /**
     * Update feedback status
     */
    public Feedback updateStatus(long feedbackId, String status, String adminResponse) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));

        if (!isValidStatus(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status. Must be: OPEN, IN_PROGRESS, or RESOLVED");
        }

        feedback.setStatus(status);
        if (adminResponse != null && !adminResponse.trim().isEmpty()) {
            feedback.setAdminResponse(adminResponse);
        }

        if ("RESOLVED".equals(status)) {
            feedback.setResolvedAt(LocalDateTime.now());
        }

        return feedbackRepository.save(feedback);
    }

    /**
     * Get feedback statistics
     */
    public FeedbackStats getStats() {
        FeedbackStats stats = new FeedbackStats();
        stats.setTotalFeedback(feedbackRepository.count());
        stats.setOpenFeedback(feedbackRepository.countByStatus("OPEN"));
        stats.setInProgressFeedback(feedbackRepository.countByStatus("IN_PROGRESS"));
        stats.setResolvedFeedback(feedbackRepository.countByStatus("RESOLVED"));
        return stats;
    }

    /**
     * Validate category
     */
    private boolean isValidCategory(String category) {
        return "BUG".equals(category) || "QUERY".equals(category) ||
                "SUGGESTION".equals(category) || "COMPLAINT".equals(category);
    }

    /**
     * Validate status
     */
    private boolean isValidStatus(String status) {
        return "OPEN".equals(status) || "IN_PROGRESS".equals(status) || "RESOLVED".equals(status);
    }

    // Inner class for feedback statistics
    public static class FeedbackStats {
        private Long totalFeedback;
        private Long openFeedback;
        private Long inProgressFeedback;
        private Long resolvedFeedback;

        public Long getTotalFeedback() {
            return totalFeedback;
        }

        public void setTotalFeedback(Long totalFeedback) {
            this.totalFeedback = totalFeedback;
        }

        public Long getOpenFeedback() {
            return openFeedback;
        }

        public void setOpenFeedback(Long openFeedback) {
            this.openFeedback = openFeedback;
        }

        public Long getInProgressFeedback() {
            return inProgressFeedback;
        }

        public void setInProgressFeedback(Long inProgressFeedback) {
            this.inProgressFeedback = inProgressFeedback;
        }

        public Long getResolvedFeedback() {
            return resolvedFeedback;
        }

        public void setResolvedFeedback(Long resolvedFeedback) {
            this.resolvedFeedback = resolvedFeedback;
        }
    }
}
