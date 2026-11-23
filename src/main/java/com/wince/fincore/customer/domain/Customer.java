package com.wince.fincore.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Entité JPA {@code customer} — table créée par
 * {@code V3__create_customer.sql} (cahier §6.1, Phase 3, F-02).
 *
 * <p><strong>Séparation session / données métier (cahier §6.1) — Single
 * Responsibility :</strong> {@code AppUser} = login + verrouillage (Phase 2),
 * {@code Customer} = données métier — le lien est une clé unique
 * {@code user_id}. Vue d'ensemble :
 * {@code CustomerRepository.findByUserId(userId)}. Le
 * {@code GET /customers/me} résout le client à partir du claim
 * {@code sub} du JWT (= id de l'utilisateur, RG-AUTH-06).</p>
 *
 * <p><strong>Pourquoi le numéro de pièce n'est-il pas un champ persistant ?
 * (CA-KYC-01)</strong> La règle l'interdit : le numéro complet ne peut figurer
 * <em>nulle part</em> — ni en base, ni dans une réponse HTTP, ni dans les
 * logs. Ici, {@link #idDocNumber} est un champ <em>transient</em> : jamais
 * persisté, jamais lu depuis la base — il ne sert qu'à calculer la forme
 * masquée {@link #idDocNumberMasked} ({@code ****6789}) au moment où
 * TELLER/ADMIN entre le document via {@link #maskIdDoc(String)}. En base,
 * seule {@code id_doc_number_masked} existe (cahier §6.1).</p>
 *
 * <p>{@link #isAdult(LocalDate)} (RG-KYC-04) — « majeur » signifie 18 ans
 * <em>passé</em> le 18ᵉ anniversaire, dans le fuseau du siège
 * {@code Africa/Porto-Novo} — {@code ClockConfig.BANKING_ZONE}. L'entité ne
 * connaît pas l'horloge : c'est {@code CustomerService} qui injecte
 * {@code Clock} et passe la date « today ».
 * </p>
 */
@Entity
@Table(name = "customer")
public class Customer {

    /** Nécessaire pour JPA — jamais utilisé directement (règle §2.2). */
    protected Customer() {
    }

    /**
     * Création à l'inscription — RG-KYC-01 : le client arrive en {@code LEVEL_1}
     * / {@code PENDING}. {@code birthDate} est donc obligatoire : la majeure
     * à 18 ans (RG-KYC-04) se calcule à partir de la date de naissance.
     */
    public Customer(UUID id, UUID userId, String firstName, String lastName,
                    LocalDate birthDate, String phone) {
        this.id = id;
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.phone = phone;
        this.kycLevel = KycLevel.LEVEL_1;
        this.kycStatus = KycStatus.PENDING;
        this.createdAt = Instant.now();
    }

    // ── Champs (cahier §6.1) ─────────────────────────────────────────────

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Clé vers {@code AppUser} — unicité garantie par la base (UNIQUE). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Inscription minimale — nom + prénom (RG-KYC-01). */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Utilisé uniquement pour le calcul de la majeure (RG-KYC-04). */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    /** Enuméré fermé — {@link IdDocType} (cahier §6.1, §7). */
    @Enumerated(EnumType.STRING)
    @Column(name = "id_doc_type", length = 20)
    private IdDocType idDocType;

    /**
     * Numéro de pièce — HACHÉ — jamais en base ni lisible (RG-KYC-03, CA-KYC-01).
     * Valeur {@code null} lorsqu'aucun document n'a été fourni.
     */
    @Transient
    private String idDocNumber;

    /** Forme masquée du numéro — {@code ****6789} — unique version exposée. */
    @Column(name = "id_doc_number_masked", length = 20)
    private String idDocNumberMasked;

    /** Enuméré fermé — {@link KycLevel} (cahier §6.1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_level", nullable = false, length = 20)
    private KycLevel kycLevel;

    /** Enuméré fermé — {@link KycStatus} (cahier §6.1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Accesseurs (lecture seule hors méthodes métier) ──────────────────

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getPhone() {
        return phone;
    }

    public IdDocType getIdDocType() {
        return idDocType;
    }

    /** Haché — jamais le numéro clair (voir classe). */
    public String getIdDocNumber() {
        return idDocNumber;
    }

    public String getIdDocNumberMasked() {
        return idDocNumberMasked;
    }

    public KycLevel getKycLevel() {
        return kycLevel;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Mutations (service / flux KYC) ───────────────────────────────────

    public void setIdDocType(IdDocType idDocType) {
        this.idDocType = idDocType;
    }

    public void setKycLevel(KycLevel kycLevel) {
        this.kycLevel = kycLevel;
    }

    /**
     * Hache le numéro de pièce et masque ses 4 derniers chiffres (RG-KYC-03).
     *
     * <p>Inverse-carré : {@link #idDocNumber} contient uniquement le SHA-256 ;
     * {@link #idDocNumberMasked} est la seule valeur exposée via l'API. Le
     * numéro clair ne reste nulle part — pas en base, pas en log, pas dans
     * les réponses.</p>
     */
    public void maskIdDoc(String idDocNumber) {
        if (idDocNumber == null || idDocNumber.isBlank()) {
            this.idDocNumber = null;
            this.idDocNumberMasked = null;
            return;
        }
        String trimmed = idDocNumber.trim();
        this.idDocNumber = sha256(trimmed);
        this.idDocNumberMasked = "****" + lastFour(trimmed);
    }

    /**
     * Majeure ou non — RG-KYC-04 : « majeur » signifie 18 ans <em>passé</em>
     * le 18ᵉ anniversaire. Sur le jour de l'anniversaire 18, le client est
     * majeur (la date limite « today − 18 ans » n'est pas encore dépassée).
     */
    public boolean isAdult(LocalDate today) {
        return birthDate != null && !birthDate.isAfter(today.minusYears(18));
    }

    // ── Internes : masquage (RG-KYC-03) ─────────────────────────────────

    /**
     * Empreinte SHA-256 d'une donnée sensible — comparable à un hachage
     * Argon2id pour les mots de passe : ici, le numéro de pièce n'est pas
     * un secret à haute entropie (il est dans un jeu de données publics),
     * mais le hachage bloque la lecture directe depuis la base.
     */
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 doit être disponible sur tout JDK", e);
        }
    }

    /** Les 4 derniers caractères de la pièce — ce qui se lit vraiment. */
    private static String lastFour(String value) {
        return value.length() >= 4 ? value.substring(value.length() - 4) : value;
    }
}
