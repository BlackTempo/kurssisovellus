package fi.lahtinen.kurssit.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import fi.lahtinen.kurssit.domain.Office;
import fi.lahtinen.kurssit.domain.Teacher;
import fi.lahtinen.kurssit.service.BusinessException;
import fi.lahtinen.kurssit.service.OfficeService;
import fi.lahtinen.kurssit.service.TeacherService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.Notifications;
import jakarta.annotation.security.PermitAll;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Objects;

/**
 * Työhuoneiden hallinta (vaatimus 2: 1:1-relaatio opettajaan). Grid näyttää huoneen opettajan.
 * Pääsy: kaikki kirjautuneet.
 */
@Route(value = "offices", layout = MainLayout.class)
@PageTitle("Työhuoneet | Kurssisovellus")
@PermitAll
public class OfficeView extends VerticalLayout {

    private final OfficeService officeService;
    private final TeacherService teacherService;
    private final Grid<Office> grid = new Grid<>(Office.class, false);

    public OfficeView(OfficeService officeService, TeacherService teacherService) {
        this.officeService = officeService;
        this.teacherService = teacherService;
        addClassName("page");
        setSizeFull();

        grid.addColumn(Office::getRoomNumber).setHeader("Huone").setSortable(true).setAutoWidth(true);
        grid.addColumn(Office::getBuilding).setHeader("Rakennus").setSortable(true);
        grid.addColumn(Office::getFloor).setHeader("Kerros").setAutoWidth(true);
        grid.addColumn(Office::getCapacity).setHeader("Paikkoja").setAutoWidth(true);
        grid.addColumn(Office::getPhoneExtension).setHeader("Alanumero").setAutoWidth(true);
        // 1:1-relaatio näkyviin myös tältä puolelta
        grid.addColumn(o -> o.getTeacher().getFullName()).setHeader("Opettaja").setSortable(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.addItemClickListener(e -> openForm(e.getItem()));

        Button add = new Button("Uusi työhuone", VaadinIcon.PLUS.create(), e -> openForm(new Office()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(new H2("Työhuoneet"), new HorizontalLayout(add), grid);
        refresh();
    }

    private void refresh() {
        grid.setItems(officeService.findAll());
    }

    private void openForm(Office office) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(office.isNew() ? "Uusi työhuone" : "Muokkaa: " + office.getRoomNumber());

        TextField roomNumber = new TextField("Huonenumero");
        roomNumber.setPlaceholder("A123");
        TextField building = new TextField("Rakennus");
        IntegerField floor = new IntegerField("Kerros");
        floor.setStepButtonsVisible(true);
        IntegerField capacity = new IntegerField("Paikkoja");
        capacity.setStepButtonsVisible(true);
        TextField phoneExtension = new TextField("Alanumero");
        ComboBox<Teacher> teacher = new ComboBox<>("Opettaja");
        teacher.setItemLabelGenerator(Teacher::getFullName);
        // 1:1: tarjotaan vain opettajia, joilla ei vielä ole huonetta (sekä tämän huoneen nykyinen opettaja).
        // Palvelukerros tarkistaa saman vielä erikseen, koska lista voi vanhentua dialogin ollessa auki.
        Long currentTeacherId = office.getTeacher() != null ? office.getTeacher().getId() : null;
        teacher.setItems(teacherService.findAll().stream()
                .filter(t -> t.getOffice() == null || Objects.equals(t.getId(), currentTeacherId))
                .toList());

        BeanValidationBinder<Office> binder = new BeanValidationBinder<>(Office.class);
        binder.forField(roomNumber).bind("roomNumber");
        binder.forField(building).bind("building");
        binder.forField(floor).bind("floor");
        binder.forField(capacity).bind("capacity");
        binder.forField(phoneExtension).withNullRepresentation("").bind("phoneExtension");
        binder.forField(teacher).bind("teacher");
        binder.readBean(office);

        dialog.add(new FormLayout(roomNumber, building, floor, capacity, phoneExtension, teacher));

        Button save = new Button("Tallenna", e -> {
            if (binder.writeBeanIfValid(office)) {
                try {
                    officeService.save(office);
                    Notifications.success("Työhuone tallennettu");
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

        if (!office.isNew()) {
            Button delete = new Button("Poista", VaadinIcon.TRASH.create(), e -> {
                ConfirmDialog confirm = new ConfirmDialog("Poistetaanko työhuone?",
                        "Huone " + office.getRoomNumber() + " poistetaan.", "Poista", ev -> {
                    officeService.delete(office);
                    Notifications.success("Työhuone poistettu");
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
        dialog.setWidth("min(600px, 95vw)");
        dialog.open();
    }
}
