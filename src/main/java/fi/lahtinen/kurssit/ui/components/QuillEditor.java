package fi.lahtinen.kurssit.ui.components;

import com.vaadin.flow.component.AbstractSinglePropertyField;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;

/**
 * Java-rajapinta ulkoiseen JavaScript-komponenttiin Quill.js (vaatimus 33).
 * <p>
 * {@link NpmPackage} lisää quill-paketin package.jsoniin, jolloin Vaadinin build asentaa sen npm:llä
 * ja Vite niputtaa sen mukaan – erillistä CDN-latausta ei tarvita, ja versio on lukittu.
 * {@link JsModule} lataa oman Lit-kääreemme ({@code frontend/components/quill-editor.ts}).
 * <p>
 * {@link AbstractSinglePropertyField} hoitaa arvon kaksisuuntaisen synkronoinnin: palvelimen
 * setValue asettaa elementin {@code value}-ominaisuuden, ja selaimen {@code value-changed}-tapahtuma
 * päivittää palvelimen arvon. Tämän ansiosta komponentti toimii Binderin kanssa kuten mikä tahansa
 * Vaadin-kenttä, validointeineen.
 */
@Tag("quill-editor")
@NpmPackage(value = "quill", version = "2.0.3")
@JsModule("./components/quill-editor.ts")
public class QuillEditor extends AbstractSinglePropertyField<QuillEditor, String>
        implements HasSize, HasValidation {

    public QuillEditor() {
        // Tyhjä merkkijono oletusarvona null-arvon sijaan: selainpuoli käsittelee tyhjää tekstiä
        // luontevammin, ja Binder muuntaa sen tarvittaessa null-arvoksi withNullRepresentation-kutsulla.
        super("value", "", false);
        setWidthFull();
    }

    public void setPlaceholder(String placeholder) {
        getElement().setProperty("placeholder", placeholder);
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        super.setReadOnly(readOnly);
        getElement().setProperty("readonly", readOnly);
    }

    @Override
    public void setErrorMessage(String errorMessage) {
        getElement().setProperty("errorMessage", errorMessage == null ? "" : errorMessage);
    }

    @Override
    public String getErrorMessage() {
        return getElement().getProperty("errorMessage", "");
    }

    @Override
    public void setInvalid(boolean invalid) {
        getElement().setProperty("invalid", invalid);
    }

    @Override
    public boolean isInvalid() {
        return getElement().getProperty("invalid", false);
    }
}
