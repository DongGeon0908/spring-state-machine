package com.goofy.springstatemachine.infrastructure.entity

import jakarta.persistence.*
import java.math.BigDecimal

/**
 * 주문 항목 엔티티
 * 
 * 주문 항목 정보를 데이터베이스에 저장하기 위한 JPA 엔티티
 */
@Entity
@Table(name = "order_items")
data class OrderItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false)
    val orderId: Long,
    
    @Column(nullable = false)
    val productId: Long,
    
    @Column(nullable = false)
    val productName: String,
    
    @Column(nullable = false)
    val quantity: Int,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val price: BigDecimal
)
