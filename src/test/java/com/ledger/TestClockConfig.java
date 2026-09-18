package com.ledger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

@Configuration
public class TestClockConfig {

    @Bean
    @Primary
    public Clock fixedClock() {
        return ApiTestBase.FIXED_CLOCK;
    }
}
