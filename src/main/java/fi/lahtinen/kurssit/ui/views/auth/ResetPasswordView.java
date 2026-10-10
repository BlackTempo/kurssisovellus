package fi.lahtinen.kurssit.ui.views.auth;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import fi.lahtinen.kurssit.service.BusinessException;
import fi.lahtinen.kurssit.service.PasswordResetService;
import fi.lahtinen.kurssit.service.UserService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.Notifications;

/**
 * Salasanan palautuksen toinen vaihe (vaatimus 27): sähköpostilinkistä avautuva uuden salasanan lomake.
 * Token luetaan URL-parametrista {@code ?token=...}.
 */
@Route(value = "reset-password", layout = MainLayout.class)
@PageTitle("Uusi salasana | Kurssisovellus")
@AnonymousAllowed
public class ResetPasswordView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    private final PasswordResetService passwordResetService;
    private final H2 title = new H2();
    private final PasswordField password = new PasswordField();
    private final PasswordField confirm = new PasswordField();
    private final Button submit = new Button();
    private final Div form = new Div();
    private final Paragraph invalid = new Paragraph();
    private final RouterLink requestNew = new RouterLink("", ForgotPasswordView.class);
    private String token;

    public ResetPasswordView(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
        addClassName("auth-view");
        setSizeFull();

        password.setWidthFull();
        confirm.setWidthFull();
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.addClickShortcut(Key.ENTER);
        submit.addClickListener(e -> reset());
        form.add(password, confirm, submit);

        Div card = new Div(title, form, invalid, requestNew);
        card.addClassName("auth-card");
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        token = event.getLocation().getQueryParameters().getSingleParameter("token").orElse(null);
        // Tarkistetaan jo sivua avattaessa, jotta käyttäjä ei turhaan keksi uutta salasanaa vanhentuneelle linkille.
        boolean valid = passwordResetService.isValid(token);
        form.setVisible(valid);
        invalid.setVisible(!valid);
        requestNew.setVisible(!valid);
    }

    private void reset() {
        if (password.getValue().length() < UserService.MIN_PASSWORD_LENGTH) {
            password.setErrorMessage("Vähintään " + UserService.MIN_PASSWORD_LENGTH + " merkkiä");
            password.setInvalid(true);
            return;
        }
        if (!password.getValue().equals(confirm.getValue())) {
            confirm.setErrorMessage(getTranslation("register.mismatch"));
            confirm.setInvalid(true);
            return;
        }
        try {
            passwordResetService.resetPassword(token, password.getValue());
            Notifications.success(getTranslation("reset.success"));
            getUI().ifPresent(ui -> ui.navigate(LoginView.class));
        } catch (BusinessException ex) {
            Notifications.error(ex.getMessage());
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        title.setText(getTranslation("reset.title"));
        password.setLabel(getTranslation("register.password"));
        confirm.setLabel(getTranslation("register.confirm"));
        submit.setText(getTranslation("reset.submit"));
        invalid.setText(getTranslation("reset.invalid"));
        requestNew.setText(getTranslation("reset.request"));
    }
}
