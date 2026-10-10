package fi.lahtinen.kurssit.service;

import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Välittää muutostapahtumat kaikille avoimille käyttöliittymille (Server Push, vaatimus 22).
 * <p>
 * Toimintaperiaate: näkymät rekisteröivät kuuntelijan liittyessään sivulle ja poistavat sen
 * irrotessaan. Kun palvelu julkaisee {@link DataChangedEvent}in, tämä luokka kutsuu kuuntelijoita,
 * jotka päivittävät oman UI:nsa {@code ui.access()}-kutsun sisällä ja @Push lähettää muutoksen
 * selaimeen ilman, että käyttäjä tekee mitään.
 * <p>
 * Suunnitteluvalintoja:
 * <ul>
 *   <li>{@link TransactionPhase#AFTER_COMMIT}: viesti lähetetään vasta kun muutos on tietokannassa.
 *       Jos lähetys tehtäisiin transaktion sisällä, muut käyttäjät saattaisivat ladata gridin
 *       uudelleen ennen commitia eivätkä näkisi muutosta – tai näkisivät muutoksen, joka perutaan.</li>
 *   <li>Oma säiepooli: kuuntelijat kutsutaan eri säikeessä kuin tallentaja, joten hidas tai jumittunut
 *       selainyhteys ei hidasta tallennuksen tehnyttä käyttäjää.</li>
 *   <li>{@link CopyOnWriteArrayList}: rekisteröinnit ja lähetykset tapahtuvat eri säikeissä;
 *       listaa luetaan paljon useammin kuin muokataan, joten kopiointi kirjoituksessa on edullista.</li>
 * </ul>
 */
@Component
public class Broadcaster {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "broadcaster");
        t.setDaemon(true);
        return t;
    });

    private final List<Consumer<DataChangedEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Palauttaa {@link Registration}in, jonka kutsuja <b>on poistettava</b> näkymän irrotessa
     * (onDetach). Muuten suljetun selainvälilehden UI jäisi muistiin kuuntelijaviittauksen kautta.
     */
    public Registration register(Consumer<DataChangedEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDataChanged(DataChangedEvent event) {
        for (Consumer<DataChangedEvent> listener : listeners) {
            executor.execute(() -> listener.accept(event));
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
