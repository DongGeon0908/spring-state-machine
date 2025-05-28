package com.goofy.springstatemachine.domain.service

import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState

/**
 * 주문 상태 머신 서비스 인터페이스
 * 
 * 이 인터페이스는 주문 상태 머신 관련 작업을 추상화하여 도메인 계층에서 정의합니다.
 * 도메인 주도 설계(DDD)에서 이러한 인터페이스는 도메인 서비스로 분류되며,
 * 특정 기술 구현에 의존하지 않고 순수한 비즈니스 로직을 표현합니다.
 * 
 * 이 인터페이스의 구현체는 인프라스트럭처 계층에 위치하며,
 * Spring State Machine과 같은 구체적인 기술을 사용하여 상태 전이를 처리합니다.
 */
interface OrderStateMachineService {
    /**
     * 주문에 대한 상태 머신을 초기화합니다.
     * 
     * @param orderNumber 주문 번호
     * @return 초기화된 상태 (일반적으로 CREATED)
     */
    fun initializeStateMachine(orderNumber: String): OrderState
    
    /**
     * 주문 상태 머신에 이벤트를 발생시켜 상태 전이를 수행합니다.
     * 
     * @param orderNumber 주문 번호
     * @param event 발생시킬 이벤트
     * @return 이벤트 처리 후의 새로운 상태
     */
    fun sendEvent(orderNumber: String, event: OrderEvent): OrderState
    
    /**
     * 현재 상태에서 특정 이벤트를 발생시킬 수 있는지 확인합니다.
     * 
     * @param currentState 현재 상태
     * @param event 확인할 이벤트
     * @return 이벤트 발생 가능 여부
     */
    fun canFireEvent(currentState: OrderState, event: OrderEvent): Boolean
    
    /**
     * 특정 이벤트가 발생했을 때 예상되는 다음 상태를 반환합니다.
     * 
     * @param currentState 현재 상태
     * @param event 발생할 이벤트
     * @return 예상되는 다음 상태
     */
    fun getTargetState(currentState: OrderState, event: OrderEvent): OrderState
}
