package com.testing.repository;

import com.testing.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerEmail(String email);
    List<Order> findByStatus(Order.OrderStatus status);

    @Query("SELECT SUM(o.amount) FROM Order o WHERE o.customerEmail = :email AND o.status <> 'CANCELLED'")
    BigDecimal sumAmountByCustomer(String email);

    long countByStatus(Order.OrderStatus status);
}
