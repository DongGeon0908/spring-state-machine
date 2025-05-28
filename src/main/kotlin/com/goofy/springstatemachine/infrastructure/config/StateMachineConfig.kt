package com.goofy.springstatemachine.infrastructure.config

import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.action.Action
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.StateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer
import org.springframework.statemachine.guard.Guard
import org.springframework.statemachine.listener.StateMachineListener
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State
import org.springframework.statemachine.support.DefaultStateMachineContext
import org.springframework.statemachine.transition.Transition
import java.util.*

/**
 * 상태 머신 설정 클래스
 * 
 * 상태 머신(State Machine)이란?
 * - 상태 머신은 시스템이 가질 수 있는 상태(State)와 상태 간 전이(Transition)를 정의하는 디자인 패턴입니다.
 * - 시스템은 한 번에 하나의 상태만 가질 수 있으며, 이벤트(Event)가 발생하면 현재 상태에서 다른 상태로 전이할 수 있습니다.
 * - 상태 머신은 복잡한 상태 관리 로직을 단순화하고 가시적으로 만들어 줍니다.
 * 
 * Spring State Machine이란?
 * - Spring에서 제공하는 상태 머신 구현체로, 상태와 전이를 쉽게 정의하고 관리할 수 있게 해줍니다.
 * - 상태 저장, 이벤트 처리, 전이 규칙 정의 등의 기능을 제공합니다.
 * - 이 클래스에서는 주문 처리 흐름에 맞는 상태 머신을 설정합니다.
 * 
 * 고급 기능:
 * - 가드(Guard): 조건부 상태 전이를 위한 조건 로직을 정의합니다.
 * - 액션(Action): 상태 전이 전/후에 실행되는 작업을 정의합니다.
 * - 리스너(Listener): 상태 머신의 상태 변화를 모니터링하고 로깅합니다.
 * - 오류 처리: 상태 전이 중 발생하는 오류를 처리합니다.
 * 
 * 주문 상태 머신의 상태, 이벤트, 전이 규칙을 설정하는 클래스입니다.
 * 이 설정을 통해 주문이 어떤 상태에서 어떤 이벤트가 발생했을 때 어떤 상태로 변경되는지 정의합니다.
 */
@Configuration
@EnableStateMachineFactory
class StateMachineConfig : StateMachineConfigurerAdapter<OrderState, OrderEvent>() {

    private val logger = LoggerFactory.getLogger(StateMachineConfig::class.java)

    /**
     * 결제 가능 여부 확인 가드
     * 
     * 이 가드는 주문이 결제 가능한 상태인지 확인합니다.
     * 예를 들어, 주문 금액이 0보다 큰지, 고객 정보가 유효한지 등을 검사할 수 있습니다.
     * 
     * @return 결제 가능 여부를 확인하는 Guard 객체
     */
    @Bean
    fun paymentPossibleGuard(): Guard<OrderState, OrderEvent> {
        return Guard { context ->
            // 주문 정보 확인 (예: 주문 금액이 0보다 큰지)
            // 실제 구현에서는 context에서 주문 정보를 추출하여 검증
            val orderAmount = context.extendedState.variables["amount"] as? Double ?: 0.0
            val isValid = orderAmount > 0.0

            if (!isValid) {
                logger.warn("결제 불가: 주문 금액이 0 이하입니다.")
            }

            isValid
        }
    }

    /**
     * 배송 가능 여부 확인 가드
     * 
     * 이 가드는 주문이 배송 가능한 상태인지 확인합니다.
     * 예를 들어, 재고가 충분한지, 배송지 정보가 유효한지 등을 검사할 수 있습니다.
     * 
     * @return 배송 가능 여부를 확인하는 Guard 객체
     */
    @Bean
    fun shippingPossibleGuard(): Guard<OrderState, OrderEvent> {
        return Guard { context ->
            // 배송 정보 확인 (예: 배송지 정보가 유효한지)
            // 실제 구현에서는 context에서 배송 정보를 추출하여 검증
            val hasShippingAddress = context.extendedState.variables["shippingAddress"] != null
            val isValid = hasShippingAddress

            if (!isValid) {
                logger.warn("배송 불가: 배송지 정보가 없습니다.")
            }

            isValid
        }
    }

