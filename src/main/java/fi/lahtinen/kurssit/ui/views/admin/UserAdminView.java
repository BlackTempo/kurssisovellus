package fi.lahtinen.kurssit.ui.views.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AccessDeniedException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AccessDeniedErrorRouter;
import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.domain.Role;
import fi.lahtinen.kurssit.security.AuthenticatedUser;
import fi.lahtinen.kurssit.service.BusinessException;
import fi.lahtinen.kurssit.service.UserService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.Notifications;
import jakarta.annotation.security.RolesAllowed;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Käyttäjähallinta – sivu vain ADMIN-roolille (vaatimus 16).
 * User- tai super-käyttäjä saa tästä oman pääsy evätty -sivun (vaatimus 18).
 */
@Route(value = "admin/users", layout = MainLayout.class)
@PageTitle("Käyttäjähallinta | Kurssisovellus")
@RolesAllowed("ADMIN")
@AccessDeniedErrorRouter(rerouteToError = AccessDeniedException.class)
public class UserAdminView extends VerticalLayout {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final UserService userService;
    private final String currentUsername;
    private final Grid<AppUser> grid = new Grid<>(AppUser.class, false);

    public UserAdminView(UserService userService, AuthenticatedUser authenticatedUser) {
        this.userService = userService;
        this.currentUsername = authenticatedUser.get().map(AppUser::getUsername).orElse("");
        addClassName("page");
        setSizeFull();

        grid.addColumn(AppUser::getUsername).setHeader("Tunnus").setAutoWidth(true).setSortable(true);
        grid.addColumn(AppUser::getFullName).setHeader("Nimi").setAutoWidth(true).setSortable(true);
        grid.addColumn(AppUser::getEmail).setHeader("Sähköposti").setAutoWidth(true);
        grid.addComponentColumn(this::roleBadges).setHeader("Roolit").setAutoWidth(true);
        grid.addComponentColumn(u -> {
            Icon icon = u.isEnabled() ? VaadinIcon.CHECK_CIRCLE.create() : VaadinIcon.BAN.create();
            icon.setColor(u.isEnabled() ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
            icon.setTooltipText(u.isEnabled() ? "Aktiivinen" : "Lukittu");
            return icon;
        }).setHeader("Tila").setAutoWidth(true);
        // Auditointikentät (vaatimus 30): kuka loi tunnuksen ja milloin
        grid.addColumn(u -> u.getCreatedDate() != null ? DATE.format(u.getCreatedDate()) : "").setHeader("Luotu")
                .setAutoWidth(true);
        grid.addColumn(AppUser::getCreatedBy).setHeader("Luonut").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.addItemClickListener(e -> openEditor(e.getItem()));

        add(new H2("Käyttäjähallinta"), new Span("Klikkaa käyttäjää muokataksesi rooleja."), grid);
        refresh();
    }

    private Component roleBadges(AppUser user) {
        Div list = new Div();
        list.addClassName("badge-list");
        user.getRoles().stream().sorted().forEach(r -> {
            Span badge = new Span(r.name());
            badge.getElement().getThemeList().add(r == Role.ADMIN ? "badge error" : r == Role.SUPER ? "badge" : "badge contrast");
            list.add(badge);
        });
        return list;
    }

    private void refresh() {
        grid.setItems(userService.findAll());
    }

    private void openEditor(AppUser user) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Käyttäjä: " + user.getUsername());

        CheckboxGroup<Role> roles = new CheckboxGroup<>("Roolit");
        roles.setItems(Role.values());
        roles.setValue(user.getRoles());
        Checkbox enabled = new Checkbox("Tunnus aktiivinen", user.isEnabled());
        dialog.add(new VerticalLayout(roles, enabled));

        Button save = new Button("Tallenna", e -> {
            try {
                userService.updateRolesAndStatus(user.getId(), roles.getValue(), enabled.getValue(), currentUsername);
                // Muuttunut käyttäjä kirjataan ulos (UserService), jotta uudet oikeudet tulevat heti voimaan.
                Notifications.success(user.getUsername().equals(currentUsername)
                        ? "Omat roolisi muuttuivat. Sinut kirjataan ulos – kirjaudu uudelleen."
                        : "Käyttäjä päivitetty. Jos hän on kirjautuneena, hänet kirjataan ulos.");
                dialog.close();
                refresh();
            } catch (BusinessException ex) {
                Notifications.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button delete = new Button("Poista", VaadinIcon.TRASH.create(), e -> {
            ConfirmDialog confirm = new ConfirmDialog("Poistetaanko käyttäjä?",
                    "Tunnus " + user.getUsername() + " poistetaan pysyvästi.", "Poista", ev -> {
                try {
                    userService.delete(user.getId(), currentUsername);
                    Notifications.success("Käyttäjä poistettu");
                    dialog.close();
                    refresh();
                } catch (BusinessException ex) {
                    Notifications.error(ex.getMessage());
                }
            }, "Peruuta", ev -> { });
            confirm.setConfirmButtonTheme("error primary");
            confirm.open();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        delete.getStyle().set("margin-inline-end", "auto");
        dialog.getFooter().add(delete, new Button("Peruuta", e -> dialog.close()), save);
        dialog.open();
    }
}
