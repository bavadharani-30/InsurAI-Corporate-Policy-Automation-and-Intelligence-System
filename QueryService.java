package com.insurai.service;

import com.insurai.model.User;
import com.insurai.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QueryService {

    private final UserRepository userRepo;

    public QueryService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public Map<String, Object> process(String query) {
        Map<String, Object> response = new HashMap<>();
        String lowerQuery = query.toLowerCase();

        if (lowerQuery.contains("agent") || lowerQuery.contains("doctor") || lowerQuery.contains("book")) {
            List<User> agents = userRepo.findAll().stream()
                    .filter(u -> "AGENT".equalsIgnoreCase(u.getRole()))
                    .filter(User::getAvailable)
                    .collect(Collectors.toList());

            response.put("type", "AGENTS_LIST");
            response.put("message", "Here are the available agents I found for you.");
            response.put("data", agents);
        } else if (lowerQuery.contains("policy") || lowerQuery.contains("plan")) {
            response.put("type", "INFO");
            response.put("message",
                    "You can view our plans in the 'Plans' section. We offer Health, Life, and Corporate insurance.");
        } else if (lowerQuery.contains("hello") || lowerQuery.contains("hi")) {
            response.put("type", "GREETING");
            response.put("message", "Hello! How can I help you with your insurance needs today?");
        } else {
            response.put("type", "UNKNOWN");
            response.put("message", "I'm not sure I understand. Try asking for 'agents' or 'policies'.");
        }

        return response;
    }
}
