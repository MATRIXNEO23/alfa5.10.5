# Neon Tides · alfa8.10.8 test Luna

Base di partenza: **alfa8.10.7** (`5fa59cd8ac35484aa0790caecdf0011393f5f4e3`).

## Regola di sviluppo della 8.10.8

Ogni modifica introdotta nella 8.10.8 deve essere registrata in questo README indicando chiaramente componente modificato, comportamento precedente, comportamento nuovo, motivazione del test, parti lasciate invariate ed esito dei test prima di estendere la modifica al gioco principale.

Le modifiche devono essere piccole, isolate, misurabili e reversibili. La 8.10.7 resta il riferimento stabile.

## Modifica 01 · contesto relazione naturale nel solo laboratorio Luna

**Stato storico:** la prima implementazione è stata applicata al vecchio `runModularMemoryLab()`. I test reali del 29/08/2026 hanno dimostrato che la schermata Luna attuale usa invece `runBaseDialogueTest()`. Questa destinazione è stata corretta dalla **Modifica 09**; il vecchio laboratorio è stato poi eliminato.

### Idea mantenuta

Affetto, Attrazione e Fiducia restano regolabili e disponibili alla diagnostica, ma il GGUF non deve ricevere valori numerici, enum o nomi tecnici della fase come testo da imitare.

Le quattro descrizioni naturali definite per il test restano:

- confidenza bassa: `Vi conoscete ancora poco e non c'è ancora particolare confidenza.`
- confidenza in crescita: `Vi conoscete abbastanza e sta crescendo la confidenza.`
- confidenza solida + attrazione: `Tra voi c'è una confidenza solida e una chiara attrazione reciproca.`
- confidenza/fiducia/attrazione molto alte: `Tra voi c'è molta confidenza, fiducia e forte attrazione reciproca.`

Ogni frase termina invitando Luna a reagire naturalmente secondo il proprio carattere.

### Obiettivo del test

Ripetere con Luna le stesse domande e gli stessi valori usati nella 8.10.7 e verificare se diminuiscono le risposte che espongono regole/stato della relazione, Luna mantiene una confidenza coerente, il dialogo diventa più naturale e il comportamento a bassa/alta confidenza resta chiaramente differenziato.

Solo dopo un miglioramento misurabile questa soluzione potrà essere valutata per il gioco principale.

## Modifica 02 · identificazione build alfa8.10.8

**Stato:** implementata.

Per evitare APK ambigui durante il test Luna, nel solo branch 8.10.8 sono stati impostati:

- `versionCode = 45`
- `versionName = "alfa8.10.8-luna-test"`

`applicationId`, firma, keystore, SDK, ABI e configurazione dual-engine non sono stati modificati.

## Modifica 03 · workflow isolato di compilazione Luna test

**Stato:** implementata e poi aggiornata dalla Modifica 09.

È stato aggiunto il workflow separato:

`.github/workflows/NeonTides_ALFA8_10_8_LUNA_TEST.yml`

Il workflow parte sul solo branch `alfa8.10.8-luna-test`, applica la trasformazione controllata, verifica la versione e compila l'APK debug con runtime GGUF e stub MLC già previsto dal progetto. Il workflow dual-engine completo rimane separato.

## Modifica 04 · prima build: preflight ha rilevato sorgente Luna non modificato

**Esito:** fallimento corretto e utile; nessun APK prodotto.

La prima esecuzione si è fermata al controllo pre-build perché `AiEngine.kt` risultava ancora puro 8.10.7. Per evitare una riscrittura estesa di un file grande, la modifica è stata mantenuta come trasformazione controllata durante la build.

## Modifica 05 · tentativo patch unificata sostituito da trasformazione controllata

**Esito:** il primo formato patch non è stato accettato da `git apply`; non è stata applicata alcuna modifica parziale.

È stato quindi usato `scripts/apply_alfa8_10_8_luna_test.py`, che verifica i blocchi attesi prima di modificarli e termina con errore se la base non corrisponde.

## Modifica 06 · repository senza Gradle Wrapper

**Stato:** corretta nel workflow.

Il repository non contiene `gradlew` né la cartella wrapper Gradle. Il workflow usa `gradle/actions/setup-gradle@v4` con **Gradle 8.10.2** e compila con `gradle :app:assembleDebug --stacktrace`. Nessun file applicativo viene modificato per compensare l'assenza del wrapper.

## Modifica 07 · prima build alfa8.10.8 Luna test riuscita

