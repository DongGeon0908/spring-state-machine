package com.goofy.springstatemachine.infrastructure.repository

import com.goofy.springstatemachine.domain.model.OrderState
import com.goofy.springstatemachine.infrastructure.entity.OrderEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * 주문 리포지토리
 * 
 * 주문 엔티티에 대한 데이터 액세스를 제공하는 리포지토리
 */
@Repository
interface OrderRepository : JpaRepository<OrderEntity, Long> {
    
    /**
     * 주문 번호로 주문을 찾음
     */
    fun findByOrderNumber(orderNumber: String): Optional<OrderEntity>
    
    /**
     * 고객 이메일로 주문 목록을 찾음
     */
    fun findByCustomerEmail(customerEmail: String): List<OrderEntity>
    
    /**
     * 주문 상태로 주문 목록을 찾음
     */
    fun findByState(state: OrderState): List<OrderEntity>
}
