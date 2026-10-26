package com.conference.planner.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handle(ex: ApiException): ResponseEntity<ApiError> =
        ResponseEntity.status(ex.status).body(ApiError(code = ex.code, message = ex.message ?: ex.code))

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun handleBadJson(ex: Exception): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(
            ApiError(code = "MALFORMED_JSON", message = "Request body is malformed"),
        )
}
