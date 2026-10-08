package com.skyroute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SkyRouteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyRouteApplication.class, args);
    }
}
