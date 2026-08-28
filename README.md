# Neon Tides · alfa8.10.5 dual-engine test

Questa build mantiene tutta la semantica modulare di alfa8.10.4 e integra due
runtime locali nella stessa app:

- **llama.cpp** per i modelli `.gguf`, invariato e ancora utilizzabile come
  motore stabile;
- **MLC LLM** per il pacchetto convertito Qwen 2.5 3B Uncensored `.tar`, con
  esecuzione GPU/OpenCL;
- un solo modello viene mantenuto in RAM: quando si cambia backend, il runtime
  precedente viene scaricato prima di caricare quello selezionato;
- memoria, selettore semantico, relazioni, prompt autorizzati e fallback online
  restano condivisi; cambia soltanto il generatore finale;
- la schermata Configurazione IA distingue modelli e backend e può importare
  sia GGUF sia il pacchetto MLC già pubblicato;
- la diagnostica mostra motore selezionato, runtime realmente caricato, presenza
  del runtime MLC nell'APK e registro separato del backend;
- la pipeline manuale **Build Neon Tides dual-engine APK** scarica il pacchetto
  MLC verificato, incorpora `mlc4j` e controlla che la libreria ARM64 sia
  realmente presente nell'APK;
- versione Android `versionCode 41`, `versionName
  alfa8.10.5-dual-engine-test`; firma e `applicationId` invariati.

La build dual-engine non include i pesi nell'APK: sul telefono va importato il
pacchetto `.tar`. Questo evita un APK di quasi 2 GB e consente di tornare al
GGUF senza reinstallare il gioco.

## alfa8.10.4 · semantica modulare

Le specifiche non ancora implementate e la trama futura sono conservate separatamente in [`SVILUPPO_FUTURO.md`](SVILUPPO_FUTURO.md), per non confonderle con le funzioni già presenti nelle build.

- Il selettore modulare non è più limitato al laboratorio: il dialogo reale costruisce moduli dinamici da profilo NPC, fatti del giocatore, ricordi condivisi e segreti già autorizzati.
- Ogni modulo conserva un proprietario esplicito (`PERSONAGGIO`, `GIOCATORE` o `RICORDO CONDIVISO`), impedendo di attribuire età, lavoro, gusti e ricordi al soggetto sbagliato.
- Il deterministico seleziona al massimo tre informazioni e applica le soglie di fiducia; il testo ordinario resta formulato dal GGUF. Una frase codificata può apparire soltanto come recupero tecnico di un output corrotto.
- I fatti dichiarati dal giocatore vengono riconosciuti anche nello stesso messaggio che contiene una domanda per l’NPC, salvati senza includere la domanda e richiamati solo quando pertinenti.
- Età, lavoro e residenza aggiornati sostituiscono il valore precedente; gusti diversi restano separati e una confidenza già nota non assegna nuovamente punti.
- L’assenza di moduli è un normale turno di conversazione: il prompt non comunica più al GGUF che mancano dati, evitando risposte tecniche come «non c’è un modulo richiamato».
- L’Area test è divisa in cinque sezioni non sovrapposte: Dialogo base, Semantica, Confronto, Progressione e Galleria.
- Il dialogo base e il laboratorio modulare conservano ciascuno fino a 30 prove. Ogni riepilogo contiene la domanda esatta, i valori di affetto/attrazione/fiducia, la risposta e la diagnostica, ed è copiabile senza screenshot.
- Nel test base la variazione proposta dei tre valori resta in attesa: viene registrata soltanto dopo `CONFERMA E REGISTRA` oppure come risultato `NON CORRETTO`.
- La sezione Confronto abbina automaticamente una prova base e una modulare quando domanda e tre valori iniziali coincidono.
- Le prove alimentano anche il Riepilogo app della diagnostica generale; anche le conversazioni normali riportano ora la domanda esatta.
- Cambiare i tre punteggi del laboratorio non ricostruisce più la cache permanente. Prima di ogni prova indipendente il cache nativo viene riportato al solo prefisso di Luna senza rivalutarlo, evitando contaminazioni fra domande.
- Diagnostica nativa ampliata con il numero di ripristini della cache senza ricostruzione.
- Diagnostica risorse con CPU del processo, capacità totale utilizzata, RAM PSS dell’app, heap JVM/nativo e RAM disponibile del telefono. Cache e generazione richiedono temporaneamente priorità Android `DISPLAY`, poi ripristinano quella precedente e registrano se la richiesta è stata concessa.
- Aggiunti test di regressione per separazione giocatore/NPC, richiami di memoria, segreti bloccati, conversazione generica, estrazione delle confidenze e prompt senza metatesto.
- Versione Android `versionCode 40`, `versionName alfa8.10.4-semantica-modulare`; firma e `applicationId` invariati.

## alfa 8.10.3 — Laboratorio memoria modulare

Questa revisione riparte dal comportamento compatto e stabile di alfa8.10.1 e aggiunge un esperimento isolato in **Test Aggiornamenti**:

- laboratorio con Luna e livelli simulabili di affetto, attrazione e fiducia;
- memoria divisa in moduli NPC, giocatore e ricordi condivisi;
- massimo tre candidati per turno, con segreti e ricordi intimi soggetti a soglie;
- nessuna risposta deterministica nel laboratorio: il testo finale è sempre generato dal GGUF;
- diagnostica separata e copiabile del percorso modulare;
- nessuna modifica a partita, galleria o salvataggi;
- contesto permanente alleggerito: fatti, hobby, segreti e cronologia vengono richiamati solo quando pertinenti.

