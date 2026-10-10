package fi.lahtinen.kurssit.ui.components;

import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.EmailField;

import java.util.List;
import java.util.Locale;

/**
 * Yhtenäisesti konfiguroidut lomakekentät.
 * <p>
 * Syy erilliseen tehtaaseen: Vaadinin EmailField ja DatePicker validoivat syötteen myös itse
 * (sähköpostin muoto, päivämäärän jäsennys) ennen Binderin Bean Validation -sääntöjä. Jos niiden
 * omia virheviestejä ei aseteta, kenttä hylkää arvon <em>tyhjällä</em> viestillä eikä entiteetin
 * annotaatioon kirjoitettu viesti koskaan näy. Lisäksi DatePickerin kalenteri on oletuksena englanniksi.
 */
public final class FormFields {

    private static final String DEFAULT_MIN_MESSAGE = "Päivämäärä on liian aikainen";
    private static final String DEFAULT_MAX_MESSAGE = "Päivämäärä ei voi olla tulevaisuudessa";

    private FormFields() {
    }

    public static EmailField email(String label) {
        EmailField field = new EmailField(label);
        field.setI18n(new EmailField.EmailFieldI18n()
                .setPatternErrorMessage("Sähköpostiosoite ei ole kelvollinen"));
        return field;
    }

    /**
     * Suomenkielinen päivämäärävalitsin. Muoto d.M.yyyy ja viikon alku maanantaina vastaavat
     * suomalaista käytäntöä; Locale asetetaan, jotta myös käsin kirjoitettu päivämäärä jäsennetään oikein.
     * <p>
     * Min/max-viestit ovat pakollisia, jos kentälle asetetaan setMin/setMax: muuten DatePicker hylkää
     * rajan ylittävän arvon tyhjällä virheviestillä, eikä käyttäjä näe, mikä on vialla.
     */
    public static DatePicker date(String label) {
        return date(label, DEFAULT_MIN_MESSAGE, DEFAULT_MAX_MESSAGE);
    }

    public static DatePicker date(String label, String minErrorMessage, String maxErrorMessage) {
        DatePicker picker = new DatePicker(label);
        picker.setLocale(Locale.of("fi", "FI"));
        picker.setI18n(new DatePicker.DatePickerI18n()
                .setMonthNames(List.of("tammikuu", "helmikuu", "maaliskuu", "huhtikuu", "toukokuu", "kesäkuu",
                        "heinäkuu", "elokuu", "syyskuu", "lokakuu", "marraskuu", "joulukuu"))
                .setWeekdays(List.of("sunnuntai", "maanantai", "tiistai", "keskiviikko", "torstai", "perjantai",
                        "lauantai"))
                .setWeekdaysShort(List.of("su", "ma", "ti", "ke", "to", "pe", "la"))
                .setFirstDayOfWeek(1)
                .setToday("Tänään")
                .setCancel("Peruuta")
                .setDateFormat("d.M.yyyy")
                .setBadInputErrorMessage("Virheellinen päivämäärä, käytä muotoa p.k.vvvv")
                .setMinErrorMessage(minErrorMessage)
                .setMaxErrorMessage(maxErrorMessage));
        return picker;
    }
}
