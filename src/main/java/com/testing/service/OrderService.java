package com.testing.service;

import com.testing.model.Order;
import com.testing.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * SİPARİŞ SERVİSİ — Test Edilebilir Kod Yazımı Örneği
 * ======================================================
 *
 * Bu sınıf kasıtlı olarak test edilebilirlik için tasarlanmıştır.
 * OrderServiceUnitTest ve OrderServiceIntegrationTest bu servisi test eder.
 *
 * Test edilebilirliği sağlayan tasarım kararları:
 *
 *   1. Constructor Injection:
 *      OrderService(repository, emailService) → Test sırasında mock geçilebilir.
 *      @Autowired field injection: final kullanılamaz, Mockito @InjectMocks zor.
 *
 *   2. Bağımlılıklar interface üzerinden:
 *      OrderRepository → JpaRepository interface → @DataJpaTest ile test edilir.
 *      EmailService → interface → @Mock ile mock edilir.
 *
 *   3. Saf iş mantığı:
 *      HTTP bilgisi yok (HttpServletRequest vb.) → unit test kolay.
 *      Static method çağrısı yok → mock edilebilir.
 *
 *   4. İstisna davranışları açık:
 *      IllegalArgumentException → @Test assertThatThrownBy ile doğrulanır.
 *      IllegalStateException → durum geçişi hataları.
 *
 * @Transactional neden sınıf seviyesinde?
 *   Tüm public metodlar transaction içinde çalışır.
 *   Exception → ROLLBACK (DB tutarlı kalır).
 *   Bazı metodlar @Transactional(readOnly=true) ile override edilir.
 */
@Service
@Transactional
public class OrderService {

    // Interface bağımlılığı — test sırasında Mockito ile mock edilir
    private final OrderRepository repository;
    private final EmailService emailService;

    /**
     * Constructor injection: @Mock repository ve emailService geçilebilir.
     * @InjectMocks OrderService service → Mockito bu constructor'ı kullanır.
     */
    public OrderService(OrderRepository repository, EmailService emailService) {
        this.repository   = repository;
        this.emailService = emailService;
    }

    /**
     * Sipariş oluşturur ve onay e-postası gönderir.
     *
     * İş kuralları (test senaryoları):
     *   - amount <= 0 → IllegalArgumentException (test: placeOrder_zeroAmount_throws)
     *   - Geçerli amount → repository.save() + emailService.sendConfirmation() (test: happy path)
     *
     * Neden validation save()'den önce yapılır?
     *   Early fail: Geçersiz verilerle DB'ye gitmek gereksiz.
     *   Test: verifyNoInteractions(repository) ile "repository çağrılmadı" doğrulanır.
     *
     * Neden e-posta gönderimi save()'den sonra?
     *   save() başarısız olursa e-posta gönderilmemeli (sipariş olmayan e-posta).
     *   @Transactional: save() hatası → rollback → emailService.sendConfirmation() çağrılmaz.
     *   Dikkat: emailService void döner — when().thenReturn() gerekmez (otomatik no-op).
     */
    public Order placeOrder(String customerEmail, BigDecimal amount) {
        // Validation: amount pozitif olmalı (iş kuralı)
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        // Yeni sipariş: başlangıç durumu PENDING (Order constructor'da set edilir)
        Order order = new Order(customerEmail, amount);
        Order saved = repository.save(order); // ID atanır (DB auto-generate)

        // E-posta: save() başarılıysa — ID artık mevcut
        emailService.sendConfirmation(customerEmail, saved.getId());

        return saved;
    }

    /**
     * Siparişi CONFIRMED durumuna geçirir.
     *
     * Durum geçiş kuralı (State Machine):
     *   PENDING → CONFIRMED: Geçerli
     *   CONFIRMED/CANCELLED/DELIVERED → CONFIRMED: IllegalStateException
     *
     * Test senaryoları:
     *   - PENDING sipariş → confirm → CONFIRMED (happy path)
     *   - Sipariş yok → IllegalArgumentException ("99" içermeli)
     *   - Zaten CONFIRMED → IllegalStateException
     *
     * thenAnswer(inv -> inv.getArgument(0)):
     *   Mockito: repository.save(order) çağrılınca aynı nesneyi döner.
     *   Bu sayede confirm()'in döndürdüğü order'da CONFIRMED durumu görülür.
     */
    public Order confirm(Long orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // Durum kontrolü: Yalnızca PENDING → CONFIRMED geçişine izin ver
        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be confirmed");
        }

        order.setStatus(Order.OrderStatus.CONFIRMED);
        // JPA dirty checking: status değişti → UPDATE otomatik atılır
        return repository.save(order);
    }

    /**
     * Siparişi CANCELLED durumuna geçirir.
     *
     * Durum geçiş kuralı:
     *   DELIVERED → CANCELLED: İzin verilmez (IllegalStateException)
     *   Diğer durumlar → CANCELLED: Geçerli
     *
     * Test senaryosu:
     *   cancel_deliveredOrder_throws: DELIVERED sipariş iptal edilmeye çalışılınca
     *   assertThatThrownBy → isInstanceOf(IllegalStateException.class)
     *   .hasMessageContaining("Delivered") doğrulanır.
     */
    public Order cancel(Long orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // DELIVERED sipariş iptal edilemez
        if (order.getStatus() == Order.OrderStatus.DELIVERED) {
            throw new IllegalStateException("Delivered orders cannot be cancelled");
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        return repository.save(order);
    }

    /**
     * Müşterinin siparişlerini getirir.
     *
     * @Transactional(readOnly=true): Yazma işlemi yok — Hibernate optimizasyonu.
     *   Test: BDD stili given(repository.findByCustomerEmail(x)).willReturn(list)
     *   then(repository).should().findByCustomerEmail(x)
     */
    @Transactional(readOnly = true)
    public List<Order> getByCustomer(String email) {
        return repository.findByCustomerEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * Müşterinin toplam harcamasını hesaplar.
     *
     * sumAmountByCustomer(): DB'de SUM aggregate query.
     *   Yeni müşteri veya hiç sipariş yoksa: NULL döner (SUM of empty set = NULL).
     *   null koruması: total != null ? total : BigDecimal.ZERO
     *
     * Test senaryosu:
     *   getTotalSpend_nullFromRepo_returnsZero:
     *   when(repository.sumAmountByCustomer(x)).thenReturn(null)
     *   → service.getTotalSpend(x) == BigDecimal.ZERO beklenir.
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalSpend(String email) {
        BigDecimal total = repository.sumAmountByCustomer(email);
        // NULL koruması: Yeni müşteri → 0 döndür (NPE riski önle)
        return total != null ? total : BigDecimal.ZERO;
    }
}
