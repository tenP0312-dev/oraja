package bms.player.beatoraja.launcher;

import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import static org.junit.jupiter.api.Assertions.*;

class HandCameraConfigurationTest {
    @Test void classicControlsAreInjectedAndLocalizedWithoutOpeningJavafx() throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                getClass().getResourceAsStream("/bms/player/beatoraja/launcher/VideoConfigurationView.fxml"));
        var ids = new HashSet<String>();
        var nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (element.hasAttribute("fx:id")) ids.add(element.getAttribute("fx:id"));
            String text = element.getAttribute("text");
            if (text.startsWith("%HAND_CAMERA")) {
                for (var locale : List.of(Locale.ROOT, Locale.JAPAN)) {
                    assertFalse(ResourceBundle.getBundle("resources.UIResources", locale).getString(text.substring(1)).isBlank());
                }
            }
        }
        for (String id : List.of("handCameraEnabled", "handCameraDevice", "handCameraRefresh", "handCameraStatus",
                "handCameraWidth", "handCameraHeight", "handCameraFps", "handCameraMirror", "handCameraFlipVertical", "handCameraBgaFrame", "handCameraOffsetX", "handCameraOffsetY", "handCameraZoom",
                "handCameraFocusX", "handCameraFocusY")) {
            assertTrue(ids.contains(id));
            assertNotNull(VideoConfigurationView.class.getDeclaredField(id).getAnnotation(javafx.fxml.FXML.class));
        }
        assertNotNull(VideoConfigurationView.class.getDeclaredMethod("refreshHandCameras").getAnnotation(javafx.fxml.FXML.class));
    }
}
