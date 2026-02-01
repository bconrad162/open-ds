package com.boomaa.opends.display.frames;

import com.boomaa.opends.display.DisplayEndpoint;
import com.boomaa.opends.display.GlobalKeyListener;
import com.boomaa.opends.display.MainJDEC;
import com.boomaa.opends.display.MultiKeyEvent;
import com.boomaa.opends.display.RobotMode;
import com.boomaa.opends.display.StdRedirect;
import com.boomaa.opends.display.TeamNumListener;
import com.boomaa.opends.display.TeamNumPersist;
import com.boomaa.opends.display.Theme;
import com.boomaa.opends.display.elements.GBCPanelBuilder;
import com.boomaa.opends.display.tabs.TabChangeListener;
import com.boomaa.opends.util.Debug;
import com.boomaa.opends.util.OperatingSystem;
import com.boomaa.opends.util.Parameter;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

public class MainFrame implements MainJDEC {
    public static final Image ICON = Toolkit.getDefaultToolkit()
        .getImage(MainFrame.class.getResource("/icon.png"))
        .getScaledInstance(32, 32, Image.SCALE_SMOOTH);
    public static final Image ICON_MIN = Toolkit.getDefaultToolkit()
        .getImage(MainFrame.class.getResource("/icon-min.png"));
    private static final GBCPanelBuilder base = new GBCPanelBuilder(TAB_CONTAINER)
        .setInsets(new Insets(5, 5, 5, 5))
        .setFill(GridBagConstraints.BOTH)
        .setAnchor(GridBagConstraints.CENTER);

    private MainFrame() {
    }

