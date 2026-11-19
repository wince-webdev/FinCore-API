package com.wince.fincore.shared.infrastructure;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Source unique d'horloge du domaine.
 *
 * <p><strong>Pourquoi injecter un {@code Clock} ?</strong> Toute règle
 * métier qui dépend du temps (cumul journalier CA-LIM-03, plafonds
 * glissants…) doit être <em>reproductible</em> dans les tests : on injecte
 * un {@code Clock} figé (« fixed ») au lieu d'attendre le temps réel.</p>
 *
 * <p><strong>Pourquoi cette zone ?</strong> Les opérations sont rattachées à
 * l'Union Économique Monétaire Ouest-Africaine : le « jour » bancaire est
 * défini en {@code Africa/Porto-Novo} (UTC+1, fuseau du siège BCEAO). Le
 * stockage reste en {@code Instant} (UTC) — seul le calcul du « jour courant »
 * se fait dans ce fuseau (§2.4).</p>
 *
 * <p>Équivalent Symfony : le service {@code clock} de la bibliothèque
 * {@code symfony/clock}.</p>
 */
@Configuration
public class ClockConfig {

    /** Fuseau du jour bancaire UEMOA (UTC+1, sans DST). */
    public static final ZoneId BANKING_ZONE = ZoneId.of("Africa/Porto-Novo");

    /**
     * Horloge système dans le fuseau bancaire. Injectée partout où le temps
     * est une règle métier — jamais {@code Instant.now()} en dur dans les
     * services (règle §2.2).
     */
    @Bean
    public Clock clock() {
        return Clock.system(BANKING_ZONE);
    }
}
