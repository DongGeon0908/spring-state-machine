package com.goofy.springstatemachine.infrastructure.service

import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState
import com.goofy.springstatemachine.domain.service.OrderStateMachineService
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.stereotype.Service

/**
 * 주문 상태 머신 서비스 구현체
 * 
 * 이 클래스는 OrderStateMachineService 인터페이스를 구현하여 
 * Spring State Machine을 사용한 상태 머신 관련 작업을 처리합니다.
 * 
 * 클린 아키텍처에서 이 클래스는 인프라스트럭처 계층에 위치하며,
 * 구체적인 기술(Spring State Machine)을 사용하여 도메인 서비스를 구현합니다.
 */
@Service
class OrderStateMachineServiceImpl(
    private val stateMachineFactory: StateMachineFactory<OrderState, OrderEvent>,
    private val stateMachinePersistenceService: StateMachinePersistenceService
) : OrderStateMachineService {

    private val logger = LoggerFactory.getLogger(OrderStateMachineServiceImpl::class.java)

    /**
     * 주문에 대한 상태 머신을 초기화합니다.
     * 
     * 이 메서드는 새로운 주문이 생성될 때 호출되며, 상태 머신을 생성하고 시작합니다.
     * 그리고 초기 상태를 Redis에 저장합니다.
     * 
     * @param orderNumber 주문 번호
     * @return 초기화된 상태 (CREATED)
     */
    override fun initializeStateMachine(orderNumber: String): OrderState {
        /** 상태 머신 생성 */
        val stateMachine = stateMachineFactory.getStateMachine(orderNumber)

        /** 상태 머신 시작 (초기 상태: CREATED) */
        stateMachine.start()

        /** 상태 머신 상태를 Redis에 저장 */
        stateMachinePersistenceService.persist(stateMachine, orderNumber)

        /** 초기 상태 반환 */
        return OrderState.CREATED
    }

    /**
     * 주문 상태 머신에 이벤트를 발생시켜 상태 전이를 수행합니다.
     * 
     * 이 메서드는 주문 상태를 변경하기 위해 호출되며, 다음 단계를 수행합니다:
     * 1. 상태 머신을 복원합니다.
     * 2. 이벤트를 발생시킵니다.
     * 3. 변경된 상태를 Redis에 저장합니다.
     * 
     * @param orderNumber 주문 번호
     * @param event 발생시킬 이벤트
     * @return 이벤트 처리 후의 새로운 상태
     */
    override fun sendEvent(orderNumber: String, event: OrderEvent): OrderState {
        /** 상태 머신 복원 */
        val stateMachine = stateMachineFactory.getStateMachine(orderNumber)
        stateMachinePersistenceService.restore(stateMachine, orderNumber)

        /** 이벤트 발생 */
        val eventAccepted = stateMachine.sendEvent(event)

        if (!eventAccepted) {
            logger.warn("이벤트가 수락되지 않았습니다: $event, 주문 번호: $orderNumber")
            throw IllegalStateException("이벤트 처리 실패: $event")
        }

        /** 변경된 상태 머신 상태를 Redis에 저장 */
        stateMachinePersistenceService.persist(stateMachine, orderNumber)

        /** 새로운 상태 반환 */
        return stateMachine.state.id
    }

    /**
     * 현재 상태에서 특정 이벤트를 발생시킬 수 있는지 확인합니다.
     * 
     * 이 메서드는 상태 머신의 전이 규칙에 따라 각 상태에서 허용되는 이벤트를 정의합니다.
     * 
     * @param currentState 현재 상태
     * @param event 확인할 이벤트
     * @return 이벤트 발생 가능 여부
     */
    override fun canFireEvent(currentState: OrderState, event: OrderEvent): Boolean {
        return when (currentState) {
            OrderState.CREATED -> event == OrderEvent.SUBMIT_PAYMENT || event == OrderEvent.CANCEL || event == OrderEvent.SELECT_PAYMENT_METHOD
            OrderState.PAYMENT_PENDING -> event == OrderEvent.PAYMENT_SUCCEEDED || event == OrderEvent.PAYMENT_FAILED || event == OrderEvent.CANCEL
            OrderState.PAYMENT_CHOICE -> event == OrderEvent.CREDIT_CARD || event == OrderEvent.BANK_TRANSFER
            OrderState.PAID -> event == OrderEvent.PREPARE || event == OrderEvent.CANCEL || event == OrderEvent.CHECK_SHIPPING
            OrderState.SHIPPING_JUNCTION -> event == OrderEvent.EXPEDITE || event == OrderEvent.STANDARD
            OrderState.PREPARING -> event == OrderEvent.SHIP || event == OrderEvent.CANCEL
            OrderState.SHIPPED -> event == OrderEvent.DELIVER
            OrderState.CANCELLED -> event == OrderEvent.REFUND
            OrderState.DELIVERED, OrderState.REFUNDED -> false
        }
    }

    /**
     * 특정 이벤트가 발생했을 때 예상되는 다음 상태를 반환합니다.
     * 
     * 이 메서드는 상태 머신의 전이 규칙에 따라 각 이벤트가 어떤 상태로 전이되는지 정의합니다.
     * 
     * @param currentState 현재 상태
     * @param event 발생할 이벤트
     * @return 예상되는 다음 상태
     */
    override fun getTargetState(currentState: OrderState, event: OrderEvent): OrderState {
        return when (event) {
            OrderEvent.SUBMIT_PAYMENT -> OrderState.PAYMENT_PENDING
            OrderEvent.PAYMENT_SUCCEEDED -> OrderState.PAID
            OrderEvent.PAYMENT_FAILED -> OrderState.CREATED
            OrderEvent.PREPARE -> OrderState.PREPARING
            OrderEvent.SHIP -> OrderState.SHIPPED
            OrderEvent.DELIVER -> OrderState.DELIVERED
            OrderEvent.CANCEL -> OrderState.CANCELLED
            OrderEvent.REFUND -> OrderState.REFUNDED
            OrderEvent.SELECT_PAYMENT_METHOD -> OrderState.PAYMENT_CHOICE
            OrderEvent.CREDIT_CARD, OrderEvent.BANK_TRANSFER -> OrderState.PAYMENT_PENDING
            OrderEvent.CHECK_SHIPPING -> OrderState.SHIPPING_JUNCTION
            OrderEvent.EXPEDITE, OrderEvent.STANDARD -> OrderState.PREPARING
        }
    }
}