## alfa 8.10.2 — Revisione semantica e prestazioni Moto G56

- Separazione esplicita tra fatti del giocatore e biografia del personaggio nel prompt persistente e nei richiami di memoria.
- Nome, età, genere e lavoro restano risposte certe e immediate; riconosciuti anche errori comuni come «quanti hanno hai?».
- Corretto il test di regressione GitHub relativo a «quanti hanno hai?»: la forma errata `hanno` viene ora riconosciuta davvero come richiesta dell'età.
- Le domande anagrafiche e i richiami di memoria sono neutri: non assegnano punti relazione.
- L'eco quasi letterale del messaggio viene trattata come errore oggettivo; una risposta semanticamente valida del GGUF non viene sostituita.
- Flirt e avances vengono valutati anche considerando la risposta effettiva dell'NPC: una risposta ricettiva non viene punita automaticamente.
- Prompt con proprietà `GIOCATORE`/`PERSONAGGIO`, fase e punteggi correnti; temperatura 0,52, repetition penalty 1,12 e massimo 56 token.
- Ricompattazione del contesto spostata da 10 a 24 generazioni per evitare pause da 40–60 secondi sul Motorola Moto G56.
- Versione Android `versionCode 38`, `versionName alfa8.10.2`; firma e `applicationId` invariati.

## alfa 8.8 — Coerenza GGUF, Qwen integrato e tempo di gioco

- Il testo visto in streaming resta la risposta definitiva del GGUF: il controllo successivo non lo sostituisce più per sinonimi, nomi, somiglianza o interpretazioni semantiche. Interviene soltanto se l'output è vuoto, contiene marcatori ChatML o perde tecnicamente il formato.
- Il sistema deterministico gestisce argomento, fatti autorizzati, memoria e punteggio, ma scrive direttamente soltanto risposte fattuali inequivocabili come nome, età, mestiere e ricordi realmente dichiarati dal giocatore.
- Limiti personali, flirt, tono emotivo, correzioni e continuità vengono formulati dal Qwen con istruzioni contestuali brevi, evitando le frasi rigide che nella 8.6 sostituivano risposte migliori.
- Gli ultimi quattro messaggi visibili vengono riallineati in forma compatta al contesto nativo: anche una risposta diretta offline resta nota al GGUF nel turno successivo senza ricostruire l'intera cache.
- Corretto «ti ricordi come mi chiamo, quanti anni ho e cosa mi piace?»: la richiesta riguarda la memoria sul giocatore e non viene più scambiata per una domanda sui segreti privati del personaggio.
- Prompt permanente e continuità abbreviati per diminuire i token analizzati dal 3B senza perdere identità, carattere, rapporto, gusti, ricordi e ultimo filo del discorso.
- Download diretto in-app del solo modello di riferimento `Qwen2.5-3B-Instruct-Uncensored.Q4_K_M.gguf`, con barra, percentuale, velocità, stima residua, pausa, ripresa, annullamento, controllo dello spazio, dimensione completa e intestazione GGUF.
- Il modello scaricato può essere selezionato, scaricato dalla RAM lasciando il file sul telefono oppure eliminato dal dispositivo.
- Ogni fascia dura ora 25 minuti reali: Mattina, Metà giorno, Pomeriggio e Notte durano uguale; un giorno completo dura 100 minuti reali. Orologio, aperture, presenze, limiti conversazione e chiamate usano la stessa fase.
- Migrazione dei vecchi salvataggi allo schema 5: vengono conservati giorno e fascia corrente senza ereditare la vecchia velocità da 15 minuti.
- I personaggi nella scena sono più grandi, distribuiti con sovrapposizione controllata e allineati visivamente ai piedi; le immagini sorgenti non vengono ricampionate o ricomprese e mantengono la definizione originale.
- Neon OS dispone di Home, Indietro e App recenti; Galleria e Mappa tornano al telefono quando sono state aperte da lì. La notifica messaggio si sente anche a telefono chiuso, ma soltanto per un nuovo messaggio telefonico ricevuto.
- `Test Aggiornamenti` conserva la prova della galleria e aggiunge una simulazione isolata di affetto, attrazione, fiducia, regressione, difficoltà e avanzamento massimo di una fase relazionale per giorno.
- La diagnostica indica anche il motivo preciso di un'eventuale correzione tecnica; `COPIA RISULTATO` resta disponibile in alto.
- Versione Android aggiornata a `versionCode 35` e `versionName alfa8.8`; firma, keystore e `applicationId` restano invariati.

### Prove consigliate per alfa 8.8

1. Chiedere nome, età e mestiere: devono arrivare quasi subito e senza preparazione GGUF.
2. Fare una domanda libera, osservare lo streaming e verificare in Diagnostica che `Correzione deterministica dopo GGUF` sia `NO` e il motivo sia `nessuno`.
3. Presentarsi con nome, età e gusto; cambiare personaggio, rientrare e chiedere «ti ricordi cosa sai di me?».
4. Aprire `Test Aggiornamenti`, provare interazioni positive, neutre e negative e usare `Giorno +1` per controllare la progressione graduale.
5. Lasciare finire una fascia durante una conversazione: l'NPC deve salutare con una variante prima di uscire.
6. Verificare che ogni fase duri 25 minuti reali e che al cambio si aggiornino insieme orologio, luoghi e personaggi.
7. Scaricare Qwen dall'app, mettere in pausa, riprendere e annullare una prova; verificare infine `Scarica dalla RAM` senza cancellare il file.
8. Aprire Mappa e Galleria da Neon OS, usare Indietro/Home/Recenti e controllare che il suono messaggio non parta nei dialoghi faccia a faccia.

