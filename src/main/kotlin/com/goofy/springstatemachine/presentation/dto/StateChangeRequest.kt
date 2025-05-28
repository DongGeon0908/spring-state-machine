package com.goofy.springstatemachine.presentation.dto

/**
 * 상태 변경 요청 DTO
 * 
 * 클라이언트로부터 주문 상태 변경 요청을 받기 위한 DTO
 */
data class StateChangeRequest(
    val event: String
)
