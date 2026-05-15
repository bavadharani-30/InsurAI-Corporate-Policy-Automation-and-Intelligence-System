package com.insurai.config;

@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "groq.api")
public class GroqProperties {
    private String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
