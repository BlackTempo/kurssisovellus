package fi.lahtinen.kurssit.ui.components;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Yhtenäiset ilmoitukset koko sovellukseen. Keskitetty, jotta kesto, sijainti ja värit ovat samat
 * kaikissa näkymissä (addThemeVariants, vaatimus 7c).
 */
public final class Notifications {

    private Notifications() {
    }

    public static void success(String message) {
        show(message, NotificationVariant.LUMO_SUCCESS, 3000);
    }

    /** Virheet näkyvät pidempään, koska käyttäjän on ehdittävä lukea, mitä pitää korjata. */
    public static void error(String message) {
        show(message, NotificationVariant.LUMO_ERROR, 6000);
    }

    public static void info(String message) {
        show(message, NotificationVariant.LUMO_CONTRAST, 4000);
    }

    private static void show(String message, NotificationVariant variant, int durationMs) {
        Notification n = Notification.show(message, durationMs, Notification.Position.BOTTOM_END);
        n.addThemeVariants(variant);
    }
}
