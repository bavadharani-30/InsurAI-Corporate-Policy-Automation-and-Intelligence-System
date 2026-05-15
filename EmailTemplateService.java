package com.insurai.service;

import com.insurai.model.Booking;
import com.insurai.model.Policy;
import com.insurai.model.UserPolicy;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Email Template Service
 * Generates HTML email templates for various notifications
 */
@Service
public class EmailTemplateService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("EEEE, MMMM dd, yyyy 'at' hh:mm a");

    /**
     * Appointment Confirmation Template (New)
     */
    public String getAppointmentConfirmationTemplate(Booking booking) {
        String userName = booking.getUser().getName();
        String policyName = booking.getPolicy().getName();
        String dateTime = booking.getStartTime().format(DATE_FORMATTER);

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #333; background-color: #f4f4f4; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 20px auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px 20px; text-align: center; }
                        .header h1 { margin: 0; font-size: 28px; font-weight: 600; }
                        .content { padding: 30px 20px; }
                        .info-box { background: #f8f9fa; border-left: 4px solid #667eea; padding: 15px; margin: 20px 0; border-radius: 5px; }
                        .footer { background: #f8f9fa; padding: 20px; text-align: center; font-size: 14px; color: #666; }
                        .icon { font-size: 48px; margin-bottom: 10px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="icon">📅</div>
                            <h1>Appointment Requested</h1>
                        </div>
                        <div class="content">
                            <p>Hello <strong>%s</strong>,</p>
                            <p>We have received your appointment request for <strong>%s</strong>.</p>

                            <div class="info-box">
                                <p><strong>Requested Date:</strong> %s</p>
                                <p><strong>Status:</strong> Pending Approval</p>
                            </div>

                            <p>An agent will review your request shortly. You will receive another email once your appointment is confirmed.</p>
                        </div>
                        <div class="footer">
                            <p>© 2026 InsurAI. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(userName, policyName, dateTime);
    }

    /**
     * Appointment Approved Email Template
     */
    public String getAppointmentApprovedTemplate(Booking booking) {
        String userName = booking.getUser().getName();
        String agentName = booking.getAgent().getName();
        String dateTime = booking.getStartTime().format(DATE_FORMATTER);
        String meetingLink = booking.getMeetingLink();
        String appointmentId = booking.getId().toString();

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            line-height: 1.6;
                            color: #333;
                            background-color: #f4f4f4;
                            margin: 0;
                            padding: 0;
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
                            padding: 30px 20px;
                            text-align: center;
                        }
                        .header h1 {
                            margin: 0;
                            font-size: 28px;
                            font-weight: 600;
                        }
                        .content {
                            padding: 30px 20px;
                        }
                        .content p {
                            margin: 15px 0;
                            font-size: 16px;
                        }
                        .info-box {
                            background: #f8f9fa;
                            border-left: 4px solid #667eea;
                            padding: 15px;
                            margin: 20px 0;
                            border-radius: 5px;
                        }
                        .info-box strong {
                            color: #667eea;
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
                            transition: transform 0.2s;
                        }
                        .button:hover {
                            transform: translateY(-2px);
                        }
                        .footer {
                            background: #f8f9fa;
                            padding: 20px;
                            text-align: center;
                            font-size: 14px;
                            color: #666;
                        }
                        .icon {
                            font-size: 48px;
                            margin-bottom: 10px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="icon">✅</div>
                            <h1>Appointment Approved!</h1>
                        </div>
                        <div class="content">
                            <p>Hello <strong>%s</strong>,</p>
                            <p>Great news! Your insurance consultation appointment has been approved.</p>

                            <div class="info-box">
                                <p><strong>👤 Agent:</strong> %s</p>
                                <p><strong>📅 Date & Time:</strong> %s</p>
                                <p><strong>🔗 Meeting Link:</strong> <a href="%s" style="color: #667eea;">Join Meeting</a></p>
                            </div>

                            <p>Please join the meeting at the scheduled time using the link above.</p>

                            <center>
                                <a href="%s" class="button">View Appointment Details</a>
                            </center>

                            <p style="margin-top: 30px; font-size: 14px; color: #666;">
                                💡 <strong>Tip:</strong> Add this appointment to your calendar to receive reminders.
                            </p>
                        </div>
                        <div class="footer">
                            <p>© 2026 InsurAI. All rights reserved.</p>
                            <p>Need help? Contact us at support@insurai.com</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(userName, agentName, dateTime, meetingLink,
                        "http://localhost:3000/appointments/" + appointmentId);
    }

    /**
     * Appointment Rejected Email Template
     */
    public String getAppointmentRejectedTemplate(Booking booking, List<Policy> alternatives, String rejectionReason) {
        String userName = booking.getUser().getName();
        String agentName = booking.getAgent().getName();
        String reason = rejectionReason != null ? rejectionReason : "Policy requirements not met";

        StringBuilder alternativesHtml = new StringBuilder();
        if (alternatives != null && !alternatives.isEmpty()) {
            alternativesHtml.append("<h3 style='color: #667eea;'>🎯 AI-Recommended Alternative Policies</h3>");
            for (Policy policy : alternatives) {
                alternativesHtml.append(String.format(
                        """
                                <div style='background: #f8f9fa; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #667eea;'>
                                    <h4 style='margin: 0 0 10px 0; color: #333;'>%s</h4>
                                    <p style='margin: 5px 0;'><strong>Type:</strong> %s</p>
                                    <p style='margin: 5px 0;'><strong>Premium:</strong> $%.2f/month</p>
                                    <p style='margin: 5px 0;'><strong>Coverage:</strong> $%.2f</p>
                                </div>
                                """,
                        policy.getName(), policy.getType(), policy.getPremium(), policy.getCoverage()));
            }
        }

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            line-height: 1.6;
                            color: #333;
                            background-color: #f4f4f4;
                            margin: 0;
                            padding: 0;
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
                            background: linear-gradient(135deg, #f093fb 0%%, #f5576c 100%%);
                            color: white;
                            padding: 30px 20px;
                            text-align: center;
                        }
                        .header h1 {
                            margin: 0;
                            font-size: 28px;
                            font-weight: 600;
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
                        .footer {
                            background: #f8f9fa;
                            padding: 20px;
                            text-align: center;
                            font-size: 14px;
                            color: #666;
                        }
                        .icon {
                            font-size: 48px;
                            margin-bottom: 10px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="icon">ℹ️</div>
                            <h1>Appointment Update</h1>
                        </div>
                        <div class="content">
                            <p>Hello <strong>%s</strong>,</p>
                            <p>Thank you for your interest in our insurance services. After reviewing your appointment request,
                               <strong>%s</strong> has provided the following feedback:</p>

                            <div style='background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; border-radius: 5px;'>
                                <p style='margin: 0;'><strong>Reason:</strong> %s</p>
                            </div>

                            %s

                            <p>We encourage you to explore these alternative options that may better suit your needs.</p>

                            <center>
                                <a href="http://localhost:3000/policies" class="button">Browse Policies</a>
                            </center>
                        </div>
                        <div class="footer">
                            <p>© 2026 InsurAI. All rights reserved.</p>
                            <p>Questions? Contact us at support@insurai.com</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(userName, agentName, reason, alternativesHtml.toString());
    }

    /**
     * Policy Approved Email Template
     */
    public String getPolicyApprovedTemplate(UserPolicy userPolicy) {
        String userName = userPolicy.getUser().getName();
        String policyName = userPolicy.getPolicy().getName();
        String premium = String.format("$%.2f", userPolicy.getPolicy().getPremium());
        String coverage = String.format("$%.2f", userPolicy.getPolicy().getCoverage());

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            line-height: 1.6;
                            color: #333;
                            background-color: #f4f4f4;
                            margin: 0;
                            padding: 0;
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
                            background: linear-gradient(135deg, #11998e 0%%, #38ef7d 100%%);
                            color: white;
                            padding: 30px 20px;
                            text-align: center;
                        }
                        .header h1 {
                            margin: 0;
                            font-size: 28px;
                            font-weight: 600;
                        }
                        .content {
                            padding: 30px 20px;
                        }
                        .info-box {
                            background: #f8f9fa;
                            border-left: 4px solid #38ef7d;
                            padding: 15px;
                            margin: 20px 0;
                            border-radius: 5px;
                        }
                        .button {
                            display: inline-block;
                            background: linear-gradient(135deg, #11998e 0%%, #38ef7d 100%%);
                            color: white !important;
                            padding: 14px 28px;
                            text-decoration: none;
                            border-radius: 25px;
                            margin: 20px 0;
                            font-weight: 600;
                        }
                        .footer {
                            background: #f8f9fa;
                            padding: 20px;
                            text-align: center;
                            font-size: 14px;
                            color: #666;
                        }
                        .icon {
                            font-size: 48px;
                            margin-bottom: 10px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="icon">🎉</div>
                            <h1>Policy Approved!</h1>
                        </div>
                        <div class="content">
                            <p>Hello <strong>%s</strong>,</p>
                            <p>Congratulations! Your policy has been approved and is ready for activation.</p>

                            <div class="info-box">
                                <p><strong>📋 Policy:</strong> %s</p>
                                <p><strong>💰 Premium:</strong> %s/month</p>
                                <p><strong>🛡️ Coverage:</strong> %s</p>
                            </div>

                            <p><strong>Next Steps:</strong></p>
                            <ol>
                                <li>Complete the payment process</li>
                                <li>Your policy will be activated immediately</li>
                                <li>You'll receive your policy documents via email</li>
                            </ol>

                            <center>
                                <a href="http://localhost:3000/payment/%s" class="button">Complete Payment</a>
                            </center>

                            <p style="margin-top: 30px; font-size: 14px; color: #666;">
                                ⏰ Please complete payment within 7 days to activate your policy.
                            </p>
                        </div>
                        <div class="footer">
                            <p>© 2026 InsurAI. All rights reserved.</p>
                            <p>Need assistance? Contact us at support@insurai.com</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(userName, policyName, premium, coverage, userPolicy.getId());
    }

    /**
     * Meeting Reminder Email Template (24 hours before)
     */
    /**
     * Meeting Reminder Email Template (24 hours before)
     */
    public String getMeetingReminderTemplate(Booking booking) {
        String userName = booking.getUser().getName();
        String agentName = booking.getAgent().getName();
        String dateTime = booking.getStartTime().format(DATE_FORMATTER);
        String meetingLink = booking.getMeetingLink();

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #333; background-color: #f4f4f4; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 20px auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                        .header { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; padding: 30px 20px; text-align: center; }
                        .header h1 { margin: 0; font-size: 28px; font-weight: 600; }
                        .content { padding: 30px 20px; }
                        .info-box { background: #f8f9fa; border-left: 4px solid #f5576c; padding: 15px; margin: 20px 0; border-radius: 5px; }
                        .button { display: inline-block; background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white !important; padding: 14px 28px; text-decoration: none; border-radius: 25px; margin: 20px 0; font-weight: 600; }
                        .footer { background: #f8f9fa; padding: 20px; text-align: center; font-size: 14px; color: #666; }
                        .icon { font-size: 48px; margin-bottom: 10px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="icon">⏰</div>
                            <h1>Meeting Reminder</h1>
                        </div>
                        <div class="content">
                            <p>Hello <strong>%s</strong>,</p>
                            <p>Just a friendly reminder about your upcoming consultation with <strong>%s</strong>.</p>

                            <div class="info-box">
                                <p><strong>Time:</strong> %s</p>
                                <p><strong>Join Link:</strong> <a href="%s">Click to Join</a></p>
                            </div>

                            <center>
                                <a href="%s" class="button">Join Meeting Now</a>
                            </center>
                        </div>
                        <div class="footer">
                            <p>© 2026 InsurAI. All rights reserved.</p>
                            <p>Please join 5 minutes early to test your audio/video.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(userName, agentName, dateTime, meetingLink, meetingLink);
    }

    /**
     * Review Request Email Template
     */
    /**
     * Review Request Email Template
     */
    public String getReviewRequestTemplate(Booking booking) {
        String userName = booking.getUser().getName();
        String agentName = booking.getAgent().getName();

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #333; background-color: #f4f4f4; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 20px auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                        .header { background: linear-gradient(135deg, #FFD700 0%, #FFA500 100%); color: white; padding: 30px 20px; text-align: center; }
                        .header h1 { margin: 0; font-size: 28px; font-weight: 600; text-shadow: 0 1px 2px rgba(0,0,0,0.1); }
                        .content { padding: 30px 20px; text-align: center; }
                        .button { display: inline-block; background: linear-gradient(135deg, #FFD700 0%, #FFA500 100%); color: white !important; padding: 14px 28px; text-decoration: none; border-radius: 25px; margin: 20px 0; font-weight: 600; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .footer { background: #f8f9fa; padding: 20px; text-align: center; font-size: 14px; color: #666; }
                        .icon { font-size: 48px; margin-bottom: 10px; }
                        .stars { font-size: 32px; color: #ffc107; margin: 15px 0; letter-spacing: 5px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div class="icon">⭐</div>
                            <h1>How was your Agent?</h1>
                        </div>
                        <div class="content">
                            <p>Hello <strong>%s</strong>,</p>
                            <p>You recently spoke with <strong>%s</strong>. We'd love to know how it went!</p>

                            <div class="stars">★ ★ ★ ★ ★</div>

                            <p>Your feedback helps us recognize great agents and improve our service.</p>

                            <center>
                                <a href="http://localhost:3000/review/%s" class="button">Submit Review</a>
                            </center>
                        </div>
                        <div class="footer">
                            <p>© 2026 InsurAI. All rights reserved.</p>
                            <p>It only takes 30 seconds!</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(userName, agentName, booking.getId());
    }
}
