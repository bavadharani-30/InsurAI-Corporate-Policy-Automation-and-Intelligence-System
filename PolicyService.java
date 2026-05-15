package com.insurai.service;

import com.insurai.model.Policy;
import com.insurai.model.User;
import com.insurai.model.UserPolicy;
import com.insurai.repository.PolicyRepository;
import com.insurai.repository.UserPolicyRepository;
import com.insurai.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PolicyService {

    private final PolicyRepository policyRepo;
    private final UserPolicyRepository userPolicyRepo;
    private final UserRepository userRepo;
    private final com.insurai.repository.UserCompanyMapRepository userCompanyMapRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private com.insurai.repository.CompanyRepository companyRepository;

    public PolicyService(PolicyRepository policyRepo,
            UserPolicyRepository userPolicyRepo,
            UserRepository userRepo,
            com.insurai.repository.UserCompanyMapRepository userCompanyMapRepo) {
        this.policyRepo = policyRepo;
        this.userPolicyRepo = userPolicyRepo;
        this.userRepo = userRepo;
        this.userCompanyMapRepo = userCompanyMapRepo;
    }

    // Role-based retrieval
    public List<Policy> getAll(String role, long userId) {
        if ("USER".equals(role)) {
            // Users see only ACTIVE policies from APPROVED companies
            return policyRepo.findByStatusAndCompany_Status("ACTIVE", "APPROVED");
        } else if ("COMPANY".equals(role)) {
            // The Company Entity itself sees only its own policies in its dashboard/manager
            User user = userRepo.findById(userId).orElseThrow();
            if (user.getCompany() == null)
                return List.of();
            return policyRepo.findByCompanyId(user.getCompany().getId());
        } else {
            // SUPER_ADMIN, COMPANY_ADMIN, and AGENT see all policies for
            // comparison/governance
            return policyRepo.findAll();
        }
    }

    public Policy create(@org.springframework.lang.NonNull Policy policy, User creator) {
        if ("COMPANY".equals(creator.getRole()) || "COMPANY_ADMIN".equals(creator.getRole())) {
            if (creator.getCompany() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not linked to a company profile.");
            }
            if (!"APPROVED".equals(creator.getCompany().getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Company account is not approved yet.");
            }
            policy.setCompany(creator.getCompany());
            policy.setStatus("ACTIVE"); // Default
            return policyRepo.save(policy);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Insurance Companies can create policies.");
        }
    }

    public Policy update(long id, Policy updates, User updater) {
        Policy policy = policyRepo.findById(java.util.Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));

        // Auth Check
        if ("COMPANY".equals(updater.getRole()) || "COMPANY_ADMIN".equals(updater.getRole())) {
            if (policy.getCompany() == null || !policy.getCompany().getId().equals(updater.getCompany().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this policy.");
            }
        } else if (!"SUPER_ADMIN".equals(updater.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized.");
        }

        policy.setName(updates.getName());
        policy.setCategory(updates.getCategory());
        policy.setType(updates.getType());
        policy.setDescription(updates.getDescription());
        policy.setPremium(updates.getPremium());
        policy.setCoverage(updates.getCoverage());
        policy.setDocumentUrl(updates.getDocumentUrl());
        policy.setClaimSettlementRatio(updates.getClaimSettlementRatio());
        policy.setExclusions(updates.getExclusions());
        policy.setWarnings(updates.getWarnings());
        policy.setMinAge(updates.getMinAge());
        policy.setMaxAge(updates.getMaxAge());
        policy.setMinIncome(updates.getMinIncome());
        policy.setTenure(updates.getTenure());

        // Status update allowed
        if (updates.getStatus() != null) {
            policy.setStatus(updates.getStatus());
        }

        return policyRepo.save(policy);
    }

    public void delete(long id, User deleter) {
        Policy policy = policyRepo.findById(java.util.Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));

        if ("COMPANY".equals(deleter.getRole()) || "COMPANY_ADMIN".equals(deleter.getRole())) {
            if (policy.getCompany() == null || !policy.getCompany().getId().equals(deleter.getCompany().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this policy.");
            }
        } else if (!"SUPER_ADMIN".equals(deleter.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized.");
        }

        policyRepo.deleteById(id);
    }

    public UserPolicy buyPolicy(long policyId, long userId) {
        Policy policy = policyRepo.findById(java.util.Objects.requireNonNull(policyId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));
        User user = userRepo.findById(java.util.Objects.requireNonNull(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!"USER".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clients can buy policies.");
        }

        // Check for existing active policy
        List<UserPolicy> existing = userPolicyRepo.findByUserIdAndPolicyId(userId, policyId);
        for (UserPolicy upCheck : existing) {
            // Prevent buying if ACTIVE or PENDING
            if ("ACTIVE".equalsIgnoreCase(upCheck.getStatus()) || "PENDING".equalsIgnoreCase(upCheck.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You already have an active or pending policy of this type.");
            }
        }

        UserPolicy up = new UserPolicy();
        up.setPolicy(policy);
        up.setUser(user);
        // User immediately buys in this simplified flow, but we can set to PENDING
        // first if payment gateway existed
        up.setStatus("ACTIVE");

        UserPolicy saved = userPolicyRepo.save(up);

        // Update User-Company Map
        if (policy.getCompany() != null) {
            updateUserCompanyMap(user, policy.getCompany(), policy);
        }

        return saved;
    }

    public UserPolicy quotePolicy(long policyId, long userId, String note) {
        Policy policy = policyRepo.findById(java.util.Objects.requireNonNull(policyId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));
        User user = userRepo.findById(java.util.Objects.requireNonNull(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!"USER".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clients can receive policy quotes.");
        }

        UserPolicy up = new UserPolicy();
        up.setPolicy(policy);
        up.setUser(user);
        up.setStatus("QUOTED");
        up.setRecommendationNote(note);

        return userPolicyRepo.save(up);
    }

    public UserPolicy purchasePolicy(long userPolicyId) {
        UserPolicy up = userPolicyRepo.findById(java.util.Objects.requireNonNull(userPolicyId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User Policy not found"));

        // Check for existing active policy (excluding current one)
        List<UserPolicy> existing = userPolicyRepo.findByUserIdAndPolicyId(up.getUser().getId(),
                up.getPolicy().getId());
        for (UserPolicy check : existing) {
            if (!check.getId().equals(up.getId()) && "ACTIVE".equalsIgnoreCase(check.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "User already has an ACTIVE policy of this type.");
            }
        }

        // Simulate Payment Success
        // In a real system, this would happen via a webhook from Stripe/Razorpay
        // Transition: QUOTED -> PURCHASED -> ACTIVE

        up.setStatus("ACTIVE"); // Final state after payment
        up.setStartDate(java.time.LocalDate.now());
        up.setEndDate(up.getStartDate().plusYears(1));

        // Update User-Company Map
        if (up.getPolicy().getCompany() != null) {
            updateUserCompanyMap(up.getUser(), up.getPolicy().getCompany(), up.getPolicy());
        }

        return userPolicyRepo.save(up);
    }

    // Renewal Prediction Logic
    public boolean needsRenewal(UserPolicy up) {
        if (up.getEndDate() == null)
            return false;
        // "Needs Renewal" if expiring within 30 days
        return up.getEndDate().minusDays(30).isBefore(java.time.LocalDate.now());
    }

    // Coverage Gap
    public boolean isUnderInsured(UserPolicy up) {
        // Simple logic: if premium is very low compared to coverage (mock logic)
        // Or if coverage < 50,000 for Health
        if ("Health".equalsIgnoreCase(up.getPolicy().getType()) && up.getPolicy().getCoverage() < 100000) {
            return true;
        }
        return false;
    }

    public List<UserPolicy> getUserPolicies(long userId) {
        return userPolicyRepo.findByUserId(userId);
    }

    public List<UserPolicy> getAllUserPolicies(org.springframework.security.core.Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return List.of();
        }
        String email = auth.getName();

        // Resolve company directly
        var companyOpt = companyRepository.findByEmail(email);
        if (companyOpt.isPresent()) {
            return userPolicyRepo.findByPolicyCompanyId(companyOpt.get().getId());
        }

        User user = userRepo.findByEmail(email).orElse(null);
        if (user != null && ("COMPANY_ADMIN".equals(user.getRole()) || "COMPANY".equals(user.getRole()))
                && user.getCompany() != null) {
            return userPolicyRepo.findByPolicyCompanyId(user.getCompany().getId());
        }

        // Must be SUPER_ADMIN or other
        return userPolicyRepo.findAll();
    }

    public UserPolicy uploadDocument(long userPolicyId, String url) {
        UserPolicy up = userPolicyRepo.findById(java.util.Objects.requireNonNull(userPolicyId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));
        up.setPdfUrl(url);
        return userPolicyRepo.save(up);
    }

    // Scheduler helper to expire policies
    public void checkExpirations() {
        List<UserPolicy> active = userPolicyRepo.findByStatus("ACTIVE");
        for (UserPolicy up : active) {
            if (up.getEndDate().isBefore(java.time.LocalDate.now())) {
                up.setStatus("EXPIRED");
                userPolicyRepo.save(up);
            }
        }
    }

    // NEW: AI-Powered Policy Recommendations with Eligibility
    public List<com.insurai.dto.PolicyRecommendationDTO> getRecommendedPolicies(long userId) {
        User user = userRepo.findById(java.util.Objects.requireNonNull(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Policy> allPolicies = policyRepo.findByStatusAndCompany_Status("ACTIVE", "APPROVED");
        List<com.insurai.dto.PolicyRecommendationDTO> recommendations = new java.util.ArrayList<>();

        for (Policy policy : allPolicies) {
            com.insurai.dto.PolicyRecommendationDTO dto = new com.insurai.dto.PolicyRecommendationDTO(policy);

            // Calculate eligibility
            String eligibility = checkEligibility(user, policy);
            dto.setEligibilityStatus(eligibility);

            // Calculate match score (0-100)
            double matchScore = calculateMatchScore(user, policy);
            dto.setMatchScore(matchScore);

            // Set premium breakdown (can be adjusted based on user profile)
            dto.setPremiumBreakdown(policy.getPremium());

            // AI recommendation logic
            if (matchScore >= 70 && "ELIGIBLE".equals(eligibility)) {
                dto.setIsRecommended(true);
                dto.setRecommendationReason("Best match for your profile");
            } else if (matchScore >= 50) {
                dto.setIsRecommended(false);
                dto.setRecommendationReason("Good option, consult agent for details");
            } else {
                dto.setIsRecommended(false);
                dto.setRecommendationReason("Consider alternatives");
            }

            recommendations.add(dto);
        }

        // Sort by match score (highest first)
        recommendations.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));

        return recommendations;
    }

    // NEW: Filtered Policy Search
    public List<com.insurai.dto.PolicyRecommendationDTO> getFilteredPolicies(
            long userId, com.insurai.dto.PolicyFilterRequest filter) {

        User user = userRepo.findById(java.util.Objects.requireNonNull(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Policy> allPolicies = policyRepo.findByStatusAndCompany_Status("ACTIVE", "APPROVED");
        List<com.insurai.dto.PolicyRecommendationDTO> filtered = new java.util.ArrayList<>();

        for (Policy policy : allPolicies) {
            // Apply filters
            if (filter.getType() != null && !filter.getType().equalsIgnoreCase(policy.getType())) {
                continue;
            }
            if (filter.getCategory() != null && !filter.getCategory().equalsIgnoreCase(policy.getCategory())) {
                continue;
            }
            if (filter.getMaxPremium() != null && policy.getPremium() > filter.getMaxPremium()) {
                continue;
            }
            if (filter.getMinCoverage() != null && policy.getCoverage() < filter.getMinCoverage()) {
                continue;
            }
            if (filter.getMaxCoverage() != null && policy.getCoverage() > filter.getMaxCoverage()) {
                continue;
            }

            com.insurai.dto.PolicyRecommendationDTO dto = new com.insurai.dto.PolicyRecommendationDTO(policy);

            // Calculate eligibility
            String eligibility = checkEligibility(user, policy);
            dto.setEligibilityStatus(eligibility);

            // Calculate match score
            double matchScore = calculateMatchScore(user, policy);
            dto.setMatchScore(matchScore);
            dto.setPremiumBreakdown(policy.getPremium());

            filtered.add(dto);
        }

        // Sort by match score
        filtered.sort((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()));

        return filtered;
    }

    // Helper: Check Eligibility
    private String checkEligibility(User user, Policy policy) {
        StringBuilder reason = new StringBuilder();
        boolean eligible = true;
        boolean partiallyEligible = false;

        // Age check
        if (policy.getMinAge() != null && user.getAge() != null && user.getAge() < policy.getMinAge()) {
            eligible = false;
            reason.append("Age below minimum requirement. ");
        }
        if (policy.getMaxAge() != null && user.getAge() != null && user.getAge() > policy.getMaxAge()) {
            eligible = false;
            reason.append("Age above maximum limit. ");
        }

        // Income check
        if (policy.getMinIncome() != null && user.getIncome() != null && user.getIncome() < policy.getMinIncome()) {
            partiallyEligible = true;
            reason.append("Income below recommended level. ");
        }

        if (!eligible) {
            return "NOT_ELIGIBLE";
        } else if (partiallyEligible) {
            return "PARTIALLY_ELIGIBLE";
        } else {
            return "ELIGIBLE";
        }
    }

    // Helper: Calculate Match Score (0-100)
    private double calculateMatchScore(User user, Policy policy) {
        double score = 50.0; // Base score

        // Age match
        if (policy.getMinAge() != null && policy.getMaxAge() != null && user.getAge() != null) {
            int midAge = (policy.getMinAge() + policy.getMaxAge()) / 2;
            int ageDiff = Math.abs(user.getAge() - midAge);
            score += Math.max(0, 20 - ageDiff); // Up to +20 for perfect age match
        }

        // Income match
        if (policy.getMinIncome() != null && user.getIncome() != null) {
            if (user.getIncome() >= policy.getMinIncome() * 1.5) {
                score += 15; // Good income buffer
            } else if (user.getIncome() >= policy.getMinIncome()) {
                score += 10; // Meets minimum
            }
        }

        // Premium affordability (premium should be < 10% of monthly income)
        if (user.getIncome() != null && policy.getPremium() != null) {
            double monthlyIncome = user.getIncome() / 12;
            double affordabilityRatio = policy.getPremium() / monthlyIncome;
            if (affordabilityRatio < 0.05) {
                score += 15; // Very affordable
            } else if (affordabilityRatio < 0.10) {
                score += 10; // Affordable
            } else if (affordabilityRatio > 0.20) {
                score -= 10; // Too expensive
            }
        }

        // Claim settlement ratio
        if (policy.getClaimSettlementRatio() != null) {
            if (policy.getClaimSettlementRatio() >= 95) {
                score += 10;
            } else if (policy.getClaimSettlementRatio() >= 90) {
                score += 5;
            }
        }

        return Math.min(100, Math.max(0, score));
    }

    // Helper: Update or Create UserCompanyMap
    private void updateUserCompanyMap(User user, com.insurai.model.Company company, Policy policy) {
        com.insurai.model.UserCompanyMap map = userCompanyMapRepo
                .findByUserAndCompany(user.getId(), company.getId())
                .orElse(new com.insurai.model.UserCompanyMap());

        if (map.getId() == null) {
            map.setUser(user);
            map.setCompany(company);
            map.setCreatedAt(java.time.LocalDateTime.now());
        }

        map.setPolicy(policy); // Link most recent policy
        map.setStatus("ACTIVE");
        map.setUpdatedAt(java.time.LocalDateTime.now());

        userCompanyMapRepo.save(map);
    }
}
