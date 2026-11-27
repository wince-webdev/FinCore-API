# Decisions (ADR)

- Partie double pour garantir l integrite financiere.
- Verrouillage pessimiste ordonne par id pour eviter deadlocks.
- Idempotence en base (UNIQUE user_id, idempotency_key).
- Outbox transactionnelle pour notifications.
- NUMERIC/BigDecimal pour l argent.
- Java 25 LTS.
- Pas de Lombok pour voir le code reel.
