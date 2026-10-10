package fi.lahtinen.kurssit.service;

import fi.lahtinen.kurssit.domain.AbstractAuditableEntity;
import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.CustomRevisionEntity;
import fi.lahtinen.kurssit.domain.Office;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.domain.Teacher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Lukee Enversin tallentaman muutoshistorian (vaatimukset 31–32).
 * <p>
 * Envers kirjoittaa jokaisesta lisäyksestä, muokkauksesta ja poistosta rivin entiteetin
 * {@code _aud}-tauluun ja liittää sen revisioon (aika + tekijä). Tämä palvelu muuntaa revisiot
 * {@link HistoryEntry}-olioiksi transaktion sisällä: historian entiteetit ovat lazy-proxyjä, joita
 * ei voi käyttää näkymässä transaktion päätyttyä.
 */
@Service
public class HistoryService {

    /** Historia, jota näytetään kerralla. Aikajana on tarkoitettu viimeaikaisten muutosten seurantaan. */
    private static final int MAX_ENTRIES = 200;

    /** Kentät, joiden muutos ei ole käyttäjälle kiinnostava (ne muuttuvat jokaisessa tallennuksessa). */
    private static final Set<String> TECHNICAL_FIELDS = Set.of("version", "lastModifiedBy", "lastModifiedDate",
            "createdBy", "createdDate");

    @PersistenceContext
    private EntityManager em;

    public enum EntityKind {
        COURSE("Kurssi", Course.class, e -> ((Course) e).getCode() + " " + ((Course) e).getName()),
        TEACHER("Opettaja", Teacher.class, e -> ((Teacher) e).getFullName()),
        OFFICE("Työhuone", Office.class, e -> ((Office) e).getRoomNumber() + " " + ((Office) e).getBuilding()),
        STUDENT("Opiskelija", Student.class, e -> ((Student) e).getFullName());

        private final String label;
        private final Class<? extends AbstractAuditableEntity> type;
        private final Function<Object, String> describer;

        EntityKind(String label, Class<? extends AbstractAuditableEntity> type, Function<Object, String> describer) {
            this.label = label;
            this.type = type;
            this.describer = describer;
        }

        public String label() {
            return label;
        }
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> latest(EntityKind kind) {
        return query(kind, null);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> forEntity(EntityKind kind, Long id) {
        return query(kind, id);
    }

    @SuppressWarnings("unchecked")
    private List<HistoryEntry> query(EntityKind kind, Long id) {
        AuditReader reader = AuditReaderFactory.get(em);
        // forRevisionsOfEntityWithChanges palauttaa riveittäin [entiteetti, revisio, tyyppi, muuttuneet
        // kentät]. Muuttuneet kentät ovat saatavilla, koska global_with_modified_flag on päällä
        // (application.properties): Envers tallentaa jokaiselle kentälle _mod-lipun.
        AuditQuery query = reader.createQuery()
                .forRevisionsOfEntityWithChanges(kind.type, true)
                .addOrder(AuditEntity.revisionNumber().desc())
                .setMaxResults(MAX_ENTRIES);
        if (id != null) {
            query.add(AuditEntity.id().eq(id));
        }
        List<Object[]> rows = query.getResultList();

        return rows.stream().map(row -> {
            AbstractAuditableEntity entity = (AbstractAuditableEntity) row[0];
            CustomRevisionEntity revision = (CustomRevisionEntity) row[1];
            RevisionType type = (RevisionType) row[2];
            Set<String> changed = new TreeSet<>((Set<String>) row[3]);
            changed.removeAll(TECHNICAL_FIELDS);
            return new HistoryEntry(
                    kind.label(),
                    entity.getId(),
                    describeSafely(kind, entity),
                    revision.getId(),
                    Instant.ofEpochMilli(revision.getTimestamp()),
                    revision.getUsername(),
                    type,
                    // Lisäyksessä kaikki kentät ovat "muuttuneita", joten listaa ei näytetä.
                    type == RevisionType.MOD ? changed : Set.of());
        }).toList();
    }

    /** Poistorevisiossa kentät voivat olla tyhjiä, jos store_data_at_delete ei olisi päällä. */
    private static String describeSafely(EntityKind kind, Object entity) {
        try {
            return kind.describer.apply(entity);
        } catch (RuntimeException e) {
            return "#" + ((AbstractAuditableEntity) entity).getId();
        }
    }

    public record HistoryEntry(String entityType, Long entityId, String label, int revision, Instant timestamp,
                               String username, RevisionType type, Set<String> changedFields) {

        public String typeLabel() {
            return switch (type) {
                case ADD -> "Lisätty";
                case MOD -> "Muokattu";
                case DEL -> "Poistettu";
            };
        }
    }
}
