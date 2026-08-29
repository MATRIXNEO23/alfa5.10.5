# Neon Tides · alfa8.10.8 test Luna

Base di partenza: **alfa8.10.7** (`5fa59cd8ac35484aa0790caecdf0011393f5f4e3`).

## Regola di sviluppo della 8.10.8

Ogni modifica introdotta nella 8.10.8 deve essere registrata in questo README indicando chiaramente componente modificato, comportamento precedente, comportamento nuovo, motivazione del test, parti lasciate invariate ed esito dei test prima di estendere la modifica al gioco principale.

Le modifiche devono essere piccole, isolate, misurabili e reversibili. La 8.10.7 resta il riferimento stabile.

## Modifica 01 · contesto relazione naturale nel solo laboratorio Luna

**Stato:** definita per implementazione isolata.

**Ambito:** esclusivamente `runModularMemoryLab()` / laboratorio Luna. Non deve modificare la chat principale.

### Comportamento 8.10.7

Il prompt del laboratorio Luna passa direttamente al GGUF i valori tecnici della relazione:

`Rapporto: affetto X, attrazione X, fiducia X.`

Questo rende disponibili al modello le variabili interne e può favorire risposte che verbalizzano lo stato della relazione invece di comportarsi naturalmente in base ad esso.

### Comportamento 8.10.8 test

Affetto, Attrazione e Fiducia continuano a essere regolabili e utilizzati dal laboratorio, ma i valori numerici non devono essere presentati al GGUF come testo della conversazione.

I tre valori vengono convertiti in una breve indicazione comportamentale naturale. Esempi:

- valori bassi: `Vi conoscete ancora poco e non c'è ancora particolare confidenza. Reagisci naturalmente in base al tuo carattere.`
- valori intermedi: indicazione di confidenza crescente senza nomi di fase o punteggi;
- valori alti: `Tra voi c'è già molta confidenza, fiducia e attrazione reciproca. Reagisci naturalmente in base al tuo carattere.`

Il GGUF deve conoscere il grado di confidenza necessario a interpretare Luna senza vedere numeri, enum o nomi tecnici dello stato relazionale.

### Diagnostica

I valori numerici originali di Affetto, Attrazione e Fiducia restano visibili nella diagnostica del test per permettere confronti ripetibili tra 8.10.7 e 8.10.8. La diagnostica deve inoltre indicare quale descrizione naturale della relazione è stata selezionata.

### Parti intenzionalmente invariate

Per questa modifica NON cambiano `replyAndEvaluate()` e chat principale, prompt dei personaggi nel gioco principale, `HybridDialogueRouter`, `SemanticMemorySelector`, memoria, calcolo e soglie relazionali, cache principale, timeout, thread llama.cpp, priorità CPU/RAM, backend llama.cpp/MLC, galleria, salvataggi e stato della partita.

### Obiettivo del test

Ripetere con Luna le stesse domande e gli stessi valori usati nella 8.10.7 e verificare se diminuiscono le risposte che espongono regole/stato della relazione, Luna mantiene una confidenza coerente, il dialogo diventa più naturale e il comportamento a bassa/alta confidenza resta chiaramente differenziato.

Solo dopo un miglioramento misurabile questa soluzione potrà essere valutata per il gioco principale.

## Storico base

La 8.10.8 deriva direttamente dalla alfa8.10.7. Laboratorio Luna isolato, diagnostica separata, streaming, misurazione CPU/RAM/termica, runtime dual-engine e comportamento della chat principale restano invariati salvo modifiche esplicitamente registrate sopra.
