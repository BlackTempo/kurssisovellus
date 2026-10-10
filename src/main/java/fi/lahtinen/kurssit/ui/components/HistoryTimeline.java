package fi.lahtinen.kurssit.ui.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import fi.lahtinen.kurssit.service.HistoryService.HistoryEntry;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Muutoshistorian aikajana (vaatimus 32). Uudelleenkäytettävä komponentti: sama aikajana näytetään
 * HistoryViewssä kaikille muutoksille ja kurssilomakkeen historiadialogissa yhdelle kurssille.
 */
public class HistoryTimeline extends Div {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public HistoryTimeline() {
        addClassName("timeline");
    }

    public void setEntries(List<HistoryEntry> entries) {
        removeAll();
        if (entries.isEmpty()) {
            add(new Span("Ei muutoksia."));
            return;
        }
        for (HistoryEntry e : entries) {
            Div dot = new Div();
            dot.addClassNames("timeline-dot", e.type().name().toLowerCase());

            Span badge = new Span(e.typeLabel());
            badge.getElement().getThemeList().add("badge small pill " + switch (e.type()) {
                case ADD -> "success";
                case MOD -> "";
                case DEL -> "error";
            });

            Div title = new Div(new Span(e.entityType() + ": " + e.label() + " "), badge);
            title.addClassName("timeline-title");

            Div meta = new Div(new Span(FORMAT.format(e.timestamp()) + " · " + e.username()
                    + " · revisio #" + e.revision()));
            meta.addClassName("timeline-meta");

            Div item = new Div(dot, title, meta);
            item.addClassName("timeline-item");
            if (!e.changedFields().isEmpty()) {
                Div fields = new Div(new Span("Muuttuneet kentät: " + String.join(", ", e.changedFields())));
                fields.addClassName("timeline-fields");
                item.add(fields);
            }
            add(item);
        }
    }
}
