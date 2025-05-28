package com.goofy.springstatemachine.presentation.dto

import com.goofy.springstatemachine.domain.model.Order
import java.math.BigDecimal

/**
 * 주문 생성 요청 DTO
 * 
 * 클라이언트로부터 주문 생성 요청을 받기 위한 DTO
 */
data class CreateOrderRequest(
    val customerName: String,
    val customerEmail: String,
    val items: List<OrderItemRequest>
) {
    /**
     * 주문 항목 요청 DTO
     */
    data class OrderItemRequest(
        val productId: Long,
        val productName: String,
        val quantity: Int,
        val price: BigDecimal
    )
    
    /**
     * DTO를 도메인 모델로 변환
     * 
     * @return 도메인 모델
     */
    fun toDomain(): Order {
        val orderItems = items.map { item ->
            Order.OrderItem(
                productId = item.productId,
                productName = item.productName,
                quantity = item.quantity,
                price = item.price
            )
        }
        
        // 총 금액 계산
        val totalAmount = orderItems.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.price.multiply(BigDecimal(item.quantity)))
        }
        
        return Order(
            customerName = customerName,
            customerEmail = customerEmail,
            amount = totalAmount,
            items = orderItems
        )
    }
}
