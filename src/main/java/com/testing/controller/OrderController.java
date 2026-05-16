package com.testing.controller;

import com.testing.model.Order;
import com.testing.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Order> place(@RequestParam String email, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(service.placeOrder(email, amount));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<Order> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(service.confirm(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Order> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    @GetMapping("/customer/{email}")
    public ResponseEntity<List<Order>> byCustomer(@PathVariable String email) {
        return ResponseEntity.ok(service.getByCustomer(email));
    }

    @GetMapping("/customer/{email}/total")
    public ResponseEntity<Map<String, Object>> totalSpend(@PathVariable String email) {
        return ResponseEntity.ok(Map.of("email", email, "total", service.getTotalSpend(email)));
    }
}
