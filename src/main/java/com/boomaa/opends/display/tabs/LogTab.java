package com.boomaa.opends.display.tabs;

import com.boomaa.opends.display.Logger;
import com.boomaa.opends.display.frames.FrameBase;
import com.boomaa.opends.display.frames.LogWindow;
import com.boomaa.opends.util.LogFilter;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JCheckBox;

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

        JCheckBox info = new JCheckBox("Info", LogFilter.isInfoEnabled());
        JCheckBox warn = new JCheckBox("Warning", LogFilter.isWarningEnabled());
        JCheckBox err = new JCheckBox("Error", LogFilter.isErrorEnabled());
        info.addActionListener(e -> LogFilter.setInfoEnabled(info.isSelected()));
        warn.addActionListener(e -> LogFilter.setWarningEnabled(warn.isSelected()));
        err.addActionListener(e -> LogFilter.setErrorEnabled(err.isSelected()));

        gbc.gridx = 1;
        super.add(info, gbc);
        gbc.gridx = 2;
        super.add(warn, gbc);
        gbc.gridx = 3;
        super.add(err, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.weighty = 1;
        super.add(Logger.PANE, gbc);
    }
}