    public static void display() {
        if (!FRAME.isHeadless()) {
            Theme.apply();
        }
        FRAME.setIconImage(MainFrame.ICON);
        if (!FRAME.isHeadless()) {
            FRAME.getContentPane().setLayout(new GridBagLayout());
            FRAME.getContentPane().setBackground(Theme.BG);
        }
        TITLE.setText(TITLE.getText() + " " + DisplayEndpoint.CURRENT_VERSION_TAG);

        valueInit();
        layoutInit();
        listenerInit();

        FRAME.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        if (!FRAME.isHeadless()) {
            FRAME.getElement().addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    Thread shutdownThread = new Thread(DisplayEndpoint::shutdown, "opends-shutdown");
                    shutdownThread.setDaemon(true);
                    shutdownThread.start();
                    System.exit(0);
                }
            });
        }
        FRAME.setResizable(true);
        if (!FRAME.isHeadless()) {
            SwingUtilities.updateComponentTreeUI(FRAME.getElement());
        }
        FRAME.pack();
        FRAME.setLocationRelativeTo(null);
        FRAME.setVisible(true);
        if (!Parameter.DISABLE_HOTKEYS.isPresent()) {
            try {
                GlobalScreen.registerNativeHook();
            } catch (NativeHookException e) {
                e.printStackTrace();
            }
        }
        Debug.println("Display elements initialized and GUI showing");
    }

    private static void valueInit() {
        IS_ENABLED.setEnabled(false);

        String prevTeamNum = TeamNumPersist.load();
        Debug.println("Team number retrieved from file (will set if not empty): " + prevTeamNum);
        if (!prevTeamNum.trim().isEmpty()) {
            TEAM_NUMBER.setText(prevTeamNum);
        }

        Parameter.applyJDECLinks();
    }

    private static void listenerInit() {
        PROTOCOL_YEAR.addActionListener(makeAsyncListener((e) -> {
            DisplayEndpoint.doProtocolUpdate();
            unsetAllInterfaces();
            DisplayEndpoint.FILE_LOGGER.restart();
            Debug.println("Protocol year changed to: " + MainJDEC.getProtocolYear());
        }));
        TAB.addChangeListener(TabChangeListener.getInstance());

        IS_ENABLED.addItemListener((e) -> RSL_INDICATOR.setFlashing(e.getStateChange() == ItemEvent.SELECTED));
        IS_ENABLED.addItemListener((e) -> Theme.styleToggleButton(IS_ENABLED.getElement(),
                e.getStateChange() == ItemEvent.SELECTED));
        DISABLE_BTN.addActionListener((e) -> IS_ENABLED.setSelected(false));

        RESTART_CODE_BTN.init();
        RESTART_CODE_BTN.addActionListener((e) -> {
            IS_ENABLED.setSelected(false);
            Debug.println("Restarting robot code");
        });
        ESTOP_BTN.init();
        ESTOP_BTN.addActionListener((e) -> {
            IS_ENABLED.setSelected(false);
            Debug.println("Emergency Stop (ESTOP) initiated");
        });
        RECONNECT_BTN.addActionListener((e) -> {
            Debug.println("Reconnecting/resetting robot interfaces");
            unsetAllInterfaces();
        });

        // Debug println method checks this, but checking before adding a listener improves performance
        if (Parameter.DEBUG.isPresent()) {
            RESTART_ROBO_RIO_BTN.init();
            RESTART_ROBO_RIO_BTN.addActionListener((e) -> Debug.println("Restarting RoboRIO"));
            IS_ENABLED.addItemListener((e) -> Debug.println(e.getStateChange() == ItemEvent.SELECTED
                    ? "Robot Enabled" : "Robot Disabled"));
            FMS_CONNECT.addItemListener((e) -> Debug.println(e.getStateChange() == ItemEvent.SELECTED
                    ? "FMS connection allowed" : "FMS connection disallowed"));
            ROBOT_DRIVE_MODE.addItemListener((e) -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    Debug.println("Drive mode changed to: " + ((RobotMode) e.getItem()).name());
                }
            });
            ALLIANCE_COLOR.addItemListener((e) -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    Debug.println("Alliance color changed to: " + e.getItem());
                }
            });
            ALLIANCE_NUM.addItemListener((e) -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    Debug.println("Alliance number changed to: " + e.getItem());
                }
            });
        }

        TEAM_NUMBER.addDocumentListener(new TeamNumListener());
        Debug.println("Initialized listeners for display elements");

        if (!Parameter.DISABLE_HOTKEYS.isPresent()) {
            StdRedirect.OUT.toNull();
            GlobalScreen.addNativeKeyListener(GlobalKeyListener.INSTANCE
                .addKeyEvent(NativeKeyEvent.VC_ENTER, () -> MainJDEC.IS_ENABLED.setSelected(false))
                .addKeyEvent(NativeKeyEvent.VC_SPACE, MainJDEC.ESTOP_BTN::doClick)
                .addMultiKeyEvent(new MultiKeyEvent(() -> MainJDEC.IS_ENABLED.setSelected(MainJDEC.IS_ENABLED.isEnabled()),
                        NativeKeyEvent.VC_OPEN_BRACKET, NativeKeyEvent.VC_CLOSE_BRACKET, NativeKeyEvent.VC_BACK_SLASH))
            );
            StdRedirect.OUT.reset();
            Debug.println("Registered global hotkey hooks (JNativeHook)");
        }
    }

    private static void layoutInit() {
        UIManager.getDefaults().put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
        UIManager.getDefaults().put("TabbedPane.tabsOverlapBorder", true);

        TAB_CONTAINER.setBackground(Theme.BG);
        JS_TAB.setBackground(Theme.CARD);
        NT_TAB.setBackground(Theme.CARD);
        STATS_TAB.setBackground(Theme.CARD);
        LOG_TAB.setBackground(Theme.CARD);
        TAB.setBackground(Theme.BG);

        TAB.addTab("Control", TAB_CONTAINER);
        TAB.addTab("Joysticks", JS_TAB);
        TAB.addTab("Shuffleboard", NT_TAB);
        TAB.addTab("Statistics", STATS_TAB);
        TAB.addTab("Log", LOG_TAB);
        FRAME.add(TAB);
        Debug.println("Swing tabs added to frame");

        Dimension dimension = new Dimension(820, 560);
        FrameBase.applyNonWindowsScaling(dimension);
        FRAME.setPreferredSize(dimension);

        Theme.styleGhostButton(RESTART_CODE_BTN.getElement());
        Theme.styleGhostButton(RESTART_ROBO_RIO_BTN.getElement());
        Theme.styleDangerButton(ESTOP_BTN.getElement());
        Theme.styleGhostButton(RECONNECT_BTN.getElement());
        Theme.styleGhostButton(DISABLE_BTN.getElement());
        Theme.styleToggleButton(IS_ENABLED.getElement(), IS_ENABLED.isSelected());

        JPanel headerCard = Theme.cardPanel();
        JPanel controlCard = Theme.cardPanel();
        JPanel actionCard = Theme.cardPanel();
        JPanel statusCard = Theme.cardPanel();

        GridBagConstraints root = new GridBagConstraints();
        root.insets = new Insets(8, 8, 8, 8);
        root.fill = GridBagConstraints.BOTH;
        root.weightx = 1;

        root.gridx = 0;
        root.gridy = 0;
        root.gridwidth = 3;
        TAB_CONTAINER.add(headerCard, root);

        root.gridy = 1;
        root.gridwidth = 2;
        root.weightx = 0.7;
        TAB_CONTAINER.add(controlCard, root);

        root.gridx = 2;
        root.weightx = 0.3;
        TAB_CONTAINER.add(statusCard, root);

        root.gridx = 0;
        root.gridy = 2;
        root.gridwidth = 3;
        root.weightx = 1;
        TAB_CONTAINER.add(actionCard, root);

        GBCPanelBuilder header = new GBCPanelBuilder(headerCard)
                .setInsets(new Insets(4, 4, 4, 4))
                .setFill(GridBagConstraints.NONE)
                .setAnchor(GridBagConstraints.LINE_START);
        header.clone().setPos(0, 0, 1, 1).build(TITLE);
        header.clone().setPos(0, 1, 1, 1).build(LINK);
        header.clone().setPos(1, 0, 1, 2).setAnchor(GridBagConstraints.LINE_END)
                .build(new JLabel(new ImageIcon(MainFrame.ICON_MIN)));

        GBCPanelBuilder control = new GBCPanelBuilder(controlCard)
                .setInsets(new Insets(6, 6, 6, 6))
                .setFill(GridBagConstraints.HORIZONTAL)
                .setAnchor(GridBagConstraints.LINE_START);
        control.clone().setPos(0, 0, 1, 1).setFill(GridBagConstraints.NONE).build(RSL_INDICATOR);
        control.clone().setPos(1, 0, 1, 1).setFill(GridBagConstraints.NONE).build(IS_ENABLED);
        control.clone().setPos(2, 0, 1, 1).setFill(GridBagConstraints.NONE).build(DISABLE_BTN);
        control.clone().setPos(3, 0, 1, 1).setFill(GridBagConstraints.NONE).build(ROBOT_DRIVE_MODE);
        control.clone().setPos(0, 1, 2, 1).setFill(GridBagConstraints.NONE).build(new JLabel("Alliance Station"));
        control.clone().setPos(0, 2, 1, 1).build(ALLIANCE_NUM);
        control.clone().setPos(1, 2, 1, 1).build(ALLIANCE_COLOR);
        control.clone().setPos(0, 3, 1, 1).setFill(GridBagConstraints.NONE).build(new JLabel("Team Number"));
        control.clone().setPos(1, 3, 1, 1).build(TEAM_NUMBER);
        control.clone().setPos(0, 4, 1, 1).setFill(GridBagConstraints.NONE).build(new JLabel("Game Data"));
        control.clone().setPos(1, 4, 1, 1).build(GAME_DATA);
        control.clone().setPos(0, 5, 1, 1).setFill(GridBagConstraints.NONE).build(new JLabel("Protocol Year"));
        control.clone().setPos(1, 5, 1, 1).build(PROTOCOL_YEAR);
        control.clone().setPos(2, 5, 1, 1).setFill(GridBagConstraints.NONE).build(new JLabel("Reconnect"));
        control.clone().setPos(2, 6, 1, 1).setFill(GridBagConstraints.NONE).build(RECONNECT_BTN);

        GBCPanelBuilder actions = new GBCPanelBuilder(actionCard)
                .setInsets(new Insets(6, 6, 6, 6))
                .setFill(GridBagConstraints.HORIZONTAL)
                .setAnchor(GridBagConstraints.LINE_START);
        actions.clone().setPos(0, 0, 1, 1).build(RESTART_CODE_BTN);
        actions.clone().setPos(1, 0, 1, 1).build(RESTART_ROBO_RIO_BTN);
        actions.clone().setPos(2, 0, 1, 1).build(ESTOP_BTN);
        actions.clone().setPos(3, 0, 1, 1).build(FMS_CONNECT);

        GBCPanelBuilder status = new GBCPanelBuilder(statusCard)
                .setInsets(new Insets(6, 6, 6, 6))
                .setFill(GridBagConstraints.NONE)
                .setAnchor(GridBagConstraints.LINE_START);
        status.clone().setPos(0, 0, 1, 1).build(BAT_VOLTAGE);
        status.clone().setPos(0, 1, 1, 1).build(new JLabel("Robot"));
        status.clone().setPos(1, 1, 1, 1).build(ROBOT_CONNECTION_STATUS);
        status.clone().setPos(0, 2, 1, 1).build(new JLabel("Link"));
        status.clone().setPos(1, 2, 1, 1).build(RIO_CONNECTION_PATH);
        status.clone().setPos(0, 3, 1, 1).build(new JLabel("Code"));
        status.clone().setPos(1, 3, 1, 1).build(ROBOT_CODE_STATUS);
        status.clone().setPos(0, 4, 1, 1).build(new JLabel("EStop"));
        status.clone().setPos(1, 4, 1, 1).build(ESTOP_STATUS);
        status.clone().setPos(0, 5, 1, 1).build(new JLabel("FMS"));
        status.clone().setPos(1, 5, 1, 1).build(FMS_CONNECTION_STATUS);
        status.clone().setPos(0, 6, 1, 1).build(new JLabel("Time"));
        status.clone().setPos(1, 6, 1, 1).build(MATCH_TIME);

        Debug.println("Swing components initialized and ready for display");
    }

    private static void unsetAllInterfaces() {
        com.boomaa.opends.networking.AddressConstants.forceClearConnectedRioAddress();
        DisplayEndpoint.RIO_UDP_CLOCK.restart();
        DisplayEndpoint.RIO_TCP_CLOCK.restart();
        DisplayEndpoint.FMS_UDP_CLOCK.restart();
        DisplayEndpoint.FMS_TCP_CLOCK.restart();
    }

    public static ActionListener makeAsyncListener(Consumer<ActionEvent> func) {
        return (e) -> new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                func.accept(e);
                return null;
            }
        }.execute();
    }

    public static Container addToPanel(Container panel, Component... comps) {
        for (Component comp : comps) {
            panel.add(comp);
        }
        return panel;
    }

    public static JLabel createLinkLabel(String text) {
        JLabel href = new JLabel("<html><a href=''>" + text + "</a></html>");
        href.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                try {
                    Desktop.getDesktop().browse(new URI("https://" + text));
                } catch (IOException | URISyntaxException exc) {
                    exc.printStackTrace();
                }
            }
        });
        return href;
    }
}
