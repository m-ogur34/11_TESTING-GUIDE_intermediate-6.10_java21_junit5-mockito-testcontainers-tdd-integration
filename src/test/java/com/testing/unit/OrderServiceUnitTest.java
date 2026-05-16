package com.testing.unit;

import com.testing.model.Order;
import com.testing.repository.OrderRepository;
import com.testing.service.EmailService;
import com.testing.service.OrderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test — gerçek Spring context yok, hızlı, izole.
 * Mockito ile bağımlılıklar sahte.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    @Mock
    OrderRepository repository;

    @Mock
    EmailService emailService;

    @InjectMocks
    OrderService service;

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder: valid amount → saves and sends email")
    void placeOrder_validAmount_savesAndSendsEmail() {
        Order saved = new Order("user@test.com", new BigDecimal("100.00"));
        when(repository.save(any(Order.class))).thenReturn(saved);

        Order result = service.placeOrder("user@test.com", new BigDecimal("100.00"));

        assertThat(result.getCustomerEmail()).isEqualTo("user@test.com");
        verify(repository, times(1)).save(any(Order.class));
        verify(emailService, times(1)).sendConfirmation(eq("user@test.com"), any());
    }

    @Test
    @DisplayName("confirm: PENDING → CONFIRMED")
    void confirm_pendingOrder_changesStatusToConfirmed() {
        Order pending = new Order("a@b.com", new BigDecimal("50"));
        pending.setStatus(Order.OrderStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order confirmed = service.confirm(1L);

        assertThat(confirmed.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("cancel: DELIVERED order → throws IllegalStateException")
    void cancel_deliveredOrder_throws() {
        Order delivered = new Order("a@b.com", new BigDecimal("50"));
        delivered.setStatus(Order.OrderStatus.DELIVERED);
        when(repository.findById(1L)).thenReturn(Optional.of(delivered));

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Delivered");
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder: zero amount → throws IllegalArgumentException")
    void placeOrder_zeroAmount_throws() {
        assertThatThrownBy(() -> service.placeOrder("a@b.com", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        verifyNoInteractions(repository);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("confirm: order not found → throws")
    void confirm_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("confirm: already CONFIRMED → throws")
    void confirm_alreadyConfirmed_throws() {
        Order order = new Order("a@b.com", new BigDecimal("50"));
        order.setStatus(Order.OrderStatus.CONFIRMED);
        when(repository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirm(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getTotalSpend: null from repo → returns ZERO")
    void getTotalSpend_nullFromRepo_returnsZero() {
        when(repository.sumAmountByCustomer("new@user.com")).thenReturn(null);

        BigDecimal total = service.getTotalSpend("new@user.com");

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Argument Captor ───────────────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder: captures saved order with PENDING status")
    void placeOrder_capturesSavedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.placeOrder("capture@test.com", new BigDecimal("250"));

        Order captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
        assertThat(captured.getCustomerEmail()).isEqualTo("capture@test.com");
        assertThat(captured.getAmount()).isEqualByComparingTo("250");
    }

    // ── BDD style (given/when/then) ───────────────────────────────────────────

    @Test
    @DisplayName("getByCustomer: returns filtered list")
    void getByCustomer_returnsFilteredList() {
        // given
        List<Order> orders = List.of(
                new Order("bob@test.com", new BigDecimal("100")),
                new Order("bob@test.com", new BigDecimal("200"))
        );
        given(repository.findByCustomerEmail("bob@test.com")).willReturn(orders);

        // when
        List<Order> result = service.getByCustomer("bob@test.com");

        // then
        assertThat(result).hasSize(2);
        then(repository).should().findByCustomerEmail("bob@test.com");
    }
}
