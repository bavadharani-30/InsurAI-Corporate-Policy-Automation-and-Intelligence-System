package com.insurai.service;

import com.insurai.model.Booking;
import com.insurai.model.Policy;
import com.insurai.model.UserPolicy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Email Service
 * Handles sending emails for various notifications
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final String FROM_EMAIL = "noreply@insurai.com";
    private static final String FROM_NAME = "InsurAI";

    private final JavaMailSender mailSender;
    private final EmailTemplateService templateService;

    public EmailService(JavaMailSender mailSender, EmailTemplateService templateService) {
        this.mailSender = mailSender;
        this.templateService = templateService;
    }

    /**
     * Send appointment requested confirmation email
     */
    @Async
    public void sendAppointmentRequested(Booking booking) {
        try {
            String htmlContent = templateService.getAppointmentConfirmationTemplate(booking);
            String subject = "📅 Appointment Request Received – InsurAI";
            String recipientEmail = booking.getUser().getEmail();
            String recipientName = booking.getUser().getName();

            sendEmail(recipientEmail, recipientName, subject, htmlContent);
            logger.info("Appointment confirmation email sent to: {}", recipientEmail);
        } catch (Exception e) {
            logger.error("Failed to send appointment confirmation email", e);
        }
    }

    /**
     * Send appointment approved email
     */
    @Async
    public void sendAppointmentApproved(Booking booking) {
        try {
            String htmlContent = templateService.getAppointmentApprovedTemplate(booking);
            String subject = "✅ Appointment Approved – InsurAI";
            String recipientEmail = booking.getUser().getEmail();
            String recipientName = booking.getUser().getName();

            sendEmail(recipientEmail, recipientName, subject, htmlContent);
            logger.info("Appointment approved email sent to: {}", recipientEmail);
        } catch (Exception e) {
            logger.error("Failed to send appointment approved email", e);
        }
    }

    /**
     * Send appointment rejected email with AI recommendations
     */
    @Async
    public void sendAppointmentRejected(Booking booking, List<Policy> alternatives, String rejectionReason) {
        try {
            String htmlContent = templateService.getAppointmentRejectedTemplate(booking, alternatives, rejectionReason);
            String subject = "ℹ️ Appointment Update – InsurAI";
            String recipientEmail = booking.getUser().getEmail();
            String recipientName = booking.getUser().getName();

            sendEmail(recipientEmail, recipientName, subject, htmlContent);
            logger.info("Appointment rejected email sent to: {}", recipientEmail);
        } catch (Exception e) {
            logger.error("Failed to send appointment rejected email", e);
        }
    }

    /**
     * Send policy approved email
     */
    @Async
    public void sendPolicyApproved(UserPolicy userPolicy) {
        try {
            String htmlContent = templateService.getPolicyApprovedTemplate(userPolicy);
            String subject = "🎉 Policy Approved – Ready for Activation";
            String recipientEmail = userPolicy.getUser().getEmail();
            String recipientName = userPolicy.getUser().getName();

            sendEmail(recipientEmail, recipientName, subject, htmlContent);
            logger.info("Policy approved email sent to: {}", recipientEmail);
        } catch (Exception e) {
            logger.error("Failed to send policy approved email", e);
        }
    }

    /**
     * Send meeting reminder (24 hours before)
     */
    @Async
    public void sendMeetingReminder(Booking booking) {
        try {
            String htmlContent = templateService.getMeetingReminderTemplate(booking);
            String subject = "⏰ Meeting Reminder – Tomorrow at " +
                    booking.getStartTime().toLocalTime().toString();
            String recipientEmail = booking.getUser().getEmail();
            String recipientName = booking.getUser().getName();

            sendEmail(recipientEmail, recipientName, subject, htmlContent);
            logger.info("Meeting reminder sent to: {}", recipientEmail);
        } catch (Exception e) {
            logger.error("Failed to send meeting reminder", e);
        }
    }

    /**
     * Send review request email
     */
    @Async
    public void sendReviewRequest(Booking booking) {
        try {
            String htmlContent = templateService.getReviewRequestTemplate(booking);
            String subject = "⭐ How was your experience with InsurAI?";
            String recipientEmail = booking.getUser().getEmail();
            String recipientName = booking.getUser().getName();

            sendEmail(recipientEmail, recipientName, subject, htmlContent);
            logger.info("Review request sent to: {}", recipientEmail);
        } catch (Exception e) {
            logger.error("Failed to send review request", e);
        }
    }

    /**
     * Send welcome email to new users
     */
    @Async
    public void sendWelcomeEmail(String email, String name) {
        try {
            String htmlContent = getWelcomeEmailTemplate(name);
            String subject = "👋 Welcome to InsurAI!";

            sendEmail(email, name, subject, htmlContent);
            logger.info("Welcome email sent to: {}", email);
        } catch (Exception e) {
            logger.error("Failed to send welcome email", e);
        }
    }

    /**
     * Generic email sending method for other services
     * Supports legacy calls from AuthController, BookingService, etc.
     */
    @Async
    public void send(String to, String subject, String text) {
        // Wrap plain text in simple HTML structure
        String htmlContent = String.format("""
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <div style="padding: 20px;">
                        %s
                    </div>
                </body>
                </html>
                """, text.replace("\n", "<br>"));

        sendEmail(to, null, subject, htmlContent);
    }

    /**
     * Core email sending method
     */
    private void sendEmail(String to, String toName, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM_EMAIL, FROM_NAME);
            helper.setTo(to != null ? to : "");
            helper.setSubject(subject != null ? subject : "");
            helper.setText(htmlContent != null ? htmlContent : "", true); // true = HTML content

            // Add reply-to
            helper.setReplyTo("support@insurai.com");

            mailSender.send(message);
            logger.debug("Email sent successfully to: {}", to);

        } catch (MessagingException e) {
            logger.error("Failed to send email to: {}", to, e);
            throw new RuntimeException("Email sending failed", e);
        } catch (Exception e) {
            logger.error("Unexpected error sending email to: {}", to, e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    /**
     * Welcome email template
     */
    private String getWelcomeEmailTemplate(String name) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            line-height: 1.6;
                            color: #333;
                            background-color: #f4f4f4;
                        }
                        .container {
                            max-width: 600px;
                            margin: 20px auto;
                            background: white;
                            border-radius: 10px;
                            overflow: hidden;
                            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                        }
                        .header {
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                            color: white;
                            padding: 40px 20px;
                            text-align: center;
                        }
                        .content {
                            padding: 30px 20px;
                        }
                        .button {
                            display: inline-block;
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                            color: white !important;
                            padding: 14px 28px;
                            text-decoration: none;
                            border-radius: 25px;
                            margin: 20px 0;
                            font-weight: 600;
                        }
                        .feature-box {
                            background: #f8f9fa;
                            padding: 15px;
                            margin: 10px 0;
                            border-radius: 5px;
                            border-left: 4px solid #667eea;
                        }
                        .footer {
                            background: #f8f9fa;
                            padding: 20px;
                            text-align: center;
                            font-size: 14px;
                            color: #666;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1 style="margin: 0; font-size: 32px;">👋 Welcome to InsurAI!</h1>
                        </div>
                        <div class="content">
                            <p>Hello <strong>%s</strong>,</p>
                            <p>Thank you for joining InsurAI! We're excited to help you find the perfect insurance coverage.</p>

                            <h3 style="color: #667eea;">What you can do with InsurAI:</h3>

                            <div class="feature-box">
                                <strong>🔍 Browse Policies</strong><br>
                                Explore our wide range of insurance options
                            </div>

                            <div class="feature-box">
                                <strong>🤖 AI Recommendations</strong><br>
                                Get personalized policy suggestions based on your profile
                            </div>

                            <div class="feature-box">
                                <strong>👥 Expert Consultations</strong><br>
                                Book appointments with certified insurance agents
                            </div>

                            <div class="feature-box">
                                <strong>⚡ Quick Activation</strong><br>
                                Activate your policy in minutes after approval
                            </div>

                            <center>
                                <a href="http://localhost:3000/policies" class="button">Browse Policies</a>
                            </center>

                            <p style="margin-top: 30px;">
                                If you have any questions, our support team is here to help!
                            </p>
                        </div>
                        <div class="footer">
                            <p>© 2026 InsurAI. All rights reserved.</p>
                            <p>Contact us: support@insurai.com | +1 (555) 123-4567</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(name);
    }

    /**
     * Test email connectivity
     */
    public boolean testEmailConnection() {
        try {
            mailSender.createMimeMessage();
            logger.info("Email connection test successful");
            return true;
        } catch (Exception e) {
            logger.error("Email connection test failed", e);
            return false;
        }
    }
}
