package fi.lahtinen.kurssit.ui.views;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.shared.Registration;
import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.CourseAttachment;
import fi.lahtinen.kurssit.domain.Role;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.domain.Teacher;
import fi.lahtinen.kurssit.security.AuthenticatedUser;
import fi.lahtinen.kurssit.service.BusinessException;
import fi.lahtinen.kurssit.service.Broadcaster;
import fi.lahtinen.kurssit.service.CourseService;
import fi.lahtinen.kurssit.service.DataChangedEvent.EntityType;
import fi.lahtinen.kurssit.service.FileStorageService;
import fi.lahtinen.kurssit.service.HistoryService;
import fi.lahtinen.kurssit.service.TeacherService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.FormFields;
import fi.lahtinen.kurssit.ui.components.HistoryTimeline;
import fi.lahtinen.kurssit.ui.components.Notifications;
import fi.lahtinen.kurssit.ui.components.QuillEditor;
import jakarta.annotation.security.PermitAll;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Kurssien hallinta master–detail-layoutilla (vaatimukset 3, 12, 22, 28, 32, 33).
 * <p>
 * Vasemmalla kurssigrid, oikealla muokkauslomake – rakenteeltaan erilainen kuin dashboardin
 * korttiruudukko tai opettajanäkymän dialogilomake. Grid näyttää 1:N-relaation (opettaja) ja
 * M:N-relaation (ilmoittautuneet opiskelijat). Lomakkeen kuvauskenttä on Quill.js-editori.
 * <p>
 * Pääsy: kaikki kirjautuneet ({@link PermitAll}).
 */
@Route(value = "courses", layout = MainLayout.class)
@PageTitle("Kurssit | Kurssisovellus")
@PermitAll
public class CourseView extends SplitLayout {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");

    private final CourseService courseService;
    private final TeacherService teacherService;
    private final FileStorageService fileStorageService;
    private final HistoryService historyService;
    private final Broadcaster broadcaster;

    private final Grid<Course> grid = new Grid<>(Course.class, false);
    // Muokattava lista + ListDataProvider: päivitys refreshAll()-kutsulla säilyttää gridin valinnan.
    // grid.setItems() nollaisi valinnan ja tyhjentäisi lomakkeen kesken muokkauksen, kun toinen
    // käyttäjä tallentaa jotain (Push-päivitys).
    private final List<Course> items = new ArrayList<>();
    private final ListDataProvider<Course> dataProvider = new ListDataProvider<>(items);
    private final TextField filter = new TextField();

    // Lomakkeen kentät: nimet vastaavat entiteetin kenttiä, jolloin bindInstanceFields sitoo ne
    // automaattisesti ja BeanValidationBinder ottaa validoinnit entiteetin annotaatioista.
    private final TextField code = new TextField("Kurssikoodi");
    private final TextField name = new TextField("Nimi");
    private final IntegerField credits = new IntegerField("Opintopisteet");
    private final IntegerField maxStudents = new IntegerField("Max. osallistujat");
    private final DatePicker startDate = FormFields.date("Alkaa");
    private final DatePicker endDate = FormFields.date("Päättyy", "Loppupäivä ei voi olla ennen alkupäivää", null);
    private final ComboBox<Teacher> teacher = new ComboBox<>("Opettaja");
    private final QuillEditor description = new QuillEditor();

    private final BeanValidationBinder<Course> binder = new BeanValidationBinder<>(Course.class);
    private final H2 formTitle = new H2();
    private final Button save = new Button("Tallenna", VaadinIcon.CHECK.create());
    private final Button cancel = new Button("Peruuta");
    private final Button delete = new Button("Poista", VaadinIcon.TRASH.create());
    private final Button history = new Button("Historia", VaadinIcon.TIME_BACKWARD.create());
    private final VerticalLayout attachmentSection = new VerticalLayout();
    private final Div attachmentList = new Div();
    private final VerticalLayout formLayout = new VerticalLayout();

    /**
     * Opiskelijoiden nimet näytetään vain rooleille, joilla on pääsy opiskelijasivulle. Muuten esim.
     * ADMIN näkisi nimet tooltipistä, vaikka itse opiskelijasivu on häneltä estetty.
     */
    private final boolean canSeeStudents;

    private Course current;
    private Registration broadcasterRegistration;

