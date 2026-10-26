package com.conference.planner.api

class ApiException(
    val status: Int,
    val code: String,
    message: String,
) : RuntimeException(message)
