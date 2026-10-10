package fi.lahtinen.kurssit.ui.views.admin;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.router.AccessDeniedException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AccessDeniedErrorRouter;
import com.vaadin.flow.theme.lumo.LumoUtility;
import fi.lahtinen.kurssit.service.HistoryService;
import fi.lahtinen.kurssit.service.HistoryService.EntityKind;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.HistoryTimeline;
import jakarta.annotation.security.RolesAllowed;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Muutoshistoria aikajanana (vaatimus 32), välilehti per entiteettityyppi.
 * Layout (välilehdet + pystysuuntainen aikajana) eroaa grid- ja korttinäkymistä (vaatimus 12).
 * Vain ADMIN-roolille, koska historia paljastaa kaikkien käyttäjien toimet.
 */
@Route(value = "admin/history", layout = MainLayout.class)
@PageTitle("Muutoshistoria | Kurssisovellus")
@RolesAllowed("ADMIN")
@AccessDeniedErrorRouter(rerouteToError = AccessDeniedException.class)
public class HistoryView extends VerticalLayout {

    private final HistoryService historyService;
    private final HistoryTimeline timeline = new HistoryTimeline();
    private final Map<Tab, EntityKind> tabKinds = new LinkedHashMap<>();

    public HistoryView(HistoryService historyService) {
        this.historyService = historyService;
        addClassName("page");

        Tabs tabs = new Tabs();
        for (EntityKind kind : EntityKind.values()) {
            Tab tab = new Tab(kind.label());
            tabKinds.put(tab, kind);
            tabs.add(tab);
        }
        tabs.addThemeVariants(TabsVariant.LUMO_EQUAL_WIDTH_TABS);
        tabs.setWidthFull();
        tabs.addSelectedChangeListener(e -> show(tabKinds.get(e.getSelectedTab())));

        Paragraph info = new Paragraph("Hibernate Envers tallentaa jokaisesta lisäyksestä, muokkauksesta ja "
                + "poistosta revision. Näytetään viimeisimmät 200 muutosta uusimmasta vanhimpaan.");
        info.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);

        timeline.setWidthFull();
        add(new H2("Muutoshistoria"), info, tabs, timeline);
        show(EntityKind.COURSE);
    }

    private void show(EntityKind kind) {
        timeline.setEntries(historyService.latest(kind));
    }
}
