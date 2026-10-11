package fi.lahtinen.kurssit.service;

import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kirjaa käyttäjän avoimet selainikkunat ulos, kun hänen käyttöoikeutensa muuttuvat (vaatimus 16).
 * <p>
 * Toimii kuten {@link Broadcaster}, mutta kuuntelijat rekisteröidään käyttäjätunnuksen mukaan:
 * MainLayout rekisteröi jokaiselle kirjautuneen käyttäjän UI:lle kuuntelijan, joka kutsuu
 * {@code ui.access()}-lohkossa uloskirjautumista. Push vie uloskirjautumisen selaimeen heti, joten
 * lukittu käyttäjä ei voi jatkaa jo avatun sivun käyttöä.
 * <p>
 * Avoimia UI:ta kattamattomat tapaukset (esim. välilehti suljettu, istunto yhä voimassa) hoitaa
 * MainLayoutin navigointitarkistus {@code AuthenticatedUser.isAccessRevoked()}.
 */
@Component
public class AccessRevocationBroadcaster {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "access-revocation");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();

    /** Palautettu {@link Registration} on poistettava UI:n irrotessa (onDetach), ettei UI jää muistiin. */
    public Registration register(String username, Runnable onRevoked) {
        listeners.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(onRevoked);
        return () -> listeners.computeIfPresent(username, (k, list) -> {
            list.remove(onRevoked);
            return list.isEmpty() ? null : list;
        });
    }

    /** AFTER_COMMIT: istunnot katkaistaan vasta, kun muutos on varmasti tallentunut. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccessChanged(UserAccessChangedEvent event) {
        for (Runnable listener : listeners.getOrDefault(event.username(), List.of())) {
            executor.execute(listener);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
