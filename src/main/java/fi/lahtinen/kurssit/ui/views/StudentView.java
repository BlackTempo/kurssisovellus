package fi.lahtinen.kurssit.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AccessDeniedErrorRouter;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.router.AccessDeniedException;
import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.service.BusinessException;
import fi.lahtinen.kurssit.service.CourseService;
import fi.lahtinen.kurssit.service.CsvService;
import fi.lahtinen.kurssit.service.StudentService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.FormFields;
import fi.lahtinen.kurssit.ui.components.Notifications;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Opiskelijoiden hallinta (vaatimus 4: M:N-relaatio kursseihin) sekä CSV-tuonti ja -vienti (vaatimus 29).
 * <p>
 * Pääsy: vain SUPER- ja USER-roolit (vaatimus 16: "super ja user-käyttäjät näkevät tietyn sivun").
 * Pelkällä ADMIN-roolilla tänne ei pääse – tällä sivulla voi kokeilla omaa pääsy evätty -sivua
 * kirjautumalla admin-tunnuksella. {@link AccessDeniedErrorRouter} ohjaa evätyn pääsyn
 * AccessDeniedExceptionille (oma virhesivu); ilman sitä Vaadin näyttäisi tuotantotilassa 404-sivun,
 * jottei suojattujen reittien olemassaolo paljastu.
 */
@Route(value = "students", layout = MainLayout.class)
@PageTitle("Opiskelijat | Kurssisovellus")
@RolesAllowed({"SUPER", "USER"})
@AccessDeniedErrorRouter(rerouteToError = AccessDeniedException.class)
public class StudentView extends VerticalLayout {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");

    private final StudentService studentService;
    private final CourseService courseService;
    private final CsvService csvService;
    private final Grid<Student> grid = new Grid<>(Student.class, false);
    private final TextField filter = new TextField();
    private List<Student> students = List.of();

