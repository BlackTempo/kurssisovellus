package fi.lahtinen.kurssit.ui.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

/** Väliaikainen etusivu projektipohjan testaamiseen; korvataan dashboardilla vaiheessa 7. */
@Route("")
public class DashboardView extends VerticalLayout {
    public DashboardView() {
        add(new H1("Kurssisovellus"));
    }
}
