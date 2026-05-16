package com.testing.service;

import com.testing.model.Order;
import com.testing.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository repository;
    private final EmailService emailService;

    public OrderService(OrderRepository repository, EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    public Order placeOrder(String customerEmail, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        Order order = new Order(customerEmail, amount);
        Order saved = repository.save(order);
        emailService.sendConfirmation(customerEmail, saved.getId());
        return saved;
    }

    public Order confirm(Long orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be confirmed");
        }
        order.setStatus(Order.OrderStatus.CONFIRMED);
        return repository.save(order);
    }

    public Order cancel(Long orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (order.getStatus() == Order.OrderStatus.DELIVERED) {
            throw new IllegalStateException("Delivered orders cannot be cancelled");
        }
        order.setStatus(Order.OrderStatus.CANCELLED);
        return repository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getByCustomer(String email) {
        return repository.findByCustomerEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalSpend(String email) {
        BigDecimal total = repository.sumAmountByCustomer(email);
        return total != null ? total : BigDecimal.ZERO;
    }
}