    /**
     * 취소 가능 여부 확인 가드
     * 
     * 이 가드는 주문이 취소 가능한 상태인지 확인합니다.
     * 예를 들어, 이미 배송이 시작된 주문은 취소할 수 없습니다.
     * 
     * @return 취소 가능 여부를 확인하는 Guard 객체
     */
    @Bean
    fun cancellationPossibleGuard(): Guard<OrderState, OrderEvent> {
        return Guard { context ->
            // 현재 상태 확인
            val currentState = context.stateMachine.state.id

            // CREATED 또는 PAID 상태일 때만 취소 가능
            val isValid = currentState == OrderState.CREATED || currentState == OrderState.PAID

            if (!isValid) {
                logger.warn("취소 불가: 이미 배송이 시작된 주문은 취소할 수 없습니다.")
            }

            isValid
        }
    }

    /**
     * 결제 액션
     * 
     * 이 액션은 결제 이벤트가 발생했을 때 실행됩니다.
     * 결제 처리, 영수증 발행, 결제 알림 등의 작업을 수행할 수 있습니다.
     * 
     * @return 결제 액션 객체
     */
    @Bean
    fun paymentAction(): Action<OrderState, OrderEvent> {
        return Action { context ->
            val orderNumber = context.stateMachine.id
            logger.info("결제 처리 중: 주문 번호 $orderNumber")

            try {
                // 결제 처리 로직 (예시)
                // 실제 구현에서는 결제 게이트웨이 API 호출 등의 작업 수행
                logger.info("결제 완료: 주문 번호 $orderNumber")

                // 결제 성공 정보를 상태 머신의 확장 상태에 저장
                context.extendedState.variables["paymentTime"] = System.currentTimeMillis()
                context.extendedState.variables["paymentStatus"] = "SUCCESS"
            } catch (e: Exception) {
                // 결제 실패 처리
                logger.error("결제 실패: ${e.message}", e)
                context.extendedState.variables["paymentStatus"] = "FAILED"
                context.extendedState.variables["paymentError"] = e.message
            }
        }
    }

    /**
     * 배송 시작 액션
     * 
     * 이 액션은 배송 시작 이벤트가 발생했을 때 실행됩니다.
     * 물류 시스템 연동, 배송 정보 업데이트, 배송 알림 등의 작업을 수행할 수 있습니다.
     * 
     * @return 배송 시작 액션 객체
     */
    @Bean
    fun shippingAction(): Action<OrderState, OrderEvent> {
        return Action { context ->
            val orderNumber = context.stateMachine.id
            logger.info("배송 처리 중: 주문 번호 $orderNumber")

            try {
                // 배송 처리 로직 (예시)
                // 실제 구현에서는 물류 시스템 API 호출 등의 작업 수행
                val trackingNumber = "TN-" + System.currentTimeMillis()
                logger.info("배송 시작: 주문 번호 $orderNumber, 운송장 번호 $trackingNumber")

                // 배송 정보를 상태 머신의 확장 상태에 저장
                context.extendedState.variables["shippingTime"] = System.currentTimeMillis()
                context.extendedState.variables["trackingNumber"] = trackingNumber
            } catch (e: Exception) {
                // 배송 처리 실패
                logger.error("배송 처리 실패: ${e.message}", e)
                context.extendedState.variables["shippingError"] = e.message
            }
        }
    }

