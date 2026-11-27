# Architecture FinCore

Monolithe modulaire. Modules: shared, identity, customer, account, ledger, payment, statement, compliance, notification, admin.

Partie double: toute transaction produit au moins deux ecritures dont somme debits = somme credits par devise. Solde = credits - debits.

Chainage de hachages par compte: entry_hash = SHA-256(prev_hash | transaction_id | account_id | direction | amount | currency | created_at).
