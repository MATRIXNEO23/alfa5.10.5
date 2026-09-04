# Regole canoniche di lavoro

Stato: CANONICO
Data: 2026-09-04

## Una repository alla volta

Salvo indicazione esplicita del proprietario, si lavora su una sola repository alla volta.

- Una sola repository è il target di lavoro attivo.
- Le altre repository possono essere lette solo quando serve verificare dipendenze o contesto.
- Non si scrive su altre repository senza istruzione esplicita del proprietario.
- Il cambio di repository attiva richiede istruzione esplicita.
- Se una modifica di un componente richiede aggiornamenti di codice, test, documentazione o continuità nella repository attiva, tali aggiornamenti vanno mantenuti coerenti nello stesso workstream.
- Non creare specifiche parallele quando esiste già un documento canonico aggiornabile.

## Repository storiche = backup

Le repository/versioni precedenti o superate sono backup/checkpoint di recupero, non target di sviluppo normale.

- Restano integre come riferimento e via di ritorno.
- Si consultano o recuperano componenti da esse se la linea corrente arriva a un punto morto, introduce una regressione grave o serve confrontare una soluzione precedente valida.
- Non si riprende automaticamente un'intera vecchia repo: si recuperano solo i componenti/commit necessari con provenienza chiara.
- Non si modificano o cancellano le repo backup senza istruzione esplicita del proprietario.

Queste regole riguardano il metodo di lavoro e non modificano da sole l'architettura runtime.
