# Neon Tides · alfa8.10.8 test Luna

Base di partenza: **alfa8.10.7** (`5fa59cd8ac35484aa0790caecdf0011393f5f4e3`).

## Regola di sviluppo della 8.10.8

Ogni modifica introdotta nella 8.10.8 deve essere registrata in questo README indicando chiaramente componente modificato, comportamento precedente, comportamento nuovo, motivazione del test, parti lasciate invariate ed esito dei test prima di estendere la modifica al gioco principale.

Le modifiche devono essere piccole, isolate, misurabili e reversibili. La 8.10.7 resta il riferimento stabile.

## Modifica 01 · contesto relazione naturale nel solo laboratorio Luna

**Stato:** implementata come trasformazione isolata e controllata nel branch `alfa8.10.8-luna-test`.

**Ambito:** esclusivamente `runModularMemoryLab()` / laboratorio Luna. La chat principale resta invariata.

### Comportamento 8.10.7

Il prompt del laboratorio Luna passava direttamente al GGUF i valori tecnici della relazione:

`Rapporto: affetto X, attrazione X, fiducia X.`

Questo rendeva disponibili al modello le variabili interne e poteva favorire risposte che verbalizzavano lo stato della relazione invece di comportarsi naturalmente in base ad esso.

### Comportamento 8.10.8 test

Affetto, Attrazione e Fiducia continuano a essere regolabili e utilizzati dal laboratorio, ma i valori numerici non vengono più presentati al GGUF come testo della conversazione.

Lo script `scripts/apply_alfa8_10_8_luna_test.py` modifica soltanto gli esatti blocchi previsti della 8.10.7 e aggiunge `lunaRelationshipGuidance()`, che converte i tre valori in una breve indicazione comportamentale naturale senza nomi di fase, enum o punteggi tecnici.

Fasce attuali del test:

- confidenza bassa: `Vi conoscete ancora poco e non c'è ancora particolare confidenza.`
- confidenza in crescita: `Vi conoscete abbastanza e sta crescendo la confidenza.`
- confidenza solida + attrazione: `Tra voi c'è una confidenza solida e una chiara attrazione reciproca.`
- confidenza/fiducia/attrazione molto alte: `Tra voi c'è molta confidenza, fiducia e forte attrazione reciproca.`

Ogni frase termina invitando Luna a reagire naturalmente secondo il proprio carattere.

### Diagnostica

I valori numerici originali di Affetto, Attrazione e Fiducia restano visibili nella diagnostica del test. Viene inoltre registrata la descrizione naturale realmente inviata al GGUF.

La cache del laboratorio usa il fingerprint `luna-modular-v3-natural-relationship` per separare chiaramente questo esperimento dal comportamento precedente.

### Parti intenzionalmente invariate

Per questa modifica NON cambiano `replyAndEvaluate()` e chat principale, prompt dei personaggi nel gioco principale, `HybridDialogueRouter`, `SemanticMemorySelector`, memoria, calcolo e soglie relazionali, cache principale, timeout, thread llama.cpp, priorità CPU/RAM, backend llama.cpp/MLC, galleria, salvataggi e stato della partita.

### Obiettivo del test

Ripetere con Luna le stesse domande e gli stessi valori usati nella 8.10.7 e verificare se diminuiscono le risposte che espongono regole/stato della relazione, Luna mantiene una confidenza coerente, il dialogo diventa più naturale e il comportamento a bassa/alta confidenza resta chiaramente differenziato.

Solo dopo un miglioramento misurabile questa soluzione potrà essere valutata per il gioco principale.

## Modifica 02 · identificazione build alfa8.10.8

**Stato:** implementata.

Il file `app/build.gradle.kts` nel ramo sorgente risultava ancora fermo ai metadati della vecchia build dual-engine (`versionCode 41`, `versionName alfa8.10.5-dual-engine-test`), nonostante il README della 8.10.7 riportasse una numerazione successiva.

Per evitare APK ambigui durante il test Luna, nel solo branch 8.10.8 sono stati impostati:

- `versionCode = 45`
- `versionName = "alfa8.10.8-luna-test"`

