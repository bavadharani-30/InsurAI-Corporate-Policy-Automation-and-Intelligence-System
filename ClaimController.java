package com.insurai.controller;

import com.insurai.model.Claim;
import com.insurai.service.ClaimService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
@CrossOrigin(origins = "http://localhost:3000")
public class ClaimController {

    private final ClaimService claimService;
    private final com.insurai.repository.UserRepository userRepo;

    public ClaimController(ClaimService claimService, com.insurai.repository.UserRepository userRepo) {
        this.claimService = claimService;
        this.userRepo = userRepo;
    }

    // User: File a claim
    @PostMapping("/{userId}")
    public Claim fileClaim(@PathVariable Long userId, @RequestBody Claim claim) {
        return claimService.fileClaim(java.util.Objects.requireNonNull(userId), claim);
    }

    // User: Get my claims
    @GetMapping("/user/{userId}")
    public List<Claim> getUserClaims(@PathVariable Long userId) {
        return claimService.getUserClaims(java.util.Objects.requireNonNull(userId));
    }

    // Admin: Get all claims (filtered by company for agents)
    @GetMapping
    public List<Claim> getAllClaims(org.springframework.security.core.Authentication auth) {
        if (auth != null && auth.isAuthenticated()) {
            String email = (String) auth.getPrincipal();
            com.insurai.model.User user = userRepo.findByEmail(email).orElse(null);
            if (user != null) {
                if ("AGENT".equals(user.getRole()) && user.getCompany() != null) {
                    return claimService.getClaimsByCompany(user.getCompany().getId());
                }
                if (("COMPANY".equals(user.getRole()) || "COMPANY_ADMIN".equals(user.getRole()))
                        && user.getCompany() != null) {
                    return claimService.getClaimsByCompany(user.getCompany().getId());
                }
            }
        }
        return claimService.getAllClaims();
    }

    // Admin: Update status
    @PutMapping("/{id}/status")
    public Claim updateStatus(@PathVariable Long id, @RequestParam String status) {
        return claimService.updateStatus(java.util.Objects.requireNonNull(id), status);
    }

    // User: Upload document to claim
    @PostMapping("/{id}/upload")
    public Claim uploadDoc(@PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            if (file.isEmpty())
                throw new RuntimeException("Empty file");

            String rawName = file.getOriginalFilename();
            String originalName = (rawName != null) ? rawName.replaceAll("\\s+", "_") : "document";
            String fileName = System.currentTimeMillis() + "_" + originalName;
            java.nio.file.Path path = java.nio.file.Paths.get("uploads/" + fileName);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.copy(file.getInputStream(), path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "http://localhost:8080/uploads/" + fileName;
            return claimService.uploadDoc(java.util.Objects.requireNonNull(id), fileUrl);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to upload claim document", e);
        }
    }
}
