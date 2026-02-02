package com.boomaa.opends.display.elements;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

public class LedIndicator extends JComponent {
    private final Color onColor;
    private final Color offColor;
    private boolean on;

    public LedIndicator(Color onColor, Color offColor) {
        this.onColor = onColor;
        this.offColor = offColor;
        this.on = false;
        setPreferredSize(new Dimension(18, 18));
        setMinimumSize(new Dimension(18, 18));
    }

    public void setOn(boolean on) {
        this.on = on;
        repaint();
    }

    public boolean isOn() {
        return on;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(on ? onColor : offColor);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
    }
}
