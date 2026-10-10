package fi.lahtinen.kurssit.ui.views;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.LumoUtility.Background;
import com.vaadin.flow.theme.lumo.LumoUtility.BorderRadius;
import com.vaadin.flow.theme.lumo.LumoUtility.BoxShadow;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.FontWeight;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;
import com.vaadin.flow.theme.lumo.LumoUtility.Padding;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.security.AuthenticatedUser;
import fi.lahtinen.kurssit.service.Broadcaster;
import fi.lahtinen.kurssit.service.CourseService;
import fi.lahtinen.kurssit.service.DataChangedEvent;
import fi.lahtinen.kurssit.service.HtmlSanitizer;
import fi.lahtinen.kurssit.service.StudentService;
import fi.lahtinen.kurssit.service.TeacherService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.views.auth.LoginView;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Etusivu / dashboard – näkyy kaikille, myös kirjautumattomille (vaatimus 16: "kaikki näkevät päänäkymän").
 * <p>
 * Tämä näkymä demonstroi useita tyylivaatimuksia:
 * <ul>
 *   <li>Vaatimus 8: näkymäkohtainen CSS – juuriluokka {@code dashboard-view} rajaa
 *       {@code views/dashboard-view.css}:n säännöt vain tähän näkymään.</li>
 *   <li>Vaatimus 9: Lumo Utility -luokat (Background, TextColor, Padding, BoxShadow, BorderRadius,
 *       Display, FontSize, Margin) hero-osiossa.</li>
 *   <li>Vaatimus 10: kurssikortit ({@code course-card}: hover-zoom, focus, transition) ja
 *       fade-in-animaatiolla ilmestyvä nappi.</li>
 *   <li>Vaatimus 7: addClassName, getStyle().set() ja addThemeVariants.</li>
 * </ul>
 * Layout on korttiruudukko (CSS grid), joka eroaa rakenteeltaan master–detail-näkymästä
 * (CourseView) ja aikajananäkymästä (HistoryView) – vaatimus 12.
 * <p>
 * Server Push (vaatimus 22): "Live-muutokset"-lista päivittyy, kun kuka tahansa muokkaa tietoja.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Etusivu | Kurssisovellus")
@AnonymousAllowed
public class DashboardView extends VerticalLayout implements LocaleChangeObserver {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_ACTIVITY = 8;

    private final AuthenticatedUser authenticatedUser;
    private final CourseService courseService;
    private final TeacherService teacherService;
    private final StudentService studentService;
    private final Broadcaster broadcaster;

    private final H1 welcome = new H1();
    private final Paragraph intro = new Paragraph();
    private final Button browseButton = new Button();
    private final HorizontalLayout stats = new HorizontalLayout();
    private final H2 upcomingTitle = new H2();
    private final Div courseGrid = new Div();
    private final H2 activityTitle = new H2();
    private final VerticalLayout activityList = new VerticalLayout();
    private final Span activityEmpty = new Span();
    private Registration broadcasterRegistration;

    public DashboardView(AuthenticatedUser authenticatedUser, CourseService courseService,
                         TeacherService teacherService, StudentService studentService, Broadcaster broadcaster) {
        this.authenticatedUser = authenticatedUser;
        this.courseService = courseService;
        this.teacherService = teacherService;
        this.studentService = studentService;
        this.broadcaster = broadcaster;

        addClassName("dashboard-view");
        addClassName("page");
        setSpacing(false);

        add(createHero());
        stats.addClassName("card-row");
        stats.setWidthFull();
        add(stats);

        courseGrid.addClassName("course-grid");
        courseGrid.setWidthFull();
        add(upcomingTitle, courseGrid);

        activityList.setPadding(false);
        activityList.setSpacing(false);
        activityEmpty.addClassName(TextColor.SECONDARY);
        activityList.add(activityEmpty);
        add(activityTitle, activityList);
    }

    /** Hero-osio rakennetaan pelkillä Lumo Utility -luokilla (vaatimus 9) ilman omaa CSS:ää. */
    private Component createHero() {
        welcome.addClassNames(FontSize.XXXLARGE, Margin.NONE, TextColor.PRIMARY_CONTRAST);
        intro.addClassNames(FontSize.LARGE, Margin.Top.SMALL, Margin.Bottom.LARGE, TextColor.PRIMARY_CONTRAST);

        // Vaatimus 7c + 10: addThemeVariants ja fade-in-animaatio (CSS-luokka fade-in-button).
        browseButton.addThemeVariants(ButtonVariant.LUMO_LARGE);
        browseButton.addClassNames("fade-in-button", "hero-button");
        browseButton.setIcon(VaadinIcon.ARROW_RIGHT.create());
        browseButton.setIconAfterText(true);
        browseButton.addClickListener(e -> openCourses());

        Div hero = new Div(welcome, intro, browseButton);
        hero.addClassNames(
                Background.PRIMARY,
                Padding.XLARGE,
                BorderRadius.LARGE,
                BoxShadow.MEDIUM,
                Display.BLOCK,
                Margin.Bottom.LARGE);
        hero.setWidthFull();
        // Vaatimus 7b: getStyle().set() – liukuväri, jota Lumo Utility -luokilla ei voi ilmaista.
        hero.getStyle().set("background-image",
                "linear-gradient(135deg, var(--lumo-primary-color) 0%, hsl(250, 60%, 45%) 100%)");
        hero.getStyle().set("box-sizing", "border-box");
        return hero;
    }

