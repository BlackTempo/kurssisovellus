package fi.lahtinen.kurssit.ui.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.theme.lumo.LumoUtility;
import fi.lahtinen.kurssit.domain.AppUser;
import fi.lahtinen.kurssit.security.AuthenticatedUser;
import fi.lahtinen.kurssit.service.BusinessException;
import fi.lahtinen.kurssit.service.PasswordResetService;
import fi.lahtinen.kurssit.service.UserService;
import fi.lahtinen.kurssit.ui.MainLayout;
import fi.lahtinen.kurssit.ui.components.Notifications;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;

/**
 * Oma profiili: profiilikuvan lisäys (vaatimus 19) ja salasanan vaihto sähköpostilinkillä (vaatimus 27).
 * Layout on keskitetty kortti – visuaalisesti erilainen kuin grid-näkymät.
 */
@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Oma profiili | Kurssisovellus")
@PermitAll
public class ProfileView extends VerticalLayout {

    private final AuthenticatedUser authenticatedUser;
    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public ProfileView(AuthenticatedUser authenticatedUser, UserService userService,
                       PasswordResetService passwordResetService) {
        this.authenticatedUser = authenticatedUser;
        this.userService = userService;
        this.passwordResetService = passwordResetService;

        addClassName("page");
        setAlignItems(FlexComponent.Alignment.CENTER);
        authenticatedUser.get().ifPresent(user -> add(createCard(user)));
    }

    private Div createCard(AppUser user) {
        Avatar avatar = new Avatar(user.getFullName());
        avatar.addThemeVariants(AvatarVariant.LUMO_XLARGE);
        avatar.setWidth("120px");
        avatar.setHeight("120px");
        if (user.getProfileImage() != null) {
            byte[] image = user.getProfileImage();
            String type = user.getProfileImageType();
            avatar.setImageHandler(DownloadHandler.fromInputStream(e ->
                    new DownloadResponse(new ByteArrayInputStream(image), "avatar", type, image.length)));
        }

        H2 name = new H2(user.getFullName());
        name.addClassNames(LumoUtility.Margin.Bottom.NONE);
        Span username = new Span("@" + user.getUsername() + " · " + user.getEmail());
        username.addClassNames(LumoUtility.TextColor.SECONDARY);

        HorizontalLayout roles = new HorizontalLayout();
        user.getRoles().forEach(r -> {
            Span badge = new Span(r.name());
            badge.getElement().getThemeList().add("badge pill");
            roles.add(badge);
        });

        // --- Profiilikuva (vaatimus 19) ---
        Upload upload = new Upload(UploadHandler.inMemory((metadata, data) ->
                getUI().ifPresent(ui -> ui.access(() -> {
                    try {
                        userService.updateProfileImage(user.getUsername(), data, metadata.contentType());
                        // Sivu ladataan uudelleen, jotta myös headerin avatar päivittyy: MainLayout on
                        // eri komponenttipuussa eikä tiedä profiilin muutoksesta muuten.
                        UI.getCurrent().getPage().reload();
                    } catch (BusinessException ex) {
                        Notifications.error(ex.getMessage());
                    }
                }))));
        upload.setAcceptedFileTypes("image/png", "image/jpeg", ".png", ".jpg", ".jpeg");
        upload.setMaxFiles(1);
        upload.setMaxFileSize((int) UserService.MAX_IMAGE_BYTES);
        upload.addFileRejectedListener(e -> Notifications.error(e.getErrorMessage()));
        upload.setDropLabel(new Span("Pudota kuva tähän (PNG/JPEG, max 2 Mt)"));

        Button removeImage = new Button("Poista kuva", VaadinIcon.TRASH.create(), e -> {
            userService.removeProfileImage(user.getUsername());
            UI.getCurrent().getPage().reload();
        });
        removeImage.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        removeImage.setVisible(user.getProfileImage() != null);

        // --- Salasanan vaihto sähköpostilla (vaatimus 27) ---
        // Kirjautunutkin käyttäjä vaihtaa salasanan sähköpostilinkin kautta: näin varmistetaan, että
        // vaihtaja hallitsee tilin sähköpostia, eikä esim. lukitsematta jätetty kone riitä tunnuksen kaappaamiseen.
        Paragraph pwInfo = new Paragraph("Saat sähköpostiisi linkin, jolla voit asettaa uuden salasanan.");
        pwInfo.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        Button sendLink = new Button("Lähetä salasanan vaihtolinkki", VaadinIcon.ENVELOPE.create(), e -> {
            passwordResetService.requestReset(user.getEmail());
            Notifications.success("Linkki lähetetty osoitteeseen " + user.getEmail());
        });

        Div card = new Div(avatar, name, username, roles,
                new H3("Profiilikuva"), upload, removeImage,
                new H3("Salasana"), pwInfo, sendLink);
        card.addClassName("auth-card");
        card.addClassNames(LumoUtility.Display.FLEX, LumoUtility.FlexDirection.COLUMN, LumoUtility.AlignItems.CENTER,
                LumoUtility.Gap.SMALL);
        card.getStyle().set("max-width", "520px");
        return card;
    }
}
