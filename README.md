# 11 — Testing Guide

**Difficulty:** Intermediate (6/10) · **Java 21** · **Spring Boot 3.2.5**

JUnit 5, Mockito, MockMvc ve TestContainers ile kapsamlı test stratejisi.

---

## Test Piramidi

```
        ┌──────────────┐
        │   E2E Tests  │  ← az, yavaş, tam sistem
        ├──────────────┤
        │  Integration │  ← orta, gerçek DB/HTTP katmanı
        ├──────────────┤
        │  Unit Tests  │  ← çok, hızlı, izole
        └──────────────┘
```

---

## Test Türleri ve Anotasyonlar

### 1. Unit Test — `@ExtendWith(MockitoExtension.class)`
```java
@Mock OrderRepository repository;     // sahte nesne
@Mock EmailService emailService;
@InjectMocks OrderService service;    // mock'ları inject et

// Stubbing
when(repository.findById(1L)).thenReturn(Optional.of(order));

// Verify
verify(repository, times(1)).save(any());
verifyNoInteractions(emailService);

// Argument Captor
ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
verify(repository).save(captor.capture());
assertThat(captor.getValue().getStatus()).isEqualTo(PENDING);

// BDD style
given(repository.findByCustomerEmail("bob")).willReturn(orders);
then(repository).should().findByCustomerEmail("bob");
```

### 2. Repository Test — `@DataJpaTest`
```java
// Sadece JPA katmanı — H2 in-memory
// Service, Controller yüklenmez → hızlı
@DataJpaTest
class OrderRepositoryIntegrationTest {
    @Autowired OrderRepository repository;  // gerçek bean
}
```

### 3. Web Layer Test — `@WebMvcTest`
```java
// Sadece Controller, Filter — gerçek HTTP yok, MockMvc
@WebMvcTest(OrderController.class)
class OrderControllerMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean OrderService service;  // service mock'lanır

    mockMvc.perform(post("/api/orders").param("email", "x").param("amount", "100"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("PENDING"));
}
```

### 4. Integration Test — `@SpringBootTest`
```java
// Tam Spring context + H2
// External dependency (Email) mock'lanır
@SpringBootTest
@Transactional  // her test sonrası rollback
class OrderServiceIntegrationTest {
    @Autowired OrderService service;
    @MockBean  EmailService emailService;
}
```

### 5. E2E Test — `@SpringBootTest(RANDOM_PORT)`
```java
// Gerçek HTTP server, gerçek request
@SpringBootTest(webEnvironment = RANDOM_PORT)
class OrderE2ETest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;

    restTemplate.postForEntity(url + "?email=e&amount=100", null, Order.class);
}
```

---

## JUnit 5 Özellikleri

```java
@Test
@DisplayName("açıklayıcı test adı")
@Disabled("geçici devre dışı")
@Tag("slow")

@BeforeEach  void setUp() {}
@AfterEach   void tearDown() {}
@BeforeAll   static void initAll() {}
@AfterAll    static void cleanAll() {}

// Parametrik test
@ParameterizedTest
@ValueSource(ints = {0, -1, Integer.MIN_VALUE})
void negativeAmount_throws(int amount) { ... }

@MethodSource("validOrders")
void validOrders(String email, BigDecimal amount) { ... }

// Exception testi
assertThatThrownBy(() -> service.placeOrder("a", BigDecimal.ZERO))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("positive");

// Grouped assertions
assertSoftly(soft -> {
    soft.assertThat(order.getStatus()).isEqualTo(PENDING);
    soft.assertThat(order.getAmount()).isEqualByComparingTo("100");
});
```

---

## AssertJ

```java
assertThat(list).hasSize(2).contains(item).doesNotContain(other);
assertThat(optional).isPresent().get().extracting(Order::getStatus).isEqualTo(PENDING);
assertThat(bigDecimal).isEqualByComparingTo("100.00");
assertThat(string).isNotBlank().startsWith("user@");
```

---

## TDD Cycle

```
RED   → Önce testi yaz, çalıştır, başarısız olsun
GREEN → En basit kodu yaz, testi geçir
REFACTOR → Kodu temizle, testler hâlâ yeşil mi?
```

---

## Mülakat Soruları

**Q: Unit vs Integration test farkı?**
A: Unit test: tek sınıf, mock bağımlılıklar, ms hızında. Integration test: birden fazla bileşen gerçek DB/context ile, saniyeler.

**Q: `@DataJpaTest` ne yükler?**
A: Sadece JPA repository bean'leri, embedded H2, EntityManager. Service/Controller yüklenmez.

**Q: `@WebMvcTest` vs `@SpringBootTest` farkı?**
A: @WebMvcTest sadece web katmanı (Controller) — service mock gerekir. @SpringBootTest tam context — E2E veya service entegrasyonu için.

**Q: `@MockBean` vs `@Mock` farkı?**
A: `@Mock` Mockito extension ile Spring context yok. `@MockBean` Spring context içine mock inject eder — @SpringBootTest ile kullanılır.

**Q: `@Transactional` test'te ne işe yarar?**
A: Her test method sonunda otomatik rollback — DB state temiz kalır, test izolasyonu sağlanır.

**Q: ArgumentCaptor nedir?**
A: Mock'a iletilen argümanı yakalamak için. `verify(repo).save(captor.capture())` → `captor.getValue()` ile kontrol.

---

## Çalıştırma

```bash
# Tüm testler
mvn test

# Sadece unit testler
mvn test -Dtest="*UnitTest"

# Coverage (JaCoCo varsa)
mvn verify
```
