package fi.lahtinen.kurssit.ui.views;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AccessDeniedException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.ParentLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import fi.lahtinen.kurssit.ui.MainLayout;

/**
 * Kustomoitu virhesivu riittämättömille käyttöoikeuksille (vaatimus 18).
 * <p>
 * {@link HasErrorParameter} rekisteröi näkymän {@link AccessDeniedException}in käsittelijäksi Vaadinin
 * oletussivun tilalle. Suojatut näkymät on merkitty {@code @AccessDeniedErrorRouter}-annotaatiolla,
 * jotta navigaation pääsynhallinta heittää juuri tämän poikkeuksen.
 * <p>
 * {@link ParentLayout}: virhesivu näytetään MainLayoutin sisällä, joten käyttäjä voi jatkaa
 * navigointia normaalisti. Kirjautumaton käyttäjä ohjataan suojatulta sivulta sen sijaan
 * kirjautumissivulle – tämä sivu koskee vain kirjautuneita, joiden rooli ei riitä.
 * HTTP-tila on 403, jotta selain ja mahdolliset välityspalvelimet tulkitsevat vastauksen oikein.
 */
@ParentLayout(MainLayout.class)
@AnonymousAllowed
public class AccessDeniedView extends VerticalLayout implements HasErrorParameter<AccessDeniedException> {

    private final H2 title = new H2();
    private final Paragraph message = new Paragraph();
    private final Paragraph hint = new Paragraph();
    private final RouterLink back = new RouterLink("", DashboardView.class);

    public AccessDeniedView() {
        addClassNames("page", "auth-view");
        setAlignItems(Alignment.CENTER);
        Icon icon = VaadinIcon.BAN.create();
        icon.addClassName("denied-icon");
        VerticalLayout card = new VerticalLayout(icon, title, message, hint, back);
        card.addClassName("auth-card");
        card.setAlignItems(Alignment.CENTER);
        add(card);
    }

    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<AccessDeniedException> parameter) {
        String path = "/" + event.getLocation().getPath();
        title.setText(getTranslation("denied.title"));
        message.setText(getTranslation("denied.message", path));
        hint.setText(getTranslation("denied.hint"));
        back.setText(getTranslation("denied.back"));
        return HttpStatusCode.FORBIDDEN.getCode();
    }
}
