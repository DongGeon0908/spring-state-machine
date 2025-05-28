package com.goofy.springstatemachine.infrastructure.repository

import com.goofy.springstatemachine.infrastructure.entity.OrderItemEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 주문 항목 리포지토리
 * 
 * 주문 항목 엔티티에 대한 데이터 액세스를 제공하는 리포지토리
 */
@Repository
interface OrderItemRepository : JpaRepository<OrderItemEntity, Long> {
    
    /**
     * 주문 ID로 주문 항목 목록을 찾음
     */
    fun findByOrderId(orderId: Long): List<OrderItemEntity>
    
    /**
     * 주문 ID로 주문 항목을 삭제
     */
    fun deleteByOrderId(orderId: Long)
}