    /**
     * 배송 완료 액션
     * 
     * 이 액션은 배송 완료 이벤트가 발생했을 때 실행됩니다.
     * 배송 상태 업데이트, 고객 알림, 리뷰 요청 등의 작업을 수행할 수 있습니다.
     * 
     * @return 배송 완료 액션 객체
     */
    @Bean
    fun deliveryAction(): Action<OrderState, OrderEvent> {
        return Action { context ->
            val orderNumber = context.stateMachine.id
            logger.info("배송 완료 처리 중: 주문 번호 $orderNumber")

            try {
                // 배송 완료 처리 로직 (예시)
                logger.info("배송 완료: 주문 번호 $orderNumber")

                // 배송 완료 정보를 상태 머신의 확장 상태에 저장
                context.extendedState.variables["deliveryTime"] = System.currentTimeMillis()

                // 리뷰 요청 이메일 발송 등의 후속 작업 트리거
                logger.info("리뷰 요청 이메일 발송: 주문 번호 $orderNumber")
            } catch (e: Exception) {
                // 배송 완료 처리 실패
                logger.error("배송 완료 처리 실패: ${e.message}", e)
                context.extendedState.variables["deliveryError"] = e.message
            }
        }
    }

    /**
     * 주문 취소 액션
     * 
     * 이 액션은 취소 이벤트가 발생했을 때 실행됩니다.
     * 환불 처리, 재고 복원, 취소 알림 등의 작업을 수행할 수 있습니다.
     * 
     * @return 주문 취소 액션 객체
     */
    @Bean
    fun cancellationAction(): Action<OrderState, OrderEvent> {
        return Action { context ->
            val orderNumber = context.stateMachine.id
            logger.info("주문 취소 처리 중: 주문 번호 $orderNumber")

            try {
                // 주문 취소 처리 로직 (예시)
                val currentState = context.stateMachine.state.id

                // 결제 완료 상태에서 취소된 경우 환불 처리
                if (currentState == OrderState.PAID) {
                    logger.info("환불 처리 중: 주문 번호 $orderNumber")
                    // 환불 처리 로직
                    context.extendedState.variables["refundTime"] = System.currentTimeMillis()
                    context.extendedState.variables["refundStatus"] = "SUCCESS"
                }

                // 취소 정보를 상태 머신의 확장 상태에 저장
                context.extendedState.variables["cancellationTime"] = System.currentTimeMillis()
                context.extendedState.variables["cancellationReason"] = 
                    context.messageHeaders["reason"] ?: "고객 요청"

                logger.info("주문 취소 완료: 주문 번호 $orderNumber")
            } catch (e: Exception) {
                // 취소 처리 실패
                logger.error("주문 취소 처리 실패: ${e.message}", e)
                context.extendedState.variables["cancellationError"] = e.message
            }
        }
    }

    /**
     * 상태 머신 리스너 빈 정의
     * 
     * 이 리스너는 상태 머신의 다양한 이벤트를 모니터링하고 로깅합니다:
     * - 상태 변경
     * - 상태 머신 시작/중지
     * - 전이 시작/완료
     * - 이벤트 처리
     * - 오류 발생
     * 
     * 이를 통해 상태 머신의 동작을 실시간으로 모니터링하고 디버깅할 수 있습니다.
     * 
     * @return 상태 머신 리스너
     */
    @Bean
    fun stateMachineListener(): StateMachineListener<OrderState, OrderEvent> {
        return object : StateMachineListenerAdapter<OrderState, OrderEvent>() {
            // 상태 변경 시 호출
            override fun stateChanged(from: State<OrderState, OrderEvent>?, to: State<OrderState, OrderEvent>) {
                val fromState = from?.id ?: "NONE"
                logger.info("상태 변경: $fromState -> ${to.id}")
            }

            // 상태 머신 시작 시 호출
            override fun stateMachineStarted(stateMachine: StateMachine<OrderState, OrderEvent>) {
                logger.info("상태 머신 시작: ${stateMachine.id}")
            }

            // 상태 머신 중지 시 호출
            override fun stateMachineStopped(stateMachine: StateMachine<OrderState, OrderEvent>) {
                logger.info("상태 머신 중지: ${stateMachine.id}")
            }

            // 전이 시작 시 호출
            override fun transitionStarted(transition: Transition<OrderState, OrderEvent>) {
                val source = transition.source?.id ?: "NONE"
                val target = transition.target?.id ?: "NONE"
                logger.debug("전이 시작: $source -> $target")
            }

            // 전이 완료 시 호출
            override fun transitionEnded(transition: Transition<OrderState, OrderEvent>) {
                val source = transition.source?.id ?: "NONE"
                val target = transition.target?.id ?: "NONE"
                logger.debug("전이 완료: $source -> $target")
            }

            // 이벤트가 수락되지 않았을 때 호출
            override fun eventNotAccepted(event: Message<OrderEvent>) {
                logger.warn("이벤트 거부됨: ${event.payload}")
            }

            // 상태 머신 오류 발생 시 호출
            override fun stateMachineError(stateMachine: StateMachine<OrderState, OrderEvent>, exception: Exception) {
                logger.error("상태 머신 오류: ${exception.message}", exception)
            }
        }
    }

