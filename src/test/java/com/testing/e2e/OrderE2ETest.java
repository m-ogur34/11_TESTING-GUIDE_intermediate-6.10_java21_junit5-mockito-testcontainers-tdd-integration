package com.testing.e2e;

import com.testing.model.Order;
import com.testing.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * E2E (End-to-End) Test — gerçek HTTP, gerçek server, H2 in-memory DB.
 * @SpringBootTest(webEnvironment=RANDOM_PORT): tam uygulama ayağa kalkar.
 * TestRestTemplate ile gerçek HTTP istek gönderilir.
 *
 * Fark:
 *   Unit Test    → sınıf izole, mock her şey, ms hızında
 *   @DataJpaTest → sadece JPA, H2, saniyeler
 *   @WebMvcTest  → sadece web katmanı, mock service
 *   @SpringBootTest(full) → tüm context + real HTTP
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderE2ETest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @MockBean
    EmailService emailService;

    String baseUrl() { return "http://localhost:" + port + "/api/orders"; }

    @Test
    void placeAndConfirmOrder_fullHttp() {
        doNothing().when(emailService).sendConfirmation(anyString(), any());

        // POST /api/orders?email=e2e@test.com&amount=999
        ResponseEntity<Order> placed = restTemplate.postForEntity(
                baseUrl() + "?email=e2e@test.com&amount=999",
                null, Order.class);

        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(placed.getBody()).isNotNull();
        assertThat(placed.getBody().getStatus()).isEqualTo(Order.OrderStatus.PENDING);

        Long orderId = placed.getBody().getId();

        // PATCH /api/orders/{id}/confirm
        ResponseEntity<Order> confirmed = restTemplate.exchange(
                baseUrl() + "/" + orderId + "/confirm",
                HttpMethod.PATCH, null, Order.class);

        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
    }

    @Test
    void byCustomer_returnsOrders() {
        doNothing().when(emailService).sendConfirmation(anyString(), any());

        restTemplate.postForEntity(baseUrl() + "?email=multi@test.com&amount=100", null, Order.class);
        restTemplate.postForEntity(baseUrl() + "?email=multi@test.com&amount=200", null, Order.class);

        ResponseEntity<Order[]> response = restTemplate.getForEntity(
                baseUrl() + "/customer/multi@test.com", Order[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(2);
    }
}
