package com.conferenceplanner.controller

import com.conferenceplanner.model.ApiError
import com.conferenceplanner.service.BookingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class SessionController(private val bookingService: BookingService) {

    @GetMapping("/api/sessions")
    fun listSessions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        if (page < 0) {
            return ResponseEntity.badRequest().body(
                ApiError("INVALID_PAGE", "Page must be >= 0")
            )
        }
        if (size < 1 || size > 100) {
            return ResponseEntity.badRequest().body(
                ApiError("INVALID_SIZE", "Size must be between 1 and 100")
            )
        }
        return ResponseEntity.ok(bookingService.listSessions(page, size))
    }
}
