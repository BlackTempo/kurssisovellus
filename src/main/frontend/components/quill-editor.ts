/**
 * <quill-editor> – Lit-kääre ulkoiselle Quill.js-tekstieditorille (vaatimus 33).
 *
 * Java-puolen QuillEditor-luokka synkronoi `value`-ominaisuuden: kun käyttäjä kirjoittaa, tämä
 * komponentti lähettää `value-changed`-tapahtuman, jonka Vaadin kuuntelee automaattisesti
 * (AbstractSinglePropertyField-konventio "<ominaisuus>-changed").
 */
import { html, LitElement, PropertyValues } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import Quill from 'quill';
// Quillin teema-CSS tuodaan globaalisti dokumenttiin; ks. createRenderRoot alla.
import 'quill/dist/quill.snow.css';

@customElement('quill-editor')
export class QuillEditorElement extends LitElement {
  @property({ type: String }) value = '';
  @property({ type: Boolean }) readonly = false;
  @property({ type: Boolean, reflect: true }) invalid = false;
  @property({ type: String }) errorMessage = '';
  @property({ type: String }) placeholder = '';

  private quill?: Quill;

  /** Estää palvelimelta tulleen arvon asettamista lähettämästä sitä takaisin palvelimelle. */
  private applyingServerValue = false;

  /**
   * Komponentti renderöidään light DOMiin eikä Shadow DOMiin, koska Quill 2 käyttää
   * document.getSelection()-rajapintaa, joka ei näe Shadow DOMin sisälle: valinnat ja muotoilunapit
   * lakkaisivat toimimasta. Samasta syystä Quillin CSS on globaali.
   */
  protected createRenderRoot() {
    return this;
  }

  render() {
    // Quill lisää työkalupalkin editorielementin edelle, joten se kääritään omaan diviinsä, ettei
    // Quillin lisäämä DOM sekoitu Litin hallinnoimiin solmuihin.
    return html`
      <div class="quill-wrapper"><div class="quill-host"></div></div>
      <div class="quill-error" ?hidden=${!this.invalid || !this.errorMessage}>${this.errorMessage}</div>
    `;
  }

  protected firstUpdated() {
    const host = this.querySelector('.quill-host') as HTMLElement;
    this.quill = new Quill(host, {
      theme: 'snow',
      readOnly: this.readonly,
      placeholder: this.placeholder,
      modules: {
        toolbar: [
          [{ header: [2, 3, false] }],
          ['bold', 'italic', 'underline', 'strike'],
          [{ list: 'ordered' }, { list: 'bullet' }],
          ['blockquote', 'link'],
          ['clean'],
        ],
      },
    });
    this.setEditorContent(this.value);

    this.quill.on('text-change', () => {
      if (this.applyingServerValue) {
        return;
      }
      // Tyhjä editori sisältää aina "<p><br></p>"; se normalisoidaan tyhjäksi merkkijonoksi, jotta
      // palvelimen validointi (esim. pakollisuus) toimii ja tietokantaan ei tallennu turhaa HTML:ää.
      const isEmpty = this.quill!.getText().trim().length === 0;
      const newValue = isEmpty ? '' : this.quill!.root.innerHTML;
      if (newValue === this.value) {
        return;
      }
      this.value = newValue;
      this.dispatchEvent(new CustomEvent('value-changed', { detail: { value: newValue } }));
    });
  }

  protected updated(changed: PropertyValues<this>) {
    if (!this.quill) {
      return;
    }
    // Päivitetään editorin sisältö vain, jos arvo todella tuli ulkoa (palvelimelta). Jos käyttäjän oma
    // kirjoitus asetettaisiin takaisin editoriin, kursori hyppäisi tekstin alkuun jokaisella näppäilyllä.
    if (changed.has('value') && this.value !== this.currentHtml()) {
      this.setEditorContent(this.value);
    }
    if (changed.has('readonly')) {
      this.quill.enable(!this.readonly);
    }
  }

  private currentHtml(): string {
    return this.quill!.getText().trim().length === 0 ? '' : this.quill!.root.innerHTML;
  }

  private setEditorContent(value: string) {
    this.applyingServerValue = true;
    try {
      // dangerouslyPasteHTML on turvallinen tässä, koska palvelin sanitoi HTML:n ennen tallennusta
      // (HtmlSanitizer) ja Quill itse hylkää tuntemattomat tagit ja attribuutit.
      this.quill!.setContents([], 'silent');
      this.quill!.clipboard.dangerouslyPasteHTML(value || '', 'silent');
    } finally {
      this.applyingServerValue = false;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'quill-editor': QuillEditorElement;
  }
}
