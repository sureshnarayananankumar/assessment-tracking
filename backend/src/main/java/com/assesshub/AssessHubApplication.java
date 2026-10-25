package com.assesshub;

import com.assesshub.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class AssessHubApplication {

    public static void main(String[] args) {
        // All instants are persisted and processed in UTC; the UI converts to local time.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AssessHubApplication.class, args);
    }
}
