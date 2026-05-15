package com.insurai.config;

import com.insurai.model.*;
import com.insurai.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * DataSeeder – Runs FIRST (Order 1).
 * Seeds: 1 SuperAdmin, 11 Companies + 11 CompanyAdmins,
 * 100 Agents, 250 Users, plus policies for all companies.
 * Populates every table in the schema.
 * Guard: only runs when DB is empty (userRepo.count() == 0).
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

        private final PolicyRepository policyRepo;
        private final UserRepository userRepo;
        private final CompanyRepository companyRepo;
        private final BookingRepository bookingRepo;
        private final UserPolicyRepository userPolicyRepo;
        private final ClaimRepository claimRepo;
        private final FeedbackRepository feedbackRepo;
        private final AuditLogRepository auditLogRepo;
        private final UserCompanyMapRepository userCompanyMapRepo;
        private final AgentReviewRepository agentReviewRepo;
        private final ExceptionCaseRepository exceptionCaseRepo;
        private final SmartReminderRepository smartReminderRepo;
        private final DocumentRepository docRepo;
        private final NotificationRepository notifRepo;
        private final PasswordEncoder passwordEncoder;

        // Passwords
        private static final String PWD_SUPER = "sUpEr@123";
        private static final String PWD_COMPANY = "cOmPaNy@123";
        private static final String PWD_AGENT = "aGeNt@123";
        private static final String PWD_USER = "uSeR@123";

        private final Random rng = new Random(42);

        public DataSeeder(PolicyRepository policyRepo, UserRepository userRepo,
                        CompanyRepository companyRepo, BookingRepository bookingRepo,
                        UserPolicyRepository userPolicyRepo, ClaimRepository claimRepo,
                        FeedbackRepository feedbackRepo, AuditLogRepository auditLogRepo,
                        UserCompanyMapRepository userCompanyMapRepo,
                        AgentReviewRepository agentReviewRepo,
                        ExceptionCaseRepository exceptionCaseRepo,
                        SmartReminderRepository smartReminderRepo,
                        DocumentRepository docRepo, NotificationRepository notifRepo,
                        PasswordEncoder passwordEncoder) {
                this.policyRepo = policyRepo;
                this.userRepo = userRepo;
                this.companyRepo = companyRepo;
                this.bookingRepo = bookingRepo;
                this.userPolicyRepo = userPolicyRepo;
                this.claimRepo = claimRepo;
                this.feedbackRepo = feedbackRepo;
                this.auditLogRepo = auditLogRepo;
                this.userCompanyMapRepo = userCompanyMapRepo;
                this.agentReviewRepo = agentReviewRepo;
                this.exceptionCaseRepo = exceptionCaseRepo;
                this.smartReminderRepo = smartReminderRepo;
                this.docRepo = docRepo;
                this.notifRepo = notifRepo;
                this.passwordEncoder = passwordEncoder;
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public void run(String... args) throws Exception {
                if (userRepo.count() > 0) {
                        System.out.println("DataSeeder: data already present – skipping.");
                        return;
                }
                System.out.println("DataSeeder: clean DB – seeding full dataset for all tables...");

                LocalDateTime now = LocalDateTime.now();

                // 1. SUPER ADMIN
                User superAdmin = userRepo.save(makeUser(
                                "Super Admin", "superadmin@insurai.com", PWD_SUPER, "SUPER_ADMIN",
                                40, "Mumbai", null, null, null));
                System.out.println("SuperAdmin seeded.");

                // 2. COMPANIES
                Company[] companies = {
                                saveCompany("LIC India", "admin@licindia.insurai.com", "REG-LIC-001",
                                                "Life Insurance Corporation of India – Nation's largest insurer.",
                                                "New Delhi", "1800-33-5232"),
                                saveCompany("HDFC Life", "admin@hdfclife.insurai.com", "REG-HDFC-002",
                                                "HDFC Life Insurance Company Ltd – Premium life & health cover.",
                                                "Mumbai", "1860-267-9999"),
                                saveCompany("ICICI Prudential", "admin@icicipru.insurai.com", "REG-ICICI-003",
                                                "ICICI Prudential Life Insurance – Smart protection plans.", "Mumbai",
                                                "1860-266-7766"),
                                saveCompany("Star Health", "admin@starhealth.insurai.com", "REG-STAR-004",
                                                "Star Health & Allied Insurance – India's largest health insurer.",
                                                "Chennai", "1800-425-2255"),
                                saveCompany("Bajaj Allianz", "admin@bajajallianz.insurai.com", "REG-BAJ-005",
                                                "Bajaj Allianz General Insurance – Comprehensive general cover.",
                                                "Pune", "1800-209-5858"),
                                saveCompany("New India Assurance", "admin@newinda.insurai.com", "REG-NIA-006",
                                                "New India Assurance Co. Ltd – Government-backed general insurance.",
                                                "Mumbai", "1800-209-1415"),
                                saveCompany("SBI Life", "admin@sbilife.insurai.com", "REG-SBI-007",
                                                "SBI Life Insurance Company Ltd – Trusted life coverage.", "Mumbai",
                                                "1800-267-9090"),
                                saveCompany("Tata AIG", "admin@tataaig.insurai.com", "REG-TATA-008",
                                                "Tata AIG General Insurance – Global expertise, local care.", "Mumbai",
                                                "1800-266-7780"),
                                saveCompany("Max Life", "admin@maxlife.insurai.com", "REG-MAX-009",
                                                "Max Life Insurance Co. Ltd – Long-term financial protection.",
                                                "New Delhi", "1860-120-5577"),
                                saveCompany("Reliance General", "admin@relgen.insurai.com", "REG-REL-010",
                                                "Reliance General Insurance – Comprehensive risk protection.", "Mumbai",
                                                "1800-102-4088"),
                                saveCompany("Aditya Birla Health", "admin@abhealth.insurai.com", "REG-ABH-011",
                                                "Aditya Birla Health Insurance – ReActivate wellness benefit.",
                                                "Mumbai", "1800-270-7000"),
                };
                System.out.println("Companies seeded (11).");

                // 3. POLICIES
                List<Policy> allPolicies = new ArrayList<>();
                String[][] companyPolicies = {
                                { "LIC Jeevan Anand", "Personal Insurance", "Life", "3200", "2000000" },
                                { "LIC Tech Term", "Personal Insurance", "Life", "1200", "10000000" },
                                { "LIC New Endowment", "Investment Plans", "Endowment", "5000", "1500000" },
                                { "LIC Money Back 20yr", "Investment Plans", "Money Back", "4200", "800000" },
                                { "LIC Group Health Shield", "Employee Benefits", "Group Health", "9000", "500000" },
                                { "HDFC Click 2 Protect", "Personal Insurance", "Life", "900", "10000000" },
                                { "HDFC Sanchay Plus", "Investment Plans", "Guaranteed", "6000", "2500000" },
                                { "HDFC Cancer Care", "Health Insurance", "Critical", "1800", "3000000" },
                                { "HDFC Pension Super", "Investment Plans", "Pension", "8000", "20000000" },
                                { "HDFC Child Advantage", "Personal Insurance", "Child", "4500", "1200000" },
                                { "ICICI iProtect Smart", "Personal Insurance", "Life", "950", "12000000" },
                                { "ICICI Signature", "Investment Plans", "ULIP", "12000", "5000000" },
                                { "ICICI Health Shield", "Health Insurance", "Health", "2200", "1000000" },
                                { "ICICI Wealth Builder", "Investment Plans", "Investment", "10000", "3000000" },
                                { "ICICI Cancer Protect", "Health Insurance", "Critical", "1400", "2500000" },
                                { "Star Comprehensive", "Health Insurance", "Health", "1400", "500000" },
                                { "Star Family Health", "Health Insurance", "Family", "2800", "1000000" },
                                { "Star Senior Red Carpet", "Health Insurance", "Senior", "2100", "1000000" },
                                { "Star Critical Illness", "Health Insurance", "Critical", "1100", "2000000" },
                                { "Star Outpatient Care", "Health Insurance", "OPD", "500", "100000" },
                                { "Bajaj Motor Own Damage", "Other Plans", "Motor", "1200", "700000" },
                                { "Bajaj Home Shield", "Other Plans", "Home", "600", "3000000" },
                                { "Bajaj Travel Elite", "Other Plans", "Travel", "400", "1000000" },
                                { "Bajaj Smart Health", "Health Insurance", "Health", "1600", "500000" },
                                { "Bajaj Life Goal", "Personal Insurance", "Life", "2200", "8000000" },
                                { "NIA Mediclaim", "Health Insurance", "Health", "1300", "500000" },
                                { "NIA Janata Personal", "Personal Insurance", "Accident", "500", "1000000" },
                                { "NIA Motor Third Party", "Other Plans", "Motor", "800", "750000" },
                                { "NIA Shopkeeper Policy", "Business Insurance", "Property", "1800", "5000000" },
                                { "NIA Health Plus", "Health Insurance", "Family", "2600", "1500000" },
                                { "SBI eShield Next", "Personal Insurance", "Life", "850", "10000000" },
                                { "SBI Smart Wealth", "Investment Plans", "ULIP", "9000", "4000000" },
                                { "SBI Retire Smart", "Investment Plans", "Pension", "7000", "15000000" },
                                { "SBI Health Top Up", "Health Insurance", "Top-Up", "700", "2000000" },
                                { "SBI Group Term", "Employee Benefits", "Group Life", "5000", "300000" },
                                { "Tata AIG Cyber Secure", "Liability", "Cyber", "3500", "10000000" },
                                { "Tata AIG Travel Guard", "Other Plans", "Travel", "350", "750000" },
                                { "Tata AIG MediPrime", "Health Insurance", "Health", "1500", "500000" },
                                { "Tata AIG Smart Home", "Other Plans", "Home", "700", "4000000" },
                                { "Tata AIG Business Guard", "Business Insurance", "Property", "2500", "8000000" },
                                { "Max Smart Term Plan", "Personal Insurance", "Life", "1100", "15000000" },
                                { "Max Whole Life Super", "Personal Insurance", "Life", "6500", "5000000" },
                                { "Max Future Genius", "Personal Insurance", "Child", "5000", "2000000" },
                                { "Max Fast Track Super", "Investment Plans", "ULIP", "11000", "4000000" },
                                { "Max Heart & Cancer", "Health Insurance", "Critical", "1700", "3000000" },
                                { "Reliance Health Gain", "Health Insurance", "Health", "1200", "500000" },
                                { "Reliance Personal", "Personal Insurance", "Accident", "600", "1500000" },
                                { "Reliance Motor Comp.", "Other Plans", "Motor", "1000", "800000" },
                                { "Reliance Critical Illness", "Health Insurance", "Critical", "1300", "2000000" },
                                { "Reliance SME Shield", "Business Insurance", "Property", "2000", "5000000" },
                                { "ABHI Activ Health", "Health Insurance", "Health", "1800", "1000000" },
                                { "ABHI Activ Assure", "Health Insurance", "Health", "1200", "500000" },
                                { "ABHI Activ Fit", "Health Insurance", "Wellness", "900", "300000" },
                                { "ABHI Group Activ", "Employee Benefits", "Group Health", "8000", "500000" },
                                { "ABHI Cancer Protect", "Health Insurance", "Critical", "1400", "2500000" },
                };
                for (int i = 0; i < companyPolicies.length; i++) {
                        Company co = companies[i / 5];
                        String[] p = companyPolicies[i];
                        Policy pol = new Policy();
                        pol.setName(p[0]);
                        pol.setCategory(p[1]);
                        pol.setType(p[2]);
                        pol.setPremium(Double.parseDouble(p[3]));
                        pol.setCoverage(Double.parseDouble(p[4]));
                        pol.setDescription(p[0] + " – Comprehensive " + p[2] + " protection by " + co.getName() + ".");
                        pol.setClaimSettlementRatio(92.0 + rng.nextDouble() * 7.9);
                        // ElementCollection: Exclusions and Warnings
                        pol.setExclusions(Arrays.asList("Pre-existing diseases not covered for 24 months",
                                        "Acts of war or terrorism excluded", "Self-inflicted injuries"));
                        pol.setWarnings(Arrays.asList("Read the fine print regarding waiting periods.",
                                        "Claim intimation must be within 48 hours."));
                        pol.setCompany(co);
                        allPolicies.add(policyRepo.save(pol));
                }
                System.out.println("Policies seeded (55).");

                // 4. COMPANY ADMINS
                String[][] caData = {
                                { "Rajesh Nair", "ca.lic@insurai.com" },
                                { "Priya Mehta", "ca.hdfc@insurai.com" },
                                { "Arjun Bose", "ca.icici@insurai.com" },
                                { "Sunita Krishnan", "ca.star@insurai.com" },
                                { "Deepak Patil", "ca.bajaj@insurai.com" },
                                { "Mohan Verma", "ca.newinda@insurai.com" },
                                { "Anita Iyer", "ca.sbi@insurai.com" },
                                { "Suresh Ranjan", "ca.tata@insurai.com" },
                                { "Kavitha Reddy", "ca.max@insurai.com" },
                                { "Vikas Sharma", "ca.reliance@insurai.com" },
                                { "Neha Joshi", "ca.abhi@insurai.com" },
                };
                String[] cities = { "New Delhi", "Mumbai", "Mumbai", "Chennai", "Pune", "Mumbai", "Mumbai", "Mumbai",
                                "New Delhi", "Mumbai", "Mumbai" };
                for (int i = 0; i < 11; i++) {
                        User ca = makeUser(caData[i][0], caData[i][1], PWD_COMPANY, "COMPANY_ADMIN", 40, cities[i],
                                        null, null, null);
                        ca.setCompany(companies[i]);
                        userRepo.save(ca);
                }

                // 5. AGENTS
                List<User> agents = new ArrayList<>();
                String[] specs = { "Life", "Health", "Motor", "Travel", "Cyber", "Property", "Investment",
                                "Critical Care", "Group Health", "Pension" };
                String[] agentRegions = { "Mumbai", "Delhi", "Bengaluru", "Chennai", "Kolkata", "Ahmedabad", "Pune" };
                for (int i = 0; i < 100; i++) {
                        Company co = companies[i % 11];
                        User ag = new User();
                        ag.setName("Agent " + (i + 1));
                        ag.setEmail("agent" + (i + 1) + "@insurai.com");
                        ag.setPassword(passwordEncoder.encode(PWD_AGENT));
                        ag.setRole("AGENT");
                        ag.setSpecialization(specs[i % specs.length]);
                        ag.setRating(3.8 + rng.nextDouble() * 1.2);
                        ag.setBio("Specialist in " + specs[i % specs.length] + " at " + co.getName());
                        ag.setIsActive(true);
                        ag.setAvailable(true);
                        ag.setCompany(co);

                        // ElementCollections: assignedRegions, assignedPolicyTypes
                        ag.setAssignedRegions(Arrays.asList(agentRegions[rng.nextInt(agentRegions.length)],
                                        "All India (Online)"));
                        ag.setAssignedPolicyTypes(Arrays.asList(specs[i % specs.length], "General"));

                        agents.add(userRepo.save(ag));
                }

                // 6. USERS
                List<User> users = new ArrayList<>();
                for (int i = 0; i < 250; i++) {
                        User u = new User();
                        u.setName("User " + (i + 1));
                        u.setEmail("user" + (i + 1) + "@insurai.com");
                        u.setPassword(passwordEncoder.encode(PWD_USER));
                        u.setRole("USER");
                        u.setAge(22 + rng.nextInt(45));
                        u.setAddress(cities[i % cities.length]);
                        u.setIncome(30000.0 + rng.nextInt(200000));
                        u.setDependents(rng.nextInt(5));
                        u.setHealthInfo("None");
                        u.setIsActive(true);
                        u.setVerified(true);
                        users.add(userRepo.save(u));
                }

                // 7. USER POLICIES + UCM
                List<UserPolicy> userPolicies = new ArrayList<>();
                List<UserCompanyMap> ucmList = new ArrayList<>();
                String[] upStatuses = { "ACTIVE", "ACTIVE", "ACTIVE", "EXPIRED", "PENDING_APPROVAL" };
                for (User u : users) {
                        int numP = 1 + rng.nextInt(3);
                        Set<Long> addedCompanies = new HashSet<>();
                        for (int p = 0; p < numP; p++) {
                                Policy pol = allPolicies.get(rng.nextInt(allPolicies.size()));
                                UserPolicy up = new UserPolicy();
                                up.setUser(u);
                                up.setPolicy(pol);
                                up.setStatus(upStatuses[rng.nextInt(upStatuses.length)]);
                                up.setPremium(pol.getPremium());
                                up.setPurchasedAt(now.minusDays(rng.nextInt(720)));

                                // ElementCollection: alternativePolicyIds
                                up.setAlternativePolicyIds(Arrays.asList(
                                                allPolicies.get(rng.nextInt(allPolicies.size())).getId(),
                                                allPolicies.get(rng.nextInt(allPolicies.size())).getId()));
                                userPolicies.add(up);

                                if (addedCompanies.add(pol.getCompany().getId())) {
                                        UserCompanyMap ucm = new UserCompanyMap();
                                        ucm.setUser(u);
                                        ucm.setCompany(pol.getCompany());
                                        ucm.setPolicy(pol);
                                        ucm.setStatus("ACTIVE");
                                        ucmList.add(ucm);
                                }
                        }
                }
                userPolicyRepo.saveAll(userPolicies);
                userCompanyMapRepo.saveAll(ucmList);

                // 8. BOOKINGS
                String[] bookStatuses = { "PENDING", "APPROVED", "COMPLETED", "REJECTED", "CANCELLED" };
                List<Booking> bookings = new ArrayList<>();
                for (int i = 0; i < 350; i++) {
                        User u = users.get(rng.nextInt(users.size()));
                        User ag = agents.get(rng.nextInt(agents.size()));
                        Policy pol = allPolicies.get(rng.nextInt(allPolicies.size()));
                        String st = bookStatuses[rng.nextInt(bookStatuses.length)];
                        LocalDateTime start = ("PENDING".equals(st) || "APPROVED".equals(st))
                                        ? now.plusDays(1 + rng.nextInt(14))
                                        : now.minusDays(1 + rng.nextInt(120));
                        Booking b = new Booking();
                        b.setUser(u);
                        b.setAgent(ag);
                        b.setPolicy(pol);
                        b.setStatus(st);
                        b.setStartTime(start);
                        b.setEndTime(start.plusHours(1));
                        b.setReason("Consultation");
                        bookings.add(b);
                }
                bookingRepo.saveAll(bookings);

                // 9. AGENT REVIEWS
                List<AgentReview> reviews = new ArrayList<>();
                for (Booking b : bookings) {
                        if ("COMPLETED".equals(b.getStatus()) && rng.nextBoolean()) {
                                AgentReview ar = new AgentReview();
                                ar.setAgent(b.getAgent());
                                ar.setUser(b.getUser());
                                ar.setBooking(b);
                                ar.setRating(3 + rng.nextInt(3));
                                ar.setFeedback("Good service.");
                                ar.setCreatedAt(b.getStartTime().plusDays(1));
                                reviews.add(ar);
                        }
                }
                agentReviewRepo.saveAll(reviews);

                // 10. CLAIMS
                String[] claimTypes = { "HEALTH", "ACCIDENT", "PROPERTY_DAMAGE", "DEATH", "CYBER" };
                String[] claimStatuses = { "INITIATED", "DOCS_UPLOADED", "UNDER_REVIEW", "APPROVED", "REJECTED" };
                List<Claim> claims = new ArrayList<>();
                for (int i = 0; i < 250; i++) {
                        User u = users.get(rng.nextInt(users.size()));
                        Policy pol = allPolicies.get(rng.nextInt(allPolicies.size()));
                        String st = claimStatuses[rng.nextInt(claimStatuses.length)];
                        double fraudScore = rng.nextDouble() * 0.8;
                        Claim c = new Claim();
                        c.setUser(u);
                        c.setPolicy(pol);
                        c.setPolicyName(pol.getName());
                        c.setClaimType(claimTypes[rng.nextInt(claimTypes.length)]);
                        c.setAmount(5000.0 + rng.nextInt(100000));
                        c.setStatus(st);
                        c.setFraudScore(fraudScore);
                        c.setDate(now.minusDays(rng.nextInt(300)));
                        c.setDescription("Claim filed for " + c.getClaimType());
                        c.setSuccessProbability(fraudScore < 0.3 ? 85 : 40);

                        // ElementCollection: documentUrls
                        c.setDocumentUrls(Arrays.asList(
                                        "https://insurai-storage.com/docs/" + UUID.randomUUID() + "-proof1.pdf",
                                        "https://insurai-storage.com/docs/" + UUID.randomUUID() + "-proof2.pdf"));
                        c.setNextAction("UNDER_REVIEW".equals(st) ? "Wait for assessment" : "Action required");
                        claims.add(c);
                }
                claimRepo.saveAll(claims);

                // 11. EXCEPTION CASES
                List<ExceptionCase> exceptions = new ArrayList<>();
                for (Claim c : claims) {
                        if ("REJECTED".equals(c.getStatus()) && rng.nextBoolean()) {
                                ExceptionCase ec = new ExceptionCase();
                                ec.setCaseType("DISPUTED_CLAIM");
                                ec.setTitle("Dispute: " + c.getPolicyName());
                                ec.setDescription("Dispute description");
                                ec.setPriority("HIGH");
                                ec.setStatus("PENDING");
                                ec.setUser(c.getUser());
                                ec.setPolicy(c.getPolicy()); // Link the policy so company admins can see it
                                if (c.getPolicy() != null) {
                                        ec.setCompany(c.getPolicy().getCompany());
                                }
                                ec.setCreatedAt(c.getDate().plusDays(2));
                                exceptions.add(ec);
                        }
                }
                exceptionCaseRepo.saveAll(exceptions);

                // 12. FEEDBACK
                List<Feedback> feedbacks = new ArrayList<>();
                for (int i = 0; i < 150; i++) {
                        User u = users.get(rng.nextInt(users.size()));
                        Feedback fb = new Feedback();
                        fb.setUser(u);
                        fb.setCategory("QUERY");
                        fb.setSubject("General App Query");
                        fb.setDescription("Query details...");
                        fb.setStatus(rng.nextBoolean() ? "OPEN" : "RESOLVED");
                        fb.setCreatedAt(now.minusDays(rng.nextInt(100)));
                        if ("RESOLVED".equals(fb.getStatus())) {
                                fb.setAdminResponse("Resolved.");
                                fb.setAssignedTo(superAdmin);
                                fb.setResolvedAt(fb.getCreatedAt().plusDays(2));
                        }
                        feedbacks.add(fb);
                }
                feedbackRepo.saveAll(feedbacks);

                // 13. SMART REMINDERS
                List<SmartReminder> reminders = new ArrayList<>();
                for (User u : users) {
                        if (rng.nextBoolean()) {
                                SmartReminder sr = new SmartReminder();
                                sr.setUser(u);
                                sr.setTitle("Policy Renewal");
                                sr.setMessage("Your policy is expiring soon.");
                                sr.setType("POLICY_RENEWAL");
                                sr.setPriority("HIGH");
                                sr.setReminderTime(now.plusDays(10));
                                sr.setCreatedAt(now);
                                reminders.add(sr);
                        }
                }
                smartReminderRepo.saveAll(reminders);

                // 14. AUDIT LOGS
                List<AuditLog> logs = new ArrayList<>();
                for (int i = 0; i < 200; i++) {
                        User performer = users.get(rng.nextInt(users.size()));
                        AuditLog log = new AuditLog("LOGIN", "USER", (long) (rng.nextInt(100) + 1),
                                        performer.getId(), performer.getRole(), performer.getName());
                        log.setDetails("Login event.");
                        log.setSeverity("INFO");
                        log.setIpAddress("192.168.1." + rng.nextInt(255));
                        logs.add(log);
                }
                auditLogRepo.saveAll(logs);

                // 15. DOCUMENTS
                List<Document> documents = new ArrayList<>();
                String[] docTypes = { "IDENTITY", "INCOME", "MEDICAL" };
                String[] docStatuses = { "PENDING", "VERIFIED", "REJECTED" };
                for (int i = 0; i < 200; i++) {
                        User u = users.get(rng.nextInt(users.size()));
                        Document d = new Document();
                        d.setUser(u);
                        d.setName(docTypes[rng.nextInt(docTypes.length)] + " Proof");
                        d.setFilename("doc_" + i + ".pdf");
                        d.setType(docTypes[rng.nextInt(docTypes.length)]);
                        d.setStatus(docStatuses[rng.nextInt(docStatuses.length)]);
                        d.setUrl("https://insurai-storage.com/docs/" + d.getFilename());
                        d.setSize((long) (1024 + rng.nextInt(5000000)));
                        d.setUploadedAt(now.minusDays(rng.nextInt(120)));
                        if ("VERIFIED".equals(d.getStatus())) {
                                d.setVerifiedBy("SYSTEM");
                                d.setVerifiedAt(d.getUploadedAt().plusDays(1));
                        }
                        documents.add(d);
                }
                docRepo.saveAll(documents);

                // 16. NOTIFICATIONS
                List<Notification> notifications = new ArrayList<>();
                String[] notifTypes = { "INFO", "SUCCESS", "WARNING", "ERROR" };
                for (int i = 0; i < 300; i++) {
                        User u = users.get(rng.nextInt(users.size()));
                        Notification n = new Notification();
                        n.setUser(u);
                        n.setMessage("System notification regarding your account.");
                        n.setType(notifTypes[rng.nextInt(notifTypes.length)]);
                        n.setRead(rng.nextBoolean());
                        n.setCreatedAt(now.minusDays(rng.nextInt(30)));
                        notifications.add(n);
                }
                notifRepo.saveAll(notifications);

                System.out.println("\n=== DataSeeder COMPLETE ===");
                System.out.println("SuperAdmin  : superadmin@insurai.com / sUpEr@123");
                System.out.println("CompanyAdmin: ca.lic@insurai.com / cOmPaNy@123");
                System.out.println("Agents      : agent1@insurai.com .. / aGeNt@123");
                System.out.println("Users       : user1@insurai.com .. / uSeR@123");
        }

        private Company saveCompany(String name, String email, String reg, String desc, String city, String phone) {
                Company c = new Company();
                c.setName(name);
                c.setEmail(email);
                c.setPassword(passwordEncoder.encode(PWD_COMPANY));
                c.setRegistrationNumber(reg);
                c.setDescription(desc);
                c.setStatus("APPROVED");
                c.setIsActive(true);
                c.setAddress(city);
                c.setPhone(phone);
                return companyRepo.save(c);
        }

        private User makeUser(String name, String email, String password, String role,
                        int age, String address, Double income, Integer dependents, String health) {
                User u = new User();
                u.setName(name);
                u.setEmail(email);
                u.setPassword(passwordEncoder.encode(password));
                u.setRole(role);
                u.setAge(age);
                u.setAddress(address);
                u.setIncome(income);
                u.setDependents(dependents);
                u.setHealthInfo(health);
                u.setIsActive(true);
                u.setVerified(true);
                return u;
        }
}