    /**
     * 상태 머신의 상태 설정
     * 
     * 이 메서드는 상태 머신이 가질 수 있는 모든 상태를 정의합니다.
     * 
     * 상태 설정 방법:
     * 1. withStates(): 상태 설정을 시작합니다.
     * 2. initial(OrderState.CREATED): 초기 상태를 CREATED로 설정합니다. 상태 머신이 처음 생성될 때 이 상태로 시작합니다.
     * 3. choice(OrderState.PAYMENT_CHOICE): 선택 상태를 정의합니다. 이 상태에서는 조건에 따라 다른 상태로 전이할 수 있습니다.
     * 4. junction(OrderState.SHIPPING_JUNCTION): 정션 상태를 정의합니다. 이 상태는 조건에 따라 다른 상태로 전이하는 분기점입니다.
     * 5. end(OrderState.DELIVERED, OrderState.CANCELLED, OrderState.REFUNDED): 최종 상태를 정의합니다. 이 상태에 도달하면 상태 머신이 종료됩니다.
     * 6. states(EnumSet.allOf(OrderState::class.java)): OrderState 열거형에 정의된 모든 상태를 상태 머신에 등록합니다.
     * 
     * 고급 상태 유형:
     * - 선택 상태(Choice State): 런타임에 조건에 따라 다른 상태로 전이하는 결정 지점입니다.
     * - 정션 상태(Junction State): 설정 시점에 조건에 따라 다른 상태로 전이하는 분기점입니다.
     * - 최종 상태(End State): 상태 머신의 실행이 종료되는 상태입니다.
     * 
     * 이렇게 설정된 상태들은 상태 머신이 가질 수 있는 모든 가능한 상태를 나타냅니다.
     */
    override fun configure(states: StateMachineStateConfigurer<OrderState, OrderEvent>) {
        // 기본 상태 설정
        states
            .withStates()
            .initial(OrderState.CREATED)  // 초기 상태는 '주문 생성' 상태
            .choice(OrderState.PAYMENT_CHOICE)  // 선택 상태: 결제 방법에 따라 다른 처리 경로로 분기
            .junction(OrderState.SHIPPING_JUNCTION)  // 정션 상태: 배송 조건에 따라 다른 처리 경로로 분기
            .end(OrderState.DELIVERED)  // 최종 상태: 배송 완료
            .end(OrderState.CANCELLED)  // 최종 상태: 주문 취소
            .end(OrderState.REFUNDED)   // 최종 상태: 환불 완료
            .states(EnumSet.allOf(OrderState::class.java))  // 모든 OrderState 열거형 값을 상태로 등록
    }

