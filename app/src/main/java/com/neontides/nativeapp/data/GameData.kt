package com.neontides.nativeapp.data

import com.neontides.nativeapp.model.CharacterProfile
import com.neontides.nativeapp.model.Location
import com.neontides.nativeapp.model.OpeningWindow

object GameData {
    val characters = listOf(
        CharacterProfile("sofia","Sofia Valli",27,"Architetta d'interni",
            "Elegante, osservatrice, ironica e selettiva.",
            listOf("caffè","design","tramonti"), listOf("bugie","caos"),
            conquestDifficulty = 5, extroversion = 2, sensuality = 2, romance = 4, jealousy = 3,
            background = "È cresciuta tra una madre restauratrice e un padre spesso assente. Ha trasformato il bisogno di ordine in talento creativo, ma una relazione passata che svalutava il suo lavoro l'ha resa selettiva.",
            innerConflict = "Desidera essere vista davvero, ma teme che mostrarsi vulnerabile significhi perdere il controllo.",
            storyBeats = listOf("parla del primo progetto fallito", "confida il rapporto difficile con il padre", "mostra il luogo che sogna di restaurare", "sceglie di affidarsi senza nascondersi dietro la perfezione"),
            workFacts = listOf("Lavora in uno studio di architettura d'interni nel centro di Neon Bay.", "Progetta appartamenti, locali e piccoli spazi commerciali.", "In questo periodo sta restaurando un loft ricavato da una vecchia tipografia."),
            personalFacts = mapOf(
                "hobby" to listOf("Restaura piccoli mobili trovati nei mercatini e fotografa dettagli architettonici.", "Le piace disegnare interni nei caffè tranquilli."),
                "cibo" to listOf("Adora il risotto ai funghi e i dolci poco zuccherati.", "Beve caffè macchiato e sceglie vino rosso nelle occasioni speciali."),
                "cinema" to listOf("Preferisce thriller psicologici e film d'autore con scenografie curate.", "Rivede volentieri In the Mood for Love per colori e ambienti."),
                "musica" to listOf("Ascolta jazz morbido e pianoforte mentre progetta."),
                "luoghi" to listOf("Il suo luogo preferito è una vecchia serra sul tetto di un edificio del centro."),
                "abitudini" to listOf("Allinea inconsciamente gli oggetti quando è nervosa e porta sempre un piccolo metro da architetto."),
                "aneddoti" to listOf("Una volta ridisegnò mentalmente un intero ristorante durante un appuntamento e si accorse di non aver ascoltato metà della conversazione."),
                "ricordi" to listOf("Il ricordo più felice è il primo mobile restaurato insieme alla madre."),
                "sogni" to listOf("Sogna di restaurare un edificio storico e trasformarlo in uno spazio aperto agli artisti."),
                "paure" to listOf("Teme che qualcuno possa svalutare di nuovo il suo talento o abbandonarla quando perde il controllo.")
            ),
            inviteTrust = 35, inviteAffection = 25, inviteTalks = 5),
        CharacterProfile("maya","Maya Rossi",24,"Personal trainer",
            "Energica, diretta, competitiva e calorosa.",
            listOf("mare","sport","sfide"), listOf("passività","ritardi"),
            conquestDifficulty = 3, extroversion = 5, sensuality = 4, romance = 2, jealousy = 2,
            background = "Da adolescente era insicura e trovò nello sport un modo per ricostruire fiducia. Dopo un infortunio perse una gara decisiva e imparò a nascondere la paura dietro energia e competizione.",
            innerConflict = "Vuole essere amata anche quando non è forte, ma teme di deludere chi conta su di lei.",
            storyBeats = listOf("racconta la gara che le cambiò la vita", "ammette la paura di fermarsi", "chiede sostegno per una nuova sfida", "accetta di non dover dimostrare sempre qualcosa"),
            workFacts = listOf("Lavora come personal trainer alla palestra Pulse.", "Segue allenamenti individuali e piccoli gruppi funzionali.", "Sta preparando una cliente alla sua prima gara amatoriale."),
            personalFacts = mapOf(
                "hobby" to listOf("Ama nuotare all'alba, andare in bicicletta e provare percorsi avventura.", "Nei giorni tranquilli coltiva piante aromatiche sul balcone."),
                "cibo" to listOf("Adora poke al salmone, pasta al pesto e gelato al pistacchio.", "Beve spremute fresche, ma dopo gli allenamenti si concede un cappuccino."),
                "cinema" to listOf("Preferisce commedie d'azione e film sportivi ispirati a storie vere."),
                "musica" to listOf("Si allena con dance, rock energico e playlist anni Duemila."),
                "luoghi" to listOf("La spiaggia Aozora è il posto in cui va quando ha bisogno di respirare."),
                "abitudini" to listOf("Conta i gradini senza accorgersene e sfida spesso gli amici a piccole gare."),
                "aneddoti" to listOf("Durante la sua prima lezione confuse due timer e fece allenare il gruppo al doppio del ritmo previsto, finendo gli esercizi insieme a loro."),
                "ricordi" to listOf("Ricorda con affetto il padre che l'aspettava al traguardo della sua prima corsa."),
                "sogni" to listOf("Vorrebbe aprire un centro accessibile anche a chi si sente fuori posto nelle palestre tradizionali."),
                "paure" to listOf("Ha paura che un nuovo infortunio possa toglierle l'indipendenza e deludere chi crede in lei.")
            ),
            inviteTrust = 20, inviteAffection = 15, inviteTalks = 3),
        CharacterProfile("elena","Elena Conti",30,"Avvocata",
            "Raffinata, composta, analitica ma sensibile.",
            listOf("vino","arte","precisione"), listOf("superficialità"),
            conquestDifficulty = 5, extroversion = 2, sensuality = 2, romance = 4, jealousy = 3,
            background = "Proviene da una famiglia esigente in cui ogni successo era considerato normale. Ha scelto legge per proteggere chi non aveva voce, ma un caso perso ingiustamente continua a tormentarla.",
            innerConflict = "Cerca giustizia e controllo, mentre teme che le emozioni possano compromettere le sue scelte.",
            storyBeats = listOf("accenna al caso che non dimentica", "rivela quanto pesa l'approvazione familiare", "chiede un parere su una scelta morale", "permette a qualcuno di sostenerla invece di affrontare tutto sola"),
            workFacts = listOf("Lavora in uno studio legale nel centro di Neon Bay.", "Si occupa soprattutto di diritto del lavoro e tutela delle persone licenziate ingiustamente.", "In questo periodo sta preparando un'udienza per una dipendente in conflitto con la propria azienda."),
            personalFacts = mapOf(
                "hobby" to listOf("Visita mostre, legge saggi storici e gioca a scacchi per rilassarsi.", "Le piace camminare sul porto la sera senza telefono."),
                "cibo" to listOf("Preferisce pasta alle vongole, cioccolato fondente e cucina mediterranea.", "Ordina tè nero al mattino e un calice di vino rosso a cena."),
                "cinema" to listOf("Ama drammi giudiziari e thriller investigativi con personaggi moralmente complessi.", "Uno dei film che apprezza di più è Anatomia di una caduta."),
                "musica" to listOf("Ascolta musica classica, soprattutto violoncello, quando prepara un'udienza."),
                "luoghi" to listOf("Si sente al sicuro nella piccola sala silenziosa del museo civico."),
                "abitudini" to listOf("Prende appunti su taccuini color crema e controlla due volte ogni porta prima di uscire."),
                "aneddoti" to listOf("Al suo primo giorno in tribunale entrò nell'aula sbagliata e rimase ad ascoltare dieci minuti prima di accorgersene."),
                "ricordi" to listOf("Conserva il ricordo della prima causa vinta per una lavoratrice che nessuno voleva ascoltare."),
                "sogni" to listOf("Vorrebbe fondare uno sportello legale gratuito per chi non può permettersi assistenza."),
                "paure" to listOf("Teme di commettere un errore capace di danneggiare una persona innocente e di non essere mai abbastanza per la famiglia.")
            ),
            inviteTrust = 40, inviteAffection = 30, inviteTalks = 6),
        CharacterProfile("luna","Luna Hayashi",22,"Cantautrice",
            "Spontanea, creativa, magnetica e imprevedibile.",
            listOf("musica","notte","libertà"), listOf("routine","controllo"),
            conquestDifficulty = 2, extroversion = 5, sensuality = 5, romance = 2, jealousy = 1,
            background = "È cresciuta cambiando spesso città con una madre musicista. Scrive canzoni per dare una casa ai ricordi, ma un contratto discografico soffocante le ha fatto temere di perdere la propria voce.",
            innerConflict = "Insegue libertà e spontaneità, ma desidera segretamente qualcuno che resti quando il palco si spegne.",
            storyBeats = listOf("racconta l'origine di una melodia incompiuta", "confessa il timore di essere usata", "fa ascoltare una canzone mai pubblicata", "sceglie di condividere il futuro senza rinunciare alla libertà"),
            workFacts = listOf("Scrive e interpreta brani pop elettronici con influenze alternative.", "Si esibisce spesso al Club Eclipse e registra demo in un piccolo studio indipendente.", "Sta completando un brano chiamato Luci sul mare, ancora inedito."),
            personalFacts = mapOf(
                "hobby" to listOf("Registra suoni della città, colleziona cassette e improvvisa melodie con una vecchia tastiera.", "Disegna stelle sui quaderni dove scrive i testi."),
                "cibo" to listOf("Ama ramen piccante, takoyaki e pancake con frutti di bosco.", "Beve tè al gelsomino di giorno e ginger ale dopo i concerti."),
                "cinema" to listOf("Preferisce musical, animazione giapponese e film indipendenti sulla crescita personale."),
                "musica" to listOf("Ascolta synth-pop, indie rock e cantautrici che scrivono testi molto personali."),
                "luoghi" to listOf("Il rooftop Aurora è il suo rifugio per scrivere quando la città si fa silenziosa."),
                "abitudini" to listOf("Tamburella ritmi sulle superfici e registra idee vocali nel cuore della notte."),
                "aneddoti" to listOf("Una melodia nata dal rumore intermittente di un distributore automatico è diventata il ritornello più applaudito dei suoi concerti."),
                "ricordi" to listOf("Il ricordo più dolce è cantare in auto con la madre durante uno dei loro tanti traslochi."),
                "sogni" to listOf("Vuole pubblicare un album completamente suo e suonarlo davanti a persone che ne comprendano davvero le parole."),
                "paure" to listOf("Teme che il successo possa trasformarla in un prodotto e che le persone amino il personaggio sul palco invece di lei.")
            ),
            inviteTrust = 15, inviteAffection = 12, inviteTalks = 2),
        CharacterProfile("chiara","Chiara Moretti",28,"Illustratrice",
            "Creativa, sarcastica, curiosa e indipendente.",
            listOf("disegno","parchi","cinema"), listOf("arroganza"),
            conquestDifficulty = 4, extroversion = 3, sensuality = 3, romance = 3, jealousy = 2,
            background = "Disegnava di nascosto in una famiglia che voleva per lei un lavoro sicuro. Una mostra andata male la spinse a lavorare da sola e a usare il sarcasmo come difesa dalle critiche.",
            innerConflict = "Vuole che la sua arte venga compresa, ma teme che ogni giudizio confermi di non essere abbastanza.",
            storyBeats = listOf("mostra un taccuino che nessuno ha visto", "racconta la mostra che l'ha ferita", "cerca ispirazione per un'opera personale", "espone qualcosa di autentico senza proteggersi con l'ironia"),
            workFacts = listOf("Lavora come illustratrice freelance dal suo studio domestico.", "Realizza copertine, manifesti e concept art per clienti editoriali.", "Sta disegnando la copertina di un romanzo fantasy ambientato in una città sommersa."),
            personalFacts = mapOf(
                "hobby" to listOf("Disegna persone incontrate sui mezzi, visita mercatini di fumetti e fotografa insegne strane.", "Coltiva un piccolo terrario vicino alla scrivania."),
                "cibo" to listOf("Adora lasagne vegetariane, gyoza e tiramisù.", "Beve cioccolata calda mentre lavora di notte."),
                "cinema" to listOf("Ama fantasy visivi, commedie nere e film d'animazione realizzati a mano."),
                "musica" to listOf("Ascolta indie folk e colonne sonore strumentali mentre disegna."),
                "luoghi" to listOf("Il suo posto preferito è una panchina appartata del Parco Sakura."),
                "abitudini" to listOf("Mastica il tappo della penna quando è bloccata e nasconde piccoli autoritratti nei suoi lavori."),
                "aneddoti" to listOf("Un cliente approvò per errore una bozza contenente un minuscolo gatto che Chiara aveva disegnato per scherzo, e il gatto finì sulla copertina definitiva."),
                "ricordi" to listOf("Ricorda il giorno in cui un'insegnante appese un suo disegno senza dirle nulla e tutta la classe si fermò a guardarlo."),
                "sogni" to listOf("Vorrebbe pubblicare un proprio romanzo illustrato senza modificare lo stile per compiacere qualcuno."),
                "paure" to listOf("Teme che le critiche dimostrino che la famiglia aveva ragione a considerare l'arte una scelta poco seria.")
            ),
            inviteTrust = 30, inviteAffection = 20, inviteTalks = 4),
        CharacterProfile("nadia","Nadia Costa",34,"Chef",
            "Sicura di sé, protettiva, intensa e ambiziosa.",
            listOf("cucina","vino","lealtà"), listOf("tradimenti"),
            conquestDifficulty = 4, extroversion = 4, sensuality = 4, romance = 3, jealousy = 4,
            background = "Ha imparato a cucinare nel piccolo locale dei nonni e ha costruito la propria carriera dopo il tradimento di un socio. Protegge la brigata come una famiglia e misura l'affetto attraverso gesti concreti.",
            innerConflict = "Desidera condividere ciò che ha costruito, ma teme che fidarsi significhi offrire a qualcuno il potere di tradirla.",
            storyBeats = listOf("prepara una ricetta legata ai nonni", "racconta il tradimento del vecchio socio", "affida una responsabilità importante", "apre la sua cucina e la sua vita senza aspettarsi un abbandono"),
            workFacts = listOf("È chef e proprietaria del ristorante Lume sul porto.", "Guida una brigata di cucina di otto persone e cambia il menu con le stagioni.", "Sta perfezionando un piatto di pesce ispirato a una ricetta dei nonni."),
            personalFacts = mapOf(
                "hobby" to listOf("Cerca ingredienti nei mercati, coltiva peperoncini e colleziona vecchi ricettari scritti a mano.", "Le piace ballare salsa quando la cucina chiude."),
                "cibo" to listOf("Il suo piatto del cuore è la zuppa di pesce dei nonni, ma ama anche il pane appena sfornato.", "Beve espresso molto corto e preferisce vini bianchi secchi."),
                "cinema" to listOf("Guarda drammi familiari, documentari gastronomici e vecchie commedie italiane."),
                "musica" to listOf("In cucina ascolta soul, salsa e cantautorato italiano."),
                "luoghi" to listOf("Il mercato del porto all'apertura è il luogo in cui si sente più viva."),
                "abitudini" to listOf("Assaggia ogni salsa con un cucchiaio diverso e cucina per gli altri quando non riesce a dire ciò che prova."),
                "aneddoti" to listOf("Alla prima serata del Lume bruciò il dessert principale e salvò il servizio inventando sul momento una crema con pane tostato e agrumi."),
                "ricordi" to listOf("Il ricordo più importante è impastare il pane con la nonna prima dell'alba."),
                "sogni" to listOf("Vorrebbe trasformare il Lume in un ristorante capace di formare giovani cuochi senza opportunità."),
                "paure" to listOf("Teme un nuovo tradimento professionale e di perdere la famiglia che ha costruito nella sua brigata.")
            ),
            inviteTrust = 32, inviteAffection = 24, inviteTalks = 4),
        CharacterProfile("luca","Luca Ferri",29,"Fotografo urbano",
            "Calmo, percettivo, ironico e riservato.",
            listOf("fotografia","jazz","pioggia"), listOf("superficialità","pose false"),
            gender = "Maschio", conquestDifficulty = 4, extroversion = 2, sensuality = 3, romance = 5, jealousy = 2,
            background = "È cresciuto nella periferia di Neon Bay con una madre infermiera e un fratello minore. Dopo aver fotografato per caso un momento doloroso invece di intervenire, ha iniziato a chiedersi dove finisca il lavoro e cominci la responsabilità verso le persone.",
            innerConflict = "Desidera avvicinarsi davvero a qualcuno, ma teme che osservare gli altri da dietro un obiettivo sia diventato il suo modo di non farsi vedere.",
            storyBeats = listOf("racconta la fotografia che non ha mai pubblicato", "confida il senso di colpa che porta con sé", "invita a sviluppare insieme un rullino importante", "sceglie di posare la macchina fotografica e vivere il momento"),
            workFacts = listOf("Lavora come fotografo freelance e documenta la vita notturna di Neon Bay.", "Realizza servizi editoriali, ritratti e reportage per piccole riviste indipendenti.", "Sta preparando una mostra chiamata Persone sotto la pioggia, ma non ha ancora scelto la fotografia centrale."),
            personalFacts = mapOf(
                "hobby" to listOf("Sviluppa pellicole in una piccola camera oscura e restaura vecchie macchine fotografiche.", "Cammina senza meta ascoltando la città e annotando possibili scatti."),
                "cibo" to listOf("Ama gli spaghetti aglio e olio, i panini con verdure grigliate e la torta di mele.", "Beve caffè lungo e ordina whisky torbato solo nelle serate speciali."),
                "cinema" to listOf("Preferisce noir, drammi intimi e film girati con luce naturale."),
                "musica" to listOf("Ascolta jazz notturno, trip-hop e vecchi dischi soul."),
                "luoghi" to listOf("Il Rooftop Aurora è il posto da cui fotografa le luci riflesse dopo la pioggia."),
                "abitudini" to listOf("Controlla istintivamente la luce entrando in una stanza e porta sempre un rullino di riserva."),
                "aneddoti" to listOf("Una volta inseguì per mezz'ora un riflesso perfetto e scoprì che proveniva dall'orologio di un turista che continuava a spostarsi."),
                "ricordi" to listOf("Il ricordo più sereno è la prima alba fotografata con il fratello dalla finestra della loro vecchia casa."),
                "sogni" to listOf("Sogna una mostra capace di far sentire viste anche le persone che la città ignora."),
                "paure" to listOf("Teme di trasformare ogni rapporto in qualcosa da osservare invece che da vivere.")
            ),
            inviteTrust = 32, inviteAffection = 24, inviteTalks = 4),
        CharacterProfile("matteo","Matteo Serra",33,"Barman e mixologist",
            "Carismatico, diretto, protettivo e istintivo.",
            listOf("cocktail","ballo","sincerità"), listOf("snobismo","promesse vuote"),
            gender = "Maschio", conquestDifficulty = 3, extroversion = 5, sensuality = 5, romance = 2, jealousy = 3,
            background = "Ha lavorato in locali di mezza Europa prima di tornare a Neon Bay per aiutare la sorella durante un periodo difficile. Dietro la sicurezza con cui gestisce il bancone nasconde la paura che gli altri vedano soltanto fascino e leggerezza.",
            innerConflict = "Sa creare intimità con chiunque per una sera, ma non è sicuro di saper restare quando un legame diventa serio.",
            storyBeats = listOf("prepara un cocktail legato a un ricordo personale", "spiega perché è tornato improvvisamente in città", "ammette di usare il fascino come difesa", "decide di restare anche quando non può controllare l'esito"),
            workFacts = listOf("Lavora come barman e responsabile del bancone al Bar Velvet.", "Crea cocktail originali partendo dai gusti e dai ricordi dei clienti.", "Sta progettando una carta stagionale ispirata ai quartieri di Neon Bay."),
            personalFacts = mapOf(
                "hobby" to listOf("Balla salsa, colleziona bicchieri vintage e sperimenta sciroppi fatti in casa.", "La mattina corre sul lungomare per liberare la testa dopo il turno."),
                "cibo" to listOf("Adora la parmigiana, il pesce alla griglia e le olive piccanti.", "Fuori dal lavoro beve acqua tonica e prepara un espresso sorprendentemente delicato."),
                "cinema" to listOf("Ama commedie brillanti, film di rapina e storie in cui il protagonista deve scegliere se restare."),
                "musica" to listOf("Ascolta latin, funk e rhythm and blues; cambia playlist in base all'umore del locale."),
                "luoghi" to listOf("Quando il Velvet chiude si ferma spesso sulla Spiaggia Aozora prima di tornare a casa."),
                "abitudini" to listOf("Fa girare il sottobicchiere fra le dita quando è preoccupato e ricorda quasi sempre cosa bevono gli amici."),
                "aneddoti" to listOf("Durante un concorso dimenticò l'ingrediente principale e inventò un cocktail analcolico che vinse comunque il premio del pubblico."),
                "ricordi" to listOf("Ricorda le estati trascorse a servire granite nel chiosco dello zio insieme alla sorella."),
                "sogni" to listOf("Vorrebbe aprire un locale piccolo in cui nessuno debba fingere di essere più importante di ciò che è."),
                "paure" to listOf("Teme che, smettendo di intrattenere gli altri, non rimanga abbastanza per farsi scegliere.")
            ),
            inviteTrust = 20, inviteAffection = 15, inviteTalks = 3),
        CharacterProfile("kenji","Kenji Nakamura",26,"Sviluppatore di videogiochi",
            "Brillante, introverso, leale e sottilmente sarcastico.",
            listOf("videogiochi","fantascienza","ramen"), listOf("invadenza","derisione"),
            gender = "Maschio", conquestDifficulty = 5, extroversion = 1, sensuality = 2, romance = 4, jealousy = 1,
            background = "È nato a Neon Bay in una famiglia italo-giapponese e ha imparato presto a rifugiarsi nei mondi che programmava. Il fallimento del suo primo studio indipendente e la rottura con il migliore amico lo hanno reso prudente nel condividere nuove idee.",
            innerConflict = "Desidera una persona con cui non debba recitare sicurezza, ma interpreta facilmente la vicinanza come il rischio di essere giudicato o abbandonato.",
            storyBeats = listOf("mostra un prototipo che tiene nascosto", "racconta il fallimento del vecchio studio", "chiede aiuto per scegliere il finale del suo gioco", "accetta di creare qualcosa insieme senza controllare ogni possibilità"),
            workFacts = listOf("Lavora da remoto come sviluppatore e game designer per un piccolo studio di Neon Bay.", "Programma sistemi di dialogo e costruisce livelli narrativi per giochi indipendenti.", "Nel tempo libero sta creando un'avventura chiamata Echo District, ambientata in una città che ricorda le persone dimenticate."),
            personalFacts = mapOf(
                "hobby" to listOf("Costruisce tastiere meccaniche, gioca a giochi da tavolo strategici e disegna mappe su carta.", "Cura un piccolo acquario e fotografa le insegne arcade più vecchie della città."),
                "cibo" to listOf("Ama ramen shoyu, curry giapponese e pizza con verdure piccanti.", "Beve tè verde freddo e dimentica spesso il caffè accanto al computer."),
                "cinema" to listOf("Preferisce fantascienza malinconica, animazione e thriller tecnologici."),
                "musica" to listOf("Lavora ascoltando lo-fi, colonne sonore elettroniche e city pop."),
                "luoghi" to listOf("Il piano alto del Nova Mall, vicino alla vecchia sala giochi, è il suo rifugio fuori casa."),
                "abitudini" to listOf("Riscrive i messaggi prima di inviarli e picchietta combinazioni immaginarie di tasti quando pensa."),
                "aneddoti" to listOf("Inserì per errore nel gioco di un cliente un personaggio di prova chiamato Signor Poligono, che divenne il preferito dei giocatori."),
                "ricordi" to listOf("Il ricordo più caro è costruire un livello a quattro mani con il suo vecchio amico durante una notte d'estate."),
                "sogni" to listOf("Vuole fondare un nuovo studio in cui le persone possano lavorare senza sacrificare la propria vita."),
                "paure" to listOf("Teme di fidarsi di nuovo di un progetto o di una persona e vedere tutto crollare senza spiegazioni.")
            ),
            inviteTrust = 38, inviteAffection = 28, inviteTalks = 6)
    )

