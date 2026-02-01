package com.boomaa.opends.display;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;

public final class Theme {
    public static final Color BG = new Color(15, 23, 42);
    public static final Color CARD = new Color(17, 24, 39);
    public static final Color SURFACE = new Color(11, 17, 32);
    public static final Color TEXT = new Color(248, 250, 252);
    public static final Color MUTED = new Color(148, 163, 184);
    public static final Color ACCENT = new Color(56, 189, 248);
    public static final Color ACCENT_DARK = new Color(14, 116, 144);
    public static final Color BORDER = new Color(51, 65, 85);

    private Theme() {
    }

    public static void apply() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {
        }

        Font baseFont = new Font("SansSerif", Font.PLAIN, 13);
        UIManager.put("defaultFont", baseFont);
        UIManager.put("Panel.background", new ColorUIResource(BG));
        UIManager.put("control", new ColorUIResource(BG));
        UIManager.put("info", new ColorUIResource(BG));
        UIManager.put("nimbusBase", new ColorUIResource(ACCENT_DARK));
        UIManager.put("nimbusBlueGrey", new ColorUIResource(30, 41, 59));
        UIManager.put("nimbusLightBackground", new ColorUIResource(CARD));
        UIManager.put("text", new ColorUIResource(TEXT));

        UIManager.put("Label.foreground", new ColorUIResource(TEXT));
        UIManager.put("CheckBox.background", new ColorUIResource(BG));
        UIManager.put("CheckBox.foreground", new ColorUIResource(TEXT));
        UIManager.put("RadioButton.background", new ColorUIResource(BG));
        UIManager.put("RadioButton.foreground", new ColorUIResource(TEXT));

        UIManager.put("Button.background", new ColorUIResource(ACCENT));
        UIManager.put("Button.foreground", new ColorUIResource(new Color(15, 23, 42)));
        UIManager.put("ToggleButton.background", new ColorUIResource(new Color(30, 41, 59)));
        UIManager.put("ToggleButton.foreground", new ColorUIResource(TEXT));

        UIManager.put("TextField.background", new ColorUIResource(SURFACE));
        UIManager.put("TextField.foreground", new ColorUIResource(TEXT));
        UIManager.put("TextField.caretForeground", new ColorUIResource(TEXT));
        UIManager.put("FormattedTextField.background", new ColorUIResource(SURFACE));
        UIManager.put("FormattedTextField.foreground", new ColorUIResource(TEXT));
        UIManager.put("ComboBox.background", new ColorUIResource(SURFACE));
        UIManager.put("ComboBox.foreground", new ColorUIResource(TEXT));

        UIManager.put("TabbedPane.background", new ColorUIResource(BG));
        UIManager.put("TabbedPane.contentAreaColor", new ColorUIResource(CARD));
        UIManager.put("TabbedPane.foreground", new ColorUIResource(TEXT));
        UIManager.put("TabbedPane.selected", new ColorUIResource(CARD));
        UIManager.put("TabbedPane.tabInsets", new Insets(6, 18, 6, 18));
        UIManager.put("TabbedPane.selectedTabPadInsets", new Insets(2, 2, 2, 2));
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(8, 8, 0, 8));

        UIManager.put("ScrollPane.background", new ColorUIResource(BG));
        UIManager.put("TextArea.background", new ColorUIResource(SURFACE));
        UIManager.put("TextArea.foreground", new ColorUIResource(TEXT));
        UIManager.put("TextArea.caretForeground", new ColorUIResource(TEXT));

        UIManager.put("Table.background", new ColorUIResource(SURFACE));
        UIManager.put("Table.foreground", new ColorUIResource(TEXT));
        UIManager.put("Table.gridColor", new ColorUIResource(BORDER));
        UIManager.put("TableHeader.background", new ColorUIResource(BG));
        UIManager.put("TableHeader.foreground", new ColorUIResource(TEXT));
    }

    public static JPanel cardPanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        return panel;
    }

    public static void stylePrimaryButton(AbstractButton button) {
        styleButton(button, ACCENT, new Color(15, 23, 42));
    }

    public static void styleGhostButton(AbstractButton button) {
        styleButton(button, new Color(30, 41, 59), TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
    }

    public static void styleDangerButton(AbstractButton button) {
        styleButton(button, new Color(239, 68, 68), Color.WHITE);
    }

    public static void styleToggleButton(AbstractButton button, boolean selected) {
        if (selected) {
            styleButton(button, new Color(34, 197, 94), new Color(15, 23, 42));
        } else {
            styleButton(button, new Color(30, 41, 59), TEXT);
        }
    }

    private static void styleButton(AbstractButton button, Color bg, Color fg) {
        button.setOpaque(true);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setBorder(createPillBorder());
        button.setMargin(new Insets(6, 14, 6, 14));
        button.setFocusPainted(false);
    }

    private static Border createPillBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        );
    }

    public static void styleLogArea(JTextArea textArea) {
        textArea.setBackground(SURFACE);
        textArea.setForeground(TEXT);
        textArea.setCaretColor(TEXT);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    }
}
