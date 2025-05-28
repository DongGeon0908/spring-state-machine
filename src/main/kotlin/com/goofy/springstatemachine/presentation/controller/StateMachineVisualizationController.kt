package com.goofy.springstatemachine.presentation.controller

import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState
import com.goofy.springstatemachine.infrastructure.repository.OrderStateTransitionRepository
import org.springframework.http.MediaType
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 상태 머신 시각화 컨트롤러
 * 
 * 이 컨트롤러는 상태 머신의 구조와 상태를 시각화하는 엔드포인트를 제공합니다.
 * 개발 및 디버깅 목적으로 사용할 수 있습니다.
 * 
 * 주요 기능:
 * 1. 상태 머신 다이어그램 생성: DOT 형식으로 상태 머신의 구조를 시각화합니다.
 * 2. 상태 머신 상태 조회: 특정 주문의 현재 상태를 조회합니다.
 * 3. 상태 머신 전이 이력 조회: 특정 주문의 상태 전이 이력을 조회합니다.
 */
@RestController
@RequestMapping("/api/state-machine")
class StateMachineVisualizationController(
    private val stateMachineFactory: StateMachineFactory<OrderState, OrderEvent>
) {
    /**
     * 상태 머신 다이어그램 생성
     * 
     * 이 엔드포인트는 상태 머신의 구조를 DOT 형식으로 시각화합니다.
     * 생성된 DOT 코드는 GraphViz 등의 도구를 사용하여 이미지로 변환할 수 있습니다.
     * 
     * 사용 방법:
     * 1. /api/state-machine/diagram 엔드포인트에 GET 요청을 보냅니다.
     * 2. 반환된 DOT 코드를 GraphViz 온라인 도구(예: http://viz-js.com/)에 붙여넣습니다.
     * 3. 상태 머신의 구조가 시각화된 이미지를 확인합니다.
     * 
     * @return DOT 형식의 상태 머신 다이어그램
     */
    @GetMapping("/diagram", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun getStateMachineDiagram(): String {
        // 수동으로 DOT 다이어그램 생성
        val dot = StringBuilder()
        dot.append("digraph OrderStateMachine {\n")
        dot.append("  rankdir=LR;\n")
        dot.append("  size=\"8,5\"\n")
        dot.append("  node [shape = circle];\n")

        // 상태 정의
        for (state in OrderState.values()) {
            val shape = when (state) {
                OrderState.CREATED -> "doublecircle" // 초기 상태
                OrderState.DELIVERED, OrderState.CANCELLED -> "doubleoctagon" // 최종 상태
                else -> "circle"
            }
            dot.append("  ${state.name} [shape = $shape, label = \"${state.name}\"];\n")
        }

        // 전이 정의
        dot.append("  CREATED -> PAID [label = \"PAY\"];\n")
        dot.append("  CREATED -> CANCELLED [label = \"CANCEL\"];\n")
        dot.append("  PAID -> SHIPPED [label = \"SHIP\"];\n")
        dot.append("  PAID -> CANCELLED [label = \"CANCEL\"];\n")
        dot.append("  SHIPPED -> DELIVERED [label = \"DELIVER\"];\n")

        dot.append("}\n")

        return dot.toString()
    }

    /**
     * 상태 머신 구조 조회
     * 
     * 이 엔드포인트는 상태 머신의 구조를 JSON 형식으로 반환합니다.
     * 
     * @return 상태 머신 구조 정보
     */
    @GetMapping("/structure")
    fun getStateMachineStructure(): Map<String, Any> {
        // 상태 정의
        val states = OrderState.values().map { it.name }

        // 이벤트 정의
        val events = OrderEvent.values().map { it.name }

        // 전이 정의
        val transitions = listOf(
            mapOf(
                "source" to "CREATED",
                "target" to "PAID",
                "event" to "PAY",
                "guard" to "paymentPossibleGuard"
            ),
            mapOf(
                "source" to "CREATED",
                "target" to "CANCELLED",
                "event" to "CANCEL",
                "guard" to "cancellationPossibleGuard"
            ),
            mapOf(
                "source" to "PAID",
                "target" to "SHIPPED",
                "event" to "SHIP",
                "guard" to "shippingPossibleGuard"
            ),
            mapOf(
                "source" to "PAID",
                "target" to "CANCELLED",
                "event" to "CANCEL",
                "guard" to "cancellationPossibleGuard"
            ),
            mapOf(
                "source" to "SHIPPED",
                "target" to "DELIVERED",
                "event" to "DELIVER"
            )
        )

        return mapOf(
            "states" to states,
            "events" to events,
            "transitions" to transitions,
            "initialState" to "CREATED",
            "endStates" to listOf("DELIVERED", "CANCELLED")
        )
    }

    /**
     * 상태 머신 상태 조회
     * 
     * 이 엔드포인트는 특정 주문의 현재 상태를 조회합니다.
     * 
     * @param orderNumber 주문 번호
     * @return 현재 상태 정보
     */
    @GetMapping("/{orderNumber}/state")
    fun getStateMachineState(@PathVariable orderNumber: String): Map<String, Any> {
        val stateMachine = stateMachineFactory.getStateMachine(orderNumber)

        return mapOf(
            "orderNumber" to orderNumber,
            "currentState" to (stateMachine.state?.id?.name ?: "UNKNOWN"),
            "isRunning" to isStateMachineRunning(stateMachine)
        )
    }

    /**
     * 상태 머신 실행 상태 확인
     * 
     * 이 메서드는 상태 머신이 실행 중인지 확인합니다.
     * 
     * @param stateMachine 상태 머신
     * @return 실행 중 여부
     */
    private fun isStateMachineRunning(stateMachine: StateMachine<OrderState, OrderEvent>): Boolean {
        return try {
            // 상태 머신이 이미 실행 중이면 start()가 예외를 발생시키거나 아무 동작도 하지 않음
            val initialState = stateMachine.state?.id
            stateMachine.start()
            val afterState = stateMachine.state?.id

            // 상태가 변경되지 않았다면 이미 실행 중이었을 가능성이 높음
            initialState == afterState
        } catch (e: Exception) {
            // 예외가 발생하면 이미 실행 중이었을 가능성이 높음
            true
        }
    }
}