## alfa 8.7 — Dialoghi naturali, Neon OS e qualità visiva

- Il sistema deterministico non scrive più i dialoghi al posto del GGUF: gestisce stato, memoria, limiti e dati certi, mentre il modello formula liberamente le risposte naturali.
- La risposta in streaming del GGUF non viene più sostituita perché usa sinonimi o parafrasi diverse dal vocabolario; il filtro interviene soltanto su errori oggettivi come testo vuoto, ruoli invertiti, metatesto, nomi estranei o ripetizioni quasi identiche.
- Eliminati dal prompt locale i blocchi tecnici `CONTROLLO`, `INPUT_UTENTE` e le etichette che potevano finire nel dialogo; le istruzioni per turno sono ora brevi e in linguaggio naturale.
- Corretto il caso reale «il mio mestiere è creare l'applicazione di cui fai parte, tu che mestiere fai?»: `parte, tu` non viene più scambiato per il richiamo «e tu» e mestiere/professione vengono riconosciuti come lavoro.
- Età, nome, genere e mestiere espliciti restano risposte offline certe e immediate; gusti, emozioni, continuazioni, correzioni e frasi ambigue vengono lasciati alla voce del GGUF.
- Aprire una chat non prepara più obbligatoriamente il costoso cache: la preparazione parte soltanto al primo messaggio che richiede il GGUF. Rientrando dallo stesso personaggio il contesto già pronto viene riutilizzato.
- Contesto GGUF portato a 1536 token e ricompattazione spostata a dieci generazioni, per ridurre le pause da 45–50 secondi durante una conversazione lunga.
- Punteggio relazionale interamente indipendente dal GGUF: domande informative e correzioni restano a zero; nuove confidenze aumentano soprattutto la fiducia; sostegno, gusti condivisi e flirt adeguato hanno effetti mirati; offese, pressioni e insistenza restano negativi.
- I fatti dichiarati dal giocatore vengono estratti anche quando nello stesso messaggio compare una domanda; aggiunto il riconoscimento di «il mio mestiere è…» e «la mia professione è…».
- Memoria persistente per personaggio ampliata con confidenze/segreti sbloccati gradualmente da fiducia, affetto e conversazioni; conflitti interiori e momenti narrativi non vengono anticipati prima delle relative soglie.
- Quando termina una fascia o chiude un locale, l'NPC saluta prima di uscire. Le frasi cambiano in base a lavoro, rapporto, tono e causa dell'uscita, evitando le ultime tre varianti già usate.
- Nuova home del telefono **Neon OS** con icone per Messaggi, Chiamate, Contatti, Galleria, Mappa, Calendario, Profilo e Impostazioni; messaggi telefonici, chiamate e conversazioni di persona sono distinti nei salvataggi.
- La notifica sonora dei messaggi è ora dedicata al telefono e più evidente; i dialoghi faccia a faccia non riproducono più il suono da chat. La suoneria delle chiamate resta separata.
- Integrato il main theme provvisorio nel menu iniziale, in loop e a volume moderato; potrà essere sostituito in futuro mantenendo lo stesso nome risorsa.
- Il tasto Invio della tastiera Android invia direttamente il messaggio tramite l'azione **Invia**, senza obbligare a chiudere la tastiera per premere il pulsante.
- Le nove miniature dei personaggi sono state ricentrate e ingrandite sul solo viso. Non è stata usata generazione grafica: volti, colori e identità derivano dagli asset esistenti.
- I 19 sprite grandi di personaggi e protagonisti sono stati convertiti in WebP lossless mantenendo risoluzione e pixel decodificati identici; galleria e sfondi non sono stati ridimensionati né ricompressi. Il peso passa da circa 21 MB a circa 12 MB senza perdita visiva.
- Salvataggi aggiornati allo schema 4 e retrocompatibili; firma, keystore e `applicationId` restano invariati.
- Versione Android aggiornata a `versionCode 34` e `versionName alfa8.7`.

### Prove consigliate per alfa 8.7

1. Aprire Luna e chiedere subito «qual è la tua età?»; la risposta deve essere immediata e il punteggio deve restare invariato.
2. Scrivere «io ho 44 anni e mi chiamo Alberto»; la fiducia può aumentare di un solo punto perché è una nuova confidenza.
3. Scrivere «il mio mestiere è creare l'applicazione di cui fai parte, tu che mestiere fai?»; Luna deve rispondere di essere cantautrice.
4. Fare una domanda personale formulata con parole non presenti nel vocabolario; la risposta del GGUF in streaming non deve essere sostituita per la sola assenza di parole coincidenti.
5. Lasciare terminare la fascia con una conversazione attiva; il personaggio deve salutare prima di sparire e usare varianti diverse nelle sessioni successive.
6. Dal telefono verificare home a icone, chiamate separate e miniature centrate; nessun suono messaggio deve partire durante il dialogo di persona.

