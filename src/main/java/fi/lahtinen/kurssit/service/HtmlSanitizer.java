package fi.lahtinen.kurssit.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Quill-editorin HTML:n puhdistus (vaatimus 33, tietoturva).
 * <p>
 * Selaimelta tuleva HTML on käyttäjäsyötettä: hyökkääjä voi ohittaa editorin ja lähettää suoraan
 * esim. {@code <img src=x onerror=...>}. Koska kuvaus näytetään muille käyttäjille HTML:nä,
 * puhdistamaton sisältö olisi tallennettu XSS-haavoittuvuus. Sallittujen tagien lista (Safelist) on
 * turvallisempi kuin kiellettyjen lista, koska uudet hyökkäysvektorit eivät läpäise sitä.
 * <p>
 * {@code data-list} sallitaan, koska Quill 2 merkitsee sillä luettelon tyypin (bullet/ordered).
 */
public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .removeTags("img")
            .addAttributes("li", "data-list")
            .addAttributes("a", "target", "rel");

    private HtmlSanitizer() {
    }

    public static String clean(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        return Jsoup.clean(html, SAFELIST);
    }

    /** Pelkkä teksti esim. korttien esikatseluun, jossa muotoilua ei haluta. */
    public static String toPlainText(String html) {
        return html == null ? "" : Jsoup.parse(html).text();
    }
}
