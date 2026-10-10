package fi.lahtinen.kurssit.ui.components;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;

/**
 * Sovelluksen footer (vaatimus 15): tekijä, copyright ja linkit.
 * <p>
 * Kiinnitys näkymän alaosaan (sticky), visuaalinen erottuminen (tumma tausta + korostusvärinen
 * yläreuna + varjo) ja responsiivisuus on toteutettu CSS:ssä ({@code layout.css}, luokka
 * {@code .app-footer}). {@code footer-secondary}-osat piilotetaan kapealla näytöllä.
 * Semanttinen {@code <footer>}-elementti auttaa ruudunlukijoita tunnistamaan alueen.
 */
public class AppFooter extends Footer implements LocaleChangeObserver {

    public static final String GITHUB_URL = "https://github.com/BlackTempo/kurssisovellus";

    private final Span author = new Span();
    private final Span copyright = new Span();
    private final Span course = new Span();
    private final Anchor sourceLink = new Anchor(GITHUB_URL, "", AnchorTarget.BLANK);

    public AppFooter() {
        addClassName("app-footer");

        course.addClassName("footer-secondary");
        Div about = new Div(author, course);
        about.addClassName("footer-section");

        sourceLink.getElement().setAttribute("rel", "noopener");
        Anchor docsLink = new Anchor("https://vaadin.com/docs/v24", "Vaadin docs", AnchorTarget.BLANK);
        docsLink.getElement().setAttribute("rel", "noopener");
        docsLink.addClassName("footer-secondary");
        Div links = new Div(VaadinIcon.CODE.create(), sourceLink, docsLink);
        links.addClassName("footer-links");

        Span version = new Span("v1.0");
        version.addClassName("footer-secondary");
        Div legal = new Div(copyright, version);
        legal.addClassName("footer-section");

        add(about, links, legal);
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        author.setText(getTranslation("footer.author"));
        course.setText(getTranslation("footer.course"));
        copyright.setText(getTranslation("footer.copyright"));
        sourceLink.setText(getTranslation("footer.source"));
    }
}