    val locations = listOf(
        Location("apartment","Appartamento","🏠","Il tuo rifugio personale."),
        Location("cafe","Caffetteria Centrale","☕","Un locale caldo nel centro città.", listOf(OpeningWindow(7*60, 23*60))),
        Location("downtown","Centro Neon Bay","🌆","Neon, boutique e movimento continuo."),
        Location("park","Parco Sakura","🌸","Un parco tranquillo con un piccolo lago.", listOf(OpeningWindow(6*60, 22*60))),
        Location("beach","Spiaggia Aozora","🏖️","Mare aperto e tramonti spettacolari.", listOf(OpeningWindow(6*60, 2*60))),
        Location("gym","Palestra Pulse","🏋️","Allenamento e incontri casuali.", listOf(OpeningWindow(8*60, 22*60))),
        Location("restaurant","Ristorante Lume","🍽️","Locale elegante sul porto.", listOf(
            OpeningWindow(11*60+30, 15*60), OpeningWindow(18*60+30, 23*60+30)
        )),
        Location("mall","Nova Mall","🛍️","Shopping, cinema e caffetterie.", listOf(OpeningWindow(9*60, 21*60))),
        Location("bar","Bar Velvet","🍸","Cocktail e conversazioni nella luce soffusa.", listOf(OpeningWindow(17*60, 2*60))),
        Location("supermarket","Supermercato Nova","🛒","Prodotti freschi e incontri quotidiani.", listOf(OpeningWindow(7*60+30, 21*60))),
        Location("rooftop","Rooftop Aurora","🌌","Una terrazza panoramica sopra Neon Bay.", listOf(OpeningWindow(18*60, 2*60))),
        Location("club","Club Eclipse","🎵","Musica, neon e notti imprevedibili.", listOf(OpeningWindow(22*60, 5*60)))
    )

