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
 * Unit Test — Test Piramidinin Temeli
 *
 * Spring context YOK — sadece JVM + Mockito.
 * Hız: millisaniyeler. İzolasyon: tam.
 *
 * Anotasyonlar:
 *   @ExtendWith(MockitoExtension.class) → Mockito'yu JUnit 5'e entegre et
 *   @Mock    → Gerçek sınıf yerine sahte nesne (hiçbir işlem yapmaz, değer döndürür)
 *   @InjectMocks → Test edilen sınıfı oluştur, @Mock'ları constructor/field injection ile ver
 *   @MockBean  → @Mock değil! Spring context içine mock enjekte eder (@SpringBootTest'te)
 *
 * Mockito temel API:
 *   when(mock.method()).thenReturn(value)     → davranış tanımla
 *   when(mock.method()).thenThrow(exception)  → exception fırlat
 *   verify(mock, times(1)).method(arg)        → metod çağrıldı mı?
 *   verifyNoInteractions(mock)                → hiç çağrılmadı mı?
 *   ArgumentCaptor<X>.capture()              → geçirilen argümanı yakala
 *
 * Test isimlendirme: methodName_condition_expectedBehavior
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    // @Mock: repository.save() çağrılınca null döner (varsayılan)
    // when(repository.save(any())).thenReturn(x) ile özelleştirilir
    @Mock
    OrderRepository repository;

    @Mock
    EmailService emailService;

    // @InjectMocks: new OrderService(repository, emailService) çağrısına eşdeğer
    // Spring olmadan bağımlılıkları inject eder
    @InjectMocks
    OrderService service;

    // ── Happy path (Başarılı senaryo) ────────────────────────────────────────

    @Test
    @DisplayName("placeOrder: valid amount → saves and sends email")
    void placeOrder_validAmount_savesAndSendsEmail() {
        // ARRANGE: Mock'ların davranışını tanımla
        Order saved = new Order("user@test.com", new BigDecimal("100.00"));
        when(repository.save(any(Order.class))).thenReturn(saved);
        // emailService void dönüyor — when().thenReturn() gerekmez, otomatik no-op

        // ACT: Test edilen metodu çağır
        Order result = service.placeOrder("user@test.com", new BigDecimal("100.00"));

        // ASSERT: Sonucu doğrula
        assertThat(result.getCustomerEmail()).isEqualTo("user@test.com");

        // VERIFY: Mock'ların beklenen metodları çağrıldı mı?
        verify(repository, times(1)).save(any(Order.class));  // save bir kez çağrıldı
        verify(emailService, times(1)).sendConfirmation(eq("user@test.com"), any()); // email gönderildi
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

    // ── Edge cases (Sınır durumları) ──────────────────────────────────────────

    @Test
    @DisplayName("placeOrder: zero amount → throws IllegalArgumentException")
    void placeOrder_zeroAmount_throws() {
        // assertThatThrownBy: exception fırlatıldığını doğrula (JUnit assertThrows alternatifi)
        // .isInstanceOf: exception tipi
        // .hasMessageContaining: hata mesajı içeriği
        assertThatThrownBy(() -> service.placeOrder("a@b.com", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        // Kritik: Exception öncesinde repository çağrılmamalı (validation önce)
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

    /**
     * ArgumentCaptor: Mock'a geçirilen argümanı yakala ve inspect et.
     * Kullanım: "repository.save() çağrıldı mı?" yerine "ne ile çağrıldı?"
     *
     * thenAnswer: save() çağrılınca aynı argümanı döndür (identity function)
     * → ID atanmadan önce nesnenin PENDING durumunda olduğunu doğrula
     */
    @Test
    @DisplayName("placeOrder: captures saved order with PENDING status")
    void placeOrder_capturesSavedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.placeOrder("capture@test.com", new BigDecimal("250"));

        Order captured = captor.getValue();  // repository.save()'e geçen Order nesnesi
        assertThat(captured.getStatus()).isEqualTo(Order.OrderStatus.PENDING);   // başlangıç durumu
        assertThat(captured.getCustomerEmail()).isEqualTo("capture@test.com");
        assertThat(captured.getAmount()).isEqualByComparingTo("250");
    }

    // ── BDD style (given/when/then) ───────────────────────────────────────────

    /**
     * BDD (Behavior Driven Development) stili:
     *   given() = when() (BDDMockito aliası — daha okunabilir)
     *   then().should() = verify() (BDDMockito aliası)
     *
     * Tercih: İş analistleri ve QA'ların test senaryolarını okuyabilmesi için.
     * "given a customer has orders, when we query, then we get the list"
     */
    @Test
    @DisplayName("getByCustomer: returns filtered list")
    void getByCustomer_returnsFilteredList() {
        // given — ön koşul (BDDMockito.given = Mockito.when)
        List<Order> orders = List.of(
                new Order("bob@test.com", new BigDecimal("100")),
                new Order("bob@test.com", new BigDecimal("200"))
        );
        given(repository.findByCustomerEmail("bob@test.com")).willReturn(orders);

        // when — test edilen aksiyon
        List<Order> result = service.getByCustomer("bob@test.com");

        // then — doğrulama
        assertThat(result).hasSize(2);
        then(repository).should().findByCustomerEmail("bob@test.com"); // BDDMockito verify
    }
}
