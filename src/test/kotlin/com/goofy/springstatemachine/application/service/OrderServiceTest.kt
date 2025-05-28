package com.goofy.springstatemachine.application.service

import com.goofy.springstatemachine.domain.model.Order
import com.goofy.springstatemachine.domain.model.OrderEvent
import com.goofy.springstatemachine.domain.model.OrderState
import com.goofy.springstatemachine.domain.service.OrderStateMachineService
import com.goofy.springstatemachine.infrastructure.entity.OrderEntity
import com.goofy.springstatemachine.infrastructure.entity.OrderItemEntity
import com.goofy.springstatemachine.infrastructure.repository.OrderItemRepository
import com.goofy.springstatemachine.infrastructure.repository.OrderRepository
import com.goofy.springstatemachine.infrastructure.repository.OrderStateTransitionRepository
import com.goofy.springstatemachine.infrastructure.service.StateMachinePersistenceService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * 주문 서비스 테스트
 */
@ExtendWith(MockitoExtension::class)
class OrderServiceTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var orderStateTransitionRepository: OrderStateTransitionRepository

    @Mock
    private lateinit var orderStateMachineService: OrderStateMachineService

    @InjectMocks
    private lateinit var orderService: OrderService

    private val testOrderId = 1L
    private val testOrderNumber = "ORD-123456"
    private val testCustomerName = "홍길동"
    private val testCustomerEmail = "hong@example.com"

    @BeforeEach
    fun setUp() {
        // 기본 설정은 여기서 하지 않고, 각 테스트 메서드에서 필요한 설정을 직접 합니다.
    }

    /**
     * 주문 생성 테스트
     */
    @Test
    fun `주문 생성 성공 테스트`() {
        // Given
        val orderItem = Order.OrderItem(
            productId = 1L,
            productName = "테스트 상품",
            quantity = 2,
            price = BigDecimal("10000")
        )

        val order = Order(
            customerName = testCustomerName,
            customerEmail = testCustomerEmail,
            amount = BigDecimal("20000"),
            items = listOf(orderItem)
        )

        val savedOrderEntity = OrderEntity(
            id = testOrderId,
            orderNumber = testOrderNumber,
            customerName = testCustomerName,
            customerEmail = testCustomerEmail,
            amount = BigDecimal("20000"),
            state = OrderState.CREATED,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedOrderItemEntity = OrderItemEntity(
            id = 1L,
            orderId = testOrderId,
            productId = 1L,
            productName = "테스트 상품",
            quantity = 2,
            price = BigDecimal("10000")
        )

        // Mock 설정
        `when`(orderRepository.save(any())).thenReturn(savedOrderEntity)
        `when`(orderItemRepository.saveAll<OrderItemEntity>(anyList())).thenReturn(listOf(savedOrderItemEntity))
        // Using lenient() to avoid "unnecessary stubbing" errors
        lenient().`when`(orderItemRepository.findByOrderId(testOrderId)).thenReturn(listOf(savedOrderItemEntity))

        // OrderStateMachineService mock 설정
        `when`(orderStateMachineService.initializeStateMachine(anyString())).thenReturn(OrderState.CREATED)

        // When
        val result = orderService.createOrder(order)

        // Then
        assertNotNull(result)
        assertEquals(testOrderId, result.id)
        assertEquals(testOrderNumber, result.orderNumber)
        assertEquals(testCustomerName, result.customerName)
        assertEquals(testCustomerEmail, result.customerEmail)
        assertEquals(OrderState.CREATED, result.state)
        assertEquals(1, result.items.size)

        // 상태 머신 서비스 검증
        // 주문 번호는 동적으로 생성되므로 정확한 값을 검증할 수 없음
        verify(orderStateMachineService).initializeStateMachine(anyString())
    }

    /**
     * 주문 상태 변경 테스트
     */
    @Test
    fun `주문 상태 변경 성공 테스트`() {
        // Given
        val orderEntity = OrderEntity(
            id = testOrderId,
            orderNumber = testOrderNumber,
            customerName = testCustomerName,
            customerEmail = testCustomerEmail,
            amount = BigDecimal("20000"),
            state = OrderState.CREATED,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val updatedOrderEntity = orderEntity.copy(
            state = OrderState.PAYMENT_PENDING,
            updatedAt = LocalDateTime.now()
        )

        val orderItemEntity = OrderItemEntity(
            id = 1L,
            orderId = testOrderId,
            productId = 1L,
            productName = "테스트 상품",
            quantity = 2,
            price = BigDecimal("10000")
        )

        // Mock 설정
        `when`(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(orderEntity))
        `when`(orderRepository.save(any())).thenReturn(updatedOrderEntity)
        `when`(orderItemRepository.findByOrderId(testOrderId)).thenReturn(listOf(orderItemEntity))
        `when`(orderStateTransitionRepository.save(any())).thenReturn(mock())

        // OrderStateMachineService mock 설정
        `when`(orderStateMachineService.canFireEvent(OrderState.CREATED, OrderEvent.SUBMIT_PAYMENT)).thenReturn(true)
        `when`(orderStateMachineService.sendEvent(testOrderNumber, OrderEvent.SUBMIT_PAYMENT)).thenReturn(OrderState.PAYMENT_PENDING)

        // When
        val result = orderService.changeOrderState(testOrderNumber, OrderEvent.SUBMIT_PAYMENT)

        // Then
        assertNotNull(result)
        assertEquals(testOrderId, result.id)
        assertEquals(testOrderNumber, result.orderNumber)
        assertEquals(OrderState.PAYMENT_PENDING, result.state)

        // 상태 머신 서비스 검증
        verify(orderStateMachineService).canFireEvent(OrderState.CREATED, OrderEvent.SUBMIT_PAYMENT)
        verify(orderStateMachineService).sendEvent(testOrderNumber, OrderEvent.SUBMIT_PAYMENT)
    }

    /**
     * 주문 상태 변경 실패 테스트 - 잘못된 이벤트
     */
    @Test
    fun `주문 상태 변경 실패 테스트 - 잘못된 이벤트`() {
        // Given
        val orderEntity = OrderEntity(
            id = testOrderId,
            orderNumber = testOrderNumber,
            customerName = testCustomerName,
            customerEmail = testCustomerEmail,
            amount = BigDecimal("20000"),
            state = OrderState.DELIVERED, // 이미 배송 완료 상태
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // Mock 설정
        `when`(orderRepository.findByOrderNumber(testOrderNumber)).thenReturn(Optional.of(orderEntity))
        // 배송 완료 상태에서는 취소 이벤트를 발생시킬 수 없음
        `when`(orderStateMachineService.canFireEvent(OrderState.DELIVERED, OrderEvent.CANCEL)).thenReturn(false)
        // 이 테스트에서는 sendEvent가 호출되지 않아야 함

        // When & Then
        val exception = assertThrows(IllegalStateException::class.java) {
            orderService.changeOrderState(testOrderNumber, OrderEvent.CANCEL)
        }

        assertTrue(exception.message!!.contains("현재 상태(${OrderState.DELIVERED})에서 이벤트(${OrderEvent.CANCEL})를 발생시킬 수 없습니다."))

        // Verify that sendEvent is not called since an exception is thrown before that
        verifyNoMoreInteractions(orderStateMachineService)
    }
}
