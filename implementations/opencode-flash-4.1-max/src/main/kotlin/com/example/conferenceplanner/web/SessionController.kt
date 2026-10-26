package com.example.conferenceplanner.web

import com.example.conferenceplanner.model.PageResponse
import com.example.conferenceplanner.model.SessionSummary
import com.example.conferenceplanner.repository.SessionRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sessions")
class SessionController(private val sessionRepository: SessionRepository) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<SessionSummary> {
        if (page < 0) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page must be >= 0 but was $page")
        }
        if (size < 1 || size > 100) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIZE", "size must be between 1 and 100 but was $size")
        }
        return sessionRepository.list(page, size)
    }
}
