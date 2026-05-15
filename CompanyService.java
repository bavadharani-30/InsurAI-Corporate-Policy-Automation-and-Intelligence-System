package com.insurai.service;

import com.insurai.model.Company;
import com.insurai.model.Policy;
import com.insurai.repository.CompanyRepository;
import com.insurai.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Register a new company
     */
    public Company registerCompany(Company company) {
        // Validate email uniqueness
        if (companyRepository.existsByEmail(company.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered");
        }

        // Validate registration number uniqueness
        if (company.getRegistrationNumber() != null &&
                companyRepository.existsByRegistrationNumber(company.getRegistrationNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration number already exists");
        }

        // Encrypt password
        company.setPassword(passwordEncoder.encode(company.getPassword()));

        // Set default status
        company.setStatus("PENDING_APPROVAL");
        company.setIsActive(false); // Inactive until approved
        company.setCreatedAt(LocalDateTime.now());

        return companyRepository.save(company);
    }

    /**
     * Get company by ID
     */
    public Company getCompanyById(long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    /**
     * Get company by email
     */
    public Company getCompanyByEmail(String identifier) {
        System.out.println("DEBUG: Looking up company for: " + identifier);
        return companyRepository.findFirstByEmailIgnoreCase(identifier)
                .or(() -> companyRepository.findFirstByName(identifier))
                .or(() -> companyRepository.findFirstByNameIgnoreCase(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Company not found for: " + identifier));
    }

    /**
     * Get all companies (for Super Admin)
     */
    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    /**
     * Get companies by status
     */
    public List<Company> getCompaniesByStatus(String status) {
        return companyRepository.findByStatus(status);
    }

    /**
     * Get active companies
     */
    public List<Company> getActiveCompanies() {
        return companyRepository.findByStatusAndIsActive("APPROVED", true);
    }

    /**
     * Update company profile
     */
    public Company updateCompany(long companyId, Company updatedCompany) {
        Company company = getCompanyById(companyId);

        // Update allowed fields
        if (updatedCompany.getName() != null) {
            company.setName(updatedCompany.getName());
        }
        if (updatedCompany.getAddress() != null) {
            company.setAddress(updatedCompany.getAddress());
        }
        if (updatedCompany.getPhone() != null) {
            company.setPhone(updatedCompany.getPhone());
        }
        if (updatedCompany.getWebsite() != null) {
            company.setWebsite(updatedCompany.getWebsite());
        }
        if (updatedCompany.getDescription() != null) {
            company.setDescription(updatedCompany.getDescription());
        }
        if (updatedCompany.getLogoUrl() != null) {
            company.setLogoUrl(updatedCompany.getLogoUrl());
        }
        if (updatedCompany.getPrimaryColor() != null) {
            company.setPrimaryColor(updatedCompany.getPrimaryColor());
        }

        company.setUpdatedAt(LocalDateTime.now());
        return companyRepository.save(company);
    }

    /**
     * Add a new policy (Company only)
     */
    public Policy addPolicy(long companyId, Policy policy) {
        Company company = getCompanyById(companyId);

        // Verify company is approved and active
        if (!"APPROVED".equals(company.getStatus()) || !company.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Company must be approved and active to add policies");
        }

        policy.setCompany(company);
        return policyRepository.save(policy);
    }

    /**
     * Update policy (Company only - own policies)
     */
    public Policy updatePolicy(long companyId, long policyId, Policy updatedPolicy) {
        // Validate company exists
        getCompanyById(companyId);
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));

        // Verify ownership
        if (policy.getCompany() == null || !policy.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own policies");
        }

        // Update fields
        if (updatedPolicy.getName() != null)
            policy.setName(updatedPolicy.getName());
        if (updatedPolicy.getCategory() != null)
            policy.setCategory(updatedPolicy.getCategory());
        if (updatedPolicy.getType() != null)
            policy.setType(updatedPolicy.getType());
        if (updatedPolicy.getDescription() != null)
            policy.setDescription(updatedPolicy.getDescription());
        if (updatedPolicy.getPremium() != null)
            policy.setPremium(updatedPolicy.getPremium());
        if (updatedPolicy.getCoverage() != null)
            policy.setCoverage(updatedPolicy.getCoverage());
        if (updatedPolicy.getDocumentUrl() != null)
            policy.setDocumentUrl(updatedPolicy.getDocumentUrl());
        if (updatedPolicy.getClaimSettlementRatio() != null)
            policy.setClaimSettlementRatio(updatedPolicy.getClaimSettlementRatio());
        if (updatedPolicy.getExclusions() != null)
            policy.setExclusions(updatedPolicy.getExclusions());
        if (updatedPolicy.getWarnings() != null)
            policy.setWarnings(updatedPolicy.getWarnings());
        if (updatedPolicy.getMinAge() != null)
            policy.setMinAge(updatedPolicy.getMinAge());
        if (updatedPolicy.getMaxAge() != null)
            policy.setMaxAge(updatedPolicy.getMaxAge());
        if (updatedPolicy.getMinIncome() != null)
            policy.setMinIncome(updatedPolicy.getMinIncome());
        if (updatedPolicy.getTenure() != null)
            policy.setTenure(updatedPolicy.getTenure());
        if (updatedPolicy.getStatus() != null)
            policy.setStatus(updatedPolicy.getStatus());

        return policyRepository.save(policy);
    }

    /**
     * Delete policy (Company only - own policies)
     */
    public void deletePolicy(long companyId, long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));

        // Verify ownership
        if (policy.getCompany() == null || !policy.getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own policies");
        }

        policyRepository.delete(policy);
    }

    /**
     * Get all policies for a company
     */
    public List<Policy> getCompanyPolicies(long companyId) {
        // Validate company exists
        getCompanyById(companyId);
        return policyRepository.findAll().stream()
                .filter(p -> p.getCompany() != null && p.getCompany().getId().equals(companyId))
                .toList();
    }

    /**
     * Toggle company active status (Super Admin only)
     */
    public Company toggleCompanyStatus(long companyId, Boolean isActive) {
        Company company = getCompanyById(companyId);
        company.setIsActive(isActive);
        company.setUpdatedAt(LocalDateTime.now());
        return companyRepository.save(company);
    }
}
