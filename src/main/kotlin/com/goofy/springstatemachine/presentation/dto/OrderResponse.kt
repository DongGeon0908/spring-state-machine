package com.goofy.springstatemachine.presentation.dto

import com.goofy.springstatemachine.domain.model.Order
import com.goofy.springstatemachine.domain.model.OrderState
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 주문 응답 DTO
 * 
 * 클라이언트에게 주문 정보를 반환하기 위한 DTO
 */
data class OrderResponse(
    val id: Long?,
    val orderNumber: String,
    val customerName: String,
    val customerEmail: String,
    val amount: BigDecimal,
    val state: String,
    val items: List<OrderItemResponse>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val possibleEvents: Map<String, Boolean>
) {
    /**
     * 주문 항목 응답 DTO
     */
    data class OrderItemResponse(
        val id: Long?,
        val productId: Long,
        val productName: String,
        val quantity: Int,
        val price: BigDecimal,
        val totalPrice: BigDecimal
    )
    
    companion object {
        /**
         * 도메인 모델을 DTO로 변환
         * 
         * @param order 도메인 모델
         * @return DTO
         */
        fun fromDomain(order: Order): OrderResponse {
            val orderItems = order.items.map { item ->
                OrderItemResponse(
                    id = item.id,
                    productId = item.productId,
                    productName = item.productName,
                    quantity = item.quantity,
                    price = item.price,
                    totalPrice = item.getTotalPrice()
                )
            }
            
            val possibleEvents = mapOf(
                "PAY" to order.canPay(),
                "SHIP" to order.canShip(),
                "DELIVER" to order.canDeliver(),
                "CANCEL" to order.canCancel()
            )
            
            return OrderResponse(
                id = order.id,
                orderNumber = order.orderNumber,
                customerName = order.customerName,
                customerEmail = order.customerEmail,
                amount = order.amount,
                state = order.state.name,
                items = orderItems,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt,
                possibleEvents = possibleEvents
            )
        }
    }
}
