package com.wince.fincore.customer.domain;

/**
 * Type de pièce d'identité — table {@code customer.id_doc_type} (cahier §6.1).
 *
 * <p>Énuméré pour le niveau {@code LEVEL_2} (RG-KYC-02) — extension future
 * facile : ajouter un constante = nouveau choix possible dans {@code PATCH
 * /customers/{id}/kyc}. Le {@code LEVEL_3} attendra un type « justificatif de
 * domicile » — voir F-02 §7 (§7 du cahier).</p>
 */
public enum IdDocType {

    /** Carte d'identité nationale ou carte d'identité nationale provisoire. */
    ID_CARD,

    /** Passeport national / international. */
    PASSPORT,

    /** Permis de conduire / permis biétrique. */
    DRIVER_LICENSE
}
