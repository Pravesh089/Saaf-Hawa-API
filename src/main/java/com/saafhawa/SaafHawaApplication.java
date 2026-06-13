package com.saafhawa;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Saaf Hawa: a historical + real-time Indian air-quality data service.
 * Modular monolith; see docs/architecture.md.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30m")
public class SaafHawaApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaafHawaApplication.class, args);
    }
}