    public StudentView(StudentService studentService, CourseService courseService, CsvService csvService) {
        this.studentService = studentService;
        this.courseService = courseService;
        this.csvService = csvService;
        addClassName("page");
        setSizeFull();

        grid.addColumn(Student::getStudentNumber).setHeader("Op.nro").setAutoWidth(true).setSortable(true);
        grid.addColumn(Student::getLastName).setHeader("Sukunimi").setAutoWidth(true).setSortable(true);
        grid.addColumn(Student::getFirstName).setHeader("Etunimi").setAutoWidth(true).setSortable(true);
        grid.addColumn(Student::getEmail).setHeader("Sähköposti").setAutoWidth(true);
        grid.addColumn(s -> DATE.format(s.getBirthDate())).setHeader("Syntynyt").setAutoWidth(true)
                .setComparator(Student::getBirthDate);
        // M:N-relaatio näkyviin (vaatimus 4): kurssit badgeina
        grid.addComponentColumn(this::courseBadges).setHeader("Kurssit").setFlexGrow(1);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();
        grid.addItemClickListener(e -> openForm(e.getItem()));

        filter.setPlaceholder("Suodata nimellä tai numerolla…");
        filter.setWidth("18em");
        filter.setPrefixComponent(VaadinIcon.FILTER.create());
        filter.setClearButtonVisible(true);
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.addValueChangeListener(e -> applyFilter());

        Button add = new Button("Uusi opiskelija", VaadinIcon.PLUS.create(), e -> openForm(new Student()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(filter, add, createExportLink(), createImportUpload());
        toolbar.addClassName("toolbar");
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        add(new H2("Opiskelijat"), toolbar, grid);
        refresh();
    }

    private Component courseBadges(Student student) {
        Div list = new Div();
        list.addClassName("badge-list");
        student.getCourses().stream().sorted(Comparator.comparing(Course::getCode)).forEach(c -> {
            Span badge = new Span(c.getCode());
            badge.getElement().getThemeList().add("badge small");
            badge.getElement().setAttribute("title", c.getName());
            list.add(badge);
        });
        return list;
    }

    private void refresh() {
        students = studentService.findAll();
        applyFilter();
    }

    private void applyFilter() {
        String term = filter.getValue().trim().toLowerCase(Locale.ROOT);
        grid.setItems(students.stream()
                .filter(s -> term.isEmpty()
                        || s.getFullName().toLowerCase(Locale.ROOT).contains(term)
                        || s.getStudentNumber().toLowerCase(Locale.ROOT).contains(term))
                .toList());
    }

    // --- CSV (vaatimus 29) -----------------------------------------------------------------------

    /**
     * CSV muodostetaan vasta, kun käyttäjä klikkaa linkkiä (DownloadHandler kutsutaan latauspyynnössä),
     * joten tiedosto sisältää aina ajantasaisen datan eikä näkymän avaamisen hetken tilaa.
     */
    private Component createExportLink() {
        Anchor export = new Anchor(DownloadHandler.fromInputStream(event -> {
            byte[] csv = csvService.exportStudents();
            return new DownloadResponse(new ByteArrayInputStream(csv), "opiskelijat-" + LocalDate.now() + ".csv",
                    "text/csv;charset=UTF-8", csv.length);
        }), AttachmentType.DOWNLOAD, "");
        Button button = new Button("Vie CSV", VaadinIcon.DOWNLOAD.create());
        button.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        export.add(button);
        return export;
    }

    private Component createImportUpload() {
        Upload upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
            UI ui = getUI().orElse(null);
            if (ui == null) {
                return;
            }
            ui.access(() -> {
                try {
                    CsvService.ImportResult result = csvService.importStudents(new ByteArrayInputStream(data));
                    refresh();
                    showImportResult(result);
                } catch (BusinessException ex) {
                    Notifications.error(ex.getMessage());
                }
            });
        }));
        upload.setAcceptedFileTypes(".csv", "text/csv");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(2 * 1024 * 1024);
        upload.setDropAllowed(false);
        Button uploadButton = new Button("Tuo CSV", VaadinIcon.UPLOAD.create());
        uploadButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        upload.setUploadButton(uploadButton);
        // Tuonnin jälkeen tiedostolista tyhjennetään, jotta saman tiedoston voi tuoda uudelleen korjattuna.
        upload.addAllFinishedListener(e -> upload.clearFileList());
        return upload;
    }

    private void showImportResult(CsvService.ImportResult result) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("CSV-tuonnin tulos");
        dialog.add(new Paragraph("Uusia opiskelijoita: " + result.created() + ", päivitettyjä: " + result.updated()
                + ", hylättyjä rivejä: " + result.errors().size()));
        if (!result.errors().isEmpty()) {
            UnorderedList errors = new UnorderedList();
            result.errors().forEach(err -> errors.add(new ListItem(err)));
            errors.getStyle().set("color", "var(--lumo-error-text-color)");
            dialog.add(errors);
        }
        dialog.getFooter().add(new Button("Sulje", e -> dialog.close()));
        dialog.setWidth("min(640px, 95vw)");
        dialog.open();
    }

    // --- Lomake ----------------------------------------------------------------------------------

    private void openForm(Student student) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(student.isNew() ? "Uusi opiskelija" : "Muokkaa: " + student.getFullName());

        TextField studentNumber = new TextField("Opiskelijanumero");
        studentNumber.setPlaceholder("S123456");
        TextField firstName = new TextField("Etunimi");
        TextField lastName = new TextField("Sukunimi");
        EmailField email = FormFields.email("Sähköposti");
        DatePicker birthDate = FormFields.date("Syntymäaika");
        birthDate.setMax(LocalDate.now().minusDays(1));
        DatePicker enrolledAt = FormFields.date("Opinnot alkaneet");
        enrolledAt.setMax(LocalDate.now());
        // M:N-relaation muokkaus: useiden kurssien valinta
        MultiSelectComboBox<Course> courses = new MultiSelectComboBox<>("Kurssit");
        courses.setItems(courseService.findAllForSelection());
        courses.setItemLabelGenerator(Course::toString);

        BeanValidationBinder<Student> binder = new BeanValidationBinder<>(Student.class);
        binder.forField(studentNumber).bind("studentNumber");
        binder.forField(firstName).bind("firstName");
        binder.forField(lastName).bind("lastName");
        binder.forField(email).bind("email");
        binder.forField(birthDate).bind("birthDate");
        binder.forField(enrolledAt).bind("enrolledAt");
        // Uusi HashSet kirjoitettaessa: MultiSelectComboBoxin palauttama joukko on muuttumaton.
        binder.forField(courses).bind(Student::getCourses, (s, v) -> s.setCourses(new HashSet<>(v)));
        binder.readBean(student);

        FormLayout form = new FormLayout(studentNumber, email, firstName, lastName, birthDate, enrolledAt, courses);
        form.setColspan(courses, 2);
        dialog.add(form);

        Button save = new Button("Tallenna", e -> {
            if (binder.writeBeanIfValid(student)) {
                try {
                    studentService.save(student);
                    Notifications.success("Opiskelija tallennettu");
                    dialog.close();
                } catch (BusinessException ex) {
                    Notifications.error(ex.getMessage());
                } catch (ObjectOptimisticLockingFailureException ex) {
                    Notifications.error("Toinen käyttäjä muokkasi tietoja samaan aikaan.");
                    dialog.close();
                }
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Peruuta", e -> dialog.close()), save);

        if (!student.isNew()) {
            Button delete = new Button("Poista", VaadinIcon.TRASH.create(), e -> {
                ConfirmDialog confirm = new ConfirmDialog("Poistetaanko opiskelija?",
                        student.getFullName() + " poistetaan ja hänen ilmoittautumisensa perutaan.", "Poista", ev -> {
                    studentService.delete(student);
                    Notifications.success("Opiskelija poistettu");
                    dialog.close();
                }, "Peruuta", ev -> { });
                confirm.setConfirmButtonTheme("error primary");
                confirm.open();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.getStyle().set("margin-inline-end", "auto");
            dialog.getFooter().addComponentAsFirst(delete);
        }
        // Grid ladataan aina uudelleen dialogin sulkeutuessa (tallennus, poisto tai peruutus). Binder
        // kirjoittaa arvot gridin riviin ennen tallennusta; jos tallennus hylätään (esim. varattu
        // sähköposti) ja käyttäjä peruuttaa, rivissä ei saa näkyä tallentamattomia arvoja.
        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                refresh();
            }
        });
        dialog.setWidth("min(680px, 95vw)");
        dialog.open();
    }
}
