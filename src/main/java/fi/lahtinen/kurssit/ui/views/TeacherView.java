package fi.lahtinen.kurssit.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import fi.lahtinen.kurssit.domain.Teacher;
import fi.lahtinen.kurssit.service.BusinessException;
import fi.lahtinen.kurssit.service.TeacherService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.Notifications;
import jakarta.annotation.security.PermitAll;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Opettajien hallinta: grid + dialogissa avautuva lomake (vaatimus 1).
 * Grid näyttää 1:1-relaation työhuoneeseen (vaatimus 2). Pääsy: kaikki kirjautuneet.
 */
@Route(value = "teachers", layout = MainLayout.class)
@PageTitle("Opettajat | Kurssisovellus")
@PermitAll
public class TeacherView extends VerticalLayout {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy");

    private final TeacherService teacherService;
    private final Grid<Teacher> grid = new Grid<>(Teacher.class, false);
    private final TextField filter = new TextField();
    private List<Teacher> teachers = List.of();

    public TeacherView(TeacherService teacherService) {
        this.teacherService = teacherService;
        addClassName("page");
        setSizeFull();

        grid.addColumn(Teacher::getLastName).setHeader("Sukunimi").setSortable(true).setAutoWidth(true);
        grid.addColumn(Teacher::getFirstName).setHeader("Etunimi").setSortable(true).setAutoWidth(true);
        grid.addColumn(Teacher::getTitle).setHeader("Titteli").setAutoWidth(true);
        grid.addColumn(Teacher::getEmail).setHeader("Sähköposti").setAutoWidth(true);
        grid.addColumn(Teacher::getPhone).setHeader("Puhelin").setAutoWidth(true);
        grid.addColumn(t -> DATE.format(t.getHireDate())).setHeader("Aloittanut").setAutoWidth(true)
                .setComparator(Teacher::getHireDate);
        // 1:1-relaatio näkyviin (vaatimus 2)
        grid.addComponentColumn(t -> {
            Span s = new Span(t.getOffice() != null ? t.getOffice().toString() : "–");
            if (t.getOffice() != null) {
                s.getElement().getThemeList().add("badge");
            }
            return s;
        }).setHeader("Työhuone").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();
        grid.addItemClickListener(e -> openForm(e.getItem()));

        filter.setPlaceholder("Suodata nimellä…");
        filter.setPrefixComponent(VaadinIcon.FILTER.create());
        filter.setClearButtonVisible(true);
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.addValueChangeListener(e -> applyFilter());

        Button add = new Button("Uusi opettaja", VaadinIcon.PLUS.create(), e -> openForm(new Teacher()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(filter, add);
        toolbar.addClassName("toolbar");
        add(new H2("Opettajat"), toolbar, grid);
        refresh();
    }

    private void refresh() {
        teachers = teacherService.findAll();
        applyFilter();
    }

    private void applyFilter() {
        String term = filter.getValue().trim().toLowerCase(Locale.ROOT);
        grid.setItems(teachers.stream()
                .filter(t -> term.isEmpty() || t.getFullName().toLowerCase(Locale.ROOT).contains(term))
                .toList());
    }

    private void openForm(Teacher teacher) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(teacher.isNew() ? "Uusi opettaja" : "Muokkaa: " + teacher.getFullName());

        TextField firstName = new TextField("Etunimi");
        TextField lastName = new TextField("Sukunimi");
        EmailField email = new EmailField("Sähköposti");
        TextField phone = new TextField("Puhelin");
        TextField title = new TextField("Titteli");
        DatePicker hireDate = new DatePicker("Työsuhde alkanut");
        hireDate.setMax(LocalDate.now());

        // BeanValidationBinder lukee validointisäännöt entiteetin annotaatioista: säännöt on määritelty
        // vain yhdessä paikassa, ja samat säännöt pätevät myös palvelu- ja tietokantatasolla.
        BeanValidationBinder<Teacher> binder = new BeanValidationBinder<>(Teacher.class);
        binder.forField(firstName).bind("firstName");
        binder.forField(lastName).bind("lastName");
        binder.forField(email).bind("email");
        binder.forField(phone).withNullRepresentation("").bind("phone");
        binder.forField(title).withNullRepresentation("").bind("title");
        binder.forField(hireDate).bind("hireDate");
        // readBean + writeBeanIfValid: muutokset kopioidaan entiteettiin vasta tallennettaessa, joten
        // peruutettu dialogi ei jätä gridin riviin keskeneräisiä muutoksia.
        binder.readBean(teacher);

        FormLayout form = new FormLayout(firstName, lastName, email, phone, title, hireDate);
        dialog.add(form);

        Button save = new Button("Tallenna", e -> {
            if (binder.writeBeanIfValid(teacher)) {
                try {
                    teacherService.save(teacher);
                    Notifications.success("Opettaja tallennettu");
                    dialog.close();
                    refresh();
                } catch (BusinessException ex) {
                    Notifications.error(ex.getMessage());
                } catch (ObjectOptimisticLockingFailureException ex) {
                    Notifications.error("Toinen käyttäjä muokkasi tietoja samaan aikaan. Avaa lomake uudelleen.");
                    dialog.close();
                    refresh();
                }
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Peruuta", e -> dialog.close());
        dialog.getFooter().add(cancel, save);

        if (!teacher.isNew()) {
            Button delete = new Button("Poista", VaadinIcon.TRASH.create(), e -> confirmDelete(teacher, dialog));
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.getStyle().set("margin-inline-end", "auto");
            dialog.getFooter().addComponentAsFirst(delete);
        }
        dialog.setWidth("min(640px, 95vw)");
        dialog.open();
        firstName.focus();
    }

    private void confirmDelete(Teacher teacher, Dialog formDialog) {
        ConfirmDialog confirm = new ConfirmDialog("Poistetaanko opettaja?",
                teacher.getFullName() + " ja hänen työhuonevarauksensa poistetaan.",
                "Poista", e -> {
                    try {
                        teacherService.delete(teacher);
                        Notifications.success("Opettaja poistettu");
                        formDialog.close();
                        refresh();
                    } catch (BusinessException ex) {
                        Notifications.error(ex.getMessage());
                    }
                }, "Peruuta", e -> { });
        confirm.setConfirmButtonTheme("error primary");
        confirm.open();
    }
}