    private void refreshContent() {
        stats.removeAll();
        stats.add(
                statCard(VaadinIcon.BOOK, courseService.count(), "dashboard.stat.courses"),
                statCard(VaadinIcon.USER_STAR, teacherService.count(), "dashboard.stat.teachers"),
                statCard(VaadinIcon.GROUP, studentService.count(), "dashboard.stat.students"));

        courseGrid.removeAll();
        List<Course> upcoming = courseService.findUpcoming(6);
        if (upcoming.isEmpty()) {
            courseGrid.add(new Span(getTranslation("dashboard.upcoming.empty")));
        }
        upcoming.forEach(c -> courseGrid.add(courseCard(c)));
    }

    private Component statCard(VaadinIcon icon, long value, String labelKey) {
        Icon i = icon.create();
        i.addClassName("stat-icon");
        Span number = new Span(String.valueOf(value));
        number.addClassName("stat-value");
        Span label = new Span(getTranslation(labelKey));
        label.addClassName(TextColor.SECONDARY);
        Div text = new Div(number, label);
        text.addClassNames(Display.FLEX, com.vaadin.flow.theme.lumo.LumoUtility.FlexDirection.COLUMN);
        Div card = new Div(i, text);
        card.addClassName("stat-card");
        return card;
    }

    /**
     * Kurssikortti: CSS-luokka {@code course-card} tuo hover-zoomin, focus-korostuksen ja transitionin
     * (vaatimus 10). tabindex tekee kortista näppäimistöllä fokusoitavan, jotta :focus-visible -tila on
     * saavutettavissa ilman hiirtä.
     */
    private Component courseCard(Course course) {
        H3 title = new H3(course.getName());
        title.addClassNames(FontSize.LARGE, Margin.Vertical.XSMALL);

        Span code = new Span(course.getCode());
        code.getElement().getThemeList().add("badge contrast small");
        Span credits = new Span(getTranslation("dashboard.credits", course.getCredits()));
        credits.getElement().getThemeList().add("badge small");

        Paragraph description = new Paragraph(truncate(HtmlSanitizer.toPlainText(course.getDescription()), 110));
        description.addClassNames(FontSize.SMALL, TextColor.SECONDARY, Margin.Vertical.SMALL);

        Span meta = new Span(getTranslation("dashboard.starts", DATE.format(course.getStartDate()))
                + " · " + course.getTeacher().getFullName());
        meta.addClassNames(FontSize.XSMALL, TextColor.TERTIARY);

        Div card = new Div(new Div(code, new Span(" "), credits), title, description, meta);
        card.addClassName("course-card");
        // Vaatimus 7b: korostusvärinen vasen reuna inline-tyylinä; värin voisi myös valita kurssikohtaisesti.
        card.getStyle().set("border-left", "4px solid var(--app-accent)");
        card.getElement().setAttribute("tabindex", "0");
        card.getElement().setAttribute("role", "link");
        card.addClickListener(e -> openCourses());
        return card;
    }

    /** Kurssisivu vaatii kirjautumisen, joten anonyymi käyttäjä ohjataan suoraan kirjautumaan. */
    private void openCourses() {
        if (authenticatedUser.isLoggedIn()) {
            UI.getCurrent().navigate(CourseView.class);
        } else {
            UI.getCurrent().navigate(LoginView.class);
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    // --- Server Push (vaatimus 22) -------------------------------------------------------------

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        refreshContent();
        // Kuuntelija kutsutaan Broadcasterin säikeessä, ei käyttäjän pyynnön säikeessä. UI:ta saa muokata
        // vain session lukon alla, ja ui.access() hankkii lukon sekä lähettää muutokset selaimeen
        // @Push-yhteyden kautta heti lukon vapautuessa.
        broadcasterRegistration = broadcaster.register(event -> ui.access(() -> {
            addActivity(event);
            refreshContent();
        }));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // Rekisteröinti poistetaan aina, kun käyttäjä poistuu näkymästä tai sulkee välilehden. Muuten
        // Broadcaster pitäisi viittausta suljettuun UI:hin ja yrittäisi päivittää sitä – muistivuoto.
        if (broadcasterRegistration != null) {
            broadcasterRegistration.remove();
            broadcasterRegistration = null;
        }
    }

    private void addActivity(DataChangedEvent event) {
        activityEmpty.setVisible(false);
        Span time = new Span(TIME.format(event.at()) + " ");
        time.addClassNames(TextColor.SECONDARY, FontSize.SMALL);
        Span text = new Span(event.describe());
        text.addClassName(FontWeight.MEDIUM);
        Div item = new Div(time, text);
        item.addClassName("activity-item");
        activityList.addComponentAtIndex(1, item);
        // Rajataan listan pituus, ettei pitkään auki ollut välilehti kerrytä loputtomasti komponentteja.
        while (activityList.getComponentCount() > MAX_ACTIVITY + 1) {
            activityList.remove(activityList.getComponentAt(activityList.getComponentCount() - 1));
        }
    }

    // --- Lokalisointi (vaatimus 23) -------------------------------------------------------------

    @Override
    public void localeChange(LocaleChangeEvent event) {
        welcome.setText(authenticatedUser.get()
                .map(u -> getTranslation("dashboard.welcome", u.getFullName()))
                .orElse(getTranslation("dashboard.welcome.anonymous")));
        intro.setText(getTranslation("dashboard.intro"));
        browseButton.setText(getTranslation("dashboard.browse"));
        upcomingTitle.setText(getTranslation("dashboard.upcoming"));
        activityTitle.setText(getTranslation("dashboard.activity"));
        activityEmpty.setText(getTranslation("dashboard.activity.empty"));
        if (isAttached()) {
            refreshContent();
        }
    }
}
