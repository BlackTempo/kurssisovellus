package fi.lahtinen.kurssit.ui.views.auth;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.data.validator.RegexpValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import fi.lahtinen.kurssit.service.BusinessException;
import fi.lahtinen.kurssit.service.UserService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.Notifications;

/**
 * Rekisteröitymissivu (vaatimus 17), lokalisoitu (vaatimus 23).
 * <p>
 * Lomake sidotaan erilliseen {@link RegistrationData}-olioon eikä AppUser-entiteettiin, koska lomakkeella
 * on kenttiä (salasana kahteen kertaan), joita entiteetissä ei ole – eikä selväkielistä salasanaa
 * haluta entiteettiin edes hetkellisesti.
 */
@Route(value = "register", layout = MainLayout.class)
@PageTitle("Rekisteröidy | Kurssisovellus")
@AnonymousAllowed
public class RegisterView extends VerticalLayout implements LocaleChangeObserver {

    /** Lomakkeen tiedot. Julkinen, jotta Binder voi käyttää gettereitä/settereitä. */
    public static class RegistrationData {
        private String username = "";
        private String fullName = "";
        private String email = "";
        private String password = "";
        private String confirm = "";

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirm() { return confirm; }
        public void setConfirm(String confirm) { this.confirm = confirm; }
    }

    private final UserService userService;
    private final H2 title = new H2();
    private final TextField username = new TextField();
    private final TextField fullName = new TextField();
    private final EmailField email = new EmailField();
    private final PasswordField password = new PasswordField();
    private final PasswordField confirm = new PasswordField();
    private final Button submit = new Button();
    private final Span haveAccount = new Span();
    private final RouterLink loginLink = new RouterLink("", LoginView.class);
    private final Binder<RegistrationData> binder = new Binder<>(RegistrationData.class);

    public RegisterView(UserService userService) {
        this.userService = userService;
        addClassName("auth-view");
        setSizeFull();

        // Säännöt vastaavat AppUser-entiteetin annotaatioita, jotta virhe näkyy jo lomakkeella eikä vasta
        // tallennettaessa. Uniikkius tarkistetaan palvelussa, koska se vaatii tietokantahaun.
        binder.forField(username).asRequired("Pakollinen")
                .withValidator(new StringLengthValidator("3–30 merkkiä", 3, 30))
                .withValidator(new RegexpValidator("Sallitut merkit: kirjaimet, numerot sekä . _ -", "^[a-zA-Z0-9._-]+$"))
                .bind(RegistrationData::getUsername, RegistrationData::setUsername);
        binder.forField(fullName).asRequired("Pakollinen")
                .withValidator(new StringLengthValidator("Enintään 100 merkkiä", 1, 100))
                .bind(RegistrationData::getFullName, RegistrationData::setFullName);
        binder.forField(email).asRequired("Pakollinen")
                .withValidator(new EmailValidator("Sähköpostiosoite ei ole kelvollinen"))
                .bind(RegistrationData::getEmail, RegistrationData::setEmail);
        binder.forField(password).asRequired("Pakollinen")
                .withValidator(new StringLengthValidator("Vähintään " + UserService.MIN_PASSWORD_LENGTH + " merkkiä",
                        UserService.MIN_PASSWORD_LENGTH, 100))
                .bind(RegistrationData::getPassword, RegistrationData::setPassword);
        binder.forField(confirm).asRequired("Pakollinen")
                .withValidator(value -> value.equals(password.getValue()), getTranslation("register.mismatch"))
                .bind(RegistrationData::getConfirm, RegistrationData::setConfirm);
        binder.setBean(new RegistrationData());

        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.addClickShortcut(Key.ENTER);
        submit.addClickListener(e -> register());

        FormLayout form = new FormLayout(username, fullName, email, password, confirm);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        Div card = new Div(title, form, submit, new Div(haveAccount, new Span(" "), loginLink));
        card.addClassName("auth-card");
        add(card);
    }

    private void register() {
        if (!binder.validate().isOk()) {
            return;
        }
        RegistrationData data = binder.getBean();
        try {
            userService.register(data.getUsername(), data.getFullName(), data.getEmail(), data.getPassword());
            Notifications.success(getTranslation("register.success"));
            getUI().ifPresent(ui -> ui.navigate(LoginView.class));
        } catch (BusinessException ex) {
            Notifications.error(ex.getMessage());
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        title.setText(getTranslation("register.title"));
        username.setLabel(getTranslation("login.username"));
        fullName.setLabel(getTranslation("register.fullname"));
        email.setLabel(getTranslation("register.email"));
        password.setLabel(getTranslation("register.password"));
        confirm.setLabel(getTranslation("register.confirm"));
        submit.setText(getTranslation("register.submit"));
        haveAccount.setText(getTranslation("register.haveaccount"));
        loginLink.setText(getTranslation("header.login"));
    }
}
