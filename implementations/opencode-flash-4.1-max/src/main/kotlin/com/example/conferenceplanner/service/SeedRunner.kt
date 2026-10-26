package com.example.conferenceplanner.service

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class SeedRunner(private val demoService: DemoService) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(SeedRunner::class.java)

    override fun run(args: ApplicationArguments) {
        try {
            if (demoService.seedIfEmpty()) {
                log.info("Seeded deterministic demo data (3 sessions, 81 bookings)")
            }
        } catch (e: Exception) {
            log.warn("Database not ready during startup, skipping seed: {}", e.message)
        }
    }
}
