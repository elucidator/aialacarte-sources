package dev.conferenceplanner.session

import dev.conferenceplanner.api.ApiException
import dev.conferenceplanner.api.ErrorCode
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class SessionController(private val sessions: PostgresSessionRepository) {

    @GetMapping("/api/sessions")
    fun list(
        @RequestParam(required = false) page: String?,
        @RequestParam(required = false) size: String?,
        @RequestParam(required = false) availableOnly: String?,
    ): PageResponse<SessionSummary> {
        val pageNumber = parseInt("page", page ?: "0", min = 0, max = Int.MAX_VALUE)
        val pageSize = parseInt("size", size ?: "20", min = 1, max = 100)
        val onlyAvailable = parseBoolean("availableOnly", availableOnly ?: "false")
        return sessions.list(pageNumber, pageSize, onlyAvailable)
    }

    private fun parseInt(name: String, raw: String, min: Int, max: Int): Int {
        val value = raw.toIntOrNull() ?: throw ApiException(ErrorCode.VALIDATION_ERROR, "$name must be an integer")
        if (value < min || value > max) throw ApiException(ErrorCode.VALIDATION_ERROR, "$name must be between $min and $max")
        return value
    }

    private fun parseBoolean(name: String, raw: String): Boolean = when (raw) {
        "true" -> true
        "false" -> false
        else -> throw ApiException(ErrorCode.VALIDATION_ERROR, "$name must be true or false")
    }
}
