package com.boomaa.opends.display;

import com.boomaa.opends.display.elements.HideableLabel;
import com.boomaa.opends.display.elements.LedIndicator;
import com.boomaa.opends.display.elements.MultiValueLabel;
import com.boomaa.opends.display.elements.RSLIndicator;
import com.boomaa.opends.display.elements.StickyButton;
import com.boomaa.opends.display.frames.MainFrame;
import com.boomaa.opends.display.tabs.JoystickTab;
import com.boomaa.opends.display.tabs.LogTab;
import com.boomaa.opends.display.tabs.NTTab;
import com.boomaa.opends.display.tabs.StatsTab;
import com.boomaa.opends.headless.elements.HButton;
import com.boomaa.opends.headless.elements.HComboBox;
import com.boomaa.opends.headless.elements.HFrame;
import com.boomaa.opends.headless.elements.HOverlayField;
import com.boomaa.opends.headless.elements.HToggleButton;
import com.boomaa.opends.headless.elements.HCheckBox;

import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

// JDEC = (J)ava (D)isplay (E)lement (C)onstants
public interface MainJDEC {
    HFrame FRAME = new HFrame("OpenDS");
    JPanel TAB_CONTAINER = new JPanel(new GridBagLayout());
    JTabbedPane TAB = new JTabbedPane();

    JLabel TITLE = new JLabel("OpenDS");
    JLabel LINK = MainFrame.createLinkLabel("github.com/Boomaa23/open-ds");
    JLabel ENABLED_BANNER = new JLabel("ROBOT ENABLED");

    HToggleButton IS_ENABLED = new HToggleButton("Enable");
    RSLIndicator RSL_INDICATOR = new RSLIndicator();
    HComboBox<RobotMode> ROBOT_DRIVE_MODE = new HComboBox<>(RobotMode.values());

    HComboBox<String> ALLIANCE_COLOR = new HComboBox<>("Red", "Blue");
    HComboBox<Integer> ALLIANCE_NUM = new HComboBox<>(1, 2, 3);

    HButton DISABLE_BTN = new HButton("Disable");
    StickyButton RESTART_CODE_BTN = new StickyButton("Restart Robot Code", 10);
    StickyButton RESTART_ROBO_RIO_BTN = new StickyButton("Restart RoboRIO", 10);
    StickyButton ESTOP_BTN = new StickyButton("Emergency Stop", 10);

    HOverlayField GAME_DATA = new HOverlayField("Game Data", 6);
    HOverlayField TEAM_NUMBER = new HOverlayField("Team Number", 6);

    HComboBox<Integer> PROTOCOL_YEAR = new HComboBox<>(DisplayEndpoint.UI_PROTOCOL_YEARS);
    HCheckBox FMS_CONNECT = new HCheckBox("Connect FMS");
    HButton RECONNECT_BTN = new HButton("↻");

    JoystickTab JS_TAB = new JoystickTab();
    NTTab NT_TAB = new NTTab();
    StatsTab STATS_TAB = new StatsTab();
    LogTab LOG_TAB = new LogTab();

    JLabel BAT_VOLTAGE = new JLabel("0.00 V");
    MultiValueLabel ROBOT_CONNECTION_STATUS = new MultiValueLabel(false, "Connected", "Simulated");
    HideableLabel RIO_CONNECTION_PATH = new HideableLabel(false);
    HideableLabel FMS_CONNECTION_STATUS = new HideableLabel(false, "Connected");
    HideableLabel ESTOP_STATUS = new HideableLabel(false, "ESTOP");
    HideableLabel MATCH_TIME = new HideableLabel(false, "0");
    MultiValueLabel ROBOT_CODE_STATUS = new MultiValueLabel(false, "Running", "Initializing");
    java.awt.Color LED_ON = new java.awt.Color(34, 197, 94);
    java.awt.Color LED_OFF = new java.awt.Color(239, 68, 68);
    LedIndicator COMM_LED = new LedIndicator(LED_ON, LED_OFF);
    LedIndicator CODE_LED = new LedIndicator(LED_ON, LED_OFF);
    LedIndicator JOYSTICK_LED = new LedIndicator(LED_ON, LED_OFF);

    JLabel CHALLENGE_RESPONSE = new JLabel("");

    static int getProtocolYear() {
        try {
            int uiYear = Integer.parseInt(String.valueOf(PROTOCOL_YEAR.getSelectedItem()));
            return DisplayEndpoint.resolveProtocolYear(uiYear);
        } catch (NullPointerException | NumberFormatException ignored) {
        }
        return DisplayEndpoint.VALID_PROTOCOL_YEARS[0];
    }

    static int getProtocolIndex() {
        return DisplayEndpoint.getProtocolIndex(getProtocolYear());
    }
}