**Esito:** SUCCESSO tecnico, ma successivamente risultato che l'esperimento era collegato al laboratorio interno sbagliato.

Workflow run: `33261210756`.

Artifact prodotto: `NeonTides-alfa8.10.8-luna-test`.

Dimensione artifact ZIP: 46.318.799 byte. Digest artifact: `sha256:0d102f7f6ac4b00a906ae3fe4ddd60f846f7a91abe81512ab6848516756cbbb9`.

La compilazione era valida, ma i test utente successivi hanno dimostrato che la schermata Luna visibile non richiamava il codice sperimentale modificato. Questa build non è quindi valida per giudicare l'efficacia del nuovo contesto relazione.

## Modifica 08 · rimozione workflow secondario ridondante

**Stato:** completata.

Un secondo workflow di compile-check creato temporaneamente è stato rimosso dopo la verifica del workflow ufficiale, per evitare doppie build e risultati ambigui.

## Modifica 09 · correzione del test Luna reale e rimozione del laboratorio obsoleto

**Stato:** implementazione in verifica di compilazione.

### Problema scoperto con i test del 29/08/2026

Dopo installazione pulita della 8.10.8, la diagnostica Luna con Thea mostrava ancora output come:

`Rapporto: Relazione. Messaggio: ...`

Inoltre nel report non comparivano le righe diagnostiche previste dalla prima trasformazione. L'analisi del codice ha confermato che la UI `LunaDiagnosticLab.kt` usa `GameViewModel.runBaseDialogueTest()`, che a sua volta usa `AiEngine.runBaseDialogueTest()` e la pipeline reale `replyAndEvaluate()` su uno stato isolato. Il vecchio `runModularMemoryLab()` non era più esposto dalla schermata corrente.

### Correzione applicata

La logica già definita nella Modifica 01 viene mantenuta, ma ora è applicata al **test realmente visibile**.

`replyAndEvaluate()` riceve un parametro opzionale `relationshipGuidanceOverride`. Il test `runBaseDialogueTest()` lo valorizza con `lunaRelationshipGuidance(affection, attraction, trust)`.

Nel turno locale il testo relazione viene costruito così:

- se il test Luna fornisce l'override, viene usata la descrizione naturale;
- se l'override è assente, resta il comportamento originale 8.10.7: `Rapporto: ${relationship.stage}. Messaggio: ...`.

Di conseguenza la chat principale non adotta ancora l'esperimento: la modifica è confinata al test isolato.

### Diagnostica

Il report `NEON TIDES - DIAGNOSTICA LUNA ISOLATA` continua a mostrare Affetto, Attrazione, Fiducia e Fase come dati diagnostici. In più registra esplicitamente:

`Contesto relazione inviato al GGUF: ...`

Questo permette di verificare dal file esportato quale formulazione naturale è stata usata dal test.

### Eliminazione del vecchio test

Il file `app/src/main/java/com/neontides/nativeapp/ai/ModularMemoryLab.kt` è stato eliminato dal branch.

Durante la trasformazione di build vengono inoltre rimossi da `AiEngine.kt` e `GameViewModel.kt` i riferimenti residui al vecchio laboratorio (`runModularMemoryLab`, `ModularLabRequest`, `ModularLabRecord`, `ModularLabResult`, history e fingerprint dedicati).

Il workflow fallisce se dopo la trasformazione trova ancora uno di questi riferimenti.

### Protezione del gioco principale

Il workflow verifica espressamente che in `AiEngine.kt` rimanga il fallback:

`Rapporto: ${relationship.stage}. Messaggio: $compactMessage`

Questa verifica serve a impedire che l'esperimento Luna venga accidentalmente esteso alla chat principale prima della valutazione dei test.

### Build di verifica

La prima esecuzione della Modifica 09 si è fermata nel transform perché il controllo cercava un blocco troppo ampio attorno a `messageLine`; nessun APK è stato compilato con una trasformazione parziale. Il transform è stato quindi ristretto alla sola riga `messageLine`, rendendo la sostituzione più chirurgica. La successiva compilazione deve superare sia il controllo di isolamento sia la compilazione Kotlin/Android prima che il nuovo APK venga considerato valido.

## Storico base

La 8.10.8 deriva direttamente dalla alfa8.10.7. Diagnostica separata, streaming, misurazione CPU/RAM/termica, runtime dual-engine e comportamento della chat principale restano invariati salvo modifiche esplicitamente registrate sopra.
