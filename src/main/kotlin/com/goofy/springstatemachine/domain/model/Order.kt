package com.goofy.springstatemachine.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 주문 도메인 모델
 * 
 * 주문의 핵심 비즈니스 로직과 상태를 관리하는 도메인 모델
 */
data class Order(
    val id: Long? = null,
    val orderNumber: String = "",
    val customerName: String,
    val customerEmail: String,
    val amount: BigDecimal,
    val items: List<OrderItem>,
    val state: OrderState = OrderState.CREATED,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 주문 항목을 나타내는 내부 클래스
     */
    data class OrderItem(
        val id: Long? = null,
        val productId: Long,
        val productName: String,
        val quantity: Int,
        val price: BigDecimal
    ) {
        /**
         * 항목의 총 가격을 계산
         */
        fun getTotalPrice(): BigDecimal = price.multiply(BigDecimal(quantity))
    }

    /**
     * 주문의 총 금액을 계산
     */
    fun calculateTotalAmount(): BigDecimal {
        return items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.getTotalPrice()) }
    }

    /**
     * 주문 상태가 취소 가능한지 확인
     */
    fun canCancel(): Boolean {
        return state == OrderState.CREATED || state == OrderState.PAID
    }

    /**
     * 주문 상태가 배송 시작 가능한지 확인
     */
    fun canShip(): Boolean {
        return state == OrderState.PAID
    }

    /**
     * 주문 상태가 배송 완료 처리 가능한지 확인
     */
    fun canDeliver(): Boolean {
        return state == OrderState.SHIPPED
    }

    /**
     * 주문 상태가 결제 가능한지 확인
     */
    fun canPay(): Boolean {
        return state == OrderState.CREATED
    }
}
