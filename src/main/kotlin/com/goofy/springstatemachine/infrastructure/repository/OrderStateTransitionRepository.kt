package com.goofy.springstatemachine.infrastructure.repository

import com.goofy.springstatemachine.domain.model.OrderState
import com.goofy.springstatemachine.infrastructure.entity.OrderStateTransitionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 주문 상태 전이 리포지토리
 * 
 * 주문 상태 전이 엔티티에 대한 데이터 액세스를 제공하는 리포지토리
 */
@Repository
interface OrderStateTransitionRepository : JpaRepository<OrderStateTransitionEntity, Long> {
    
    /**
     * 주문 ID로 상태 전이 목록을 찾음
     */
    fun findByOrderId(orderId: Long): List<OrderStateTransitionEntity>
    
    /**
     * 주문 ID와 대상 상태로 상태 전이 목록을 찾음
     */
    fun findByOrderIdAndTargetState(orderId: Long, targetState: OrderState): List<OrderStateTransitionEntity>
    
    /**
     * 특정 기간 내의 상태 전이 목록을 찾음
     */
    fun findByTransitionTimeBetween(startTime: LocalDateTime, endTime: LocalDateTime): List<OrderStateTransitionEntity>
}
