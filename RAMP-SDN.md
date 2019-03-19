# Utilizzo di SDN
    
Passi da compiere:
- Istanziare `ControllerService` (metodo `getInstance()`) sul nodo avente il ruolo di **controller**.
- Istanziare `ControllerClient` (metodo `getInstance()`) su **ciascun nodo** (compreso il nodo controller).

Può risultare opportuno prevedere un’attesa di qualche secondo (ad esempio 5) prima di attivare `ControllerClient`, al fine di consentire al nodo RAMP di entrare in contatto con gli altri nodi.

Per l’invio di messaggi all’interno di un nuovo flow, creare un oggetto `ApplicationRequirements` specificando i **requisiti** del flow da avviare (tipo, bitrate o dimensioni, secondi prima dell’inizio e durata) e definire due array di tipo int che contengano rispettivamente i **nodeId** dei destinatari e, nel caso di messaggio multicast, le rispettive **porte di destinazione** (non significativo in caso non sia prevista la modalità multicast).
Successivamente, ottenere il flowId relativo al nuovo flow attraverso l’invocazione del metodo `getFlowId()` sull’istanza di `ControllerClient` di cui si ha un riferimento, passando come argomenti l’oggetto `ApplicationRequirements` e i due array.
Al momento dell’invio di ciascun messaggio, specificare il **flowId** come argomento del metodo `sendUnicast()`.

# Selezione della politica
In `ControllerClient` assegnare al campo `trafficEngineeringPolicy` il valore di `FlowPolicy` (enumerativo) corrispondente alla **politica** da attivare e al campo `dataPlaneForwarder` l’istanza del **forwarder** relativo.
In `ControllerService` assegnare al campo `trafficEngineeringPolicy` il valore di `FlowPolicy` (enumerativo) corrispondente alla **politica** da attivare.

In alternativa: metodo `updateFlowPolicy()` di `ControllerService`, per modificare la politica a tempo di esecuzione.

# Selettori di percorso e di priorità
In `ControllerService`, assegnare ai campi `flowPathSelector`, `defaultPathSelector` e `prioritySelector` opportuni istanze di classi per la **selezione di percorso**. Il primo campo è utilizzato per selezionare i percorsi per flow aventi valore definito, il secondo per selezionare i percorsi di flow che possiedono il valore di default, mentre il terzo per selezionare i valori di priorità da associare ai flow.

**Selettori di percorso** disponibili:
- `BreadthFirstFlowPathSelector`
- `FewestIntersectionsFlowPathSelector`
- `MinimumLoadFlowPathSelector`

**Selettori di priorità** disponibili:
- `ApplicationTypeFlowPrioritySelector`

# Politica Tree-based Multicast
Per l’invio di messaggi sfruttando la modalità di multicast, invocare il metodo `sendUnicast()` passando come argomenti un array contenente **il solo indirizzo** del nodo locale come campo `dest`, il **nodeId** del nodo locale e la porta **40000**. I nodeId e le porte di ricezione dei reali destinatari dei messaggi sono indicate durante l’invocazione del metodo `getFlowId()` di `ControllerClient`, che precede l’invio.

# Package di test
Le classi sono dotate di metodo main e sono pertanto eseguibili. Per lanciarne l'esecuzione, creare gli opportuni file **jar** esportando le classi da Eclipse con le impostazioni di default e avviarli da shell sui diversi nodi.

## Classi del package
### ControllerClientTest
Da eseguire sui nodi che partecipano alla rete da **intermedi**, senza svolgere alcuna particolare operazione dal punto di vista applicativo.

### ControllerClientTestSender
Da eseguire sui nodi che si occupano di **iniziare una comunicazione**, selezionando il metodo di test opportuno all’interno del metodo `main()` (coerentemente con il metodo scelto per il destinatario.

### ControllerClientTestReceiver
Da eseguire sui nodi che si occupano di **ricevere i messaggi** spediti dai mittenti, selezionando il metodo di test opportuno all’interno del metodo `main()` (coerentemente con il metodo scelto per il mittente).

### ControllerServiceTest
Da eseguire sul nodo che svolge il ruolo di **controller**.

## Metodi di test (classi ControllerClientTestSender / ControllerClientTestReceiver)
- `sendTwoMessages()` / `receiveTwoMessages()`: test per politica **Best Path** (invio di due stringhe consecutive)
- `sendAFile()` / `receiveFile()`: test per **no SDN** (invio di un singolo file)
- `sendTwoFilesToDifferentReceivers()` / `receiveTwoFilesInDifferentThreads()`: test per politiche di **traffic engineering** (invio di due file sfruttando due diversi servizi RAMP)
- `sendThreeFilesToDifferentReceivers()` / `receiveThreeFilesInDifferentThreads()`: test per politiche di **traffic engineering** (invio di tre file sfruttando tre diversi servizi RAMP)
- `sendTwoSeriesOfPacketsToDifferentReceivers()` / `receiveTwoSeriesOfPacketsInDifferentThreads()`: test per politiche di **traffic engineering** utilizzando il protocollo **UDP** (invio di due serie di pacchetti consecutivi sfruttando due diversi servizi RAMP)
- `sendThreeSeriesOfPacketsToDifferentReceivers()` / `receiveThreeSeriesOfPacketsInDifferentThreads()`: test per politiche di **traffic engineering** utilizzando il protocollo **UDP** (invio di tre serie di pacchetti consecutivi sfruttando tre diversi servizi RAMP)
- `sendMessageToMultipleReceivers()` / `receiveMessage()`: test per politica **Tree-based Multicast** (invio di una **stringa** a più destinatari utilizzando la modalità di multicast; metodo di ricezione da avviare su tutti i destinatari)
- `sendMultipleMessagesToMultipleReceivers()` / `receiveMultipleMessages()`: test per politica **Tree-based Multicast** (invio di due **payload** a più destinatari, il primo utilizzando una comunicazione per ciascuno dei destinatari, il secondo utilizzando la modalità di multicast)