    private val weekdaySchedules = mapOf(
        "sofia" to listOf("cafe", "downtown", "mall", "rooftop"),
        "maya" to listOf("gym", "beach", "park", "bar"),
        "elena" to listOf("downtown", "cafe", "restaurant", "bar"),
        "luna" to listOf("cafe", "park", "club", "club"),
        "chiara" to listOf("supermarket", "park", "mall", "downtown"),
        "nadia" to listOf("supermarket", "restaurant", "restaurant", "rooftop"),
        "luca" to listOf("cafe", "downtown", "park", "rooftop"),
        "matteo" to listOf("cafe", "downtown", "bar", "bar"),
        "kenji" to listOf("supermarket", "mall", "cafe", "club")
    )

    private val alternateSchedules = mapOf(
        "sofia" to listOf("mall", "cafe", "park", "bar"),
        "maya" to listOf("beach", "gym", "mall", "club"),
        "elena" to listOf("cafe", "downtown", "mall", "rooftop"),
        "luna" to listOf("park", "cafe", "beach", "club"),
        "chiara" to listOf("park", "supermarket", "cafe", "rooftop"),
        "nadia" to listOf("supermarket", "restaurant", "restaurant", "bar"),
        "luca" to listOf("park", "cafe", "mall", "bar"),
        "matteo" to listOf("beach", "park", "gym", "bar"),
        "kenji" to listOf("cafe", "downtown", "mall", "rooftop")
    )

