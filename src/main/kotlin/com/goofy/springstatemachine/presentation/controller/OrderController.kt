package com.goofy.springstatemachine.presentation.controller

import com.goofy.springstatemachine.application.service.OrderService
import com.goofy.springstatemachine.domain.model.Order
import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState
import com.goofy.springstatemachine.presentation.dto.CreateOrderRequest
import com.goofy.springstatemachine.presentation.dto.OrderResponse
import com.goofy.springstatemachine.presentation.dto.StateChangeRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 주문 컨트롤러
 * 
 * 주문 관련 API 엔드포인트를 제공하는 컨트롤러
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {
    /**
     * 주문 생성 API
     * 
     * @param request 주문 생성 요청 DTO
     * @return 생성된 주문 정보
     */
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<OrderResponse> {
        val order = request.toDomain()
        val createdOrder = orderService.createOrder(order)
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.fromDomain(createdOrder))
    }

    /**
     * 주문 상태 변경 API
     * 
     * @param orderNumber 주문 번호
     * @param request 상태 변경 요청 DTO
     * @return 업데이트된 주문 정보
     */
    @PostMapping("/{orderNumber}/events")
    fun changeOrderState(
        @PathVariable orderNumber: String,
        @RequestBody request: StateChangeRequest
    ): ResponseEntity<OrderResponse> {
        val event = OrderEvent.valueOf(request.event)
        val updatedOrder = orderService.changeOrderState(orderNumber, event)
        return ResponseEntity.ok(OrderResponse.fromDomain(updatedOrder))
    }

    /**
     * 주문 조회 API
     * 
     * @param orderNumber 주문 번호
     * @return 주문 정보
     */
    @GetMapping("/{orderNumber}")
    fun getOrder(@PathVariable orderNumber: String): ResponseEntity<OrderResponse> {
        val order = orderService.getOrder(orderNumber)
        return ResponseEntity.ok(OrderResponse.fromDomain(order))
    }

    /**
     * 모든 주문 조회 API
     * 
     * @return 모든 주문 목록
     */
    @GetMapping
    fun getAllOrders(): ResponseEntity<List<OrderResponse>> {
        val orders = orderService.getAllOrders()
        return ResponseEntity.ok(orders.map { OrderResponse.fromDomain(it) })
    }

    /**
     * 주문 상태별 조회 API
     * 
     * @param state 주문 상태
     * @return 해당 상태의 주문 목록
     */
    @GetMapping("/by-state/{state}")
    fun getOrdersByState(@PathVariable state: String): ResponseEntity<List<OrderResponse>> {
        val orderState = OrderState.valueOf(state)
        val orders = orderService.getOrdersByState(orderState)
        return ResponseEntity.ok(orders.map { OrderResponse.fromDomain(it) })
    }

    /**
     * 가능한 이벤트 조회 API
     * 
     * @param orderNumber 주문 번호
     * @return 현재 상태에서 가능한 이벤트 목록
     */
    @GetMapping("/{orderNumber}/possible-events")
    fun getPossibleEvents(@PathVariable orderNumber: String): ResponseEntity<Map<String, Boolean>> {
        val order = orderService.getOrder(orderNumber)

        val possibleEvents = mapOf(
            OrderEvent.SUBMIT_PAYMENT.name to order.canPay(),
            OrderEvent.SHIP.name to order.canShip(),
            OrderEvent.DELIVER.name to order.canDeliver(),
            OrderEvent.CANCEL.name to order.canCancel()
        )

        return ResponseEntity.ok(possibleEvents)
    }
}
