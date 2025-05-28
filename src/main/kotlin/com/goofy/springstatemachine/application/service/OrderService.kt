package com.goofy.springstatemachine.application.service

import com.goofy.springstatemachine.domain.model.Order
import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState
import com.goofy.springstatemachine.domain.service.OrderStateMachineService
import com.goofy.springstatemachine.infrastructure.entity.OrderEntity
import com.goofy.springstatemachine.infrastructure.entity.OrderItemEntity
import com.goofy.springstatemachine.infrastructure.entity.OrderStateTransitionEntity
import com.goofy.springstatemachine.infrastructure.repository.OrderItemRepository
import com.goofy.springstatemachine.infrastructure.repository.OrderRepository
import com.goofy.springstatemachine.infrastructure.repository.OrderStateTransitionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 주문 서비스
 * 
 * 이 서비스는 주문 관련 비즈니스 로직을 처리하며, 주문 상태 관리를 위해 OrderStateMachineService를 활용합니다.
 * 
 * 클린 아키텍처 및 도메인 주도 설계(DDD) 원칙에 따라:
 * - 주문 비즈니스 로직과 상태 머신 로직이 분리되어 있습니다.
 * - OrderService는 비즈니스 로직에 집중합니다.
 * - 상태 머신 관련 로직은 OrderStateMachineService에 위임합니다.
 * - 이를 통해 단일 책임 원칙(SRP)을 준수합니다.
 * 
 * 상태 관리 아키텍처:
 * - 상태 머신 로직은 도메인 서비스 인터페이스(OrderStateMachineService)를 통해 추상화됩니다.
 * - 구체적인 구현은 인프라스트럭처 계층에 있어 도메인 계층이 기술적 세부사항에 의존하지 않습니다.
 * - 상태 전이 이력은 MySQL 데이터베이스에 저장됩니다.
 * 
 * 주요 기능:
 * 1. 주문 생성: 새로운 주문을 생성하고 OrderStateMachineService를 통해 초기 상태를 설정합니다.
 * 2. 주문 상태 변경: OrderStateMachineService를 통해 이벤트를 발생시켜 주문 상태를 변경하고, 상태 전이 이력을 저장합니다.
 * 3. 주문 조회: 주문 정보와 현재 상태를 조회합니다.
 * 4. 상태별 주문 조회: 특정 상태의 모든 주문을 조회합니다.
 * 
 * 이 서비스는 애플리케이션 계층에 위치하며, 도메인 모델과 인프라스트럭처 계층 사이의 중개자 역할을 합니다.
 */
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderStateTransitionRepository: OrderStateTransitionRepository,
    private val orderStateMachineService: OrderStateMachineService
) {
    /**
     * 주문 생성
     * 
     * 이 메서드는 새로운 주문을 생성하고 OrderStateMachineService를 통해 초기 상태를 설정합니다.
     * 
     * 주문 생성 과정:
     * 1. 고유한 주문 번호를 생성합니다.
     * 2. 주문 엔티티를 생성하고 초기 상태(CREATED)로 설정합니다.
     * 3. 주문 항목 엔티티를 생성하고 저장합니다.
     * 4. OrderStateMachineService를 통해 주문에 대한 상태 머신을 초기화합니다.
     * 
     * 클린 아키텍처 적용:
     * - 상태 머신 관련 로직은 OrderStateMachineService에 위임하여 관심사를 분리합니다.
     * - 이를 통해 OrderService는 주문 생성이라는 핵심 비즈니스 로직에 집중할 수 있습니다.
     * 
     * @param order 생성할 주문 정보
     * @return 생성된 주문
     */
    @Transactional
    fun createOrder(order: Order): Order {
        /** 1. 주문 번호 생성 - 고유한 주문 식별자 */
        val orderNumber = generateOrderNumber()

        /** 2. 주문 엔티티 생성 및 저장 - 초기 상태는 CREATED */
        val orderEntity = OrderEntity(
            orderNumber = orderNumber,
            customerName = order.customerName,
            customerEmail = order.customerEmail,
            amount = order.amount,
            /** 초기 상태 설정 */
            state = OrderState.CREATED,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedOrder = orderRepository.save(orderEntity)

        /** 3. 주문 항목 저장 - 주문에 포함된 상품 정보 */
        val orderItems = order.items.map { item ->
            OrderItemEntity(
                orderId = savedOrder.id!!,
                productId = item.productId,
                productName = item.productName,
                quantity = item.quantity,
                price = item.price
            )
        }

        orderItemRepository.saveAll(orderItems)

        /** 4. 상태 머신 초기화 및 시작
         * - 주문 상태 머신 서비스를 통해 상태 머신 초기화
         */
        orderStateMachineService.initializeStateMachine(orderNumber)

        /** 6. 도메인 모델로 변환하여 반환 */
        return mapToOrderDomain(savedOrder, orderItems)
    }

    /**
     * 주문 상태 변경
     * 
     * 이 메서드는 주문의 상태를 변경하기 위해 OrderStateMachineService를 통해 이벤트를 발생시키고, 
     * 상태 전이 이력을 저장합니다.
     * 
     * 상태 변경 과정:
     * 1. 주문 번호로 주문 엔티티를 조회합니다.
     * 2. OrderStateMachineService를 통해 현재 상태에서 이벤트 발생 가능 여부를 확인합니다.
     * 3. OrderStateMachineService를 통해 이벤트를 발생시켜 상태 전이를 수행합니다.
     * 4. 주문 엔티티의 상태를 업데이트합니다.
     * 5. 상태 전이 이력을 저장합니다.
     * 
     * 클린 아키텍처 및 DDD 적용:
     * - 상태 머신 관련 로직은 OrderStateMachineService에 위임하여 관심사를 분리합니다.
     * - OrderService는 주문 상태 변경이라는 비즈니스 로직에 집중합니다.
     * - 도메인 서비스 인터페이스를 통해 기술적 세부사항(Spring State Machine)으로부터 독립적입니다.
     * 
     * 상태 전이 유효성 검사:
     * - OrderStateMachineService.canFireEvent() 메서드를 통해 현재 상태에서 이벤트 발생 가능 여부를 확인합니다.
     * - 유효하지 않은 상태 전이 시도 시 IllegalStateException이 발생합니다.
     * 
     * @param orderNumber 주문 번호
     * @param event 발생 이벤트
     * @return 업데이트된 주문
     * @throws NoSuchElementException 주문을 찾을 수 없는 경우
     * @throws IllegalStateException 현재 상태에서 이벤트를 발생시킬 수 없는 경우
     */
    @Transactional
    fun changeOrderState(orderNumber: String, event: OrderEvent): Order {
        /** 1. 주문 조회 - 주문 번호로 주문 엔티티 조회 */
        val orderEntity = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow { NoSuchElementException("주문을 찾을 수 없습니다: $orderNumber") }

        /** 3. 현재 상태 확인 - 주문 엔티티의 현재 상태 */
        val sourceState = orderEntity.state

        /** 4. 이벤트 발생 가능 여부 확인 - 현재 상태에서 이벤트 발생 가능 여부 검사 */
        if (!orderStateMachineService.canFireEvent(sourceState, event)) {
            throw IllegalStateException("현재 상태(${sourceState})에서 이벤트(${event})를 발생시킬 수 없습니다.")
        }

        /** 5. 이벤트 발생 - 상태 머신 서비스를 통해 이벤트 발생 */
        val targetState = orderStateMachineService.sendEvent(orderNumber, event)

        /** 7. 주문 상태 업데이트 - 주문 엔티티의 상태 필드 업데이트 */
        val updatedOrderEntity = orderEntity.copy(
            state = targetState,
            updatedAt = LocalDateTime.now()
        )

        val savedOrder = orderRepository.save(updatedOrderEntity)

        /** 8. 상태 전이 이력 저장 - 상태 변경 이력을 데이터베이스에 저장 */
        val stateTransition = OrderStateTransitionEntity(
            orderId = savedOrder.id!!,
            /** 이전 상태 */
            sourceState = sourceState,
            /** 새로운 상태 */
            targetState = targetState,
            /** 발생 이벤트 */
            event = event,
            /** 전이 시간 */
            transitionTime = LocalDateTime.now()
        )

        orderStateTransitionRepository.save(stateTransition)

        /** 상태 머신 상태 저장은 orderStateMachineService.sendEvent 메서드에서 이미 처리됨 */

        /** 10. 주문 항목 조회 - 주문에 포함된 상품 정보 조회 */
        val orderItems = orderItemRepository.findByOrderId(savedOrder.id!!)

        /** 11. 도메인 모델로 변환하여 반환 */
        return mapToOrderDomain(savedOrder, orderItems)
    }

    /**
     * 주문 조회
     * 
     * @param orderNumber 주문 번호
     * @return 주문 정보
     */
    @Transactional(readOnly = true)
    fun getOrder(orderNumber: String): Order {
        val orderEntity = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow { NoSuchElementException("주문을 찾을 수 없습니다: $orderNumber") }

        val orderItems = orderItemRepository.findByOrderId(orderEntity.id!!)

        return mapToOrderDomain(orderEntity, orderItems)
    }

    /**
     * 모든 주문 조회
     * 
     * @return 모든 주문 목록
     */
    @Transactional(readOnly = true)
    fun getAllOrders(): List<Order> {
        val orders = orderRepository.findAll()

        return orders.map { orderEntity ->
            val orderItems = orderItemRepository.findByOrderId(orderEntity.id!!)
            mapToOrderDomain(orderEntity, orderItems)
        }
    }

    /**
     * 주문 상태별 조회
     * 
     * @param state 주문 상태
     * @return 해당 상태의 주문 목록
     */
    @Transactional(readOnly = true)
    fun getOrdersByState(state: OrderState): List<Order> {
        val orders = orderRepository.findByState(state)

        return orders.map { orderEntity ->
            val orderItems = orderItemRepository.findByOrderId(orderEntity.id!!)
            mapToOrderDomain(orderEntity, orderItems)
        }
    }

    /**
     * 주문 번호 생성
     * 
     * @return 생성된 주문 번호
     */
    private fun generateOrderNumber(): String {
        val timestamp = System.currentTimeMillis()
        val random = Random().nextInt(1000)
        return "ORD-$timestamp-$random"
    }

    /** 이벤트 발생 가능 여부 확인 및 대상 상태 확인 로직은 OrderStateMachineService로 이동됨 */

    /**
     * 엔티티를 도메인 모델로 변환
     * 
     * @param orderEntity 주문 엔티티
     * @param orderItemEntities 주문 항목 엔티티 목록
     * @return 도메인 모델
     */
    private fun mapToOrderDomain(orderEntity: OrderEntity, orderItemEntities: List<OrderItemEntity>): Order {
        val orderItems = orderItemEntities.map { itemEntity ->
            Order.OrderItem(
                id = itemEntity.id,
                productId = itemEntity.productId,
                productName = itemEntity.productName,
                quantity = itemEntity.quantity,
                price = itemEntity.price
            )
        }

        return Order(
            id = orderEntity.id,
            orderNumber = orderEntity.orderNumber,
            customerName = orderEntity.customerName,
            customerEmail = orderEntity.customerEmail,
            amount = orderEntity.amount,
            items = orderItems,
            state = orderEntity.state,
            createdAt = orderEntity.createdAt,
            updatedAt = orderEntity.updatedAt
        )
    }
}
