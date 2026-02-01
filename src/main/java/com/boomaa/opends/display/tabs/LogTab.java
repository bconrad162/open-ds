package com.boomaa.opends.display.tabs;

import com.boomaa.opends.display.Logger;
import com.boomaa.opends.display.frames.FrameBase;
import com.boomaa.opends.display.frames.LogWindow;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;

public class LogTab extends TabBase {
    public LogTab() {
        super(new Dimension(450, 270));
        Logger.PANE.setPreferredSize(super.dimension);
    }

    @Override
    public void config() {
        super.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.gridx = 0;
        gbc.gridy = 0;
        JButton openWindow = new JButton("Open Log Window");
        openWindow.addActionListener(e -> {
            if (!FrameBase.isAlive(LogWindow.class)) {
                new LogWindow();
            } else {
                FrameBase.getAlive(LogWindow.class).forceShow();
            }
        });
        super.add(openWindow, gbc);

        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 1;
        super.add(Logger.PANE, gbc);
    }
}