## alfa 8.6 — Dialoghi immediati e miniature viso

- Aggiunte 19 miniature dedicate da 320×320 pixel: nove per i personaggi e dieci per gli aspetti selezionabili del protagonista.
- Nelle conversazioni, nei contatti e nel profilo del telefono viene ora mostrato un primo piano del volto invece dello sprite intero rimpicciolito.
- Le miniature usano WebP ad alta definizione con trasparenza e pesano complessivamente circa 1 MB, evitando di decodificare gli sprite completi dentro cerchi da 42–122 dp senza rendere sfocati i volti.
- Gli sprite originali a figura intera e le immagini grandi restano invariati e continuano a essere usati nelle scene e nei dialoghi visual novel.
- Età, nome, genere, lavoro e preferenze già presenti nel profilo ricevono una risposta offline immediata, senza preparare né interrogare il GGUF.
- I saluti isolati restano neutrali e non generano più metatesto, argomenti inesistenti o aumenti relazionali.
- Le risposte brevi «sì» e «no» vengono collegate all'ultima battuta del personaggio senza trascinare automaticamente un vecchio argomento.
- Domande come «c'è qualche problema?» usano la memoria emotiva reale: il personaggio non inventa tensioni se non è avvenuto un evento negativo.
- Diagnostica IA riorganizzata con il pulsante `COPIA RISULTATO` sempre in alto e un riepilogo degli ultimi sei passaggi: percorso deterministico/GGUF, tema, decisione sulla cache, primo testo percepito, durata totale, correzione automatica e variazione dei punteggi.
- Il registro nativo misura separatamente caricamento del modello, preparazione cache, valutazione del prompt e generazione; il primo token riporta ora anche il tempo trascorso dall'inizio reale della chiamata GGUF.
- Versione Android aggiornata a `versionCode 33` e `versionName alfa8.6`, mantenendo firma e `applicationId` invariati.

## alfa 8.5 — Tre personaggi maschili

- Aggiunti **Luca Ferri**, fotografo urbano riservato e romantico; **Matteo Serra**, barman carismatico e diretto; **Kenji Nakamura**, sviluppatore introverso e difficile da conquistare.
- Ogni nuovo personaggio dispone di background, conflitto interiore, momenti narrativi, lavoro, hobby, gusti, ricordi, sogni, paure e soglie di relazione proprie.
- Relazioni, cronologie, memorie, disponibilità nei luoghi e progressione della galleria restano completamente separate per ciascuno dei nove personaggi.
- Aggiunti calendari differenti per giorni alterni: Luca frequenta centro, parco e rooftop; Matteo gravita fra spiaggia, palestra e Bar Velvet; Kenji alterna mall, caffetteria, centro e luoghi notturni.
- Il motore di dialogo riconosce il genere maschile dei personaggi, usa forme grammaticali coerenti e distingue correttamente il loro genere da quello scelto dal giocatore.
- Estesi i controlli contro offese e pressioni anche alle forme maschili e rese neutre le memorie emotive condivise dal sistema relazionale.
- Aggiunte per ogni nuovo personaggio cinque immagini in ordine cronologico: profilo, casual, flirt, appuntamento e relazione intima. Tutte hanno sfondo alfa realmente trasparente e outfit distinti.
- I salvataggi precedenti restano compatibili: caricandoli, Luca, Matteo e Kenji vengono aggiunti automaticamente con relazioni iniziali separate.
- Versione Android aggiornata a `versionCode 32` e `versionName alfa8.5`, mantenendo firma e `applicationId` invariati.

## alfa 8.4 — Memoria relazionale offline

