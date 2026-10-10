package fi.lahtinen.kurssit.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;

/**
 * Sovelluksen "kuori" eli koko SPA:n yhteiset sivutason asetukset.
 * <p>
 * Vaadin sallii {@link Theme}- ja {@link Push}-annotaatiot vain yhdessä paikassa, koska ne
 * vaikuttavat index.html:ään ja WebSocket-yhteyteen, jotka ovat yhteisiä kaikille näkymille.
 * Siksi ne on koottu tähän erilliseen luokkaan eikä esim. MainLayoutiin.
 * <p>
 * Vaatimukset: 6 (globaali teema), 22 (Server Push).
 */
@Theme("kurssiteema")
@Push
public class AppShell implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        // SVG-favicon skaalautuu kaikkiin kokoihin, joten erillisiä PNG-versioita ei tarvita.
        settings.addFavIcon("icon", "icons/favicon.svg", "any");
    }
}
