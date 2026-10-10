package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.Course;
import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.domain.Teacher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kurssien dynaaminen haku Criteria API:lla (Suodattaminen, kohdat 1–5).
 * <p>
 * Miksi Criteria API eikä JPQL-merkkijono: ehtojen määrä vaihtelee käyttäjän syötteen mukaan, ja
 * merkkijonojen liimaaminen olisi sekä virhealtista että SQL-injektiolle altis. Criteria API
 * rakentaa kyselyn tyyppiturvallisesti, ja kaikki arvot sidotaan parametreina.
 * <p>
 * Sama {@link #buildPredicates}-metodi palvelee sekä hakua että count-kyselyä, jotta gridin
 * sivutuksen kokonaismäärä vastaa aina täsmälleen näytettyjä rivejä.
 */
public class CourseRepositoryImpl implements CourseRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Course> search(CourseFilter filter, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Kaksivaiheinen haku: ensin sivun id:t ehtojen mukaan, sitten kurssit opettajineen.
        // Fetch-joinia ja sivutusta ei voi yhdistää samaan kyselyyn turvallisesti, kun mukana voi olla
        // M:N-join (opiskelijat), joka monistaa rivejä – LIMIT kohdistuisi monistettuihin riveihin.
        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<Course> course = idQuery.from(Course.class);
        Join<Course, Teacher> teacher = course.join("teacher", JoinType.INNER);
        idQuery.select(course.get("id"))
                .where(buildPredicates(cb, idQuery, course, teacher, filter))
                .orderBy(cb.asc(course.get("startDate")), cb.asc(course.get("id")));
        List<Long> ids = em.createQuery(idQuery)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
        if (ids.isEmpty()) {
            return List.of();
        }

        CriteriaQuery<Course> dataQuery = cb.createQuery(Course.class);
        Root<Course> c = dataQuery.from(Course.class);
        c.fetch("teacher", JoinType.INNER);
        dataQuery.select(c)
                .where(c.get("id").in(ids))
                .orderBy(cb.asc(c.get("startDate")), cb.asc(c.get("id")));
        return em.createQuery(dataQuery).getResultList();
    }

    @Override
    public long count(CourseFilter filter) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Course> course = query.from(Course.class);
        Join<Course, Teacher> teacher = course.join("teacher", JoinType.INNER);

        // countDistinct, koska M:N-join opiskelijoihin voi monistaa saman kurssin useaksi riviksi.
        query.select(cb.countDistinct(course))
                .where(buildPredicates(cb, query, course, teacher, filter));
        return em.createQuery(query).getSingleResult();
    }

    /**
     * Kokoaa WHERE-ehdot. Jokainen predikaatti lisätään vain, jos käyttäjä on antanut vastaavan
     * ehdon – tyhjällä suodattimella kysely palauttaa kaikki kurssit. Lopuksi listan ehdot
     * yhdistetään AND:llä ({@code where(Predicate...)}).
     */
    private Predicate[] buildPredicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Course> course,
                                        Join<Course, Teacher> teacher, CourseFilter f) {
        List<Predicate> predicates = new ArrayList<>();

        // Kohta 1: suodatus useilla syötekentillä (osittainen tekstihaku LIKE + numeerinen raja).
        if (hasText(f.code())) {
            predicates.add(likeIgnoreCase(cb, course.get("code"), f.code()));
        }
        if (hasText(f.name())) {
            predicates.add(likeIgnoreCase(cb, course.get("name"), f.name()));
        }
        if (f.minCredits() != null) {
            predicates.add(cb.greaterThanOrEqualTo(course.get("credits"), f.minCredits()));
        }

        // Kohta 2: päivämääräväli. Kumpikin raja on valinnainen, joten väli voi olla myös avoin
        // toisesta päästä ("alkaa aikaisintaan" / "alkaa viimeistään").
        if (f.startFrom() != null && f.startTo() != null) {
            predicates.add(cb.between(course.get("startDate"), f.startFrom(), f.startTo()));
        } else if (f.startFrom() != null) {
            predicates.add(cb.greaterThanOrEqualTo(course.get("startDate"), f.startFrom()));
        } else if (f.startTo() != null) {
            predicates.add(cb.lessThanOrEqualTo(course.get("startDate"), f.startTo()));
        }

        // Kohta 3: suodatus relaatioentiteetin perusteella JOINilla (M:N kurssi–opiskelija).
        // Join luodaan vain tarvittaessa: turha join hidastaisi kyselyä ja monistaisi rivejä.
        // Yksi opiskelija voi esiintyä kurssilla vain kerran, joten tämä join ei monista tuloksia.
        if (f.student() != null) {
            Join<Course, Student> students = course.join("students", JoinType.INNER);
            predicates.add(cb.equal(students.get("id"), f.student().getId()));
        }

        // Kohta 4: suodatus relaatioentiteetin ominaisuudella – opettajan sukunimi (JOIN Teacher).
        if (hasText(f.teacherLastName())) {
            predicates.add(likeIgnoreCase(cb, teacher.get("lastName"), f.teacherLastName()));
        }

        // Kohta 5: (X OR Y) AND Z – nimi TAI kuvaus sisältää hakusanan, JA kurssi kuuluu valitulle
        // opettajalle. OR kootaan omaksi predikaatikseen, jotta se sulkeistuu oikein: AND sitoo
        // tiukemmin kuin OR, ja ilman sulkuja ehto tarkoittaisi X OR (Y AND Z).
        // Kumpaakin osaa voi käyttää myös yksinään.
        if (hasText(f.keyword())) {
            Predicate nameMatches = likeIgnoreCase(cb, course.get("name"), f.keyword());
            Predicate descriptionMatches = likeIgnoreCase(cb, course.get("description"), f.keyword());
            predicates.add(cb.or(nameMatches, descriptionMatches));
        }
        if (f.teacher() != null) {
            predicates.add(cb.equal(teacher.get("id"), f.teacher().getId()));
        }

        return predicates.toArray(Predicate[]::new);
    }

    /*
     * Haku on kirjainkoosta riippumaton (lower molemmin puolin). Käyttäjän syötteen %- ja _-merkit
     * escapetaan, jotta ne tulkitaan kirjaimellisesti eivätkä jokerimerkkeinä.
     */
    private static Predicate likeIgnoreCase(CriteriaBuilder cb, Expression<String> field, String value) {
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return cb.like(cb.lower(field), "%" + escaped + "%", '\\');
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
