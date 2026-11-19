package com.wince.fincore.shared.domain;

import java.util.Map;

/**
 * Exception métier transverse de FinCore : transporte un {@link ErrorCode}
 * (statut HTTP + code JSON) et un message explicatif en français.
 *
 * <p><strong>Flux :</strong> un service lève {@code FinCoreException} →
 * {@link com.wince.fincore.shared.api.GlobalExceptionHandler} la intercepte →
 * le client reçoit un corps RFC 9457 correctement typé, sans stack trace.</p>
 *
 * <p>Équivalent Symfony : une exception {@code ApiException} avec un code
 * métier, attrapée par un listener kernel.exception.</p>
 */
public class FinCoreException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    /**
     * @param errorCode code métier (statut HTTP + slug d'erreur)
     * @param detail    message français explicatif — doit dire <em>pourquoi</em>
     *                  et, si pertinent, les valeurs en jeu (jamais de secret)
     */
    public FinCoreException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of());
    }

    /**
     * Variante avec détails structurés ajoutés au corps d'erreur
     * (ex. {@code plafondRestant}, {@code soldeDisponible}).
     */
    public FinCoreException(ErrorCode errorCode, String detail, Map<String, Object> details) {
        super(detail);
        this.errorCode = errorCode;
        this.details = Map.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** Détails additionnels sérialisés dans les propriétés du ProblemDetail. */
    public Map<String, Object> details() {
        return details;
    }
}