- Streaming nativo della risposta GGUF: con il Qwen 3B il testo viene mostrato progressivamente appena sono disponibili i primi token, senza attendere la fine dell'intera generazione.
- La bozza in streaming resta separata dalla cronologia: soltanto la risposta completa, ripulita e validata dal motore ibrido viene salvata nella conversazione.
- Diagnostica ampliata con durata della preparazione del cache, tempo al primo token, numero di token generati e token al secondo, così è possibile distinguere il caricamento del contesto dalla generazione.
- Qwen 2.5 3B Q4_K_M è il modello offline di riferimento per l'equilibrio tra coerenza e velocità; il 4B resta importabile, ma sui dispositivi mobili può essere sensibilmente più lento.
- Valutatore relazionale deterministico indipendente dal GGUF: ascolto, domande interessate, confidenze, interessi compatibili e complimenti adeguati possono aumentare affetto, fiducia o attrazione anche quando il modello non restituisce punteggi affidabili.
- Corretta la polarità di gusti e antipatie: dire «odio l'arroganza» a un personaggio che non sopporta l'arroganza crea sintonia, mentre apprezzare ciò che detesta può ridurla.
- La difficoltà del personaggio regola la frequenza degli aumenti; saluti, messaggi vuoti e ripetizioni non producono punti automatici, mentre offese, pressione e insistenza possono farli diminuire.
- Domande naturali riconosciute anche senza forma rigida o punto interrogativo, comprese richieste come «parlami di te», «raccontami» e «vorrei conoscerti».
- Correzioni, limiti personali e avances intime premature ricevono una risposta relazionale coerente prima di preparare il GGUF, riducendo l'attesa nei casi che non richiedono generazione.
- Il risultato dei controlli deterministici viene passato al GGUF come blocco `CONTROLLO` compatto con tema, soggetto, intenzione, relazione, azione, fatto certo e divieti; il modello deve soltanto trasformarlo in dialogo naturale.
- Nessun secondo modello viene mantenuto in RAM: sul telefono la classificazione resta deterministica e quasi istantanea, evitando concorrenza di memoria e processori con il GGUF 3B.
- Memoria del giocatore e memoria emotiva vengono aggiunte al turno soltanto quando pertinenti, in campi brevi e separati.
- Rimossi dalle istruzioni e filtrati in uscita i riempitivi da assistente come «ho capito quello che mi stai dicendo»; le risposte devono reagire al dettaglio concreto e variare l'apertura.
- Le risposte ripetute o non ancorate al fatto pertinente vengono sostituite senza avviare una seconda generazione completa.
- Ogni personaggio conserva separatamente fatti esplicitamente dichiarati dal giocatore, ultimi argomenti e una sintesi breve degli scambi precedenti.
- Uscendo e rientrando nella chat, cambiando personaggio, riavviando l'app o caricando una partita, il contesto pertinente viene recuperato senza reinviare l'intera cronologia al GGUF.
- Ricerca dei ricordi per argomento: lavoro, gusti, hobby, luoghi, identità e famiglia vengono richiamati soltanto quando servono alla risposta.
- Memoria emotiva del rapporto per attenzioni, scuse, sintonia, insistenza, offese e limiti non rispettati.
- Affetto, attrazione e fiducia possono crescere o diminuire; le conseguenze dipendono anche da difficoltà, sensualità e livello attuale della relazione.
- Dialoghi adulti senza moralismi: un approccio sessualmente esplicito non viene considerato offensivo quando esistono sintonia e consenso; prima della necessaria confidenza il personaggio stabilisce invece un limite coerente.
- Le allucinazioni del GGUF non diventano fatti permanenti: la memoria personale viene estratta principalmente dalle dichiarazioni del giocatore e dalle risposte già validate.
- Formato di salvataggio aggiornato allo schema 3, mantenendo la lettura degli slot creati dalle versioni precedenti.
- Versione Android aggiornata a `versionCode 31` e `versionName alfa8.4`, con firma e `applicationId` invariati.

## alfa 8.3 — Dialoghi ibridi offline

- Nuovo router deterministico semantico completamente offline davanti al GGUF.
- Comprensione pesata di frasi, sinonimi e radici italiane invece della sola corrispondenza con una parola chiave.
- Riconoscimento separato di argomento, soggetto e tipo di domanda: personaggio, giocatore, entrambi, chi/cosa/dove/perché, preferenze ed esperienze.
- Gestione esplicita delle negazioni e delle richieste di cambiare argomento: il personaggio non continua a parlare del tema appena escluso.
- Continuità breve dell'argomento per domande come “perché?”, “e poi?” o “dimmi altro”, senza trascinare indiscriminatamente tutta la cronologia.
- Recupero di un solo fatto offline pertinente da lavoro, hobby, cibo, cinema, musica, luoghi, abitudini, aneddoti, ricordi, sogni, paure e famiglia.
- Soglie di fiducia applicate prima del GGUF: i dettagli sensibili non entrano nel prompt quando la relazione non li autorizza.
- Il prompt permanente non contiene più un fatto sul lavoro o un momento drammatico fisso; questo evita che Elena torni sempre al caso legale o che gli altri personaggi restino bloccati sul proprio lavoro.
- Il GGUF viene usato soltanto per trasformare intenzione e fatto verificato in una risposta naturale in prima persona.
- Validazione successiva della risposta: se il GGUF ignora il fatto, confonde il soggetto, copia il giocatore, inventa nomi o perde l'argomento, subentra subito una risposta deterministica coerente senza una seconda generazione lenta.
- Cache GGUF mantenuta tra i turni e ricompattata ogni sei risposte, con prompt per turno più corto.
- Firma, `applicationId` e compatibilità con salvataggi e alfa8 invariati.
- Versione Android aggiornata a `versionCode 30` e `versionName alfa8.3`.

## alfa 8

