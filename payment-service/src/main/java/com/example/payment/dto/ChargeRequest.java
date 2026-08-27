package com.example.payment.dto;

public record ChargeRequest(String orderId, int quantity) {
}
