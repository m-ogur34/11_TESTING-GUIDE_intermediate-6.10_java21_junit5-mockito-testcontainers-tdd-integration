package com.testing.integration;

import com.testing.model.Order;
import com.testing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest — sadece web katmanı (Controller, Filter, Advice).
 * Service mock'lanır → gerçek iş mantığı çalışmaz.
 * MockMvc ile HTTP request/response doğrulama.
 */
@WebMvcTest(com.testing.controller.OrderController.class)
class OrderControllerMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OrderService service;

    @Test
    void placeOrder_returns200() throws Exception {
        Order order = new Order("user@test.com", new BigDecimal("100"));
        when(service.placeOrder(anyString(), any())).thenReturn(order);

        mockMvc.perform(post("/api/orders")
                        .param("email", "user@test.com")
                        .param("amount", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerEmail").value("user@test.com"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void byCustomer_returnsList() throws Exception {
        when(service.getByCustomer("alice@test.com")).thenReturn(List.of(
                new Order("alice@test.com", new BigDecimal("100")),
                new Order("alice@test.com", new BigDecimal("200"))
        ));

        mockMvc.perform(get("/api/orders/customer/alice@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void confirm_delegatesToService() throws Exception {
        Order confirmed = new Order("a@b.com", new BigDecimal("50"));
        confirmed.setStatus(Order.OrderStatus.CONFIRMED);
        when(service.confirm(1L)).thenReturn(confirmed);

        mockMvc.perform(patch("/api/orders/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirm_serviceThrows_returns500() throws Exception {
        when(service.confirm(99L)).thenThrow(new IllegalArgumentException("Order not found: 99"));

        mockMvc.perform(patch("/api/orders/99/confirm"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void totalSpend_returnsMap() throws Exception {
        when(service.getTotalSpend("bob@test.com")).thenReturn(new BigDecimal("750.00"));

        mockMvc.perform(get("/api/orders/customer/bob@test.com/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@test.com"))
                .andExpect(jsonPath("$.total").value(750.0));
    }
}
