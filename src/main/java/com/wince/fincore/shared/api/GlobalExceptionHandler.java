package com.wince.fincore.shared.api;

import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import com.wince.fincore.shared.infrastructure.CorrelationIdFilter;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traducteur unique des exceptions en corps d'erreur <strong>RFC 9457</strong>
 * ({@code application/problem+json}) — §9 du cahier des charges.
 *
 * <p>Une seule porte de sortie : quel que soit le chemin qui échoue, le client
 * reçoit le même format, avec :</p>
 * <ul>
 *   <li>{@code type} / {@code title} / {@code status} / {@code detail} /
 *       {@code instance} — les champs canoniques RFC 9457 ;</li>
 *   <li>{@code code} — le code métier stable ({@code INSUFFICIENT_FUNDS}…)
 *       sur lequel le client peut brancher sa logique (le message français,
 *       lui, peut changer) ;</li>
 *   <li>{@code traceId} — identique à celui des logs (MDC) pour corréler une
 *       plainte client avec la ligne de log exacte ;</li>
 *   <li>{@code timestamp} — horodatage injecté via {@link Clock} (testable,
 *       jamais {@code Instant.now()} en dur).</li>
 * </ul>
 *
 * <p>Équivalent Symfony : un listener {@code kernel.exception} qui construit
 * une {@code JsonErrorResponse} normalisée.</p>
 *
 * <p>Jamais de stack trace dans la réponse : le détail est logué côté serveur,
 * le client ne reçoit que le message métier.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    /** Injection par constructeur uniquement (règle §2.2). */
    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // ── 1. Toute exception métier explicite ──────────────────────────────

    /**
     * Les services métier lèvent {@link FinCoreException} : on respecte
     * simplement son statut et son code. Niveau LOG = WARN (cas nominal
     * d'un refus métier, pas un bug).
     */
    @ExceptionHandler(FinCoreException.class)
    public ProblemDetail onFinCore(FinCoreException ex, HttpServletRequest request) {
        log.warn("Règle métier refusée : code={} detail={}", ex.errorCode().name(), ex.getMessage());
        ProblemDetail problem = problem(ex.errorCode(), ex.getMessage(), request);
        ex.details().forEach(problem::setProperty);
        return problem;
    }

    // ── 2. Erreurs de validation des DTO (Bean Validation) ───────────────

    /**
     * Corps de requête invalide ({@code @Valid} échoué) → 400
     * {@code VALIDATION_FAILED} avec la liste champ → message, pour que le
     * front sache exactement quoi corriger (CA-AUTH-01).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fields.putIfAbsent(fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "valeur invalide"));
        log.debug("Validation échouée sur {} champ(s) : {}", fields.size(), fields.keySet());

        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED,
                "Le corps de la requête contient " + fields.size() + " erreur(s) de validation.", request);
        problem.setProperty("errors", fields);
        return problem;
    }

    /** Type de paramètre incorrect (ex. {@code ?page=abc}) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = "Le paramètre '" + ex.getName() + "' a une valeur invalide : '"
                + ex.getValue() + "'.";
        log.debug(detail);
        return problem(ErrorCode.VALIDATION_FAILED, detail, request);
    }

    /** Corps JSON malformé ou lecture impossible → 400. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.debug("Corps illisible : {}", ex.getMessage());
        return problem(ErrorCode.VALIDATION_FAILED,
                "Le corps de la requête est illisible ou ne correspond pas au format attendu.", request);
    }

    // ── 3. Ressource absente ─────────────────────────────────────────────

    /**
     * Chemin inexistant → 404. Volontairement <strong>même réponse</strong>
     * qu'une ressource existante mais interdite pour un client : on ne révèle
     * jamais l'existence d'une ressource (règle §5 — un CUSTOMER qui tente
     * de lire le compte d'un autre reçoit 404, pas 403).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail onNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("Ressource introuvable : {}", ex.getResourcePath());
        return problem(ErrorCode.RESOURCE_NOT_FOUND, "La ressource demandée n'existe pas.", request);
    }

    // ── 3.bis Refus d'accès levé DANS un contrôleur ─────────────────────

    /**
     * Piège Spring Security × MVC : une {@link AccessDeniedException}
     * levée <em>à l'intérieur</em> d'un handler ({@code @PreAuthorize} sur
     * une méthode) est interceptée par le {@code DispatcherServlet} et
     * n'atteint jamais l'{@code ExceptionTranslationFilter} de la chaîne de
     * sécurité. Sans ce handler, elle tomberait sur le catch-all ci-dessous
     * → 500 au lieu du 403 attendu par CA-AUTH-04.
     *
     * <p>Les refus produits <em>par les filtres</em> (URL non autorisée)
     * continuent d'être traités par le {@code accessDeniedHandler} de
     * {@code SecurityConfig} : les deux chemins répondent la même forme.</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Accès refusé : {}", ex.getMessage());
        return problem(ErrorCode.ACCESS_DENIED,
                "Vous n'avez pas les droits nécessaires pour cette ressource.", request);
    }

    // ── 4. Filet de sécurité : toute autre exception ─────────────────────

    /**
     * Dernier recours : 500 {@code INTERNAL_ERROR}, détail générique en
     * français, stack trace loguée en ERROR côté serveur uniquement.
     *
     * <p>On ne propage jamais le message brut de l'exception (il pourrait
     * contenir un nom de table, un chemin disque… — fuite d'information).</p>
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erreur interne non gérée : {}", ex.getMessage(), ex);
        return problem(ErrorCode.INTERNAL_ERROR,
                "Une erreur interne est survenue. Contactez l'assistance avec le traceId.", request);
    }

    // ── Construction du corps RFC 9457 ───────────────────────────────────

    private ProblemDetail problem(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(code.httpStatus()), detail);
        problem.setType(URI.create(code.type()));
        problem.setTitle(code.title());
        // instance = chemin réellement appelé (RFC 9457 §3 « instance »)
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.name());
        // traceId = correlationId du MDC : le même que dans les logs.
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        problem.setProperty("traceId", traceId != null ? traceId : "unknown");
        problem.setProperty("timestamp", clock.instant().toString());
        return problem;
    }
}
