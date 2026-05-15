package com.insurai.service;

import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class GoogleCalendarService {

    /**
     * Creates a meeting event and returns the meeting link.
     * Uses Jitsi Meet for immediate, no-auth valid meeting rooms.
     */
    public String createMeeting(String summary, String description, String startTime, String endTime,
            String... attendees) {
        // Generate a consistent, valid Jitsi Meet URL
        // Format: https://meet.jit.si/InsurAI_Consultation_<RandomCode>
        String meetingCode = generateMeetingCode();
        return "https://meet.jit.si/InsurAI_Consultation_" + meetingCode;
    }

    private String generateMeetingCode() {
        // Generates a random alphanumeric string for unique room
        return generateRandomString(10);
    }

    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
