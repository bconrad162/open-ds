package com.boomaa.opends.headless.elements;

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import javax.swing.JToggleButton;

public class HToggleButton extends HideComponent<JToggleButton> {
    protected boolean selected;

    public HToggleButton(String text) {
        super(() -> new JToggleButton(text));
    }

    public boolean isSelected() {
        return !isHeadless() ? getElement().isSelected() : selected;
    }

    public void setSelected(boolean selected) {
        if (!isHeadless()) {
            getElement().setSelected(selected);
        } else {
            this.selected = selected;
        }
    }

    public void addActionListener(ActionListener listener) {
        if (!isHeadless()) {
            getElement().addActionListener(listener);
        }
    }

    public void addItemListener(ItemListener listener) {
        if (!isHeadless()) {
            getElement().addItemListener(listener);
        }
    }
}
