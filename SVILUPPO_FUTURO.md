# Neon Tides · sviluppo futuro

Questo documento conserva le idee e le specifiche **non ancora necessariamente implementate**. Il `README.md` descrive invece le versioni già costruite. Quando una voce viene completata, va indicata come implementata nella relativa versione e rimossa dalle attività aperte di questo documento.

## Stato corrente

### alfa8.10.7 diagnostica isolata — `in sviluppo`

- Nessuna modifica prevista a prompt, cache, timeout, memoria, deterministico,
  punteggi o logica della partita rispetto alla 8.10.6.
- Laboratorio separato di Luna con lo stesso aspetto e lo stesso percorso IA
  della chat reale: avatar, messaggi, streaming, campo di testo e modello attivo.
- Affetto, Attrazione e Fiducia regolabili nel laboratorio senza scrivere nella
  relazione reale, nei salvataggi o nella cronologia principale.
- Diagnostica Luna separata con domanda, testo grezzo del motore, testo visto in
  streaming, risposta finale, eventuale correzione e relativo motivo.
- Campionamento per ogni inferenza di CPU media/picco, RAM media/picco, heap
  nativo/JVM, RAM disponibile, memoria bassa e pressione termica.
- Copia ed esportazione in TXT sia per il laboratorio Luna sia per la
  diagnostica principale.
- La risposta in streaming sostituita dal deterministico deve rimanere nel log
  per consentire di riconoscere correzioni inutili o falsi positivi.
- Il difetto del personaggio che sparisce mentre si scrive resta registrato, ma
  non viene corretto in questa build diagnostica per non introdurre una seconda
  variabile durante i test.

## Decisioni tecniche confermate

- Le versioni seguono sempre l'ordine numerico: dopo 8.10.6 viene 8.10.7.
- Una build di prova non deve cambiare parti non coinvolte dell'architettura o
  dell'interfaccia principale.
- I motori reali llama.cpp/GGUF e MLC devono poter essere provati in un ambiente
  isolato, mantenendo separati dati e diagnostica dalla partita.
- La trama, le attività, i luoghi, gli inventari e le relazioni sono stato
  deterministico del gioco; il modello riceve soltanto scena e blocchi di memoria
  pertinenti e formula il dialogo.
- Prima di modificare il deterministico in base ai test Thea/Qwen si raccolgono
  testo grezzo, streaming, risposta finale e motivo esatto della correzione.
- Il test Thea su 8.10.6 ha mostrato parziali di circa 26–43 token prima del
  timeout di generazione: non vanno classificati come risposte semanticamente
  fallite finché la 8.10.7 non permette di leggerli. Solo dopo la misura si
  valuterà il recupero dell'ultima frase completa senza alterare la cache.
- La specifica canonica per un futuro fine-tuning è mantenuta in
  `README_ADDESTRAMENTO_MODELLO.md`. Il modello formula i dialoghi, mentre stato,
  trama, memoria, attività, punteggi e autorizzazioni restano deterministici.

## Trama canonica confermata

- L’ambientazione futura è Tokyo contemporanea.
- Strane sparizioni, inizialmente inspiegabili, vengono collegate ad antiche leggende del folklore giapponese tornate a manifestarsi nel mondo moderno.
- Il gruppo principale indaga sugli avvenimenti e scopre progressivamente segreti, legami e contraddizioni degli altri NPC.
- Le entità previste comprendono Yōkai, Yūrei, Onryō, Oni e Shinigami. Fra gli esempi già scelti figurano Kitsune, Kappa e Tsukumogami.
- Il soprannaturale non deve ridursi a incontri casuali scollegati: reazioni, testimonianze e segreti dei personaggi devono modificare gli sviluppi della trama.

## Sistemi narrativi pianificati

- Gestore deterministico di tempo e luoghi di Tokyo, con fascia oraria e luogo coerenti per ogni evento.
- Motore degli incontri capace di selezionare due o più NPC presenti nella zona in base a orari e probabilità.
- Eventi soprannaturali contestuali, per esempio un’ombra che spegne le luci di Omoide Yokocho a mezzanotte.
- Reazioni differenti secondo il carattere: un NPC scettico può confrontarsi con un personaggio esperto di folklore.
- Memoria locale distinta per ogni NPC: confidenza, affinità, segreti condivisi, ricordi e rapporti con gli altri personaggi.
- Incontri casuali fra NPC che possono rientrare successivamente nelle conversazioni e influenzare l’indagine.
- Messaggi di testo e vocali sugli avvistamenti; chat di gruppo, pettegolezzi, screenshot di conversazioni private e immagini degli eventi quando narrativamente giustificati.
- Scelte di risposta capaci di modificare fiducia e affinità e di sbloccare confidenze e dialoghi esclusivi.
- Motore deterministico responsabile dei valori e delle autorizzazioni; GGUF responsabile del tono e della formulazione naturale coerente con personalità e relazione.

## Principi già fissati per relazioni e personaggi

- Le relazioni crescono gradualmente attraverso scelte, conversazioni e azioni; non avanzano di più livelli nello stesso giorno.
- Affetto, Attrazione e Fiducia devono restare distinti e verificabili.
- Ogni personaggio conserva identità, personalità, backstory, hobby, gusti, esperienze, ricordi e segreti propri.
- Il personaggio non conosce il nome del giocatore finché questo non glielo comunica.
- Le conversazioni devono essere concrete, coerenti nei soggetti e prive di prefissi o metatesto tecnico.
- L’intimità e gli inviti dipendono da fiducia, sintonia, consenso, carattere e fase della relazione.

## Decisioni ancora aperte

- Evento iniziale preciso che fa scattare l’indagine.
- Elenco definitivo delle location di Tokyo e relativi orari.
- Schede complete degli NPC e collegamento di ciascuno a una parte del mistero.
- Regole narrative specifiche per l’intervento degli Shinigami.
- Struttura degli archi, antagonista o conflitto centrale e condizioni dei finali.

## Regola di manutenzione

Ogni nuovo suggerimento futuro va aggiunto qui con uno stato chiaro: `proposto`, `confermato`, `in sviluppo` oppure `implementato`. Non va presentato come già disponibile nell’app finché non compare anche nel riepilogo della versione in `README.md`.