    public CourseView(CourseService courseService, TeacherService teacherService,
                      FileStorageService fileStorageService, HistoryService historyService,
                      Broadcaster broadcaster, AuthenticatedUser authenticatedUser) {
        this.courseService = courseService;
        this.teacherService = teacherService;
        this.fileStorageService = fileStorageService;
        this.historyService = historyService;
        this.broadcaster = broadcaster;
        this.canSeeStudents = authenticatedUser.hasAnyRole(Role.SUPER, Role.USER);

        setSizeFull();
        setSplitterPosition(58);
        addToPrimary(createGridSection());
        addToSecondary(createFormSection());
        edit(null);
    }

    // --- Listaus ---------------------------------------------------------------------------------

    private Component createGridSection() {
        grid.addColumn(Course::getCode).setHeader("Koodi").setAutoWidth(true).setFlexGrow(0).setSortable(true);
        grid.addColumn(Course::getName).setHeader("Nimi").setFlexGrow(1).setSortable(true);
        // 1:N-relaatio näkyviin (vaatimus 3)
        grid.addColumn(c -> c.getTeacher().getFullName()).setHeader("Opettaja").setAutoWidth(true).setSortable(true);
        grid.addColumn(Course::getCredits).setHeader("op").setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(c -> DATE.format(c.getStartDate())).setHeader("Alkaa").setAutoWidth(true)
                .setComparator(Course::getStartDate);
        // M:N-relaatio näkyviin (vaatimus 4): täyttöaste badgena, nimet tooltipissä (jos rooli sallii).
        grid.addComponentColumn(this::studentsBadge).setHeader("Opiskelijat").setAutoWidth(true)
                .setComparator(c -> c.getStudents().size());
        // Spring Data Auditingin kentät (vaatimus 30)
        grid.addColumn(Course::getLastModifiedBy).setHeader("Muokannut").setAutoWidth(true);

        // Vaatimus 7c: addThemeVariants
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setDataProvider(dataProvider);
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(e -> edit(e.getValue()));

        filter.setPlaceholder("Suodata koodilla tai nimellä…");
        filter.setPrefixComponent(VaadinIcon.FILTER.create());
        filter.setClearButtonVisible(true);
        // LAZY: suodatus käynnistyy vasta kirjoittamisen tauottua, ei jokaisesta näppäilystä.
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.addValueChangeListener(e -> applyFilter());

        Button add = new Button("Uusi kurssi", VaadinIcon.PLUS.create(), e -> {
            grid.asSingleSelect().clear();
            edit(new Course());
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(filter, add);
        toolbar.addClassName("toolbar");
        toolbar.setWidthFull();
        toolbar.expand(filter);

        VerticalLayout section = new VerticalLayout(new H2("Kurssit"), toolbar, grid);
        section.setSizeFull();
        section.addClassName("page");
        return section;
    }

    private Component studentsBadge(Course course) {
        int count = course.getStudents().size();
        Span badge = new Span(count + " / " + course.getMaxStudents());
        boolean full = count >= course.getMaxStudents();
        badge.getElement().getThemeList().add(full ? "badge error" : "badge success");
        String tooltip;
        if (count == 0) {
            tooltip = "Ei ilmoittautuneita";
        } else if (canSeeStudents) {
            tooltip = course.getStudents().stream().map(Student::getFullName).sorted()
                    .collect(Collectors.joining(", "));
        } else {
            tooltip = count + " ilmoittautunutta";
        }
        badge.getElement().setAttribute("title", tooltip);
        return badge;
    }

    private void applyFilter() {
        String term = filter.getValue().trim().toLowerCase(Locale.ROOT);
        dataProvider.setFilter(c -> term.isEmpty()
                || c.getCode().toLowerCase(Locale.ROOT).contains(term)
                || c.getName().toLowerCase(Locale.ROOT).contains(term));
    }

    private void refreshGrid() {
        items.clear();
        items.addAll(courseService.findAllWithDetails());
        dataProvider.refreshAll();
    }

    // --- Lomake ----------------------------------------------------------------------------------

    private Component createFormSection() {
        teacher.setItemLabelGenerator(Teacher::getFullName);
        description.setPlaceholder("Kurssin kuvaus…");
        code.setPlaceholder("ABC-123");
        code.setHelperText("2–4 isoa kirjainta, viiva ja 3 numeroa");

        FormLayout form = new FormLayout(code, name, credits, maxStudents, startDate, endDate, teacher);
        form.setColspan(name, 2);
        form.setColspan(teacher, 2);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

        // Loppupäivän tarkistus suoraan kenttään: entiteetin @AssertTrue on luokkatason sääntö, jota
        // BeanValidationBinder ei osaa kohdistaa yksittäiseen kenttään, joten käyttäjä ei näkisi sitä.
        Binder.Binding<Course, ?> endBinding = binder.forField(endDate)
                .withValidator(end -> end == null || startDate.getValue() == null || !end.isBefore(startDate.getValue()),
                        "Loppupäivä ei voi olla ennen alkupäivää")
                .bind("endDate");
        startDate.addValueChangeListener(e -> {
            endBinding.validate();
            endDate.setMin(e.getValue());
        });
        binder.forField(description).withNullRepresentation("").bind("description");
        binder.bindInstanceFields(this);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> saveCurrent());
        cancel.addClickListener(e -> {
            grid.asSingleSelect().clear();
            edit(null);
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        delete.addClickListener(e -> confirmDelete());
        history.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        history.addClickListener(e -> showHistory());

        HorizontalLayout buttons = new HorizontalLayout(save, cancel, delete, history);
        buttons.addClassName("toolbar");

        attachmentSection.setPadding(false);
        attachmentSection.add(new H3("Liitteet"), attachmentList, createUpload());

        NativeLabel descLabel = new NativeLabel("Kuvaus");
        descLabel.addClassName("vaadin-label");
        formLayout.add(formTitle, form, descLabel, description, buttons, attachmentSection);
        formLayout.addClassName("page");
        formLayout.setWidthFull();
        return formLayout;
    }

    private void edit(Course course) {
        if (course == null) {
            current = null;
            formLayout.setVisible(false);
            return;
        }
        // Lomakkeelle haetaan aina tuore kopio tietokannasta: gridin rivi voi olla vanhentunut (Push),
        // ja binder muokkaa oliota suoraan – peruutettu muokkaus ei saa jäädä gridin riviin.
        current = course.isNew() ? course : courseService.findWithDetails(course.getId()).orElse(null);
        if (current == null) {
            Notifications.error("Kurssia ei enää löydy – se on ehkä poistettu.");
            refreshGrid();
            return;
        }
        teacher.setItems(teacherService.findAll());
        binder.setBean(current);
        formTitle.setText(current.isNew() ? "Uusi kurssi" : "Muokkaa: " + current.getCode());
        delete.setVisible(!current.isNew());
        history.setVisible(!current.isNew());
        attachmentSection.setVisible(!current.isNew());
        refreshAttachments();
        formLayout.setVisible(true);
    }

    private void saveCurrent() {
        if (current == null || !binder.validate().isOk()) {
            Notifications.error("Tarkista lomakkeen virheet.");
            return;
        }
        try {
            Course saved = courseService.save(current);
            Notifications.success("Kurssi " + saved.getCode() + " tallennettu");
            refreshGrid();
            edit(saved);
        } catch (BusinessException ex) {
            Notifications.error(ex.getMessage());
        } catch (ObjectOptimisticLockingFailureException ex) {
            // @Version havaitsi, että joku muu ehti tallentaa saman kurssin välissä.
            Notifications.error("Toinen käyttäjä muokkasi kurssia samaan aikaan. Lomake ladattiin uudelleen.");
            edit(current);
        }
    }

    private void confirmDelete() {
        ConfirmDialog dialog = new ConfirmDialog(
                "Poistetaanko kurssi?",
                "Kurssi " + current.getCode() + " poistetaan liitteineen ja ilmoittautumisineen. "
                        + "Muutos näkyy muutoshistoriassa.",
                "Poista", e -> {
                    try {
                        courseService.delete(current);
                        Notifications.success("Kurssi poistettu");
                        grid.asSingleSelect().clear();
                        edit(null);
                        refreshGrid();
                    } catch (BusinessException ex) {
                        Notifications.error(ex.getMessage());
                    }
                },
                "Peruuta", e -> { });
        dialog.setConfirmButtonTheme("error primary");
        dialog.open();
    }

    /** Yksittäisen kurssin muutoshistoria aikajanana (vaatimus 32). */
    private void showHistory() {
        HistoryTimeline timeline = new HistoryTimeline();
        timeline.setEntries(historyService.forEntity(HistoryService.EntityKind.COURSE, current.getId()));
        Dialog dialog = new Dialog(timeline);
        dialog.setHeaderTitle("Historia: " + current.getCode());
        dialog.setWidth("min(560px, 95vw)");
        dialog.getFooter().add(new Button("Sulje", e -> dialog.close()));
        dialog.open();
    }

    // --- Liitteet (vaatimus 28) ------------------------------------------------------------------

    private Upload createUpload() {
        Upload upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
            // Upload-pyyntö käsitellään omassa HTTP-pyynnössään; UI:n päivitys tehdään access-kutsulla,
            // jotta session lukko on varmasti hallussa ja muutos lähtee selaimeen Pushilla.
            Long courseId = current.getId();
            getUI().ifPresent(ui -> ui.access(() -> {
                try {
                    fileStorageService.store(courseId, metadata.fileName(), metadata.contentType(), data);
                    Notifications.success("Liite " + metadata.fileName() + " tallennettu");
                    refreshAttachments();
                } catch (BusinessException ex) {
                    Notifications.error(ex.getMessage());
                }
            }));
        }));
        upload.setAcceptedFileTypes(".pdf", ".png", ".jpg", ".jpeg", ".txt", ".csv", ".docx", ".xlsx", ".pptx");
        // Raja asetetaan myös selaimeen, jotta liian suurta tiedostoa ei edes lähetetä palvelimelle.
        upload.setMaxFileSize((int) CourseAttachment.MAX_SIZE_BYTES);
        upload.addFileRejectedListener(e -> Notifications.error(e.getErrorMessage()));
        upload.setDropLabel(new Span("Pudota tiedosto tähän (max 10 Mt)"));
        return upload;
    }

