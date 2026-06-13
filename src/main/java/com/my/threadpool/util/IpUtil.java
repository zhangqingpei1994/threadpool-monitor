package com.my.threadpool.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * IP工具类
 * @author zhangqingpei
 */
public final class IpUtil {
    /**
     * 获取当前IP地址
     */
    public static String getLocalIp() {
        return "127.0.0.1";
    }

    /**
     * 获取当前线程的IP地址
     */
    public static String getIpV4Address() {
        try {
            // 1. 获取本机所有的网络接口（包括物理网卡和虚拟网卡）
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = networkInterfaces.nextElement();

                // 2. 过滤无效的网络接口：
                // - 排除回环接口 (如 lo)
                // - 排除虚拟网卡 (如 docker0, vboxnet 等)
                // - 排除未启动的网卡
                if (netInterface.isLoopback() || netInterface.isVirtual() || !netInterface.isUp()) {
                    continue;
                }

                // 3. 遍历该网卡下绑定的所有 IP 地址
                Enumeration<InetAddress> inetAddresses = netInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    // 4. 过滤条件：必须是 IPv4 地址，且不能是回环地址 (127.x.x.x)
                    if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        // 未找到有效的 IPv4 地址
        return "";
    }

}
