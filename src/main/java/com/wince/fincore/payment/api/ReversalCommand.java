package com.wince.fincore.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReversalCommand(
        @NotBlank(message = "le motif est obligatoire")
        @Size(min = 10, message = "le motif doit contenir au moins 10 caracteres")
        String motif) {
}
