package com.qinggan.travel.execution.application;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutionConfiguration {
    @Bean
    Clock executionClock() {
        return Clock.systemUTC();
    }
}
