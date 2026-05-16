package com.testing.service;

import org.springframework.stereotype.Service;

@Service
public class EmailService {
    public void sendConfirmation(String email, Long orderId) {
        // production: SMTP / SES call
        System.out.printf("Email sent to %s for order %d%n", email, orderId);
    }
}
