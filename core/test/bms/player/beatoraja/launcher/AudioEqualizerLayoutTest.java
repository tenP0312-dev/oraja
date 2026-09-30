package bms.player.beatoraja.launcher;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;
import static org.junit.jupiter.api.Assertions.*;

class AudioEqualizerLayoutTest {
    @Test void equalizerControlsAndHandlersAreWiredWithoutLaunchingJavafx() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (InputStream stream = getClass().getResourceAsStream("AudioConfigurationView.fxml")) {
            assertNotNull(stream);
            var document = factory.newDocumentBuilder().parse(stream);
            var elements = document.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                String id = element.getAttributeNS("http://javafx.com/fxml/1", "id");
                if (id.startsWith("equalizer")) {
                    var field = AudioConfigurationView.class.getDeclaredField(id);
                    assertNotNull(field.getAnnotation(javafx.fxml.FXML.class));
                    assertTrue(javafx.scene.Node.class.isAssignableFrom(field.getType()));
                }
                String action = element.getAttribute("onAction");
                if (action.equals("#updateEqualizer") || action.equals("#resetEqualizer")) {
                    assertNotNull(AudioConfigurationView.class.getMethod(action.substring(1)));
                }
                String text = element.getAttribute("text");
                if (text.startsWith("%EQ_")) {
                    for (Locale locale : new Locale[] {Locale.ROOT, Locale.JAPAN}) {
                        assertFalse(ResourceBundle.getBundle("resources.UIResources", locale)
                                .getString(text.substring(1)).isBlank());
                    }
                }
            }
        }
        // Both layouts share the same movable workspace, including its dynamic band editors.
        String sidebar = Files.readString(Path.of("src/bms/player/beatoraja/launcher/PlayConfigurationView.java"));
        assertTrue(sidebar.contains("sidebarMovable(audioTab, \"equalizerPanel\")"));
        for (Locale locale : new Locale[] {Locale.ROOT, Locale.JAPAN}) {
            var bundle = ResourceBundle.getBundle("resources.UIResources", locale);
            for (String key : new String[] {"EQ_MODE_OFF", "EQ_MODE_SWITCH", "EQ_MODE_LR2", "EQ_GAIN", "EQ_DESCRIPTION", "EQ_OPENAL_UNSUPPORTED"}) {
                assertFalse(bundle.getString(key).isBlank());
            }
        }
    }
}
