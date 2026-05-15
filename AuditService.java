package com.insurai.service;

import org.springframework.stereotype.Service;

import com.insurai.model.AuditLog;
import com.insurai.repository.AuditLogRepository;

@Service
public class AuditService {
  private final AuditLogRepository repo;

  public AuditService(AuditLogRepository repo) {
    this.repo = repo;
  }

  public void log(String action, Long userId) {
    AuditLog l = new AuditLog();
    l.setAction(action);
    l.setUserId(userId);

    // Set default values for required fields to prevent DB errors
    l.setEntityType("GENERAL");
    l.setEntityId(0L);
    l.setPerformedBy(userId); // Explicitly set performedBy
    l.setPerformedByRole("UNKNOWN");
    l.setPerformedByName("User " + userId);
    l.setTimestamp(java.time.LocalDateTime.now());
    l.setSuccess(true);

    repo.save(l);
  }
}
