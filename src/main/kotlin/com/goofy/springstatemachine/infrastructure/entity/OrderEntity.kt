package com.goofy.springstatemachine.infrastructure.entity

import com.goofy.springstatemachine.domain.model.OrderState
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 주문 엔티티
 * 
 * 주문 정보를 데이터베이스에 저장하기 위한 JPA 엔티티
 */
@Entity
@Table(name = "orders")
data class OrderEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false, unique = true)
    val orderNumber: String,
    
    @Column(nullable = false)
    val customerName: String,
    
    @Column(nullable = false)
    val customerEmail: String,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val state: OrderState = OrderState.CREATED,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
