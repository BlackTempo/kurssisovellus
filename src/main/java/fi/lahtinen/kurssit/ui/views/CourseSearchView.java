package fi.lahtinen.kurssit.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.Role;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.domain.Teacher;
import fi.lahtinen.kurssit.repository.CourseFilter;
import fi.lahtinen.kurssit.security.AuthenticatedUser;
import fi.lahtinen.kurssit.service.CourseService;
import fi.lahtinen.kurssit.service.StudentService;
import fi.lahtinen.kurssit.service.TeacherService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.FormFields;
import jakarta.annotation.security.PermitAll;

import java.time.format.DateTimeFormatter;

/**
 * Edistynyt kurssihaku (Suodattaminen, kohdat 1–5). Kaikki haku tehdään Criteria API:lla
 * ({@code CourseRepositoryImpl}); tämä näkymä vain kokoaa ehdot {@link CourseFilter}-olioksi.
 * <p>
 * Grid käyttää lazy-dataprovideria: tietokannasta haetaan vain näkyvissä oleva sivu ja erillinen
 * count-kysely kertoo kokonaismäärän. Näin haku toimii myös suurella datamäärällä.
 */
@Route(value = "search", layout = MainLayout.class)
@PageTitle("Kurssihaku | Kurssisovellus")
@PermitAll
public class CourseSearchView extends VerticalLayout {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");

    // Kohta 1: useita syötekenttiä
    private final TextField code = new TextField("Kurssikoodi sisältää");
    private final TextField name = new TextField("Nimi sisältää");
    private final IntegerField minCredits = new IntegerField("Opintopisteitä vähintään");
    // Kohta 2: päivämääräväli
    private final DatePicker startFrom = FormFields.date("Alkaa aikaisintaan");
    private final DatePicker startTo = FormFields.date("Alkaa viimeistään", "Yläraja ei voi olla ennen alarajaa", null);
    // Kohta 3: relaatioentiteetti (JOIN M:N)
    private final ComboBox<Student> student = new ComboBox<>("Ilmoittautunut opiskelija");
    // Kohta 4: relaatioentiteetin ominaisuus (JOIN + LIKE)
    private final TextField teacherLastName = new TextField("Opettajan sukunimi sisältää");
    // Kohta 5: (X OR Y) AND Z
    private final TextField keyword = new TextField("Hakusana (nimi TAI kuvaus)");
    private final ComboBox<Teacher> teacher = new ComboBox<>("JA opettaja on");

    private final Grid<Course> grid = new Grid<>(Course.class, false);
    private final Span resultCount = new Span();
    private final CallbackDataProvider<Course, Void> dataProvider;
    private CourseFilter currentFilter = CourseFilter.empty();

    public CourseSearchView(CourseService courseService, StudentService studentService, TeacherService teacherService,
                            AuthenticatedUser authenticatedUser) {
        addClassName("page");
        setSizeFull();

        // Opiskelijalista paljastaa opiskelijoiden nimet, joten ehto näytetään vain rooleille, joilla on
        // pääsy opiskelijasivulle (SUPER, USER). ADMIN-käyttäjältä kenttä piilotetaan, eikä listaa ladata.
        if (authenticatedUser.hasAnyRole(Role.SUPER, Role.USER)) {
            student.setItems(studentService.findAll());
            student.setItemLabelGenerator(Student::toString);
        } else {
            student.setVisible(false);
        }
        teacher.setItems(teacherService.findAll());
        teacher.setItemLabelGenerator(Teacher::getFullName);
        minCredits.setMin(1);
        startFrom.addValueChangeListener(e -> startTo.setMin(e.getValue()));

        // fromCallbacks: ensimmäinen callback hakee sivun (offset/limit gridiltä), toinen laskee kokonaismäärän.
        dataProvider = DataProvider.fromCallbacks(
                query -> courseService.search(currentFilter, query.getOffset(), query.getLimit()).stream(),
                query -> {
                    int count = (int) courseService.countSearch(currentFilter);
                    resultCount.setText(count + " kurssia");
                    return count;
                });

        grid.addColumn(Course::getCode).setHeader("Koodi").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(Course::getName).setHeader("Nimi");
        grid.addColumn(c -> c.getTeacher().getFullName()).setHeader("Opettaja").setAutoWidth(true);
        grid.addColumn(Course::getCredits).setHeader("op").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(c -> DATE.format(c.getStartDate())).setHeader("Alkaa").setAutoWidth(true);
        grid.addColumn(c -> DATE.format(c.getEndDate())).setHeader("Päättyy").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setItems(dataProvider);
        grid.setSizeFull();

        resultCount.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        add(new H2("Kurssihaku"), createFilterPanel(), resultCount, grid);
    }

    private Component createFilterPanel() {
        for (TextField f : new TextField[]{code, name, teacherLastName, keyword}) {
            f.setClearButtonVisible(true);
        }
        student.setClearButtonVisible(true);
        teacher.setClearButtonVisible(true);

        FormLayout basic = new FormLayout(code, name, minCredits, startFrom, startTo);
        basic.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 3));

        FormLayout relations = new FormLayout(student, teacherLastName);
        relations.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));

        Paragraph complexHelp = new Paragraph("Monimutkainen haku: (nimi sisältää hakusanan TAI kuvaus sisältää "
                + "hakusanan) JA kurssin opettaja on valittu opettaja. Kumpaakin ehtoa voi käyttää myös yksinään.");
        complexHelp.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY, LumoUtility.Margin.NONE);
        FormLayout complex = new FormLayout(keyword, teacher);
        complex.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));

        Details relationDetails = new Details("Relaatioihin perustuvat ehdot", relations);
        relationDetails.setOpened(true);
        Details complexDetails = new Details("Monimutkainen haku (X OR Y) AND Z", new VerticalLayout(complexHelp, complex));
        complexDetails.setOpened(true);

        Button search = new Button("Hae", VaadinIcon.SEARCH.create(), e -> runSearch());
        search.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        search.addClickShortcut(Key.ENTER);
        Button clear = new Button("Tyhjennä", e -> clearFilters());
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        VerticalLayout panel = new VerticalLayout(basic, relationDetails, complexDetails, new HorizontalLayout(search, clear));
        panel.setPadding(false);
        panel.setSpacing(false);
        return panel;
    }

    private void runSearch() {
        currentFilter = new CourseFilter(
                code.getValue(),
                name.getValue(),
                minCredits.getValue(),
                startFrom.getValue(),
                startTo.getValue(),
                student.getValue(),
                teacherLastName.getValue(),
                keyword.getValue(),
                teacher.getValue());
        dataProvider.refreshAll();
    }

    private void clearFilters() {
        code.clear();
        name.clear();
        minCredits.clear();
        startFrom.clear();
        startTo.clear();
        student.clear();
        teacherLastName.clear();
        keyword.clear();
        teacher.clear();
        runSearch();
    }
}