    /**
     * 상태 머신의 전이 설정
     * 
     * 이 메서드는 상태 머신의 상태 전이(Transition) 규칙을 정의합니다.
     * 전이란 특정 상태에서 특정 이벤트가 발생했을 때 다른 상태로 변경되는 것을 의미합니다.
     * 
     * 전이 설정 방법:
     * 1. withExternal(): 외부 전이를 정의합니다. 외부 전이는 한 상태에서 다른 상태로 변경되는 것을 의미합니다.
     * 2. source(상태): 전이의 출발 상태를 지정합니다.
     * 3. target(상태): 전이의 도착 상태를 지정합니다.
     * 4. event(이벤트): 전이를 발생시키는 이벤트를 지정합니다.
     * 5. guard(가드): 전이가 발생하기 위한 조건을 지정합니다.
     * 6. and(): 다음 전이 규칙을 정의하기 위해 사용합니다.
     * 
     * 주문 처리 흐름에서의 전이 규칙:
     * 1. CREATED -> PAY -> PAID: 주문 생성 후 결제 시 결제 완료 상태로 변경 (결제 가능 여부 확인)
     * 2. PAID -> SHIP -> SHIPPED: 결제 완료 후 배송 시작 시 배송 중 상태로 변경 (배송 가능 여부 확인)
     * 3. SHIPPED -> DELIVER -> DELIVERED: 배송 중에서 배송 완료 처리 시 배송 완료 상태로 변경
     * 4. CREATED -> CANCEL -> CANCELLED: 주문 생성 후 취소 시 취소 상태로 변경 (취소 가능 여부 확인)
     * 5. PAID -> CANCEL -> CANCELLED: 결제 완료 후 취소 시 취소 상태로 변경 (취소 가능 여부 확인)
     * 
     * 가드(Guard) 사용:
     * - 가드는 전이가 발생하기 위한 조건을 정의합니다.
     * - 가드가 false를 반환하면 전이가 발생하지 않습니다.
     * - 이를 통해 비즈니스 규칙을 상태 머신에 적용할 수 있습니다.
     * 
     * 이 전이 규칙들은 주문의 라이프사이클을 정의하며, 어떤 상태에서 어떤 이벤트가 발생했을 때
     * 어떤 상태로 변경될 수 있는지를 명확하게 보여줍니다.
     */
    /**
     * 결제 방법 선택 가드 - 신용카드
     */
    @Bean
    fun creditCardGuard(): Guard<OrderState, OrderEvent> {
        return Guard { context ->
            val paymentMethod = context.extendedState.variables["paymentMethod"] as? String
            paymentMethod == "CREDIT_CARD"
        }
    }

    /**
     * 결제 방법 선택 가드 - 계좌이체
     */
    @Bean
    fun bankTransferGuard(): Guard<OrderState, OrderEvent> {
        return Guard { context ->
            val paymentMethod = context.extendedState.variables["paymentMethod"] as? String
            paymentMethod == "BANK_TRANSFER"
        }
    }

    /**
     * 배송 방법 선택 가드 - 빠른 배송
     */
    @Bean
    fun expediteShippingGuard(): Guard<OrderState, OrderEvent> {
        return Guard { context ->
            val expedite = context.extendedState.variables["expediteShipping"] as? Boolean
            expedite == true
        }
    }

    /**
     * 배송 방법 선택 가드 - 일반 배송
     */
    @Bean
    fun standardShippingGuard(): Guard<OrderState, OrderEvent> {
        return Guard { context ->
            val expedite = context.extendedState.variables["expediteShipping"] as? Boolean
            expedite != true
        }
    }

