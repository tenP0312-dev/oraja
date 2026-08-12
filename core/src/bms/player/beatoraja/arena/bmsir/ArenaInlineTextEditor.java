package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.modmenu.ImGuiInputCapture;
import bms.player.beatoraja.modmenu.ImGuiRenderer;
import com.badlogic.gdx.Gdx;
import imgui.type.ImString;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Places an IME-capable native editor directly over an Arena ImGui field. */
final class ArenaInlineTextEditor {
    private static final Logger logger = LoggerFactory.getLogger(ArenaInlineTextEditor.class);
    private static final AtomicReference<ImString> ACTIVE_TARGET = new AtomicReference<>();

    private ArenaInlineTextEditor() {
    }

    static void open(
            ImString target,
            int maxCodePoints,
            float itemX,
            float itemY,
            float itemWidth,
            float itemHeight
    ) {
        if (!ACTIVE_TARGET.compareAndSet(null, target)) {
            return;
        }
        ImGuiInputCapture.setExternalEditorOpen(true);
        String initialValue = target.get();
        SwingUtilities.invokeLater(() -> showEditor(
                initialValue,
                target,
                maxCodePoints,
                Math.round(itemX),
                Math.round(itemY),
                Math.max(120, Math.round(itemWidth)),
                Math.max(24, Math.round(itemHeight))
        ));
    }

    private static void showEditor(
            String initialValue,
            ImString target,
            int maxCodePoints,
            int itemX,
            int itemY,
            int itemWidth,
            int itemHeight
    ) {
        try {
            JDialog window = new JDialog((java.awt.Frame) null);
            JTextField editor = new JTextField(initialValue);
            AtomicBoolean finished = new AtomicBoolean();
            AtomicBoolean focusAcquired = new AtomicBoolean();
            editor.setBackground(new Color(41, 41, 41));
            editor.setForeground(new Color(245, 245, 245));
            editor.setCaretColor(Color.WHITE);
            editor.setSelectionColor(new Color(65, 105, 160));
            editor.setSelectedTextColor(Color.WHITE);
            editor.setFont(new Font(
                    Font.SANS_SERIF,
                    Font.PLAIN,
                    Math.max(13, itemHeight - 9)
            ));
            editor.setBorder(new CompoundBorder(
                    BorderFactory.createLineBorder(new Color(110, 110, 110)),
                    BorderFactory.createEmptyBorder(1, 5, 1, 5)
            ));

            window.setUndecorated(true);
            window.setAlwaysOnTop(true);
            window.setFocusableWindowState(true);
            window.setAutoRequestFocus(true);
            window.setContentPane(editor);
            window.setSize(itemWidth, itemHeight);
            position(window, itemX, itemY);

            Timer followWindow = new Timer(
                    50,
                    event -> position(window, itemX, itemY)
            );
            Runnable accept = () -> finish(
                    window,
                    followWindow,
                    finished,
                    target,
                    limitCodePoints(editor.getText(), maxCodePoints)
            );
            Runnable cancel = () -> finish(
                    window,
                    followWindow,
                    finished,
                    target,
                    null
            );
            editor.addActionListener(event -> accept.run());
            editor.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        event.consume();
                        cancel.run();
                    }
                }
            });
            editor.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    focusAcquired.set(true);
                }

                @Override
                public void focusLost(FocusEvent event) {
                    if (focusAcquired.get()) {
                        accept.run();
                    }
                }
            });

            window.setVisible(true);
            followWindow.start();
            window.toFront();
            window.requestFocus();
            SwingUtilities.invokeLater(() -> {
                editor.requestFocusInWindow();
                editor.setCaretPosition(editor.getText().length());
            });
        } catch (RuntimeException error) {
            logger.warn("Arena inline IME editor could not be opened", error);
            ACTIVE_TARGET.compareAndSet(target, null);
            ImGuiInputCapture.setExternalEditorOpen(false);
        }
    }

    static boolean isOpenFor(ImString target) {
        return ACTIVE_TARGET.get() == target;
    }

    private static void position(JDialog window, int itemX, int itemY) {
        window.setLocation(
                ImGuiRenderer.getWindowScreenX() + itemX,
                ImGuiRenderer.getWindowScreenY() + itemY
        );
    }

    private static void finish(
            JDialog window,
            Timer followWindow,
            AtomicBoolean finished,
            ImString target,
            String result
    ) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        followWindow.stop();
        window.setVisible(false);
        window.dispose();
        Runnable complete = () -> {
            if (result != null) {
                target.set(result);
            }
            ACTIVE_TARGET.compareAndSet(target, null);
            ImGuiInputCapture.setExternalEditorOpen(false);
        };
        if (Gdx.app != null) {
            Gdx.app.postRunnable(complete);
        } else {
            complete.run();
        }
    }

    static String limitCodePoints(String value, int maxCodePoints) {
        String text = value == null ? "" : value;
        int limit = Math.max(0, maxCodePoints);
        int count = text.codePointCount(0, text.length());
        if (count <= limit) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, limit));
    }
}
