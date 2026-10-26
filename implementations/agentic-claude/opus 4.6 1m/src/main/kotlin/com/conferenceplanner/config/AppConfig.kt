package com.conferenceplanner.config

import com.conferenceplanner.service.SeedService
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

@Configuration
class AppConfig {

    @Bean
    fun transactionTemplate(dataSource: DataSource): TransactionTemplate {
        val tm = DataSourceTransactionManager(dataSource)
        return TransactionTemplate(tm)
    }

    @Bean
    @Profile("local-demo")
    fun seedRunner(seedService: SeedService) = CommandLineRunner {
        seedService.resetToSeed()
    }
}
