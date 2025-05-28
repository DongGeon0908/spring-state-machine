package com.goofy.springstatemachine.infrastructure.service

import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.service.StateMachineService
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * 상태 머신 영속성 서비스
 * 
 * 상태 머신 영속성이란?
 * - 상태 머신의 현재 상태를 외부 저장소에 저장하고 필요할 때 복원하는 메커니즘입니다.
 * - 이를 통해 애플리케이션이 재시작되거나 다른 서버로 요청이 라우팅되더라도 상태 머신의 상태가 유지됩니다.
 * - 분산 시스템에서 상태 일관성을 유지하는 데 중요한 역할을 합니다.
 * 
 * Redis를 사용하는 이유:
 * - Redis는 인메모리 데이터 저장소로, 빠른 읽기/쓰기 성능을 제공합니다.
 * - 키-값 구조로 상태 머신의 상태를 쉽게 저장하고 조회할 수 있습니다.
 * - TTL(Time-To-Live) 기능을 통해 오래된 상태 데이터를 자동으로 정리할 수 있습니다.
 * 
 * 이 서비스는 주문 상태 머신의 상태를 Redis에 저장하고 복원하는 기능을 제공합니다.
 * 각 주문은 고유한 키(주문 번호)로 식별되며, 해당 키를 사용하여 상태를 저장하고 조회합니다.
 */
