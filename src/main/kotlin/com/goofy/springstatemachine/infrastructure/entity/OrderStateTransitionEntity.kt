package com.goofy.springstatemachine.infrastructure.entity

import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 주문 상태 전이 엔티티
 * 
 * 주문 상태 변경 이력을 데이터베이스에 저장하기 위한 JPA 엔티티
 */
@Entity
@Table(name = "order_state_transitions")
data class OrderStateTransitionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false)
    val orderId: Long,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val sourceState: OrderState,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val targetState: OrderState,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val event: OrderEvent,
    
    @Column(nullable = false)
    val transitionTime: LocalDateTime = LocalDateTime.now(),
    
    @Column
    val message: String? = null
)