`applicationId`, firma, keystore, SDK, ABI e configurazione dual-engine non sono stati modificati.

## Modifica 03 · workflow isolato di compilazione Luna test

**Stato:** implementata.

Il workflow dual-engine V9 esistente eseguiva automaticamente i push soltanto sul branch `main`, quindi i commit del branch `alfa8.10.8-luna-test` non producevano alcuna build automatica.

È stato aggiunto il workflow separato:

`.github/workflows/NeonTides_ALFA8_10_8_LUNA_TEST.yml`

Il workflow:

- parte automaticamente sui push del solo branch `alfa8.10.8-luna-test`;
- mantiene intatti i workflow della 8.10.7 e il branch `main`;
- applica la trasformazione Luna con controlli stretti prima della compilazione;
- verifica che la build sia realmente `45 / alfa8.10.8-luna-test`;
- verifica la presenza del fingerprint Luna v3 e della diagnostica dei valori test;
- fallisce se trova ancora nel laboratorio la vecchia iniezione testuale dei tre punteggi;
- prepara JDK 21, Gradle 8.10.2, Android SDK 35, CMake 3.22.1 e NDK 27.2.12479018;
- compila l'APK debug con runtime GGUF e lo stub MLC già previsto dal progetto quando il runtime MLC completo non viene rigenerato;
- pubblica come artifact `NeonTides-alfa8.10.8-luna-test.apk`.

Questo workflow serve esclusivamente a validare la modifica Luna e la compilabilità del ramo. Il workflow dual-engine completo V9 rimane disponibile e invariato per una successiva build con runtime MLC completo.

## Modifica 04 · prima build: preflight ha rilevato sorgente Luna non modificato

**Esito:** fallimento corretto e utile; nessun APK prodotto.

La prima esecuzione si è fermata al controllo pre-build perché `AiEngine.kt` risultava ancora puro 8.10.7: fingerprint `luna-modular-v2` e vecchia stringa numerica presenti. La causa era uno spostamento precedente del riferimento del branch che aveva escluso il commit sperimentale di `AiEngine.kt`.

Per non riscrivere un file grande e introdurre differenze collaterali, la modifica è stata resa esterna, minima e reversibile.

## Modifica 05 · tentativo patch unificata sostituito da trasformazione controllata

**Esito:** la seconda build si è fermata prima della compilazione durante `git apply`.

Il primo formato scelto per mantenere la modifica isolata era `patches/alfa8.10.8-luna-natural-relationship.patch`. Il formato della patch non è stato accettato dal controllo `git apply`, quindi non è stata applicata alcuna modifica parziale.

La patch è stata superata dal nuovo script `scripts/apply_alfa8_10_8_luna_test.py`. Lo script cerca esattamente e una sola volta i blocchi originali 8.10.7; se il sorgente non corrisponde, termina con errore. Solo dopo questi controlli sostituisce fingerprint, prompt Luna e diagnostica e inserisce la funzione di contesto naturale.

Nella terza esecuzione questa trasformazione ha superato il controllo con successo, così come i successivi controlli su versione, fingerprint, diagnostica e assenza della vecchia iniezione numerica.

## Modifica 06 · repository senza Gradle Wrapper

**Stato:** corretta nel workflow.

La terza esecuzione ha raggiunto per la prima volta il vero passo di compilazione, ma si è fermata immediatamente perché il repository non contiene `gradlew` né la cartella wrapper Gradle. Non è un errore della modifica Luna.

Il workflow usa ora `gradle/actions/setup-gradle@v4` per installare **Gradle 8.10.2** e compila con `gradle :app:assembleDebug --stacktrace`. Il progetto usa Android Gradle Plugin 8.7.3 e Kotlin 2.1.10; nessun file applicativo viene modificato per compensare l'assenza del wrapper.

## Storico base

La 8.10.8 deriva direttamente dalla alfa8.10.7. Laboratorio Luna isolato, diagnostica separata, streaming, misurazione CPU/RAM/termica, runtime dual-engine e comportamento della chat principale restano invariati salvo modifiche esplicitamente registrate sopra.
