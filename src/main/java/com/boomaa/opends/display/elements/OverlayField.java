package com.boomaa.opends.display.elements;

import com.boomaa.opends.display.Theme;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.JTextField;

public class OverlayField extends JTextField implements FocusListener {
    private final String hint;
    private boolean showingHint;

    public OverlayField(final String hint, final int col) {
        super(hint, col);
        super.setForeground(Theme.MUTED);
        this.hint = hint;
        this.showingHint = true;
        super.addFocusListener(this);
    }

    public void reset() {
        super.setVisible(false);
        super.setText(hint);
        showingHint = true;
        super.setVisible(true);
    }

    @Override
    public void focusGained(FocusEvent e) {
        super.setForeground(Theme.TEXT);
        if (this.getText().isEmpty()) {
            super.setText("");
            showingHint = false;
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
        if (this.getText().isEmpty()) {
            super.setForeground(Theme.MUTED);
            super.setText(hint);
            showingHint = true;
        }
    }

    @Override
    public String getText() {
        String out = showingHint ? "" : super.getText();
        return out != null ? out : "";
    }

    @Override
    public void setText(String t) {
        showingHint = false;
        super.setText(t);
    }

    public int checkedIntParse() {
        return checkedIntParse(-1);
    }

    public int checkedIntParse(int defRtn) {
        try {
            return Integer.parseInt(this.getText());
        } catch (NumberFormatException ignored) {
        }
        return defRtn;
    }
}
