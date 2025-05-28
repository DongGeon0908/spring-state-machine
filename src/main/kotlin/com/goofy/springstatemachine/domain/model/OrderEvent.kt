package com.goofy.springstatemachine.domain.model

/**
 * 주문 이벤트를 나타내는 열거형
 * 
 * 상태 머신에서 '이벤트(Event)'란?
 * - 이벤트는 상태 머신의 상태 전이를 트리거하는 행동이나 발생을 나타냅니다.
 * - 이벤트가 발생하면 현재 상태에서 다른 상태로 전이할 수 있습니다.
 * - 모든 이벤트가 모든 상태에서 유효한 것은 아니며, 특정 상태에서만 특정 이벤트가 처리될 수 있습니다.
 * 
 * 주문 처리 흐름에서의 이벤트:
 * 1. SUBMIT_PAYMENT - 결제 제출: 결제 정보를 제출하는 이벤트입니다. CREATED 상태에서 발생합니다.
 *    - 결과: CREATED -> PAYMENT_PENDING 상태로 전이
 * 
 * 2. PAYMENT_SUCCEEDED - 결제 성공: 결제가 성공적으로 처리된 이벤트입니다. PAYMENT_PENDING 상태에서 발생합니다.
 *    - 결과: PAYMENT_PENDING -> PAID 상태로 전이
 * 
 * 3. PAYMENT_FAILED - 결제 실패: 결제가 실패한 이벤트입니다. PAYMENT_PENDING 상태에서 발생합니다.
 *    - 결과: PAYMENT_PENDING -> CREATED 상태로 전이 (재시도 가능)
 * 
 * 4. PREPARE - 상품 준비 시작: 배송을 위한 상품 준비를 시작하는 이벤트입니다. PAID 상태에서 발생합니다.
 *    - 결과: PAID -> PREPARING 상태로 전이
 * 
 * 5. SHIP - 배송 시작: 준비된 상품의 배송을 시작하는 이벤트입니다. PREPARING 상태에서 발생합니다.
 *    - 결과: PREPARING -> SHIPPED 상태로 전이
 * 
 * 6. DELIVER - 배송 완료 처리: 배송 중인 주문이 고객에게 배달되었음을 처리하는 이벤트입니다. SHIPPED 상태에서 발생합니다.
 *    - 결과: SHIPPED -> DELIVERED 상태로 전이
 * 
 * 7. CANCEL - 주문 취소 요청: 주문을 취소하는 이벤트입니다. CREATED, PAYMENT_PENDING, PAID 상태에서 발생 가능합니다.
 *    - 결과: 해당 상태 -> CANCELLED 상태로 전이
 * 
 * 8. REFUND - 환불 처리: 취소된 주문에 대해 환불을 처리하는 이벤트입니다. CANCELLED 상태에서 발생합니다.
 *    - 결과: CANCELLED -> REFUNDED 상태로 전이
 * 
 * 9. SELECT_PAYMENT_METHOD - 결제 방법 선택: 결제 방법을 선택하는 이벤트입니다. CREATED 상태에서 발생합니다.
 *    - 결과: CREATED -> PAYMENT_CHOICE 상태로 전이
 * 
 * 10. CREDIT_CARD - 신용카드 결제 선택: 신용카드로 결제하는 이벤트입니다. PAYMENT_CHOICE 상태에서 발생합니다.
 *    - 결과: PAYMENT_CHOICE -> PAYMENT_PENDING 상태로 전이
 * 
 * 11. BANK_TRANSFER - 계좌이체 결제 선택: 계좌이체로 결제하는 이벤트입니다. PAYMENT_CHOICE 상태에서 발생합니다.
 *    - 결과: PAYMENT_CHOICE -> PAYMENT_PENDING 상태로 전이
 * 
 * 12. CHECK_SHIPPING - 배송 조건 확인: 배송 조건을 확인하는 이벤트입니다. PAID 상태에서 발생합니다.
 *    - 결과: PAID -> SHIPPING_JUNCTION 상태로 전이
 * 
 * 13. EXPEDITE - 빠른 배송 선택: 빠른 배송을 선택하는 이벤트입니다. SHIPPING_JUNCTION 상태에서 발생합니다.
 *    - 결과: SHIPPING_JUNCTION -> PREPARING 상태로 전이
 * 
 * 14. STANDARD - 일반 배송 선택: 일반 배송을 선택하는 이벤트입니다. SHIPPING_JUNCTION 상태에서 발생합니다.
 *    - 결과: SHIPPING_JUNCTION -> PREPARING 상태로 전이
 * 
 * 이벤트는 사용자 액션(예: 버튼 클릭)이나 시스템 이벤트(예: 자동 처리)에 의해 트리거될 수 있습니다.
 * 각 이벤트는 특정 상태에서만 유효하며, 유효하지 않은 상태에서 이벤트가 발생하면 상태 전이가 일어나지 않습니다.
 */
enum class OrderEvent {
    // 기본 이벤트
    SUBMIT_PAYMENT,      // 결제 제출 - CREATED 상태에서 PAYMENT_PENDING 상태로 전이
    PAYMENT_SUCCEEDED,   // 결제 성공 - PAYMENT_PENDING 상태에서 PAID 상태로 전이
    PAYMENT_FAILED,      // 결제 실패 - PAYMENT_PENDING 상태에서 CREATED 상태로 전이
    PREPARE,             // 상품 준비 시작 - PAID 상태에서 PREPARING 상태로 전이
    SHIP,                // 배송 시작 - PREPARING 상태에서 SHIPPED 상태로 전이
    DELIVER,             // 배송 완료 처리 - SHIPPED 상태에서 DELIVERED 상태로 전이
    CANCEL,              // 주문 취소 요청 - 여러 상태에서 CANCELLED 상태로 전이
    REFUND,              // 환불 처리 - CANCELLED 상태에서 REFUNDED 상태로 전이

    // 선택 상태 이벤트
    SELECT_PAYMENT_METHOD, // 결제 방법 선택 - CREATED 상태에서 PAYMENT_CHOICE 상태로 전이
    CREDIT_CARD,         // 신용카드 결제 선택 - PAYMENT_CHOICE 상태에서 PAYMENT_PENDING 상태로 전이
    BANK_TRANSFER,       // 계좌이체 결제 선택 - PAYMENT_CHOICE 상태에서 PAYMENT_PENDING 상태로 전이

    // 정션 상태 이벤트
    CHECK_SHIPPING,      // 배송 조건 확인 - PAID 상태에서 SHIPPING_JUNCTION 상태로 전이
    EXPEDITE,            // 빠른 배송 선택 - SHIPPING_JUNCTION 상태에서 PREPARING 상태로 전이
    STANDARD             // 일반 배송 선택 - SHIPPING_JUNCTION 상태에서 PREPARING 상태로 전이
}
