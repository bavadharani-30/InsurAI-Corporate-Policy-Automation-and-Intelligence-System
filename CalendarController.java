package com.insurai.controller;

import com.insurai.model.Booking;
import com.insurai.repository.BookingRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * REST Controller for Calendar Integration
 * Generates ICS files for calendar applications
 */
@RestController
@RequestMapping("/api/calendar")
@CrossOrigin(origins = "*")
public class CalendarController {

    private final BookingRepository bookingRepository;

    public CalendarController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /**
     * Generate ICS file for appointment
     * GET /api/calendar/add/{appointmentId}
     */
    @GetMapping("/add/{appointmentId}")
    @PreAuthorize("hasAnyRole('USER', 'AGENT')")
    public ResponseEntity<byte[]> generateICS(@PathVariable long appointmentId) {
        Booking booking = bookingRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Appointment not found"));

        if (booking.getMeetingLink() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Meeting link not available for this appointment");
        }

        String icsContent = generateICSContent(booking);
        byte[] icsBytes = icsContent.getBytes();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar"));
        headers.setContentDispositionFormData("attachment",
                "appointment_" + appointmentId + ".ics");
        headers.setContentLength(icsBytes.length);

        return new ResponseEntity<>(icsBytes, headers, HttpStatus.OK);
    }

    /**
     * Get Google Calendar URL
     * GET /api/calendar/google/{appointmentId}
     */
    @GetMapping("/google/{appointmentId}")
    @PreAuthorize("hasAnyRole('USER', 'AGENT')")
    public ResponseEntity<CalendarUrlResponse> getGoogleCalendarUrl(@PathVariable long appointmentId) {
        Booking booking = bookingRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Appointment not found"));

        String googleUrl = generateGoogleCalendarUrl(booking);

        CalendarUrlResponse response = new CalendarUrlResponse();
        response.setProvider("google");
        response.setUrl(googleUrl);
        response.setAppointmentId(appointmentId);

        return ResponseEntity.ok(response);
    }

    /**
     * Generate ICS content
     */
    private String generateICSContent(Booking booking) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

        String startTime = booking.getStartTime().format(formatter);
        String endTime = booking.getEndTime().format(formatter);
        String now = LocalDateTime.now().format(formatter);

        String title = "Insurance Consultation - " + booking.getAgent().getName();
        String description = "Policy: "
                + (booking.getPolicy() != null ? booking.getPolicy().getName() : "General Consultation") +
                "\\nMeeting Link: " + booking.getMeetingLink() +
                "\\nAgent: " + booking.getAgent().getName() +
                "\\nReason: " + (booking.getReason() != null ? booking.getReason() : "");

        String location = booking.getMeetingLink();

        return "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//InsurAI//Appointment//EN\r\n" +
                "CALSCALE:GREGORIAN\r\n" +
                "METHOD:PUBLISH\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:appointment-" + booking.getId() + "@insurai.com\r\n" +
                "DTSTAMP:" + now + "\r\n" +
                "DTSTART:" + startTime + "\r\n" +
                "DTEND:" + endTime + "\r\n" +
                "SUMMARY:" + title + "\r\n" +
                "DESCRIPTION:" + description + "\r\n" +
                "LOCATION:" + location + "\r\n" +
                "STATUS:CONFIRMED\r\n" +
                "SEQUENCE:0\r\n" +
                "BEGIN:VALARM\r\n" +
                "TRIGGER:-PT15M\r\n" +
                "ACTION:DISPLAY\r\n" +
                "DESCRIPTION:Reminder: Meeting in 15 minutes\r\n" +
                "END:VALARM\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";
    }

    /**
     * Generate Google Calendar URL
     */
    private String generateGoogleCalendarUrl(Booking booking) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

        String startTime = booking.getStartTime().format(formatter);
        String endTime = booking.getEndTime().format(formatter);

        String title = "Insurance Consultation - " + booking.getAgent().getName();
        String details = "Policy: " + (booking.getPolicy() != null ? booking.getPolicy().getName() : "General") +
                "\nMeeting Link: " + booking.getMeetingLink() +
                "\nAgent: " + booking.getAgent().getName();

        String location = booking.getMeetingLink();

        return "https://calendar.google.com/calendar/render?action=TEMPLATE" +
                "&text=" + urlEncode(title) +
                "&dates=" + startTime + "/" + endTime +
                "&details=" + urlEncode(details) +
                "&location=" + urlEncode(location);
    }

    /**
     * Simple URL encoding
     */
    private String urlEncode(String value) {
        if (value == null)
            return "";
        return value.replace(" ", "+")
                .replace("\n", "%0A")
                .replace(":", "%3A")
                .replace("/", "%2F");
    }

    // DTO Classes
    public static class CalendarUrlResponse {
        private String provider;
        private String url;
        private Long appointmentId;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Long getAppointmentId() {
            return appointmentId;
        }

        public void setAppointmentId(Long appointmentId) {
            this.appointmentId = appointmentId;
        }
    }
}
