package fi.lahtinen.kurssit.ui.views.auth;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import fi.lahtinen.kurssit.service.PasswordResetService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.FormFields;
import fi.lahtinen.kurssit.ui.components.Notifications;

/**
 * Salasanan palautuksen ensimmäinen vaihe (vaatimus 27): sähköpostiosoitteen syöttö.
 */
@Route(value = "forgot-password", layout = MainLayout.class)
@PageTitle("Unohtunut salasana | Kurssisovellus")
@AnonymousAllowed
public class ForgotPasswordView extends VerticalLayout implements LocaleChangeObserver {

    private final H2 title = new H2();
    private final Paragraph info = new Paragraph();
    private final EmailField email = FormFields.email(null);
    private final Button submit = new Button();
    private final RouterLink back = new RouterLink("", LoginView.class);

    public ForgotPasswordView(PasswordResetService passwordResetService) {
        addClassName("auth-view");
        setSizeFull();

        email.setWidthFull();
        email.setRequiredIndicatorVisible(true);
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.addClickShortcut(Key.ENTER);
        submit.addClickListener(e -> {
            if (email.isEmpty() || email.isInvalid()) {
                email.setInvalid(true);
                return;
            }
            passwordResetService.requestReset(email.getValue());
            // Sama viesti löytyi tunnus tai ei – ks. PasswordResetService.requestReset.
            Notifications.success(getTranslation("forgot.sent"));
            email.clear();
        });

        Div card = new Div(title, info, email, submit, back);
        card.addClassName("auth-card");
        add(card);
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        title.setText(getTranslation("forgot.title"));
        info.setText(getTranslation("forgot.info"));
        email.setLabel(getTranslation("register.email"));
        submit.setText(getTranslation("forgot.submit"));
        back.setText(getTranslation("forgot.back"));
    }
}
