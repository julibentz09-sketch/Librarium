package py.librarium.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Red {

    private static final String ADAPTADORES_VIRTUALES =
            ".*(virtual|vmware|vbox|hyper-v|vethernet|docker|wsl|tap|tun|bluetooth).*";

    private Red() {
    }

    /** IPs de la red local (WiFi o cable), sin contar adaptadores virtuales. */
    public static List<String> ipsLocales() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface interfaz : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!interfaz.isUp() || interfaz.isLoopback() || interfaz.isVirtual()) {
                    continue;
                }
                String nombre = (interfaz.getName() + " " + interfaz.getDisplayName()).toLowerCase();
                if (nombre.matches(ADAPTADORES_VIRTUALES)) {
                    continue;
                }
                for (InetAddress direccion : Collections.list(interfaz.getInetAddresses())) {
                    if (direccion instanceof Inet4Address && direccion.isSiteLocalAddress()) {
                        ips.add(direccion.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("No se pudieron leer las interfaces de red: " + e.getMessage());
        }
        return ips;
    }
}
