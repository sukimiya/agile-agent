package com.jowo.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.sukimiya.agileagent.config.DefaultLlmProperties;
import io.github.sukimiya.agileagent.service.OpenAiCompatibleLlmClient;

// Temporary scan bridge until the application package migrates in Task 3.
@SpringBootApplication(scanBasePackageClasses = {AgentApplication.class, OpenAiCompatibleLlmClient.class})
@EnableScheduling
@ConfigurationPropertiesScan(basePackageClasses = {AgentApplication.class, DefaultLlmProperties.class})
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
