package fi.lahtinen.kurssit.ui.views.auth;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import fi.lahtinen.kurssit.security.AuthenticatedUser;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.views.DashboardView;

/**
 * Kirjautumissivu (vaatimus 16), lokalisoitu (vaatimus 23).
 * <p>
 * LoginForm lähettää tunnukset tavallisena HTML-lomakkeena (POST /login) Spring Securityn
 * käsiteltäväksi – salasana ei kulje Vaadinin oman kommunikaation kautta, ja Spring Securityn
 * istuntokiinnityksen esto (session fixation protection) vaihtaa session id:n kirjautuessa.
 */
@Route(value = "login", layout = MainLayout.class)
@PageTitle("Kirjaudu | Kurssisovellus")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    private final AuthenticatedUser authenticatedUser;
    private final LoginForm loginForm = new LoginForm();
    private final Span noAccount = new Span();
    private final RouterLink registerLink = new RouterLink("", RegisterView.class);
    private final Paragraph demo = new Paragraph();

    public LoginView(AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
        addClassName("auth-view");
        setSizeFull();

        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(true);
        loginForm.addForgotPasswordListener(e -> getUI().ifPresent(ui -> ui.navigate(ForgotPasswordView.class)));

        demo.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        Div card = new Div(loginForm, new Div(noAccount, new Span(" "), registerLink), demo);
        card.addClassName("auth-card");
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Jo kirjautunut käyttäjä ohjataan etusivulle, ettei hän näe turhaa kirjautumislomaketta.
        if (authenticatedUser.isLoggedIn()) {
            event.forwardTo(DashboardView.class);
            return;
        }
        // Spring Security palauttaa epäonnistuneen kirjautumisen osoitteeseen /login?error
        loginForm.setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle(getTranslation("login.title"));
        i18n.getForm().setUsername(getTranslation("login.username"));
        i18n.getForm().setPassword(getTranslation("login.password"));
        i18n.getForm().setSubmit(getTranslation("login.submit"));
        i18n.getForm().setForgotPassword(getTranslation("login.forgot"));
        i18n.getErrorMessage().setTitle(getTranslation("login.error.title"));
        i18n.getErrorMessage().setMessage(getTranslation("login.error.message"));
        loginForm.setI18n(i18n);
        noAccount.setText(getTranslation("login.noaccount"));
        registerLink.setText(getTranslation("header.register"));
        demo.setText(getTranslation("login.demo"));
    }
}