@Service
class StateMachinePersistenceService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    companion object {
        private const val REDIS_KEY_PREFIX = "order:state:"
        /** 24시간 (초 단위) */
        private const val EXPIRATION_TIME = 24L * 60L * 60L
    }

    /**
     * 상태 머신의 상태를 Redis에 저장
     * 
     * 이 메서드는 상태 머신의 현재 상태를 Redis에 저장합니다.
     * 
     * 작동 방식:
     * 1. 상태 머신에서 현재 상태(OrderState)를 가져옵니다.
     * 2. 주문 ID를 기반으로 Redis 키를 생성합니다.
     * 3. 상태 이름을 Redis에 저장합니다.
     * 4. 저장된 데이터에 만료 시간(TTL)을 설정합니다.
     * 
     * 이렇게 저장된 상태는 나중에 restore() 메서드를 통해 복원할 수 있습니다.
     * 
     * @param stateMachine 저장할 상태 머신
     * @param orderId 주문 ID (Redis 키로 사용)
     */
    fun persist(stateMachine: StateMachine<OrderState, OrderEvent>, orderId: String) {
        try {
            /** 상태 머신에서 현재 상태 가져오기 */
            val state = stateMachine.state.id

            /** Redis 키 생성 */
            val key = getKey(orderId)

            /** 상태 이름을 Redis에 저장 */
            redisTemplate.opsForValue().set(key, state.name)

            /** 만료 시간 설정 (24시간) */
            redisTemplate.expire(key, EXPIRATION_TIME, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw RuntimeException("상태 머신 저장 중 오류 발생: ${e.message}", e)
        }
    }

    private val logger = LoggerFactory.getLogger(StateMachinePersistenceService::class.java)

    /**
     * Redis에서 상태 머신의 상태를 복원
     * 
     * 이 메서드는 Redis에 저장된 상태 머신의 상태를 복원합니다.
     * 
     * 작동 방식:
     * 1. 주문 ID를 기반으로 Redis 키를 생성합니다.
     * 2. Redis에서 저장된 상태 값을 조회합니다.
     * 3. 상태 값이 존재하면 OrderState 열거형으로 변환합니다.
     * 4. 상태 머신을 시작하고, 필요한 이벤트를 발생시켜 원하는 상태로 전이시킵니다.
     * 
     * 향상된 기능:
     * - 실제로 상태 머신의 상태를 복원합니다 (단순 로깅이 아님).
     * - 오류 발생 시 재시도 로직을 포함합니다.
     * - 상태가 없는 경우 기본 상태(CREATED)로 초기화합니다.
     * 
     * @param stateMachine 복원할 상태 머신
     * @param orderId 주문 ID (Redis 키로 사용)
     * @return 복원된 상태 머신
     */
    fun restore(stateMachine: StateMachine<OrderState, OrderEvent>, orderId: String): StateMachine<OrderState, OrderEvent> {
        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                /** Redis 키 생성 */
                val key = getKey(orderId)

                /** Redis에서 상태 값 조회 */
                val stateValue = redisTemplate.opsForValue().get(key) as? String

                /** 상태 머신 시작 (초기 상태: CREATED) */
                try {
                    stateMachine.start()
                } catch (e: Exception) {
                    logger.debug("상태 머신이 이미 실행 중이거나 시작할 수 없습니다: ${e.message}")
                }

                /** 상태 값이 존재하면 OrderState 열거형으로 변환하고 상태 머신에 적용 */
                if (stateValue != null) {
                    try {
                        val targetState = OrderState.valueOf(stateValue)
                        val currentState = stateMachine.state.id

                        logger.info("상태 복원 시도: 주문 ID=$orderId, 현재 상태=$currentState, 목표 상태=$targetState")

                        /** 현재 상태와 목표 상태가 다른 경우에만 상태 전이 시도 */
                        if (currentState != targetState) {
                            /** 상태 전이 경로 결정 및 이벤트 발생 */
                            transitionToTargetState(stateMachine, currentState, targetState)
                        }

                        logger.info("상태 머신 복원 완료: 주문 ID=$orderId, 상태=${stateMachine.state.id}")
                        return stateMachine
                    } catch (e: IllegalArgumentException) {
                        /** 잘못된 상태 값인 경우 (예: 열거형에 없는 값) */
                        logger.warn("잘못된 상태 값: $stateValue, 기본 상태(CREATED)로 초기화합니다.")
                        return stateMachine  // 이미 CREATED 상태로 시작됨
                    }
                } else {
                    /** 저장된 상태가 없는 경우 기본 상태(CREATED)로 시작 */
                    logger.info("저장된 상태 없음: 주문 ID=$orderId, 기본 상태(CREATED)로 초기화합니다.")
                    return stateMachine  // 이미 CREATED 상태로 시작됨
                }
            } catch (e: Exception) {
                lastException = e
                retryCount++

                if (retryCount < maxRetries) {
                    /** 재시도 전 잠시 대기 */
                    Thread.sleep(100 * retryCount.toLong())
                    logger.warn("상태 머신 복원 재시도 ($retryCount/$maxRetries): ${e.message}")
                }
            }
        }

        /** 모든 재시도 실패 후 */
        logger.error("상태 머신 복원 실패 (최대 재시도 횟수 초과): ${lastException?.message}")
        throw RuntimeException("상태 머신 복원 중 오류 발생: ${lastException?.message}", lastException)
    }

    /**
     * 목표 상태로 전이
     * 
     * 이 메서드는 현재 상태에서 목표 상태로 전이하기 위해 필요한 이벤트를 발생시킵니다.
     * 
     * @param stateMachine 상태 머신
     * @param currentState 현재 상태
     * @param targetState 목표 상태
     */
    private fun transitionToTargetState(
        stateMachine: StateMachine<OrderState, OrderEvent>,
        currentState: OrderState,
        targetState: OrderState
    ) {
        /** 상태 전이 경로 결정 */
        val transitionPath = determineTransitionPath(currentState, targetState)

        /** 경로에 따라 이벤트 발생 */
        for (event in transitionPath) {
            logger.debug("이벤트 발생: $event (현재 상태: ${stateMachine.state.id})")

            val message = MessageBuilder.withPayload(event)
                .setHeader("restore", true)
                .build()

            val accepted = stateMachine.sendEvent(message)

            if (!accepted) {
                logger.warn("이벤트 거부됨: $event (현재 상태: ${stateMachine.state.id})")
                break
            }

            /** 이벤트 처리 시간 부여 */
            Thread.sleep(50)
        }
    }

    /**
     * 상태 전이 경로 결정
     * 
     * 이 메서드는 현재 상태에서 목표 상태로 전이하기 위해 필요한 이벤트 목록을 반환합니다.
     * 
     * @param currentState 현재 상태
     * @param targetState 목표 상태
     * @return 필요한 이벤트 목록
     */
    private fun determineTransitionPath(currentState: OrderState, targetState: OrderState): List<OrderEvent> {
        /** 상태 전이 경로 맵 */
        val transitionPaths = mapOf(
            /** CREATED -> PAYMENT_PENDING */
            Pair(OrderState.CREATED, OrderState.PAYMENT_PENDING) to listOf(OrderEvent.SUBMIT_PAYMENT),

            /** CREATED -> PAYMENT_CHOICE */
            Pair(OrderState.CREATED, OrderState.PAYMENT_CHOICE) to listOf(OrderEvent.SELECT_PAYMENT_METHOD),

            /** CREATED -> PAID */
            Pair(OrderState.CREATED, OrderState.PAID) to listOf(OrderEvent.SUBMIT_PAYMENT, OrderEvent.PAYMENT_SUCCEEDED),

            /** CREATED -> PREPARING */
            Pair(OrderState.CREATED, OrderState.PREPARING) to listOf(OrderEvent.SUBMIT_PAYMENT, OrderEvent.PAYMENT_SUCCEEDED, OrderEvent.PREPARE),

            /** CREATED -> SHIPPED */
            Pair(OrderState.CREATED, OrderState.SHIPPED) to listOf(OrderEvent.SUBMIT_PAYMENT, OrderEvent.PAYMENT_SUCCEEDED, OrderEvent.PREPARE, OrderEvent.SHIP),

            /** CREATED -> DELIVERED */
            Pair(OrderState.CREATED, OrderState.DELIVERED) to listOf(OrderEvent.SUBMIT_PAYMENT, OrderEvent.PAYMENT_SUCCEEDED, OrderEvent.PREPARE, OrderEvent.SHIP, OrderEvent.DELIVER),

            /** CREATED -> CANCELLED */
            Pair(OrderState.CREATED, OrderState.CANCELLED) to listOf(OrderEvent.CANCEL),

            /** PAYMENT_CHOICE -> PAYMENT_PENDING (Credit Card) */
            Pair(OrderState.PAYMENT_CHOICE, OrderState.PAYMENT_PENDING) to listOf(OrderEvent.CREDIT_CARD),

            /** PAYMENT_CHOICE -> PAYMENT_PENDING (Bank Transfer)
             * Note: This is a separate entry for the same transition but with a different event
             * In practice, only one of these would be used based on the user's choice
             */
            Pair(OrderState.PAYMENT_CHOICE, OrderState.PAYMENT_PENDING) to listOf(OrderEvent.BANK_TRANSFER),

            /** PAYMENT_PENDING -> PAID */
            Pair(OrderState.PAYMENT_PENDING, OrderState.PAID) to listOf(OrderEvent.PAYMENT_SUCCEEDED),

            /** PAYMENT_PENDING -> CREATED (Payment Failed) */
            Pair(OrderState.PAYMENT_PENDING, OrderState.CREATED) to listOf(OrderEvent.PAYMENT_FAILED),

            /** PAYMENT_PENDING -> CANCELLED */
            Pair(OrderState.PAYMENT_PENDING, OrderState.CANCELLED) to listOf(OrderEvent.CANCEL),

            /** PAID -> PREPARING */
            Pair(OrderState.PAID, OrderState.PREPARING) to listOf(OrderEvent.PREPARE),

            /** PAID -> SHIPPING_JUNCTION */
            Pair(OrderState.PAID, OrderState.SHIPPING_JUNCTION) to listOf(OrderEvent.CHECK_SHIPPING),

            /** PAID -> SHIPPED */
            Pair(OrderState.PAID, OrderState.SHIPPED) to listOf(OrderEvent.PREPARE, OrderEvent.SHIP),

            /** PAID -> DELIVERED */
            Pair(OrderState.PAID, OrderState.DELIVERED) to listOf(OrderEvent.PREPARE, OrderEvent.SHIP, OrderEvent.DELIVER),

            /** PAID -> CANCELLED */
            Pair(OrderState.PAID, OrderState.CANCELLED) to listOf(OrderEvent.CANCEL),

            /** SHIPPING_JUNCTION -> PREPARING (Expedite) */
            Pair(OrderState.SHIPPING_JUNCTION, OrderState.PREPARING) to listOf(OrderEvent.EXPEDITE),

            /** SHIPPING_JUNCTION -> PREPARING (Standard)
             * Note: This is a separate entry for the same transition but with a different event
             * In practice, only one of these would be used based on the shipping choice
             */
            Pair(OrderState.SHIPPING_JUNCTION, OrderState.PREPARING) to listOf(OrderEvent.STANDARD),

            /** PREPARING -> SHIPPED */
            Pair(OrderState.PREPARING, OrderState.SHIPPED) to listOf(OrderEvent.SHIP),

            /** PREPARING -> CANCELLED */
            Pair(OrderState.PREPARING, OrderState.CANCELLED) to listOf(OrderEvent.CANCEL),

            /** SHIPPED -> DELIVERED */
            Pair(OrderState.SHIPPED, OrderState.DELIVERED) to listOf(OrderEvent.DELIVER),

            /** CANCELLED -> REFUNDED */
            Pair(OrderState.CANCELLED, OrderState.REFUNDED) to listOf(OrderEvent.REFUND)
        )

        /** 경로 조회 */
        val path = transitionPaths[Pair(currentState, targetState)]

        /** 경로가 없는 경우 (예: DELIVERED -> PAID는 불가능) */
        if (path == null) {
            logger.warn("상태 전이 경로 없음: $currentState -> $targetState")
            return emptyList()
        }

        return path
    }

    /**
     * Redis 키 생성
     * 
     * 이 메서드는 주문 ID를 기반으로 Redis에 저장할 키를 생성합니다.
     * 
     * 작동 방식:
     * 1. 미리 정의된 접두사(REDIS_KEY_PREFIX)와 주문 ID를 결합하여 고유한 키를 생성합니다.
     * 2. 예: "order:state:ORD-123456789"
     * 
     * 키 접두사를 사용하는 이유:
     * - 다른 데이터와 구분하여 관리할 수 있습니다.
     * - 키 패턴을 사용한 검색이 가능합니다.
     * - 특정 패턴의 키만 일괄 삭제하는 등의 관리가 용이합니다.
     * 
     * @param orderId 주문 ID
     * @return 생성된 Redis 키
     */
    private fun getKey(orderId: String): String {
        /** "order:state:" + 주문 ID */
        return REDIS_KEY_PREFIX + orderId
    }
}
