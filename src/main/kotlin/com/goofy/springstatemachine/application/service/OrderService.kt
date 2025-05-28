package com.goofy.springstatemachine.application.service

import com.goofy.springstatemachine.domain.model.Order
import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState
import com.goofy.springstatemachine.infrastructure.entity.OrderEntity
import com.goofy.springstatemachine.infrastructure.entity.OrderItemEntity
import com.goofy.springstatemachine.infrastructure.entity.OrderStateTransitionEntity
import com.goofy.springstatemachine.infrastructure.repository.OrderItemRepository
import com.goofy.springstatemachine.infrastructure.repository.OrderRepository
import com.goofy.springstatemachine.infrastructure.repository.OrderStateTransitionRepository
import com.goofy.springstatemachine.infrastructure.service.StateMachinePersistenceService
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * 주문 서비스
 * 
 * 이 서비스는 주문 관련 비즈니스 로직을 처리하며, Spring State Machine을 통합하여 주문 상태 관리를 수행합니다.
 * 
 * 상태 머신 통합이란?
 * - 상태 머신은 주문의 상태 전이를 관리하는 데 사용됩니다.
 * - 각 주문은 고유한 상태 머신 인스턴스를 가지며, 주문 번호로 식별됩니다.
 * - 상태 머신의 상태는 Redis에 저장되어 영속성을 유지합니다.
 * - 상태 전이 이력은 MySQL 데이터베이스에 저장됩니다.
 * 
 * 주요 기능:
 * 1. 주문 생성: 새로운 주문을 생성하고 초기 상태(CREATED)의 상태 머신을 설정합니다.
 * 2. 주문 상태 변경: 이벤트를 발생시켜 주문 상태를 변경하고, 상태 전이 이력을 저장합니다.
 * 3. 주문 조회: 주문 정보와 현재 상태를 조회합니다.
 * 4. 상태별 주문 조회: 특정 상태의 모든 주문을 조회합니다.
 * 
 * 이 서비스는 DDD(Domain-Driven Design) 원칙을 따르며, 도메인 모델과 인프라스트럭처 계층 사이의 중개자 역할을 합니다.
 */
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderStateTransitionRepository: OrderStateTransitionRepository,
    private val stateMachineFactory: StateMachineFactory<OrderState, OrderEvent>,
    private val stateMachinePersistenceService: StateMachinePersistenceService
) {
    /**
     * 주문 생성
     * 
     * 이 메서드는 새로운 주문을 생성하고 초기 상태의 상태 머신을 설정합니다.
     * 
     * 주문 생성 과정:
     * 1. 고유한 주문 번호를 생성합니다.
     * 2. 주문 엔티티를 생성하고 초기 상태(CREATED)로 설정합니다.
     * 3. 주문 항목 엔티티를 생성하고 저장합니다.
     * 4. 주문에 대한 상태 머신을 초기화합니다.
     * 5. 상태 머신의 상태를 Redis에 저장합니다.
     * 
     * 상태 머신 관련 작업:
     * - stateMachineFactory.getStateMachine(orderNumber): 주문 번호를 ID로 사용하여 새 상태 머신 인스턴스를 생성합니다.
     * - stateMachine.start(): 상태 머신을 시작하여 초기 상태(CREATED)로 설정합니다.
     * - stateMachinePersistenceService.persist(): 상태 머신의 상태를 Redis에 저장합니다.
     * 
     * @param order 생성할 주문 정보
     * @return 생성된 주문
     */
    @Transactional
    fun createOrder(order: Order): Order {
        // 1. 주문 번호 생성 - 고유한 주문 식별자
        val orderNumber = generateOrderNumber()

        // 2. 주문 엔티티 생성 및 저장 - 초기 상태는 CREATED
        val orderEntity = OrderEntity(
            orderNumber = orderNumber,
            customerName = order.customerName,
            customerEmail = order.customerEmail,
            amount = order.amount,
            state = OrderState.CREATED,  // 초기 상태 설정
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedOrder = orderRepository.save(orderEntity)

        // 3. 주문 항목 저장 - 주문에 포함된 상품 정보
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

        // 4. 상태 머신 초기화 및 시작
        // - 주문 번호를 ID로 사용하여 새 상태 머신 인스턴스 생성
        val stateMachine = stateMachineFactory.getStateMachine(orderNumber)
        // - 상태 머신 시작 (초기 상태 CREATED로 설정)
        stateMachine.start()

        // 5. 상태 머신 상태를 Redis에 저장
        // - 상태 머신의 현재 상태(CREATED)를 Redis에 저장
        stateMachinePersistenceService.persist(stateMachine, orderNumber)

        // 6. 도메인 모델로 변환하여 반환
        return mapToOrderDomain(savedOrder, orderItems)
    }

    /**
     * 주문 상태 변경
     * 
     * 이 메서드는 주문의 상태를 변경하기 위해 상태 머신에 이벤트를 발생시키고, 
     * 상태 전이 이력을 저장합니다.
     * 
     * 상태 변경 과정:
     * 1. 주문 번호로 주문 엔티티를 조회합니다.
     * 2. 주문 번호로 상태 머신을 복원합니다.
     * 3. 현재 상태에서 이벤트 발생 가능 여부를 확인합니다.
     * 4. 상태 머신에 이벤트를 발생시켜 상태 전이를 트리거합니다.
     * 5. 주문 엔티티의 상태를 업데이트합니다.
     * 6. 상태 전이 이력을 저장합니다.
     * 7. 변경된 상태 머신의 상태를 Redis에 저장합니다.
     * 
     * 상태 머신 관련 작업:
     * - stateMachineFactory.getStateMachine(orderNumber): 주문 번호로 상태 머신 인스턴스를 가져옵니다.
     * - stateMachinePersistenceService.restore(): Redis에서 상태 머신의 상태를 복원합니다.
     * - stateMachine.sendEvent(event): 상태 머신에 이벤트를 발생시켜 상태 전이를 트리거합니다.
     * - stateMachinePersistenceService.persist(): 변경된 상태 머신의 상태를 Redis에 저장합니다.
     * 
     * 상태 전이 유효성 검사:
     * - canFireEvent() 메서드를 통해 현재 상태에서 이벤트 발생 가능 여부를 확인합니다.
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
        // 1. 주문 조회 - 주문 번호로 주문 엔티티 조회
        val orderEntity = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow { NoSuchElementException("주문을 찾을 수 없습니다: $orderNumber") }

        // 2. 상태 머신 복원 - Redis에서 상태 머신의 상태 복원
        val stateMachine = stateMachineFactory.getStateMachine(orderNumber)
        stateMachinePersistenceService.restore(stateMachine, orderNumber)

        // 3. 현재 상태 확인 - 주문 엔티티의 현재 상태
        val sourceState = orderEntity.state

        // 4. 이벤트 발생 가능 여부 확인 - 현재 상태에서 이벤트 발생 가능 여부 검사
        if (!canFireEvent(sourceState, event)) {
            throw IllegalStateException("현재 상태(${sourceState})에서 이벤트(${event})를 발생시킬 수 없습니다.")
        }

        // 5. 이벤트 발생 - 상태 머신에 이벤트를 발생시켜 상태 전이 트리거
        stateMachine.sendEvent(event)

        // 6. 새로운 상태 확인 - 이벤트 발생 후 예상되는 대상 상태
        val targetState = getTargetState(sourceState, event)

        // 7. 주문 상태 업데이트 - 주문 엔티티의 상태 필드 업데이트
        val updatedOrderEntity = orderEntity.copy(
            state = targetState,
            updatedAt = LocalDateTime.now()
        )

        val savedOrder = orderRepository.save(updatedOrderEntity)

        // 8. 상태 전이 이력 저장 - 상태 변경 이력을 데이터베이스에 저장
        val stateTransition = OrderStateTransitionEntity(
            orderId = savedOrder.id!!,
            sourceState = sourceState,     // 이전 상태
            targetState = targetState,     // 새로운 상태
            event = event,                 // 발생 이벤트
            transitionTime = LocalDateTime.now()  // 전이 시간
        )

        orderStateTransitionRepository.save(stateTransition)

        // 9. 상태 머신 상태 저장 - 변경된 상태 머신의 상태를 Redis에 저장
        stateMachinePersistenceService.persist(stateMachine, orderNumber)

        // 10. 주문 항목 조회 - 주문에 포함된 상품 정보 조회
        val orderItems = orderItemRepository.findByOrderId(savedOrder.id!!)

        // 11. 도메인 모델로 변환하여 반환
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

    /**
     * 이벤트 발생 가능 여부 확인
     * 
     * 이 메서드는 현재 상태에서 특정 이벤트가 발생 가능한지 여부를 확인합니다.
     * 상태 머신의 전이 규칙에 따라 각 상태에서 허용되는 이벤트를 정의합니다.
     * 
     * 상태별 허용 이벤트:
     * - CREATED: PAY(결제), CANCEL(취소) 이벤트 허용
     * - PAID: SHIP(배송 시작), CANCEL(취소) 이벤트 허용
     * - SHIPPED: DELIVER(배송 완료) 이벤트만 허용
     * - DELIVERED: 어떤 이벤트도 허용하지 않음 (최종 상태)
     * - CANCELLED: 어떤 이벤트도 허용하지 않음 (최종 상태)
     * 
     * 이 메서드는 상태 머신의 전이 규칙을 코드로 표현한 것으로,
     * StateMachineConfig에 정의된 전이 규칙과 일치해야 합니다.
     * 
     * @param state 현재 상태
     * @param event 발생 이벤트
     * @return 이벤트 발생 가능 여부 (true: 가능, false: 불가능)
     */
    private fun canFireEvent(state: OrderState, event: OrderEvent): Boolean {
        return when (state) {
            OrderState.CREATED -> event == OrderEvent.SUBMIT_PAYMENT || event == OrderEvent.CANCEL || event == OrderEvent.SELECT_PAYMENT_METHOD  // 주문 생성 상태에서는 결제 제출, 결제 방법 선택 또는 취소만 가능
            OrderState.PAYMENT_PENDING -> event == OrderEvent.PAYMENT_SUCCEEDED || event == OrderEvent.PAYMENT_FAILED || event == OrderEvent.CANCEL  // 결제 대기 상태에서는 결제 성공, 결제 실패 또는 취소만 가능
            OrderState.PAYMENT_CHOICE -> event == OrderEvent.CREDIT_CARD || event == OrderEvent.BANK_TRANSFER  // 결제 방법 선택 상태에서는 신용카드 또는 계좌이체 선택만 가능
            OrderState.PAID -> event == OrderEvent.PREPARE || event == OrderEvent.CANCEL || event == OrderEvent.CHECK_SHIPPING  // 결제 완료 상태에서는 상품 준비 시작, 배송 조건 확인 또는 취소만 가능
            OrderState.SHIPPING_JUNCTION -> event == OrderEvent.EXPEDITE || event == OrderEvent.STANDARD  // 배송 분기점 상태에서는 빠른 배송 또는 일반 배송 선택만 가능
            OrderState.PREPARING -> event == OrderEvent.SHIP || event == OrderEvent.CANCEL  // 상품 준비 중 상태에서는 배송 시작 또는 취소만 가능
            OrderState.SHIPPED -> event == OrderEvent.DELIVER  // 배송 중 상태에서는 배송 완료 처리만 가능
            OrderState.CANCELLED -> event == OrderEvent.REFUND  // 취소 상태에서는 환불 처리만 가능
            OrderState.DELIVERED, OrderState.REFUNDED -> false  // 배송 완료 또는 환불 완료 상태에서는 더 이상 상태 변경 불가
        }
    }

    /**
     * 이벤트 발생 후 대상 상태 확인
     * 
     * 이 메서드는 특정 이벤트가 발생했을 때 전이될 대상 상태를 결정합니다.
     * 상태 머신의 전이 규칙에 따라 각 이벤트가 어떤 상태로 전이되는지 정의합니다.
     * 
     * 이벤트별 대상 상태:
     * - PAY: PAID (결제 완료) 상태로 전이
     * - SHIP: SHIPPED (배송 중) 상태로 전이
     * - DELIVER: DELIVERED (배송 완료) 상태로 전이
     * - CANCEL: CANCELLED (주문 취소) 상태로 전이
     * 
     * 이 메서드는 상태 머신의 전이 결과를 코드로 표현한 것으로,
     * StateMachineConfig에 정의된 전이 규칙과 일치해야 합니다.
     * 
     * 참고: 실제 상태 전이는 상태 머신에 의해 처리되지만, 이 메서드는 
     * 데이터베이스 엔티티 업데이트를 위해 대상 상태를 미리 계산합니다.
     * 
     * @param sourceState 현재 상태 (사용되지 않지만 명확성을 위해 포함)
     * @param event 발생 이벤트
     * @return 대상 상태
     */
    private fun getTargetState(sourceState: OrderState, event: OrderEvent): OrderState {
        return when (event) {
            OrderEvent.SUBMIT_PAYMENT -> OrderState.PAYMENT_PENDING  // 결제 제출 이벤트 -> 결제 대기 중 상태
            OrderEvent.PAYMENT_SUCCEEDED -> OrderState.PAID  // 결제 성공 이벤트 -> 결제 완료 상태
            OrderEvent.PAYMENT_FAILED -> OrderState.CREATED  // 결제 실패 이벤트 -> 주문 생성 상태로 돌아감
            OrderEvent.PREPARE -> OrderState.PREPARING  // 상품 준비 시작 이벤트 -> 상품 준비 중 상태
            OrderEvent.SHIP -> OrderState.SHIPPED  // 배송 시작 이벤트 -> 배송 중 상태
            OrderEvent.DELIVER -> OrderState.DELIVERED  // 배송 완료 이벤트 -> 배송 완료 상태
            OrderEvent.CANCEL -> OrderState.CANCELLED  // 취소 이벤트 -> 취소 상태
            OrderEvent.REFUND -> OrderState.REFUNDED  // 환불 처리 이벤트 -> 환불 완료 상태
            OrderEvent.SELECT_PAYMENT_METHOD -> OrderState.PAYMENT_CHOICE  // 결제 방법 선택 이벤트 -> 결제 방법 선택 상태
            OrderEvent.CREDIT_CARD, OrderEvent.BANK_TRANSFER -> OrderState.PAYMENT_PENDING  // 신용카드/계좌이체 선택 이벤트 -> 결제 대기 중 상태
            OrderEvent.CHECK_SHIPPING -> OrderState.SHIPPING_JUNCTION  // 배송 조건 확인 이벤트 -> 배송 분기점 상태
            OrderEvent.EXPEDITE, OrderEvent.STANDARD -> OrderState.PREPARING  // 빠른 배송/일반 배송 선택 이벤트 -> 상품 준비 중 상태
        }
    }

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
