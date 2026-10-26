package com.example.conferenceplanner.service

import com.example.conferenceplanner.model.DemoState
import com.example.conferenceplanner.repository.DemoRepository
import com.example.conferenceplanner.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class DemoService(
    private val demoRepository: DemoRepository,
    private val notificationRepository: NotificationRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)

    fun reset() {
        tx.execute { demoRepository.resetToSeed() }
    }

    fun seedIfEmpty(): Boolean = tx.execute { demoRepository.seedIfEmpty() } ?: false

    fun state(): DemoState = DemoState(
        controls = demoRepository.controls(),
        pendingNotifications = notificationRepository.countPending(),
        acknowledgedNotifications = notificationRepository.countAcknowledged(),
        totalBookings = demoRepository.countBookings(),
        totalActivity = demoRepository.countActivity(),
    )

    fun setFailureSimulation(enabled: Boolean) {
        demoRepository.setControl(SeedData.FAILURE_SIMULATION_CONTROL, enabled.toString())
    }
}
