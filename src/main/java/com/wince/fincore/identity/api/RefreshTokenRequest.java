package com.wince.fincore.identity.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Corps de {@code POST /api/v1/auth/refresh} et de
 * {@code POST /api/v1/auth/logout} : les deux endpoints n'ont besoin que
 * du refresh token opaque présenté.
 *
 * <p>Le refresh token voyage dans le <strong>corps</strong> de la requête
 * et non dans un en-tête : il n'a pas à figurer dans les journaux d'accès
 * ni dans la barre d'URL d'un éventuel proxy.</p>
 */
public record RefreshTokenRequest(
        @NotBlank(message = "le refresh token est obligatoire")
        String refreshToken) {
}
