package com.wince.fincore.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wince.fincore.shared.domain.ErrorCode;
import com.wince.fincore.shared.domain.FinCoreException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires de la politique de mot de passe (RG-AUTH-01).
 *
 * <p>On teste la <em>liste</em> des violations et pas seulement « ça jette » :
 * CA-AUTH-01 impose que le client reçoive en une seule fois <strong>toutes</strong>
 * les règles manquantes, pour corriger en un seul passe.</p>
 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("Mot de passe correct : aucune violation")
    void accepte_un_mot_de_passe_conforme() {
        assertThat(policy.violations("Aa1!Aa1!Aa1!Aa1!")).isEmpty();
    }

    @Test
    @DisplayName("« abc » viole 4 règles sur 5 : longueur, majuscule, chiffre, caractère spécial")
    void liste_toutes_les_regles_violees() {
        assertThat(policy.violations("abc"))
                .containsExactly(
                        "au moins 12 caractères",
                        "au moins une lettre majuscule",
                        "au moins un chiffre",
                        "au moins un caractère spécial");
    }

    @Test
    @DisplayName("Longueur seule en défaut : une seule violation signalée")
    void ne_signale_que_la_longueur_manquante() {
        // 11 caractères, majuscule, minuscule, chiffre et spécial présents.
        assertThat(policy.violations("Abcdefghi1!"))
                .containsExactly("au moins 12 caractères");
    }

    @Test
    @DisplayName("CA-AUTH-01 : rejet 400 VALIDATION_FAILED avec errors.password[]")
    void leve_une_erreur_metier_avec_le_detail_des_regles() {
        assertThatThrownBy(() -> policy.validate("abc"))
                .isInstanceOf(FinCoreException.class)
                .satisfies(ex -> {
                    FinCoreException fincore = (FinCoreException) ex;
                    assertThat(fincore.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);

                    Object errors = fincore.details().get("errors");
                    assertThat(errors).isInstanceOf(Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> byField = (Map<String, Object>) errors;
                    assertThat(byField).containsKey("password");
                    assertThat((List<?>) byField.get("password")).hasSize(4);
                });
    }

    @Test
    @DisplayName("Null ou vide : toutes les règles remontent, jamais de NullPointerException")
    void gere_le_null() {
        assertThat(policy.violations(null)).hasSize(5);
        assertThat(policy.violations("")).hasSize(5);
    }
}