    override fun configure(transitions: StateMachineTransitionConfigurer<OrderState, OrderEvent>) {
        transitions
            // 기본 흐름 전이

            // 1. CREATED -> SUBMIT_PAYMENT -> PAYMENT_PENDING: 주문 생성 후 결제 제출
            .withExternal()
            .source(OrderState.CREATED)
            .target(OrderState.PAYMENT_PENDING)
            .event(OrderEvent.SUBMIT_PAYMENT)
            .guard(paymentPossibleGuard())
            .action(paymentAction())
            .and()

            // 2. PAYMENT_PENDING -> PAYMENT_SUCCEEDED -> PAID: 결제 성공
            .withExternal()
            .source(OrderState.PAYMENT_PENDING)
            .target(OrderState.PAID)
            .event(OrderEvent.PAYMENT_SUCCEEDED)
            .and()

            // 3. PAYMENT_PENDING -> PAYMENT_FAILED -> CREATED: 결제 실패
            .withExternal()
            .source(OrderState.PAYMENT_PENDING)
            .target(OrderState.CREATED)
            .event(OrderEvent.PAYMENT_FAILED)
            .and()

            // 4. PAID -> PREPARE -> PREPARING: 상품 준비 시작
            .withExternal()
            .source(OrderState.PAID)
            .target(OrderState.PREPARING)
            .event(OrderEvent.PREPARE)
            .and()

            // 5. PREPARING -> SHIP -> SHIPPED: 배송 시작
            .withExternal()
            .source(OrderState.PREPARING)
            .target(OrderState.SHIPPED)
            .event(OrderEvent.SHIP)
            .guard(shippingPossibleGuard())
            .action(shippingAction())
            .and()

            // 6. SHIPPED -> DELIVER -> DELIVERED: 배송 완료
            .withExternal()
            .source(OrderState.SHIPPED)
            .target(OrderState.DELIVERED)
            .event(OrderEvent.DELIVER)
            .action(deliveryAction())
            .and()

            // 취소 관련 전이

            // 7. CREATED -> CANCEL -> CANCELLED: 주문 생성 후 취소
            .withExternal()
            .source(OrderState.CREATED)
            .target(OrderState.CANCELLED)
            .event(OrderEvent.CANCEL)
            .guard(cancellationPossibleGuard())
            .action(cancellationAction())
            .and()

            // 8. PAYMENT_PENDING -> CANCEL -> CANCELLED: 결제 대기 중 취소
            .withExternal()
            .source(OrderState.PAYMENT_PENDING)
            .target(OrderState.CANCELLED)
            .event(OrderEvent.CANCEL)
            .guard(cancellationPossibleGuard())
            .action(cancellationAction())
            .and()

            // 9. PAID -> CANCEL -> CANCELLED: 결제 완료 후 취소
            .withExternal()
            .source(OrderState.PAID)
            .target(OrderState.CANCELLED)
            .event(OrderEvent.CANCEL)
            .guard(cancellationPossibleGuard())
            .action(cancellationAction())
            .and()

            // 10. CANCELLED -> REFUND -> REFUNDED: 취소 후 환불
            .withExternal()
            .source(OrderState.CANCELLED)
            .target(OrderState.REFUNDED)
            .event(OrderEvent.REFUND)
            .and()

            // 선택 상태 관련 전이

            // 11. CREATED -> SELECT_PAYMENT_METHOD -> PAYMENT_CHOICE: 결제 방법 선택
            .withExternal()
            .source(OrderState.CREATED)
            .target(OrderState.PAYMENT_CHOICE)
            .event(OrderEvent.SELECT_PAYMENT_METHOD)
            .and()

            // 12. PAYMENT_CHOICE -> CREDIT_CARD -> PAYMENT_PENDING: 신용카드 결제 선택
            .withChoice()
            .source(OrderState.PAYMENT_CHOICE)
            .first(OrderState.PAYMENT_PENDING, creditCardGuard())
            .last(OrderState.PAYMENT_PENDING)
            .and()

            // 정션 상태 관련 전이

            // 13. PAID -> CHECK_SHIPPING -> SHIPPING_JUNCTION: 배송 조건 확인
            .withExternal()
            .source(OrderState.PAID)
            .target(OrderState.SHIPPING_JUNCTION)
            .event(OrderEvent.CHECK_SHIPPING)
            .and()

            // 14. SHIPPING_JUNCTION -> EXPEDITE/STANDARD -> PREPARING: 배송 방법에 따른 분기
            .withJunction()
            .source(OrderState.SHIPPING_JUNCTION)
            .first(OrderState.PREPARING, expediteShippingGuard())
            .last(OrderState.PREPARING)
    }