- Il gioco e l'invio dei messaggi restano bloccati finché un GGUF selezionato non ha completato il caricamento reale in RAM.
- Un solo modello può essere attivo: cambiandolo, il precedente viene scaricato e il nuovo contesto riparte pulito.
- I GGUF possono essere eliminati direttamente dalla configurazione; un file incompleto non viene conservato né impostato come attivo.
- Recupero automatico dopo blocco/timeout senza ripetere subito la generazione: il modello viene riavviato e il testo resta nel campo per un nuovo invio consapevole.
- Salvataggio transazionale: messaggio, risposta e variazioni di relazione vengono registrati insieme soltanto dopo una risposta valida.
- Indicatore IA sempre visibile e diagnostica coerente con lo stato nativo.
- Conversazioni dinamiche senza contatore visibile: minimo assoluto 6; Sconosciuti 8–15, Conoscenza 10–20, Amicizia 15–25, Flirt 20–35; oltre il flirt nessun limite rigido.
- Il limite viene deciso una sola volta all'inizio della conversazione, resta memorizzato chiudendo e riaprendo la chat e non può essere aggirato.
- `talks` rappresenta ora conversazioni realmente concluse e non singoli messaggi; i vecchi salvataggi vengono convertiti automaticamente.
- Fasce visibili, disponibilità dei personaggi, chiamate e limiti delle conversazioni usano lo stesso orologio: Mattina 06–11, Metà giorno 12–14, Pomeriggio 15–19, Notte 20–05.
- Un giorno di gioco continua a durare un'ora reale e l'orario scorre continuamente da 00:00 a 23:59.
- Orari multipli supportati: Ristorante Lume 11:30–15:00 e 18:30–23:30; Spiaggia Aozora 06:00–02:00.
- Quando un luogo chiude, il giocatore torna automaticamente nell'appartamento e la conversazione in corso viene conclusa.
- Template del prompt selezionato in base al GGUF: ChatML per Qwen/SmolLM, formato Llama 3 per Llama e formato generico per gli altri modelli.
- Il contesto già preparato viene riutilizzato riaprendo la stessa chat; eliminata la ricostruzione forzata che rallentava ogni accesso.
- Prompt di base alleggerito mantenendo identità, genere, personalità, relazione, fatto pertinente e ultimo scambio.
- Eliminato il secondo tentativo GGUF completo sulle risposte incoerenti, che poteva portare l'attesa oltre 80 secondi.
- Nomi estranei, confusione fra soggetti, metatesto e frammenti di prompt non vengono reinseriti nel contesto; in caso di errore l'app usa una risposta breve e coerente e ricostruisce il cache al turno successivo.
- Stato nativo e diagnostica non attendono più il mutex della generazione sul thread grafico, riducendo i rischi di schermata “l'app non risponde”.
- Timeout nativo ridotto e unico: massimo 21 secondi di valutazione del prompt e 18 secondi di generazione.
- Risposte locali con margine sufficiente per terminare la frase e filtri aggiuntivi contro token, prefissi di ruolo e trascrizioni.
- La regressione della relazione può avvenire, ma al massimo di un livello per volta.
- Test Aggiornamenti isolato: le simulazioni non modificano più relazioni, galleria o autosalvataggio della partita reale.
- Gli slot manuali non possono essere sovrascritti dal menu prima di aver iniziato o caricato una partita.
- Menu principale scorrevole, gestione dei modelli eliminabili e pulsante Riavvia IA nella diagnostica.
- Suoneria per le chiamate in arrivo e notifica sonora per le nuove risposte, entrambe sostituibili dalle risorse dell'app.
- APK prodotto come release non debuggabile, compatibile con arm64-v8a e x86_64, mantenendo firma e `applicationId` stabili.
- Il workflow confronta il certificato prodotto con l'impronta stabile attesa e interrompe la pubblicazione se la firma cambia.
- `versionCode 29` consente di installare questa revisione di alfa8 come aggiornamento sopra la precedente alfa8.

Visual novel nativa Android con personaggi, luoghi dinamici, relazioni e dialoghi IA offline tramite modelli GGUF. Il repository GitHub dell'utente è denominato **alfa2**.

## Compilazione APK

1. Caricare nella radice del repository il contenuto della cartella del progetto.
2. Aprire **Actions** su GitHub.
3. Avviare **Build Neon Tides APK**, se non parte automaticamente.
4. Scaricare l'artifact APK prodotto dalla build.

## Modelli IA

L'app permette di importare un file `.gguf` dalla memoria del telefono. In alfa 8.8 è disponibile anche il download diretto del Qwen 2.5 3B Instruct Uncensored Q4_K_M di riferimento; non viene più mostrato un catalogo generico di modelli non verificati.

## Cronologia aggiornamenti

### alfa7 — Coerenza, conoscenze e dialoghi concreti

- Il nome configurato nel profilo non viene più comunicato automaticamente ai personaggi.
- Ogni personaggio conserva separatamente la conoscenza del nome e lo apprende soltanto dopo una presentazione esplicita del giocatore.
- La conoscenza del nome e la sintesi compatta della conversazione sono incluse nell'autosalvataggio e negli slot manuali, mantenendo compatibilità con i vecchi salvataggi.
- Aggiunti fatti professionali concreti per tutti i personaggi: luogo di lavoro, mansioni e progetto corrente.
- Le domande dirette sul lavoro richiedono una risposta concreta; una risposta vaga viene rigenerata e, se necessario, sostituita con fatti verificati del profilo.
- Separati i fatti ordinari dai segreti narrativi: traumi, casi passati e conflitti non vengono più introdotti prematuramente.
- Vietata l'invenzione di casi, clienti, eventi passati, relazioni o emozioni non presenti nel background.
- Memoria contestuale alleggerita: sintesi affidabile e ultimi due messaggi invece dell'accumulo indiscriminato della chat.
- Dossier personali e professionali conservati offline: il GGUF riceve soltanto il fatto pertinente alla domanda corrente e lo amplia in linguaggio naturale.
- Hobby, cibi, bevande, cinema, musica, luoghi, abitudini, aneddoti, ricordi, sogni e paure definiti separatamente per ciascun personaggio.
- Accesso progressivo ai dettagli: gusti e hobby sono disponibili presto, piccoli aneddoti con poca fiducia, ricordi familiari con fiducia media e paure intime soltanto con fiducia alta.
- Le soglie dei dettagli sensibili aumentano per i personaggi con maggiore difficoltà di conquista.
- Conversazioni a durata realistica: 2–3 messaggi con gli sconosciuti, 4–5 durante la conoscenza, 7 in amicizia e 9 durante il flirt.
- Dall'attrazione reciproca le conversazioni non hanno più un limite rigido.
- Raggiunto il limite, il personaggio conclude naturalmente e torna disponibile nella fascia oraria successiva, favorendo incontri con persone diverse.
- Galleria suddivisa in sei sottosezioni selezionabili, una per personaggio, con contatore delle immagini sbloccate.
- Dal livello Amicizia è possibile chiamare ogni personaggio direttamente dal telefono.
- Chiamate in arrivo casuali valutate al cambio di fascia oraria: la probabilità cresce con affetto, attrazione, fiducia ed estroversione.
- Chiamate e risposte vengono registrate nella chat separata e conservate nei salvataggi.
- Chat del telefono ridisegnate con miniature circolari del volto del personaggio e del protagonista accanto a ogni messaggio.
- La sintesi precedente viene reinserita soltanto quando condivide davvero l'argomento del nuovo messaggio; cambiando tema il personaggio non resta fissato sul lavoro o sul problema precedente.
- Punteggi relazione meno permissivi: saluti e messaggi generici non assegnano più automaticamente affetto e fiducia.
- Corretto lo scorrimento della chat: l'ultimo messaggio viene portato sopra il campo di scrittura e non rimane coperto.
- Versione Android aggiornata a `versionCode 27` e `versionName alfa7`, mantenendo firma e `applicationId` stabili.

