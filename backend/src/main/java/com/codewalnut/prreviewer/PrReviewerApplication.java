package com.codewalnut.prreviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PrReviewerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrReviewerApplication.class, args);
    }
}
