package com.boomaa.opends.display.frames;

import com.boomaa.opends.display.Logger;

import java.awt.Dimension;

public class LogWindow extends FrameBase {
    public LogWindow() {
        super("OpenDS Log", new Dimension(900, 600));
    }

    @Override
    public void preConfig() {
        super.preConfig();
        this.setResizable(true);
    }

    @Override
    public void postConfig() {
        this.getContentPane().add(Logger.createMirrorPane());
    }
}
