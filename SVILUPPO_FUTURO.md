# Neon Tides · sviluppo futuro

Questo documento conserva le idee e le specifiche **non ancora necessariamente implementate**. Il `README.md` descrive invece le versioni già costruite. Quando una voce viene completata, va indicata come implementata nella relativa versione e rimossa dalle attività aperte di questo documento.

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