    private void refreshAttachments() {
        attachmentList.removeAll();
        if (current == null || current.isNew()) {
            return;
        }
        List<CourseAttachment> attachments = fileStorageService.findByCourse(current.getId());
        if (attachments.isEmpty()) {
            attachmentList.add(new Span("Ei liitteitä."));
        }
        for (CourseAttachment a : attachments) {
            // AttachmentType.DOWNLOAD lisää Content-Disposition: attachment -otsakkeen: selain tallentaa
            // tiedoston eikä yritä näyttää sitä, jolloin esim. HTML:ksi naamioitu tiedosto ei suoritu.
            Anchor link = new Anchor(DownloadHandler.fromInputStream(event -> {
                try {
                    return new DownloadResponse(fileStorageService.open(a), a.getFileName(), a.getContentType(),
                            a.getSizeBytes());
                } catch (IOException ex) {
                    return DownloadResponse.error(404);
                }
            }), AttachmentType.DOWNLOAD, a.getFileName());
            Span size = new Span(" (" + humanSize(a.getSizeBytes()) + ")");
            Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> confirmAttachmentDelete(a));
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
            remove.setAriaLabel("Poista liite " + a.getFileName());
            HorizontalLayout row = new HorizontalLayout(VaadinIcon.PAPERCLIP.create(), link, size, remove);
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            attachmentList.add(row);
        }
    }

    private void confirmAttachmentDelete(CourseAttachment attachment) {
        ConfirmDialog dialog = new ConfirmDialog("Poistetaanko liite?",
                "Liite " + attachment.getFileName() + " poistetaan pysyvästi.",
                "Poista", e -> {
                    try {
                        fileStorageService.delete(attachment);
                        Notifications.success("Liite poistettu");
                    } catch (RuntimeException ex) {
                        // Esim. toinen käyttäjä ehti poistaa liitteen tai koko kurssin välissä.
                        Notifications.error("Liitteen poisto epäonnistui – se on ehkä jo poistettu.");
                    }
                    refreshAttachments();
                },
                "Peruuta", e -> { });
        dialog.setConfirmButtonTheme("error primary");
        dialog.open();
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " t";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f kt", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f Mt", bytes / (1024.0 * 1024));
    }

    // --- Server Push (vaatimus 22) ---------------------------------------------------------------

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        refreshGrid();
        UI ui = attachEvent.getUI();
        broadcasterRegistration = broadcaster.register(event -> {
            // Opettajan nimi näkyy gridissä, joten myös opettajamuutos päivittää listan.
            if (event.entityType() == EntityType.OFFICE) {
                return;
            }
            ui.access(() -> {
                refreshGrid();
                Notifications.info("Live: " + event.describe());
            });
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (broadcasterRegistration != null) {
            broadcasterRegistration.remove();
            broadcasterRegistration = null;
        }
    }
}
