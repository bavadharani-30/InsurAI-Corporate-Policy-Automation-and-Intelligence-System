package com.insurai.service;

import com.insurai.model.Booking;
import com.insurai.model.SmartReminder;
import com.insurai.model.User;
import com.insurai.repository.BookingRepository;
import com.insurai.repository.SmartReminderRepository;
import com.insurai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Smart Reminder Service
 * Manages intelligent reminders for appointments and pending actions
 */
@Service
public class SmartReminderService {

    @Autowired
    private SmartReminderRepository smartReminderRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Get all pending reminders for a user
     */
    public List<SmartReminder> getPendingReminders(Long userId) {
        return smartReminderRepository.findByUserIdAndSentFalse(userId);
    }

    /**
     * Get all reminders for a user
     */
    public List<SmartReminder> getAllReminders(Long userId) {
        return smartReminderRepository.findByUserIdOrderByReminderTimeDesc(userId);
    }

    /**
     * Create appointment reminder
     */
    public SmartReminder createAppointmentReminder(Long bookingId) {
        Booking booking = bookingRepository.findById(java.util.Objects.requireNonNull(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        SmartReminder reminder = new SmartReminder();
        reminder.setUser(booking.getUser());
        reminder.setType("APPOINTMENT");
        reminder.setBookingId(bookingId);
        reminder.setTitle("Upcoming Appointment Reminder");
        reminder.setMessage(String.format(
                "You have an appointment with %s on %s. Please be available at the scheduled time.",
                booking.getAgent().getName(),
                booking.getStartTime().toLocalDate()));

        // Set reminder for 24 hours before appointment
        reminder.setReminderTime(booking.getStartTime().minusHours(24));
        reminder.setPriority("HIGH");
        reminder.setActionUrl("/my-bookings");
        reminder.setActionLabel("View Appointment");

        return smartReminderRepository.save(reminder);
    }

    /**
     * Create pending action reminder
     */
    public SmartReminder createPendingActionReminder(User user, String action, String message, String priority) {
        SmartReminder reminder = new SmartReminder();
        reminder.setUser(user);
        reminder.setType("PENDING_ACTION");
        reminder.setTitle("Action Required: " + action);
        reminder.setMessage(message);
        reminder.setReminderTime(LocalDateTime.now());
        reminder.setPriority(priority);

        return smartReminderRepository.save(reminder);
    }

    /**
     * Create document upload reminder
     */
    public SmartReminder createDocumentUploadReminder(Long userId, Long policyId, String documentName) {
        User user = userRepository.findById(java.util.Objects.requireNonNull(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        SmartReminder reminder = new SmartReminder();
        reminder.setUser(user);
        reminder.setType("DOCUMENT_UPLOAD");
        reminder.setPolicyId(policyId);
        reminder.setTitle("Document Upload Required");
        reminder.setMessage(String.format(
                "Please upload %s to complete your policy application. This is required for approval.",
                documentName));
        reminder.setReminderTime(LocalDateTime.now());
        reminder.setPriority("MEDIUM");
        reminder.setActionUrl("/my-policies");
        reminder.setActionLabel("Upload Document");

        return smartReminderRepository.save(reminder);
    }

    /**
     * Create payment due reminder
     */
    public SmartReminder createPaymentDueReminder(Long userId, Long policyId, Double amount, LocalDateTime dueDate) {
        User user = userRepository.findById(java.util.Objects.requireNonNull(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        SmartReminder reminder = new SmartReminder();
        reminder.setUser(user);
        reminder.setType("PAYMENT_DUE");
        reminder.setPolicyId(policyId);
        reminder.setTitle("Payment Due");
        reminder.setMessage(String.format(
                "Your premium payment of ₹%.2f is due on %s. Please make the payment to avoid policy lapse.",
                amount,
                dueDate.toLocalDate()));
        reminder.setReminderTime(dueDate.minusDays(3)); // 3 days before due date
        reminder.setPriority("URGENT");
        reminder.setActionUrl("/my-policies");
        reminder.setActionLabel("Make Payment");

        return smartReminderRepository.save(reminder);
    }

    /**
     * Mark reminder as sent
     */
    public void markAsSent(Long reminderId) {
        SmartReminder reminder = smartReminderRepository.findById(java.util.Objects.requireNonNull(reminderId))
                .orElseThrow(() -> new RuntimeException("Reminder not found"));

        reminder.setSent(true);
        reminder.setSentAt(LocalDateTime.now());
        smartReminderRepository.save(reminder);
    }

    @Autowired
    private EmailService emailService;

    /**
     * Scheduled task to process due reminders
     * Runs every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void processDueReminders() {
        List<SmartReminder> dueReminders = smartReminderRepository
                .findBySentFalseAndReminderTimeBefore(LocalDateTime.now());

        for (SmartReminder reminder : dueReminders) {
            // Integrate with notification service (email, SMS, push)
            System.out.println("Sending reminder: " + reminder.getTitle() + " to user: " + reminder.getUser().getId());

            try {
                emailService.send(
                        reminder.getUser().getEmail(),
                        reminder.getTitle(),
                        reminder.getMessage() + "\n\nAction: " + reminder.getActionLabel());
            } catch (Exception e) {
                System.err.println("Failed to send email reminder: " + e.getMessage());
            }

            // Mark as sent
            markAsSent(reminder.getId());
        }
    }

    /**
     * Auto-create reminders for upcoming appointments
     * Runs daily at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void createUpcomingAppointmentReminders() {
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        LocalDateTime dayAfterTomorrow = LocalDateTime.now().plusDays(2);

        List<Booking> upcomingBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getStartTime().isAfter(tomorrow) && b.getStartTime().isBefore(dayAfterTomorrow))
                .filter(b -> "CONFIRMED".equals(b.getStatus()) || "APPROVED".equals(b.getStatus()))
                .toList();

        for (Booking booking : upcomingBookings) {
            // Check if reminder already exists
            List<SmartReminder> existing = smartReminderRepository.findByBookingId(booking.getId());
            if (existing.isEmpty()) {
                createAppointmentReminder(booking.getId());
            }
        }
    }

    /**
     * Delete reminder
     */
    public void deleteReminder(Long reminderId) {
        smartReminderRepository.deleteById(java.util.Objects.requireNonNull(reminderId));
    }
}
