package com.testing.integration;

import com.testing.model.Order;
import com.testing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import com.testing.service.EmailService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Full Spring context + H2 (in-memory).
 * EmailService mock'lanır (external dependency).
 * Gerçek service + repository akışı doğrulanır.
 */
@SpringBootTest
@Transactional
class OrderServiceIntegrationTest {

    @Autowired
    OrderService service;

    @MockBean
    EmailService emailService;

    @Test
    void fullOrderLifecycle() {
        doNothing().when(emailService).sendConfirmation(anyString(), any());

        // place
        Order order = service.placeOrder("lifecycle@test.com", new BigDecimal("500"));
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PENDING);

        // confirm
        Order confirmed = service.confirm(order.getId());
        assertThat(confirmed.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);

        // cancel confirmed
        Order cancelled = service.cancel(confirmed.getId());
        assertThat(cancelled.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
    }

    @Test
    void getTotalSpend_multipleOrders() {
        doNothing().when(emailService).sendConfirmation(anyString(), any());

        service.placeOrder("spend@test.com", new BigDecimal("100"));
        service.placeOrder("spend@test.com", new BigDecimal("250"));
        Order toCancel = service.placeOrder("spend@test.com", new BigDecimal("999"));
        service.cancel(toCancel.getId()); // should be excluded

        BigDecimal total = service.getTotalSpend("spend@test.com");
        assertThat(total).isEqualByComparingTo("350");
    }

    @Test
    void placeOrder_negativeAmount_throws() {
        assertThatThrownBy(() -> service.placeOrder("bad@test.com", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_deliveredOrder_throws() {
        doNothing().when(emailService).sendConfirmation(anyString(), any());
        Order order = service.placeOrder("del@test.com", new BigDecimal("300"));
        order.setStatus(Order.OrderStatus.DELIVERED);

        assertThatThrownBy(() -> service.cancel(order.getId()))
                .isInstanceOf(IllegalStateException.class);
    }
}
