package com.insurai.controller;

import com.insurai.model.User;
import com.insurai.repository.UserRepository;
import com.insurai.security.JwtTokenProvider;
import com.insurai.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    private final UserRepository userRepository;
    private final com.insurai.repository.CompanyRepository companyRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final com.insurai.service.NotificationService notificationService;

    public AuthController(UserRepository userRepository,
            com.insurai.repository.CompanyRepository companyRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            EmailService emailService,
            com.insurai.service.NotificationService notificationService) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user, org.springframework.security.core.Authentication auth) {
        if (user.getEmail() == null || user.getPassword() == null || user.getRole() == null) {
            return ResponseEntity.badRequest().body("Missing required fields");
        }

        // Security: Block SuperAdmin creation
        if ("SUPER_ADMIN".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(403).body("Registration is restricted for this role.");
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        user.setAvailable(false);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setIsActive(true);

        // Handle Company assignment
        // Case 1: Created by Super Admin or explicit company ID provided
        if (user.getMappingCompanyId() != null) {
            Long cId = user.getMappingCompanyId();
            companyRepository.findById(java.util.Objects.requireNonNull(cId)).ifPresent(user::setCompany);
        }
        // Case 2: Created by Company Admin (automatic mapping for all roles they
        // create)
        else if (auth != null && auth.isAuthenticated()) {
            // Check if creator is a Super Admin - they might be registering without
            // explicit companyId
            // but usually they use the dropdown. If they don't, we don't auto-assign.

            String creatorEmail = auth.getName();
            // Check if creator is a User (e.g., a COMPANY_ADMIN role in users table)
            userRepository.findByEmail(creatorEmail).ifPresent(currentUser -> {
                if (currentUser.getCompany() != null) {
                    user.setCompany(currentUser.getCompany());
                }
            });

            // If not found in users or no company there, check if creator is a Company
            // entity itself
            if (user.getCompany() == null) {
                companyRepository.findByEmail(creatorEmail).ifPresent(user::setCompany);
            }
        }

        User saved = userRepository.save(user);

        // Notify Admins for real-time dashboard update
        notificationService.broadcastUpdate("admin-updates", "NEW_USER_REGISTERED");

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User request) {
        // 1. Try User Login
        var userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                return ResponseEntity.status(401).body("Invalid email or password");
            }

            if (Boolean.FALSE.equals(user.getIsActive())) {
                return ResponseEntity.status(403).body("Account is deactivated. Contact Admin.");
            }

            // Generate Token
            String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole(), user.getId());

            // Auto-set Agent to Online
            if ("AGENT".equals(user.getRole())) {
                user.setAvailable(true);
                userRepository.save(user);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("user", user);
            response.put("token", token);

            return ResponseEntity.ok(response);
        }

        // 2. Try Company Login
        var companyOpt = companyRepository.findByEmail(request.getEmail());
        if (companyOpt.isPresent()) {
            com.insurai.model.Company company = companyOpt.get();
            if (!passwordEncoder.matches(request.getPassword(), company.getPassword())) {
                return ResponseEntity.status(401).body("Invalid email or password");
            }

            if (Boolean.FALSE.equals(company.getIsActive())) { // Assuming Company has isActive
                return ResponseEntity.status(403).body("Company account is deactivated. Contact Admin.");
            }

            // Generate Token
            String token = jwtTokenProvider.generateToken(company.getEmail(), "COMPANY", company.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("user", company); // Sends company object with role "COMPANY_ADMIN" via getter
            response.put("token", token);

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(401).body("Invalid email or password");
    }

    @GetMapping("/forgot")
    public ResponseEntity<?> forgot(@RequestParam String email) {
        User u = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        String token = UUID.randomUUID().toString();
        u.setResetToken(token);
        userRepository.save(u);

        // Send Email
        String link = "http://localhost:3000/reset-password?token=" + token;
        try {
            emailService.send(email, "Reset Your Password", "Click here to reset: " + link);
            return ResponseEntity.ok("Reset link sent to email.");
        } catch (Exception e) {
            System.err.println("Email failed: " + e.getMessage());
            // For dev/demo only: Return link if email fails
            return ResponseEntity.ok("Email failed. Dev Link: " + link);
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        String newPassword = payload.get("newPassword");

        User u = userRepository.findByResetToken(token).orElseThrow(() -> new RuntimeException("Invalid token"));
        u.setPassword(passwordEncoder.encode(newPassword));
        u.setResetToken(null);
        userRepository.save(u);

        return ResponseEntity.ok("Password updated successfully");
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> payload,
            org.springframework.security.core.Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        String oldPassword = payload.get("oldPassword");
        String newPassword = payload.get("newPassword");
        if (oldPassword == null || newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest().body("Invalid request: password must be at least 8 characters");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email).map(user -> {
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                return ResponseEntity.status(400).body(Map.of("message", "Current password is incorrect"));
            }
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
        }).orElse(ResponseEntity.status(404).body(Map.of("message", "User not found")));
    }

    @GetMapping("/verify")
    public String verify(@RequestParam String email) {
        User u = userRepository.findByEmail(email).orElseThrow();
        u.setVerified(true);
        userRepository.save(u);
        return "Account verified successfully!";
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "timestamp", System.currentTimeMillis()));
    }
}
