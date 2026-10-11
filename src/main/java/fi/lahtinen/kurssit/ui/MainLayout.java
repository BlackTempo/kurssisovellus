package fi.lahtinen.kurssit.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.LumoUtility;
import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.security.AuthenticatedUser;
import fi.lahtinen.kurssit.service.AccessRevocationBroadcaster;
import fi.lahtinen.kurssit.service.UserService;
import fi.lahtinen.kurssit.ui.components.AppFooter;
import fi.lahtinen.kurssit.ui.views.CourseSearchView;
import fi.lahtinen.kurssit.ui.views.CourseView;
import fi.lahtinen.kurssit.ui.views.DashboardView;
import fi.lahtinen.kurssit.ui.views.OfficeView;
import fi.lahtinen.kurssit.ui.views.ProfileView;
import fi.lahtinen.kurssit.ui.views.StudentView;
import fi.lahtinen.kurssit.ui.views.TeacherView;
import fi.lahtinen.kurssit.ui.views.admin.HistoryView;
import fi.lahtinen.kurssit.ui.views.admin.UserAdminView;
import fi.lahtinen.kurssit.ui.views.auth.LoginView;
import fi.lahtinen.kurssit.ui.views.auth.RegisterView;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Sovelluksen pääasettelu (SPA, vaatimukset 11, 13, 14, 15 ja 23).
 * <p>
 * Kaikki näkymät määritellään {@code @Route(value = ..., layout = MainLayout.class)}, joten Vaadin
 * vaihtaa navigoidessa vain sisältöalueen; header, navigaatio ja footer pysyvät paikallaan eikä
 * sivua ladata uudelleen.
 * <p>
 * AppLayoutissa ei ole valmista footer-paikkaa, joten {@link #showRouterLayoutContent} on
 * ylikirjoitettu: näkymä sijoitetaan omaan säiliöönsä, jonka alla footer on aina.
 * <p>
 * Koska layout on kaikkien näkymien yhteinen, se myös katkaisee istunnon, jos ylläpitäjä lukitsee
 * tunnuksen tai muuttaa sen rooleja (ks. {@link #onAttach} ja {@link #beforeEnter}).
 */
public class MainLayout extends AppLayout implements LocaleChangeObserver, BeforeEnterObserver {

    /** Navigaatiokohde: käännösavain, näkymäluokka ja oma ikoni jokaiselle näkymälle. */
    private record NavEntry(String key, Class<? extends Component> view, VaadinIcon icon) {
    }

    private static final List<NavEntry> NAV_ENTRIES = List.of(
            new NavEntry("nav.dashboard", DashboardView.class, VaadinIcon.HOME),
            new NavEntry("nav.courses", CourseView.class, VaadinIcon.BOOK),
            new NavEntry("nav.search", CourseSearchView.class, VaadinIcon.SEARCH),
            new NavEntry("nav.teachers", TeacherView.class, VaadinIcon.USER_STAR),
            new NavEntry("nav.offices", OfficeView.class, VaadinIcon.OFFICE),
            new NavEntry("nav.students", StudentView.class, VaadinIcon.GROUP),
            new NavEntry("nav.profile", ProfileView.class, VaadinIcon.USER_CARD),
            new NavEntry("nav.users", UserAdminView.class, VaadinIcon.KEY),
            new NavEntry("nav.history", HistoryView.class, VaadinIcon.TIME_BACKWARD));

    private static final Locale FINNISH = Locale.of("fi");

    private final AuthenticatedUser authenticatedUser;
    private final AccessAnnotationChecker accessChecker;
    private final UserService userService;
    private final AccessRevocationBroadcaster revocations;

    private final Div viewContainer = new Div();
    private final H1 appTitle = new H1();
    private final Span drawerBrand = new Span();
    private final List<SideNavItem> navItems = new ArrayList<>();
    private final List<NavEntry> visibleEntries = new ArrayList<>();
    private final Select<Locale> languageSelect = new Select<>();
    private Button logoutButton;
    private Button loginButton;
    private Button registerButton;
    private Registration revocationRegistration;

    public MainLayout(AuthenticatedUser authenticatedUser, AccessAnnotationChecker accessChecker,
                      UserService userService, AccessRevocationBroadcaster revocations) {
        this.authenticatedUser = authenticatedUser;
        this.accessChecker = accessChecker;
        this.userService = userService;
        this.revocations = revocations;

        // Drawer ensisijaiseksi: leveällä näytöllä navigaatio on aina näkyvissä vasemmalla, kapealla
        // se muuttuu DrawerTogglella avattavaksi overlayksi.
        setPrimarySection(Section.DRAWER);
        addToNavbar(true, createHeader());
        addToDrawer(createDrawer());

        viewContainer.addClassName("app-content");
        Div shell = new Div(viewContainer, new AppFooter());
        shell.addClassName("app-shell");
        setContent(shell);
    }

    // --- Header (vaatimus 13) ------------------------------------------------------------------

    private Component createHeader() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Valikko");

        Icon logo = VaadinIcon.ACADEMY_CAP.create();
        logo.addClassName("app-logo");
        appTitle.addClassName("app-title");
        RouterLink brand = new RouterLink(DashboardView.class);
        brand.add(logo, appTitle);
        brand.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.SMALL,
                LumoUtility.TextColor.HEADER);

        // Kielivalinta tallennetaan VaadinSessioniin: Vaadin päivittää silloin kaikki saman session
        // UI:t ja kutsuu jokaisen LocaleChangeObserverin localeChange-metodia, joten näkymät kääntyvät
        // ilman sivun uudelleenlatausta, ja valinta säilyy navigoitaessa.
        languageSelect.setItems(FINNISH, Locale.ENGLISH);
        languageSelect.setItemLabelGenerator(l -> l.getLanguage().equals("fi") ? "FI" : "EN");
        languageSelect.setValue(currentLocaleOrDefault());
        languageSelect.setWidth("5.5em");
        languageSelect.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                VaadinSession.getCurrent().setLocale(e.getValue());
            }
        });

        HorizontalLayout header = new HorizontalLayout(toggle, brand);
        header.addClassName("app-header");
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        Div spacer = new Div();
        header.addAndExpand(spacer);
        header.add(languageSelect);
        header.add(createUserSection());
        return header;
    }

    private Component createUserSection() {
        HorizontalLayout section = new HorizontalLayout();
        section.setAlignItems(FlexComponent.Alignment.CENTER);
        section.setSpacing(true);

        Optional<AppUser> maybeUser = authenticatedUser.get();
        if (maybeUser.isPresent()) {
            AppUser user = maybeUser.get();
            Avatar avatar = createAvatar(user);
            Span name = new Span(user.getFullName());
            name.addClassName("user-name");

            // Avatar ja nimi vievät omaan profiiliin, jossa kuvan voi vaihtaa (vaatimus 19).
            RouterLink profileLink = new RouterLink(ProfileView.class);
            profileLink.add(avatar, name);
            profileLink.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER,
                    LumoUtility.Gap.SMALL, LumoUtility.TextColor.BODY);

            logoutButton = new Button(VaadinIcon.SIGN_OUT.create(), e -> authenticatedUser.logout());
            logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            section.add(profileLink, logoutButton);
        } else {
            loginButton = new Button(VaadinIcon.SIGN_IN.create(),
                    e -> UI.getCurrent().navigate(LoginView.class));
            loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            registerButton = new Button("", e -> UI.getCurrent().navigate(RegisterView.class));
            registerButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            // Piilotetaan kapealla näytöllä (layout.css); rekisteröitymislinkki löytyy myös kirjautumissivulta.
            registerButton.addClassName("header-register");
            section.add(registerButton, loginButton);
        }
        return section;
    }

    /**
     * Profiilikuva ladataan DownloadHandlerin kautta omana HTTP-pyyntönään: kuvaa ei upoteta sivuun
     * base64-muodossa, joten selain voi välimuistittaa sen eikä UI:n tila kasva megatavuilla.
     */
    private Avatar createAvatar(AppUser user) {
        Avatar avatar = new Avatar(user.getFullName());
        avatar.addThemeVariants(com.vaadin.flow.component.avatar.AvatarVariant.LUMO_SMALL);
        if (user.getProfileImage() != null) {
            String username = user.getUsername();
            avatar.setImageHandler(DownloadHandler.fromInputStream(event -> userService.findByUsername(username)
                    .filter(u -> u.getProfileImage() != null)
                    .map(u -> new DownloadResponse(new ByteArrayInputStream(u.getProfileImage()),
                            "avatar", u.getProfileImageType(), u.getProfileImage().length))
                    .orElse(DownloadResponse.error(404))));
        }
        return avatar;
    }

    // --- Navigaatio (vaatimus 14) ---------------------------------------------------------------

    private Component createDrawer() {
        drawerBrand.addClassName("drawer-brand");
        SideNav nav = new SideNav();
        for (NavEntry entry : NAV_ENTRIES) {
            // Näytetään vain linkit, joihin käyttäjällä on oikeus. Pääsynhallinta perustuu silti näkymien
            // annotaatioihin – tämä on vain käytettävyyttä, ei tietoturvaa (URL:n voi kirjoittaa käsin).
            if (accessChecker.hasAccess(entry.view())) {
                SideNavItem item = new SideNavItem("", entry.view(), entry.icon().create());
                navItems.add(item);
                visibleEntries.add(entry);
                nav.addItem(item);
            }
        }
        // SideNav korostaa automaattisesti nykyistä reittiä vastaavan linkin (current-attribuutti),
        // joten aktiivisen sivun korostus ei vaadi omaa navigointikuuntelijaa.
        Scroller scroller = new Scroller(nav);
        Div drawer = new Div(drawerBrand, scroller);
        drawer.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN);
        return drawer;
    }

    // --- Sisältö + footer (vaatimukset 11 ja 15) --------------------------------------------------

    @Override
    public void showRouterLayoutContent(HasElement content) {
        viewContainer.getElement().removeAllChildren();
        if (content != null) {
            viewContainer.getElement().appendChild(content.getElement());
        }
    }

    @Override
    public void removeRouterLayoutContent(HasElement oldContent) {
        viewContainer.getElement().removeAllChildren();
    }

    // --- Käyttöoikeuksien muutokset (vaatimus 16) ---------------------------------------------

    /**
     * Ylläpitäjän muuttaessa rooleja tai lukitessa tunnuksen käyttäjä kirjataan ulos heti Pushilla.
     * Vaadinin logout() toimii myös taustasäikeestä ui.access()-lohkossa: WebSocket-tilassa se tekee
     * ensin kierroksen selaimeen ja kirjaa ulos vasta sen jälkeen tulevassa HTTP-pyynnössä.
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent); // AppLayout alustaa tässä drawerin tilan
        UI ui = attachEvent.getUI();
        authenticatedUser.get().ifPresent(user -> revocationRegistration = revocations.register(
                user.getUsername(), () -> ui.access(authenticatedUser::logout)));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (revocationRegistration != null) {
            revocationRegistration.remove();
            revocationRegistration = null;
        }
    }

    /**
     * Varmistus tapauksiin, joissa Push-ilmoitus ei tavoita käyttäjää (esim. välilehti oli suljettu,
     * mutta istunto on yhä voimassa): jokaisella navigoinnilla tarkistetaan, vastaako istunto vielä
     * tietokannan tilaa.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (authenticatedUser.isAccessRevoked()) {
            authenticatedUser.logout();
        }
    }

    // --- Lokalisointi (vaatimus 23) -------------------------------------------------------------

    @Override
    public void localeChange(LocaleChangeEvent event) {
        appTitle.setText(getTranslation("app.name"));
        drawerBrand.setText(getTranslation("app.tagline"));
        for (int i = 0; i < navItems.size(); i++) {
            navItems.get(i).setLabel(getTranslation(visibleEntries.get(i).key()));
        }
        languageSelect.setAriaLabel(getTranslation("header.language"));
        if (logoutButton != null) {
            logoutButton.setText(getTranslation("header.logout"));
        }
        if (loginButton != null) {
            loginButton.setText(getTranslation("header.login"));
            registerButton.setText(getTranslation("header.register"));
        }
        // Valitsin päivitetään vastaamaan kieltä, jos se vaihdettiin toisessa välilehdessä.
        languageSelect.setValue(currentLocaleOrDefault());
    }

    private Locale currentLocaleOrDefault() {
        Locale locale = UI.getCurrent() != null ? UI.getCurrent().getLocale() : FINNISH;
        return "en".equals(locale.getLanguage()) ? Locale.ENGLISH : FINNISH;
    }
}
