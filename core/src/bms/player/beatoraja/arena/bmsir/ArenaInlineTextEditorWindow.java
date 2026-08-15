package bms.player.beatoraja.arena.bmsir;

import bms.player.beatoraja.modmenu.ImGuiRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.CompoundBorder;

/** Swing implementation loaded in-process only outside macOS. */
final class ArenaInlineTextEditorWindow {
    private ArenaInlineTextEditorWindow() {
    }

    static void openEmbedded(
            String initialValue,
            int maxCodePoints,
            int itemX,
            int itemY,
            int itemWidth,
            int itemHeight,
            BooleanSupplier stillActive,
            BooleanSupplier markReady,
            Consumer<String> complete,
            Consumer<Throwable> failure
    ) {
        open(
                initialValue,
                maxCodePoints,
                () -> ImGuiRenderer.getWindowScreenX() + itemX,
                () -> ImGuiRenderer.getWindowScreenY() + itemY,
                itemWidth,
                itemHeight,
                stillActive,
                markReady,
                complete,
                failure
        );
    }

    static void openStandalone(
            ArenaInlineTextEditorProtocol.Request request,
            BooleanSupplier parentAlive,
            BooleanSupplier markReady,
            Consumer<String> complete,
            Consumer<Throwable> failure
    ) {
        open(
                request.initialValue(),
                request.maxCodePoints(),
                request::x,
                request::y,
                request.width(),
                request.height(),
                parentAlive,
                markReady,
                complete,
                failure
        );
    }

    private static void open(
            String initialValue,
            int maxCodePoints,
            IntSupplier screenX,
            IntSupplier screenY,
            int itemWidth,
            int itemHeight,
            BooleanSupplier stillActive,
            BooleanSupplier markReady,
            Consumer<String> complete,
            Consumer<Throwable> failure
    ) {
        try {
            SwingUtilities.invokeLater(() -> showEditor(
                    initialValue,
                    maxCodePoints,
                    screenX,
                    screenY,
                    itemWidth,
                    itemHeight,
                    stillActive,
                    markReady,
                    complete,
                    failure
            ));
        } catch (RuntimeException error) {
            failure.accept(error);
        }
    }

    private static void showEditor(
            String initialValue,
            int maxCodePoints,
            IntSupplier screenX,
            IntSupplier screenY,
            int itemWidth,
            int itemHeight,
            BooleanSupplier stillActive,
            BooleanSupplier markReady,
            Consumer<String> complete,
            Consumer<Throwable> failure
    ) {
        JDialog window = null;
        try {
            if (!stillActive.getAsBoolean()) {
                return;
            }
            window = new JDialog((java.awt.Frame) null);
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

            window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            window.setUndecorated(true);
            window.setAlwaysOnTop(true);
            window.setFocusableWindowState(true);
            window.setAutoRequestFocus(true);
            window.setContentPane(editor);
            window.setSize(itemWidth, itemHeight);
            position(window, screenX, screenY);

            JDialog editorWindow = window;
            Timer followWindow = new Timer(50, event -> {
                if (!stillActive.getAsBoolean()) {
                    finish(editorWindow, (Timer) event.getSource(), finished, complete, null);
                    return;
                }
                position(editorWindow, screenX, screenY);
            });
            Runnable accept = () -> finish(
                    editorWindow,
                    followWindow,
                    finished,
                    complete,
                    ArenaInlineTextEditor.limitCodePoints(editor.getText(), maxCodePoints)
            );
            Runnable cancel = () -> finish(
                    editorWindow,
                    followWindow,
                    finished,
                    complete,
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
            if (!markReady.getAsBoolean()) {
                cancel.run();
            }
        } catch (RuntimeException error) {
            if (window != null) {
                window.setVisible(false);
                window.dispose();
            }
            failure.accept(error);
        }
    }

    private static void position(
            JDialog window,
            IntSupplier screenX,
            IntSupplier screenY
    ) {
        window.setLocation(screenX.getAsInt(), screenY.getAsInt());
    }

    private static void finish(
            JDialog window,
            Timer followWindow,
            AtomicBoolean finished,
            Consumer<String> complete,
            String result
    ) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        followWindow.stop();
        window.setVisible(false);
        window.dispose();
        complete.accept(result);
    }
}