### alfa6 — Dialoghi, storie personali e orari

- Scelta del genere del protagonista (`Maschio` o `Femmina`) durante la creazione del profilo.
- Selezione visiva del protagonista con cinque aspetti maschili e cinque femminili, coerenti con lo stile anime semi-realistico del gioco e dotati di sfondo trasparente reale.
- Le opzioni cambiano automaticamente in base al genere scelto; l'aspetto selezionato viene salvato nell'autosave e negli slot manuali e mostrato nel profilo del telefono.
- Genere del giocatore incluso in autosalvataggio, slot manuali, profilo e contesto IA; i vecchi salvataggi restano compatibili.
- Genere di ogni personaggio definito come dato certo nel prompt, con istruzioni per pronomi e riferimenti coerenti e senza scambio dei soggetti.
- Progressione della relazione distribuita su più giorni: non è possibile superare più livelli nello stesso giorno di gioco.
- Tempi di conquista personalizzati in base alla difficoltà e all'indole di ciascun personaggio; i punteggi possono accumularsi, ma la relazione avanza solo quando esistono tempo e requisiti sufficienti.
- Giorno del primo incontro e ultimo cambio di livello inclusi nei salvataggi, mantenendo la compatibilità con quelli precedenti.
- Immagini di profilo, casual, flirt, appuntamento e intimità collegate al livello effettivo della relazione, evitando sblocchi multipli nello stesso giorno.
- Luoghi accessibili solo nelle fasce in cui sono aperti; la mappa mostra chiaramente stato e fasce disponibili.
- Presenza dei personaggi sincronizzata sia con il loro programma sia con l'apertura del luogo.
- Risposte locali più lunghe, con limite dinamico secondo il modello e arresto alla fine di una frase completa.
- Timeout di generazione adattato ai modelli 3B per ridurre i messaggi troncati.
- Turni ChatML chiusi esplicitamente per impedire che Qwen confonda giocatore e personaggio dopo più messaggi.
- Ruoli rigidi `GIOCATORE` e `PERSONAGGIO`, ricompattazione del contesto ogni quattro conversazioni e memoria recente controllata.
- Filtro di prefissi, trascrizioni, metatesto e formule come “Ecco Luna a rispondere”.
- Controllo anti-eco con una seconda generazione quando il modello copia o trasforma la frase del giocatore in domanda.
- Background approfondito, conflitto interiore e quattro momenti narrativi progressivi per ciascuna delle sei protagoniste.
- Il prompt usa soltanto il dettaglio narrativo pertinente alla relazione, evitando di recitare intere biografie.
- Versione Android `versionCode 26`, `versionName alfa6`, mantenendo la firma stabile introdotta con alfa5.

### alfa5 — Orologio, ordine galleria e aggiornamenti APK

- Orologio di gioco visibile nella scena e nella mappa.
- Un'ora reale equivale a un giorno completo di gioco.
- Quattro fasi sincronizzate di 15 minuti: Mattina, Metà giorno, Pomeriggio e Notte.
- Posizione e disponibilità dei personaggi collegate alla fase mostrata dall'orologio.
- Il cambio di luogo non altera più artificialmente la fascia oraria.
- Epoca della simulazione inclusa in autosalvataggio e slot manuali, con compatibilità per i vecchi salvataggi.
- Trenta immagini della galleria riordinate, per ogni personaggio, dalla meno succinta alla più intima: profilo, casual, flirt, appuntamento e intimità.
- Anche “Test Aggiornamenti” segue rigorosamente la stessa progressione.
- Versione Android aggiornata a `versionCode 25` e `versionName alfa5`.
- Firma APK stabile e selezionata esplicitamente, senza dipendere dalla cache GitHub.
- Verifica automatica del certificato dopo ogni compilazione GitHub.
- Corretto il percorso di `apksigner` nel workflow GitHub per evitare l'errore `command not found` dopo una compilazione riuscita.
- Dopo la prima installazione firmata alfa5, le build successive potranno essere installate come aggiornamenti mantenendo la stessa chiave e lo stesso `applicationId`.

