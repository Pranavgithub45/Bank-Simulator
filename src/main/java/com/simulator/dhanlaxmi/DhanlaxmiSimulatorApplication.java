package com.simulator.dhanlaxmi;

import com.simulator.dhanlaxmi.config.BankProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DhanlaxmiSimulatorApplication {

    private static final Logger log = LoggerFactory.getLogger(DhanlaxmiSimulatorApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DhanlaxmiSimulatorApplication.class, args);
    }


    @Bean
    CommandLineRunner logEffectiveReturnUrl(BankProperties bankProperties) {
        return args -> log.info("dhanbank.default-return-url is currently: {}", bankProperties.getDefaultReturnUrl());
    }
}