    fun characterLocation(characterId: String, day: Int, periodIndex: Int, minuteOfDay: Int): String? {
        val schedule = if (day % 2 == 0) alternateSchedules else weekdaySchedules
        return schedule[characterId]?.getOrNull(periodIndex.coerceIn(0, 3))
            ?.takeIf { isLocationOpenAt(it, minuteOfDay) }
    }

    fun isLocationOpenAt(locationId: String, minuteOfDay: Int): Boolean {
        val location = locations.firstOrNull { it.id == locationId } ?: return false
        return location.openingWindows.any { window ->
            val open = window.opensAtMinute
            val close = window.closesAtMinute
            if (open == close) true
            else if (open < close) minuteOfDay in open until close
            else minuteOfDay >= open || minuteOfDay < close
        }
    }

    fun openingLabel(location: Location): String {
        if (location.openingWindows.any { it.opensAtMinute == it.closesAtMinute }) return "Sempre accessibile"
        fun hm(value: Int) = "%02d:%02d".format(value / 60, value % 60)
        return location.openingWindows.joinToString(" · ") { "${hm(it.opensAtMinute)}–${hm(it.closesAtMinute)}" }
    }

    fun charactersAt(
        locationId: String,
        day: Int,
        periodIndex: Int,
        minuteOfDay: Int,
        guestCharacterId: String? = null
    ): List<CharacterProfile> = characters.filter {
        characterLocation(it.id, day, periodIndex, minuteOfDay) == locationId ||
            (locationId == "apartment" && it.id == guestCharacterId)
    }.distinctBy { it.id }
}