### alfa4 — Galleria e nuova numerazione

- La progressione delle build passa alla serie `alfa`: le successive saranno `alfa5`, `alfa6` e così via.
- Galleria accessibile direttamente dal menu principale.
- Cinque livelli di immagini per ogni personaggio: primo incontro, momento personale, complicità, appuntamento privato e intimità.
- Sblocchi collegati a conversazioni, affetto, attrazione, fiducia e fase della relazione.
- Sblocchi conservati localmente anche se la relazione successivamente diminuisce.
- Dati della galleria inclusi nell'autosalvataggio e negli slot manuali.
- Incluse 30 illustrazioni progressive: 5 livelli dedicati per ciascuna delle 6 protagoniste.
- Gli abiti diventano gradualmente più audaci con l'aumento di confidenza, attrazione e relazione.
- Popup a schermo intero nel momento esatto in cui viene sbloccata una nuova immagine, con accesso diretto alla galleria.
- Voce menu **Test Aggiornamenti**: simula i cinque livelli di ogni personaggio usando lo stesso sistema di sblocco del gioco.
- Corrette le immagini della galleria: trasparenza reale, rimozione degli sfondi bianchi/scacchi e dei frammenti delle pose adiacenti.
- Ottimizzato il GGUF 3B: cache KV nativa reale, invio del solo nuovo turno, ricompattazione ogni 8 conversazioni e risposte limitate a 40 token.
- Corretto il nome del motore mostrato sotto le risposte; un timeout locale non disabilita più il GGUF fino al riavvio.
- Predisposta la struttura per inserire immagini progressive coerenti con l'aspetto di ciascun personaggio.

### 0.23 — Dialoghi più umani e Qwen 3B

- Dialoghi adattati automaticamente alla fase della relazione.
- Maggiore intimità soltanto con crescita di affetto, attrazione e fiducia.
- Valutazione delle risposte in base a indole, interessi, argomenti sgraditi e livello di confidenza.
- Punteggi in grado di aumentare o diminuire e fase della relazione reversibile.
- Penalità per avances premature e messaggi ripetuti.
- Regole contro contraddizioni evidenti e mancata accettazione delle correzioni del giocatore.
- Catalogo alleggerito: rimossi i modelli meno validi e aggiunti Qwen 3B normale e Qwen 3B Uncensored.
- Unico README con cronologia centralizzata; rimossi i vecchi file informativi duplicati.

Problema noto: Qwen 3B Q4_K_M richiede circa 1,9 GB per il file e può essere sensibilmente più lento dei modelli 1,5B sui telefoni con poca RAM disponibile.

### 0.22 — Dialoghi e relazioni

- Separati correttamente interlocutore e personaggio nel prompt.
- Cronologia inviata con ruoli `user` e `assistant`.
- Filtro contro nomi dei ruoli, trascrizioni e note interne nelle risposte.
- Indole, difficoltà di conquista, estroversione, romanticismo, sensualità e gelosia distinti per personaggio.
- Reazioni influenzate da interessi e argomenti sgraditi.
- Invito nell'appartamento con requisiti personalizzati.
- Presenza del personaggio invitato nello scenario dell'appartamento.
- Salvataggio locale di visite, decisioni e conseguenze.
- Catalogo per scaricare GGUF normali o uncensored.
- Importazione di modelli GGUF già presenti sul telefono.
- Conferma prima di sostituire una partita esistente.
- Eliminata la creazione di un autosalvataggio vuoto al primo avvio.

### 0.21 — Profilo giocatore e coerenza

- Creazione del protagonista con nome, età e stile.
- Profilo incluso nell'autosalvataggio e nei tre slot manuali.
- Compatibilità con i salvataggi precedenti.
- Contesto con gli ultimi quattro messaggi.
- Parametri di generazione meno casuali e risposte meno troncate.

### 0.20 — Ottimizzazione ARM e diagnostica

- Ottimizzazioni ARMv8.2 e dot-product per dispositivi compatibili.
- Utilizzo fino a sei thread per llama.cpp.
- Prompt locale compatto.
- Tempi di tokenizzazione, elaborazione e generazione nel registro diagnostico.
- Distinzione del motore GGUF attivo.

### 0.18–0.19 — Diagnostica e schermo attivo

- Schermata diagnostica IA nel menu principale.
- Stato libreria llama.cpp, modello, RAM e registro nativo.
- Schermo mantenuto acceso durante l'utilizzo dell'app.

### Versioni precedenti

- Interfaccia nativa Android con menu, mappa, telefono e chat in finestra.
- Sfondi dedicati ai luoghi e personaggi disponibili secondo giorno e orario.
- Cronologie separate per ogni personaggio.
- Autosalvataggio e tre slot manuali.
- Fallback Gemini e OpenAI configurabile sul dispositivo.
- Importazione e selezione di modelli GGUF locali.

## Regola per le versioni future

Ogni nuova build deve aggiungere una sezione in cima alla cronologia con numero di versione, modifiche, correzioni e problemi noti. Evitare file README o note di versione duplicati.
