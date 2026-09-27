package com.codewithmosh.store.payment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CheckoutRequest {
    @NotNull(message = "Cart id is required")
    private UUID cartId;
}
