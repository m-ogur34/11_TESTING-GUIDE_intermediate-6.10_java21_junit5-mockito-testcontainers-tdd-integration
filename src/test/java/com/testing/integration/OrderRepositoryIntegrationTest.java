package com.testing.integration;

import com.testing.model.Order;
import com.testing.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Test — sadece JPA katmanı.
 * @DataJpaTest: in-memory H2, entity scan, repository beans.
 * Spring MVC, services, beans yüklenmez → hızlı.
 */
@DataJpaTest
class OrderRepositoryIntegrationTest {

    @Autowired
    OrderRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void saveAndFindById() {
        Order order = repository.save(new Order("a@b.com", new BigDecimal("150")));
        assertThat(order.getId()).isNotNull();
        assertThat(repository.findById(order.getId())).isPresent();
    }

    @Test
    void findByCustomerEmail() {
        repository.save(new Order("alice@test.com", new BigDecimal("100")));
        repository.save(new Order("alice@test.com", new BigDecimal("200")));
        repository.save(new Order("bob@test.com", new BigDecimal("300")));

        List<Order> aliceOrders = repository.findByCustomerEmail("alice@test.com");
        assertThat(aliceOrders).hasSize(2);
    }

    @Test
    void findByStatus_defaultPending() {
        repository.save(new Order("a@b.com", new BigDecimal("100")));
        repository.save(new Order("c@d.com", new BigDecimal("200")));

        List<Order> pending = repository.findByStatus(Order.OrderStatus.PENDING);
        assertThat(pending).hasSize(2);
    }

    @Test
    void sumAmountByCustomer_excludesCancelled() {
        Order o1 = repository.save(new Order("x@y.com", new BigDecimal("100")));
        Order o2 = new Order("x@y.com", new BigDecimal("50"));
        o2.setStatus(Order.OrderStatus.CANCELLED);
        repository.save(o2);

        BigDecimal total = repository.sumAmountByCustomer("x@y.com");
        assertThat(total).isEqualByComparingTo("100");
    }

    @Test
    void sumAmountByCustomer_noOrders_returnsNull() {
        BigDecimal total = repository.sumAmountByCustomer("nobody@test.com");
        assertThat(total).isNull();
    }

    @Test
    void countByStatus() {
        repository.save(new Order("a@b.com", new BigDecimal("100")));
        repository.save(new Order("c@d.com", new BigDecimal("200")));
        assertThat(repository.countByStatus(Order.OrderStatus.PENDING)).isEqualTo(2);
        assertThat(repository.countByStatus(Order.OrderStatus.CONFIRMED)).isEqualTo(0);
    }
}
