package fi.lahtinen.kurssit.repository;

import fi.lahtinen.kurssit.domain.Student;
import fi.lahtinen.kurssit.domain.Teacher;

import java.time.LocalDate;

/**
 * Hakunäkymän ehdot. Jokainen kenttä on valinnainen: {@code null} tai tyhjä merkkijono tarkoittaa
 * "ei rajausta", jolloin vastaavaa predikaattia ei lisätä kyselyyn lainkaan.
 *
 * @param code            kurssikoodin osa (LIKE)
 * @param name            nimen osa (LIKE)
 * @param minCredits      opintopisteiden alaraja
 * @param startFrom       alkupäivän alaraja (päivämäärähaku)
 * @param startTo         alkupäivän yläraja
 * @param student         kurssille ilmoittautunut opiskelija (JOIN M:N-relaatioon)
 * @param teacherLastName opettajan sukunimen osa (JOIN + relaatioentiteetin ominaisuus)
 * @param keyword         hakusana nimestä TAI kuvauksesta – (X OR Y) AND Z -rakenteen X ja Y
 * @param teacher         valittu opettaja – (X OR Y) AND Z -rakenteen Z
 */
public record CourseFilter(
        String code,
        String name,
        Integer minCredits,
        LocalDate startFrom,
        LocalDate startTo,
        Student student,
        String teacherLastName,
        String keyword,
        Teacher teacher) {

    public static CourseFilter empty() {
        return new CourseFilter(null, null, null, null, null, null, null, null, null);
    }
}
