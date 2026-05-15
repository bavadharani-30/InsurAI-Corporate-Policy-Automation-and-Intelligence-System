package com.insurai.service;

import com.insurai.model.*;
import com.insurai.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class TestDataGeneratorService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PolicyRepository policyRepository;
    private final BookingRepository bookingRepository;
    private final UserPolicyRepository userPolicyRepository;
    private final AgentReviewRepository agentReviewRepository;
    private final FeedbackRepository feedbackRepository;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    public TestDataGeneratorService(UserRepository userRepository,
            CompanyRepository companyRepository,
            PolicyRepository policyRepository,
            BookingRepository bookingRepository,
            UserPolicyRepository userPolicyRepository,
            AgentReviewRepository agentReviewRepository,
            FeedbackRepository feedbackRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.policyRepository = policyRepository;
        this.bookingRepository = bookingRepository;
        this.userPolicyRepository = userPolicyRepository;
        this.agentReviewRepository = agentReviewRepository;
        this.feedbackRepository = feedbackRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void generateTestData() {
        generateCompaniesAndPolicies();
        generateUsers();
        generateAgents();
        generateAdmins();
        generateWorkflows();
        generateFeedback();
    }

    private void generateCompaniesAndPolicies() {
        System.out.println("🌱 Generating Companies and Policies...");

        String[][] companies = {
                { "SafeGuard", "safeguard@example.com", "REG-98255" },
                { "SecureLife", "securelife@example.com", "REG-30264" },
                { "TrustShield", "trustshield@example.com", "REG-61645" },
                { "FutureProtect", "futureprotect@example.com", "REG-8396" },
                { "ReliableInsure", "reliableinsure@example.com", "REG-94142" },
                { "GlobalCover", "globalcover@example.com", "REG-17480" },
                { "PrimeAssure", "primeassure@example.com", "REG-52598" },
                { "EliteCare", "elitecare@example.com", "REG-72487" },
                { "UnityMutual", "unitymutual@example.com", "REG-1113" },
                { "ApexSurance", "apexsurance@example.com", "REG-83217" }
        };

        for (String[] data : companies) {
            String name = data[0];
            String email = data[1];
            String regNo = data[2];

            if (companyRepository.findByEmail(email).isEmpty()) {
                Company company = new Company();
                company.setName(name);
                company.setEmail(email);
                company.setPassword(passwordEncoder.encode("password"));
                company.setStatus("APPROVED");
                company.setRegistrationNumber(regNo);
                company.setDescription("Leading insurance provider " + name);
                company.setIsActive(true);
                company = companyRepository.save(company);

                // Generate Policies for this company
                generatePoliciesForCompany(company);
            }
        }
    }

    private void generatePoliciesForCompany(Company company) {
        String[] categories = {
                "Personal Insurance", "Health Insurance", "Investment Plans",
                "Business Insurance", "Employee Benefits", "Liability",
                "Engineering", "Other Plans", "Small Business", "Corporate"
        };

        for (String category : categories) {
            // Create 1-2 policies per category
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                Policy policy = new Policy();
                String type = category.split(" ")[0]; // Simple type extraction
                policy.setName(company.getName() + " " + type + " Plan " + (char) ('A' + i));
                policy.setCategory(category);
                policy.setType(type);
                policy.setCompany(company);
                policy.setPremium(500.0 + random.nextInt(5000));
                policy.setCoverage(500000.0 + random.nextInt(10000000));
                policy.setDescription("Comprehensive " + category + " coverage provided by " + company.getName());
                policy.setClaimSettlementRatio(90.0 + random.nextDouble() * 9.9);
                policy.setStatus("ACTIVE");
                policy.setMinAge(18);
                policy.setMaxAge(70);
                policy.setMinIncome(300000.0);
                policy.setTenure(1 + random.nextInt(5)); // 1-5 years

                // Add some realistic features/benefits as JSON or simple string if model
                // supports
                // policy.setFeatures("{\"cashless\": true, \"global_cover\": " +
                // random.nextBoolean() + "}");

                policyRepository.save(policy);
            }
        }
    }

    private void generateUsers() {
        System.out.println("🌱 Generating Users...");
        for (int i = 1; i <= 100; i++) { // Generate 100 users
            String email = "user" + i + "@test.com";
            if (userRepository.findByEmail(email).isEmpty()) {
                User user = new User();
                user.setName("Test User " + i);
                user.setEmail(email);
                user.setPassword(passwordEncoder.encode("password"));
                user.setRole("USER");
                user.setIsActive(true);
                user.setVerified(true);
                user.setAge(20 + random.nextInt(50));
                user.setIncome(300000.0 + random.nextInt(2000000));
                userRepository.save(user);
            }
        }
    }

    private void generateAgents() {
        System.out.println("🌱 Generating Agents...");
        for (int i = 1; i <= 50; i++) { // Generate 50 agents
            String email = "agent" + i + "@test.com";
            if (userRepository.findByEmail(email).isEmpty()) {
                User agent = new User();
                agent.setName("Agent " + i);
                agent.setEmail(email);
                agent.setPassword(passwordEncoder.encode("password"));
                agent.setRole("AGENT");
                agent.setIsActive(true);
                agent.setAvailable(random.nextBoolean());
                agent.setVerified(true);
                agent.setSpecialization(random.nextBoolean() ? "Health" : "Life");
                agent.setRating(3.5 + random.nextDouble() * 1.5); // 3.5 to 5.0
                agent.setBio("Expert agent with " + (1 + random.nextInt(10)) + " years experience.");
                userRepository.save(agent);
            }
        }
    }

    private void generateAdmins() {
        // Super Admin
        if (userRepository.findByEmail("superadmin@insurai.com").isEmpty()) {
            User superAdmin = new User();
            superAdmin.setName("Super Admin");
            superAdmin.setEmail("superadmin@insurai.com");
            superAdmin.setPassword(passwordEncoder.encode("password"));
            superAdmin.setRole("SUPER_ADMIN");
            superAdmin.setIsActive(true);
            userRepository.save(superAdmin);
        }
    }

    private void generateWorkflows() {
        System.out.println("🌱 Generating Workflows (Bookings, Policies, Reviews)...");
        List<User> users = userRepository.findByRole("USER");
        List<User> agents = userRepository.findByRole("AGENT");
        List<Policy> policies = policyRepository.findAll();

        if (users.isEmpty() || agents.isEmpty() || policies.isEmpty())
            return;

        for (User user : users) {
            // Create 1-3 bookings per user
            int bookingsCount = 1 + random.nextInt(3);
            for (int k = 0; k < bookingsCount; k++) {
                User agent = agents.get(random.nextInt(agents.size()));
                Policy policy = policies.get(random.nextInt(policies.size()));

                Booking booking = new Booking();
                booking.setUser(user);
                booking.setAgent(agent);
                booking.setPolicy(policy);
                booking.setStartTime(LocalDateTime.now().minusDays(random.nextInt(30)));
                booking.setEndTime(booking.getStartTime().plusMinutes(30));
                booking.setReason("Consultation for " + policy.getName());
                booking.setCreatedAt(booking.getStartTime().minusDays(1));

                // Save first to generate ID
                booking = bookingRepository.save(booking);

                // Randomize status
                int statusRoll = random.nextInt(100);
                if (statusRoll < 20) {
                    booking.setStatus("PENDING");
                } else if (statusRoll < 30) {
                    booking.setStatus("REJECTED");
                    booking.setRejectionReason("Policy requirements not met.");
                    booking.setCompletedAt(LocalDateTime.now());
                } else if (statusRoll < 50) {
                    booking.setStatus("APPROVED");
                    booking.setMeetingLink("https://meet.jit.si/test-meeting-" + booking.getId());
                } else {
                    // CONSULTED / COMPLETED
                    booking.setStatus("CONSULTED");
                    booking.setMeetingLink("https://meet.jit.si/test-meeting-" + booking.getId());
                    booking.setCompletedAt(booking.getEndTime());
                    booking.setAgentNotes("Customer is interested. Recommended moving forward.");

                    // Maybe user purchased the policy?
                    if (random.nextBoolean()) {
                        booking.setStatus("POLICY_APPROVED");
                        createUserPolicy(user, policy, agent);
                    }

                    // Add Review
                    if (random.nextBoolean()) {
                        createAgentReview(booking, user, agent);
                    }
                }
                bookingRepository.save(booking);
            }
        }
    }

    private void createUserPolicy(User user, Policy policy, User agent) {
        UserPolicy up = new UserPolicy();
        up.setUser(user);
        up.setPolicy(policy);
        up.setStatus("ACTIVE");
        up.setStartDate(java.time.LocalDate.now());
        up.setEndDate(java.time.LocalDate.now().plusYears(1));
        up.setPremium(policy.getPremium());
        up.setPurchasedAt(LocalDateTime.now());
        // We lack specific fields linking agent to UserPolicy in some models, but
        // workflow implies it.
        userPolicyRepository.save(up);
    }

    private void createAgentReview(Booking booking, User user, User agent) {
        if (agentReviewRepository.existsByBooking(booking))
            return;

        AgentReview review = new AgentReview();
        review.setBooking(booking);
        review.setUser(user);
        review.setAgent(agent);
        review.setRating(3 + random.nextInt(3)); // 3, 4, or 5
        review.setFeedback("Great service! Very helpful.");
        review.setCreatedAt(LocalDateTime.now());
        agentReviewRepository.save(review);
    }

    private void generateFeedback() {
        System.out.println("🌱 Generating Feedback...");
        List<User> users = userRepository.findByRole("USER");
        String[] categories = { "BUG", "QUERY", "SUGGESTION", "COMPLAINT" };

        for (int i = 0; i < 20; i++) {
            if (users.isEmpty())
                break;
            User user = users.get(random.nextInt(users.size()));
            Feedback feedback = new Feedback();
            feedback.setUser(user);
            feedback.setCategory(categories[random.nextInt(categories.length)]);
            feedback.setSubject("Test Feedback " + i);
            feedback.setDescription("This is a generated test feedback description " + i);
            feedback.setStatus("OPEN");
            feedback.setCreatedAt(LocalDateTime.now().minusDays(random.nextInt(10)));
            feedbackRepository.save(feedback);
        }
    }
}
