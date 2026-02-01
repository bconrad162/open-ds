package com.boomaa.opends.networking;

import com.boomaa.opends.data.holders.Remote;
import com.boomaa.opends.display.DisplayEndpoint;
import com.boomaa.opends.display.MainJDEC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class AddressConstants {
    public static final String LOCALHOST = "localhost";
    private static final String LOCALHOST_IP = "127.0.0.1";
    public static final String USB_RIO_IP = "172.22.11.2";
    public static final String FMS_IP = "10.0.100.5";
    public static final String IPV4_REGEX = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$";
    private static final PortTriple FMS_PORTS_2020 = new PortTriple(1750, 1160, 1121);
    private static final PortQuad RIO_PORTS_2020 = new PortQuad(1740, 1110, 1150, 1735);
    private static PortTriple fmsPorts;
    private static PortQuad rioPorts;
    private static final int PROBE_INTERVAL_MS = 1000;
    private static final int PROBE_TIMEOUT_MS = 100;
    private static long lastProbeMs;
    private static String lastProbedAddress;
    private static String lastConnectedRioAddress;
    private static String lastConnectedRioLabel;

    static {
        reloadProtocol();
    }

    private AddressConstants() {
    }

    public static void reloadProtocol() {
        fmsPorts = (PortTriple) getProtoYearValue("FMS_PORTS");
        rioPorts = (PortQuad) getProtoYearValue("RIO_PORTS");
    }

    public static PortTriple getFMSPorts() {
        return fmsPorts;
    }

    public static PortQuad getRioPorts() {
        return rioPorts;
    }

    public static String getRioAddress() throws NumberFormatException {
        if (!DisplayEndpoint.NET_IF_INIT.isInit(Remote.ROBO_RIO)) {
            lastConnectedRioAddress = null;
        }
        if (lastConnectedRioAddress != null) {
            return lastConnectedRioAddress;
        }
        int teamNum = MainJDEC.TEAM_NUMBER.checkedIntParse();
        String teamText = MainJDEC.TEAM_NUMBER.getText();

        if (teamText.matches(IPV4_REGEX)) {
            return teamText;
        } else if (teamText.equalsIgnoreCase(LOCALHOST) || teamText.equals(LOCALHOST_IP)) {
            return LOCALHOST;
        }

        long now = System.currentTimeMillis();
        if (now - lastProbeMs < PROBE_INTERVAL_MS && lastProbedAddress != null) {
            return lastProbedAddress;
        }
        lastProbeMs = now;

        String teamMdns = teamNum != -1 ? "roboRIO-" + teamNum + "-FRC.local" : null;
        if (teamMdns != null && isTcpReachable(teamMdns, getRioPorts().getTcp())) {
            lastProbedAddress = teamMdns;
            return teamMdns;
        }
        if (isTcpReachable(USB_RIO_IP, getRioPorts().getTcp())) {
            lastProbedAddress = USB_RIO_IP;
            return USB_RIO_IP;
        }
        if (isTcpReachable(LOCALHOST_IP, getRioPorts().getShuffleboard())) {
            lastProbedAddress = LOCALHOST;
            return LOCALHOST;
        }
        if (teamMdns != null) {
            lastProbedAddress = teamMdns;
            return teamMdns;
        }
        lastProbedAddress = "240.0.0.0";
        return lastProbedAddress;
    }

    private static Object getProtoYearValue(String base) {
        try {
            return AddressConstants.class.getDeclaredField(base + "_" + MainJDEC.getProtocolYear()).get(null);
        } catch (NoSuchFieldException e0) {
            try {
                return AddressConstants.class.getDeclaredField(base + "_2020").get(null);
            } catch (NoSuchFieldException | IllegalAccessException e1) {
                e1.printStackTrace();
            }
        } catch (IllegalAccessException e2) {
            e2.printStackTrace();
        }
        return null;
    }

    private static boolean isTcpReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void noteConnectedRioAddress(String address) {
        lastConnectedRioAddress = address;
        lastConnectedRioLabel = computeRioLabel(address);
    }

    public static void clearConnectedRioAddressIfDisconnected() {
        if (!DisplayEndpoint.NET_IF_INIT.isInit(Remote.ROBO_RIO)) {
            lastConnectedRioAddress = null;
            lastConnectedRioLabel = null;
        }
    }

    public static void forceClearConnectedRioAddress() {
        lastConnectedRioAddress = null;
        lastConnectedRioLabel = null;
    }

    public static String getConnectedRioLabel() {
        return lastConnectedRioLabel != null ? lastConnectedRioLabel : "";
    }


    private static String computeRioLabel(String address) {
        if (address == null) {
            return "";
        }
        if (USB_RIO_IP.equals(address)) {
            return "USB (" + USB_RIO_IP + ")";
        }
        if (LOCALHOST.equalsIgnoreCase(address) || LOCALHOST_IP.equals(address)) {
            return "Sim (localhost)";
        }
        return "Wi-Fi (" + address + ")";
    }
}
