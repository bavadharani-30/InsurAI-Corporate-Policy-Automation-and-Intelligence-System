package com.insurai.service;

import com.insurai.repository.BookingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SchedulerService {

    private final BookingRepository bookingRepo;

    public SchedulerService(BookingRepository bookingRepo) {
        this.bookingRepo = bookingRepo;
    }

    // Run every minute
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoUpdateBookings() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Expire Pending bookings that are past their time
        int expiredPending = bookingRepo.expirePending(now);

        // 2. Expire Approved bookings that have finished without agent action
        int expiredApproved = bookingRepo.expireUnattended(now);

        if (expiredPending > 0 || expiredApproved > 0) {
            System.out.println("SYSTEM_AUTO_EXPIRED: Time slot exceeded. Expired Pending: " + expiredPending
                    + ", Expired Approved: " + expiredApproved + " at " + now);
        } else {
            System.out.println("Scheduler run: No expirations at " + now);
        }
    }
}