    /**
     * 상태 머신 설정
     * 
     * 이 메서드는 상태 머신의 일반적인 설정을 정의합니다.
     * 
     * 설정 방법:
     * 1. withConfiguration(): 설정을 시작합니다.
     * 2. autoStartup(false): 상태 머신이 생성될 때 자동으로 시작되지 않도록 설정합니다.
     *    - true로 설정하면 상태 머신이 생성될 때 자동으로 시작됩니다.
     *    - false로 설정하면 명시적으로 start() 메서드를 호출해야 상태 머신이 시작됩니다.
     * 3. listener(): 상태 머신 리스너를 등록합니다.
     *    - 상태 변경, 전이, 오류 등의 이벤트를 모니터링하고 로깅합니다.
     * 4. machineId(): 상태 머신의 고유 ID를 설정합니다.
     *    - 여러 상태 머신을 구분하는 데 사용됩니다.
     * 
     * 이 프로젝트에서는 주문이 생성될 때 명시적으로 상태 머신을 시작하기 위해 autoStartup을 false로 설정합니다.
     * 이렇게 하면 상태 머신의 시작 시점을 더 정확하게 제어할 수 있습니다.
     */
    override fun configure(config: StateMachineConfigurationConfigurer<OrderState, OrderEvent>) {
        config
            .withConfiguration()
            .autoStartup(false)  // 상태 머신이 자동으로 시작되지 않도록 설정
            .listener(stateMachineListener())  // 상태 머신 리스너 등록
            .machineId("orderStateMachine")  // 상태 머신 ID 설정
    }

    /**
     * 오류 처리 액션
     * 
     * 이 액션은 상태 머신에서 오류가 발생했을 때 실행됩니다.
     * 오류 로깅, 알림 발송, 복구 시도 등의 작업을 수행할 수 있습니다.
     * 
     * @return 오류 처리 액션 객체
     */
    @Bean
    fun errorAction(): Action<OrderState, OrderEvent> {
        return Action { context ->
            val exception = context.exception
            val stateMachineId = context.stateMachine.id

            logger.error("상태 머신 오류 발생: 머신 ID=$stateMachineId, 오류=${exception?.message}", exception)

            // 오류 정보를 확장 상태에 저장
            context.extendedState.variables["lastError"] = exception?.message ?: "Unknown error"
            context.extendedState.variables["errorTime"] = System.currentTimeMillis()

            // 알림 로직 (예시)
            logger.info("오류 알림 발송: 머신 ID=$stateMachineId")

            // 복구 시도 로직 (예시)
            val retryCount = context.extendedState.variables["retryCount"] as? Int ?: 0
            if (retryCount < 3) {
                context.extendedState.variables["retryCount"] = retryCount + 1
                logger.info("복구 시도: 머신 ID=$stateMachineId, 시도 횟수=${retryCount + 1}")
            } else {
                logger.warn("최대 재시도 횟수 초과: 머신 ID=$stateMachineId")
            }
        }
    }

    /**
     * 알림 액션
     * 
     * 이 액션은 상태 변경 시 알림을 발송합니다.
     * 
     * @return 알림 액션 객체
     */
    @Bean
    fun notificationAction(): Action<OrderState, OrderEvent> {
        return Action { context ->
            val orderNumber = context.stateMachine.id
            val currentState = context.stateMachine.state.id

            when (currentState) {
                OrderState.PAYMENT_PENDING -> logger.info("알림: 결제가 시작되었습니다. 주문 번호: $orderNumber")
                OrderState.PAID -> logger.info("알림: 결제가 완료되었습니다. 주문 번호: $orderNumber")
                OrderState.SHIPPED -> logger.info("알림: 배송이 시작되었습니다. 주문 번호: $orderNumber")
                OrderState.DELIVERED -> logger.info("알림: 배송이 완료되었습니다. 주문 번호: $orderNumber")
                OrderState.CANCELLED -> logger.info("알림: 주문이 취소되었습니다. 주문 번호: $orderNumber")
                else -> logger.debug("상태 변경: $currentState, 주문 번호: $orderNumber")
            }
        }
    }
}
