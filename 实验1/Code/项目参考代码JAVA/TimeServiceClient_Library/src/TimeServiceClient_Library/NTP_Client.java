package TimeServiceClient_Library;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.*;
import java.util.Arrays;

/** 基于参考客户端库完善；保留原有公共接口，供控制台及 GUI 调用。 */
public class NTP_Client {
    private static final int PACKET_SIZE = 48;
    private static final long NTP_EPOCH = 2208988800L;
    private final int port;
    private final int timeoutMillis;
    private DatagramSocket socket;
    private InetAddress address;
    private String addressError = "ADDRESS_NOT_SET";
    private long resolveMillis;
    private boolean started;
    private long previousT1;
    private Socket proxyControl;
    private InetSocketAddress proxyRelay;
    private String socketError = "Socket is not open";
    private String transport = "DIRECT_UDP";

    public enum NTP_Client_ResultCode {
        NTP_Success, NTP_ServerAddressNotSet, NTP_SendFailed, NTP_ReceiveFailed
    }

    public final class NTP_Timestamp_Data {
        public NTP_Client_ResultCode eResultCode = NTP_Client_ResultCode.NTP_ServerAddressNotSet;
        public long lUnixTime, lHour, lMinute, lSecond;
        public long lOffsetMillis, lDelayMillis, lLocalUnixTimeMillis, lCorrectedUnixTimeMillis;
        // 未发生或未匹配的时间戳使用 null；失败行不伪造为零。
        public Long t1, t2, t3, t4;
        public String sTransport = "DIRECT_UDP";
        public String sReason = "";
        public String sLastDiscardReason = "";
        public String sRequestTimestampHex = "";
        public long lWaitMillis, lResolveMillis;
        public int iDiscardedPackets, iOriginMismatchPackets;
        public boolean bSent;
    }

    public NTP_Client() { this(123, 500); }

    /** 非默认端口供本地可复现测试使用，正式服务器默认使用 UDP 123。 */
    public NTP_Client(int port, int timeoutMillis) {
        if (port < 1 || port > 65535 || timeoutMillis < 1)
            throw new IllegalArgumentException("Invalid port or timeout");
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    public Boolean CreateSocket() {
        CloseSocket();
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(timeoutMillis);
            String proxyHost = System.getProperty("ntp.socksHost", "");
            if (!proxyHost.isEmpty()) {
                int proxyPort = Integer.parseInt(System.getProperty("ntp.socksPort", "1080"));
                openProxy(proxyHost, proxyPort);
                transport = "SOCKS5_UDP@" + proxyHost + ":" + proxyPort;
            } else transport = "DIRECT_UDP";
            socketError = "";
            return true;
        } catch (Exception ex) {
            socketError = "SOCKET_OR_PROXY_SETUP_FAILED: " + ex.toString();
            CloseSocket();
            return false;
        }
    }

    public InetAddress SetUp_TimeService_AddressStruct(String host) {
        // 先清除旧地址，避免新地址解析失败后继续向旧服务器发包。
        address = null;
        long start = System.nanoTime();
        try {
            address = InetAddress.getByName(host);
            addressError = "";
        } catch (Exception ex) {
            addressError = "DNS_FAILED: " + ex.toString();
        } finally { resolveMillis = (System.nanoTime() - start) / 1000000L; }
        return address;
    }

    public int GetPort() { return port; }

    /** 每次调用串行执行；用本次请求的原始 8 字节 T1 匹配响应 Origin Timestamp。 */
    public synchronized NTP_Timestamp_Data Get_NTP_Timestamp() {
        NTP_Timestamp_Data result = new NTP_Timestamp_Data();
        result.lResolveMillis = resolveMillis;
        result.sTransport = transport;
        if (address == null) {
            result.sReason = addressError;
            return result;
        }
        byte[] request = new byte[PACKET_SIZE];
        request[0] = 0x23; // LI=0, version=4, client mode=3
        long sendStart = System.nanoTime();
        result.t1 = System.currentTimeMillis();
        putTimestamp(request, 40, result.t1);
        // 同一毫秒快速重试时仍保持请求时间戳唯一：增加一个 NTP 小数最低位。
        // 该增量小于 1 ns，不改变用于毫秒计算的本地 T1。
        long stamp = readRaw(request, 40);
        if (Long.compareUnsigned(stamp, previousT1) <= 0) stamp = previousT1 + 1;
        previousT1 = stamp;
        for (int i = 7; i >= 0; i--) { request[40+i] = (byte) stamp; stamp >>>= 8; }
        byte[] expected = Arrays.copyOfRange(request, 40, 48);
        StringBuilder hex = new StringBuilder();
        for (byte b : expected) hex.append(String.format("%02x", b & 255));
        result.sRequestTimestampHex = hex.toString();
        try {
            if (socket == null || socket.isClosed()) throw new java.io.IOException(socketError);
            sendRequest(request);
            result.bSent = true;
        } catch (Exception ex) {
            result.eResultCode = NTP_Client_ResultCode.NTP_SendFailed;
            result.sReason = "SEND_FAILED: " + ex.toString();
            result.lWaitMillis = (System.nanoTime() - sendStart) / 1000000L;
            return result;
        }
        long waitStart = System.nanoTime();
        long deadline = waitStart + timeoutMillis * 1000000L;
        result.eResultCode = NTP_Client_ResultCode.NTP_ReceiveFailed;
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new SocketTimeoutException();
                socket.setSoTimeout((int) Math.max(1, (remaining + 999999L) / 1000000L));
                byte[] bytes = new byte[512];
                DatagramPacket reply = new DatagramPacket(bytes, bytes.length);
                socket.receive(reply);
                long receivedNanos = System.nanoTime();
                long receivedMillis = System.currentTimeMillis(); // 收包后立即记录 T4
                if (receivedNanos > deadline) throw new SocketTimeoutException();
                String discarded = null;
                // 代理先验证中继来源及封装中的实际服务器，再进行 NTP 原始检查。
                if (proxyRelay != null) {
                    try {
                        reply = unwrapProxy(reply);
                        bytes = reply.getData();
                    } catch (IOException ex) { discarded = "INVALID_PROXY_PACKET"; }
                }
                if (discarded != null) { /* 统一进入下方丢弃分支 */ }
                else if (!reply.getAddress().equals(address) || reply.getPort() != port)
                    discarded = "SOURCE_MISMATCH";
                else if (reply.getLength() < PACKET_SIZE) discarded = "SHORT_PACKET";
                else if (!Arrays.equals(expected, Arrays.copyOfRange(bytes, 24, 32)))
                    discarded = "ORIGIN_MISMATCH";
                else if ((bytes[0] & 7) != 4 || ((bytes[0] >>> 3) & 7) < 3
                         || ((bytes[0] >>> 3) & 7) > 4) discarded = "INVALID_HEADER";
                else if (((bytes[0] & 255) >>> 6) == 3 || (bytes[1] & 255) < 1
                         || (bytes[1] & 255) > 15) discarded = "SERVER_UNSYNCHRONIZED_OR_KOD";
                else if (readRaw(bytes, 32) == 0 || readRaw(bytes, 40) == 0)
                    discarded = "ZERO_SERVER_TIMESTAMP";
                if (discarded != null) {
                    result.iDiscardedPackets++;
                    if (discarded.equals("ORIGIN_MISMATCH")) result.iOriginMismatchPackets++;
                    result.sLastDiscardReason = discarded;
                    continue; // 丢弃旧包/无效包，继续等待；不重置本次总等待期限。
                }
                result.t2 = readTimestampMillis(bytes, 32);
                result.t3 = readTimestampMillis(bytes, 40);
                result.t4 = receivedMillis;
                result.lLocalUnixTimeMillis = receivedMillis;
                long offsetSum = (result.t2 - result.t1) + (result.t3 - result.t4);
                result.lOffsetMillis = offsetSum / 2; // 整数毫秒，向零截断
                result.lDelayMillis = (result.t4 - result.t1) - (result.t3 - result.t2);
                result.lCorrectedUnixTimeMillis = result.t4 + result.lOffsetMillis;
                result.lUnixTime = result.lCorrectedUnixTimeMillis / 1000L;
                result.lHour = Math.floorMod(result.lUnixTime, 86400L) / 3600;
                result.lMinute = Math.floorMod(result.lUnixTime, 3600L) / 60;
                result.lSecond = Math.floorMod(result.lUnixTime, 60L);
                result.eResultCode = NTP_Client_ResultCode.NTP_Success;
                result.sReason = "OK";
                return result;
            }
        } catch (SocketTimeoutException ex) {
            result.sReason = "TIMEOUT";
        } catch (Exception ex) {
            result.sReason = "RECEIVE_FAILED: " + ex.toString();
        } finally { result.lWaitMillis = (System.nanoTime() - waitStart) / 1000000L; }
        return result;
    }

    private static long readRaw(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (bytes[offset+i] & 255L);
        return value;
    }

    private static void putTimestamp(byte[] b, int offset, long unixMillis) {
        long seconds = unixMillis / 1000L + NTP_EPOCH;
        long fraction = Math.floorMod(unixMillis, 1000L) * 0x100000000L / 1000L;
        for (int i = 3; i >= 0; i--) { b[offset+i] = (byte) seconds; seconds >>>= 8; }
        for (int i = 3; i >= 0; i--) { b[offset+4+i] = (byte) fraction; fraction >>>= 8; }
    }

    private static long readTimestampMillis(byte[] b, int offset) {
        long seconds = 0, fraction = 0;
        for (int i = 0; i < 4; i++) seconds = (seconds << 8) | (b[offset+i] & 255L);
        for (int i = 0; i < 4; i++) fraction = (fraction << 8) | (b[offset+4+i] & 255L);
        // 四舍五入到毫秒，避免 NTP 编码量化后整毫秒时间被向下截断 1 ms。
        return (seconds - NTP_EPOCH) * 1000L + (fraction * 1000L + 0x80000000L) / 0x100000000L;
    }

    public Boolean Get_ClientStarted_Flag() { return started; }
    public void Set_ClientStarted_Flag(Boolean value) { started = value; }
    public void CloseSocket() {
        if (socket != null) socket.close();
        if (proxyControl != null) try { proxyControl.close(); } catch (IOException ignored) {}
        proxyControl = null;
        proxyRelay = null;
    }

    /** RFC 1928 UDP ASSOCIATE；仅支持不需认证的已有本地 SOCKS5 服务。 */
    private void openProxy(String host, int proxyPort) throws IOException {
        InetAddress proxyAddress = InetAddress.getByName(host);
        if (!proxyAddress.isLoopbackAddress()) throw new IOException("Only loopback SOCKS proxy is supported");
        proxyControl = new Socket();
        proxyControl.connect(new InetSocketAddress(proxyAddress, proxyPort), 3000);
        proxyControl.setSoTimeout(3000);
        DataInputStream in = new DataInputStream(proxyControl.getInputStream());
        OutputStream out = proxyControl.getOutputStream();
        out.write(new byte[]{5,1,0}); out.flush();
        if (in.readUnsignedByte()!=5 || in.readUnsignedByte()!=0)
            throw new IOException("SOCKS5 no-auth method rejected");
        out.write(new byte[]{5,3,0,1,0,0,0,0,0,0}); out.flush();
        if (in.readUnsignedByte()!=5 || in.readUnsignedByte()!=0 || in.readUnsignedByte()!=0)
            throw new IOException("SOCKS5 UDP ASSOCIATE rejected");
        InetAddress relayAddress = readProxyAddress(in);
        int relayPort = in.readUnsignedShort();
        if (relayAddress.isAnyLocalAddress()) relayAddress = proxyAddress;
        if (!relayAddress.isLoopbackAddress() || relayPort==0)
            throw new IOException("Unexpected non-local UDP relay");
        proxyRelay = new InetSocketAddress(relayAddress, relayPort);
        // TCP 控制连接在整个 UDP 会话期间保持开启。
    }

    private static InetAddress readProxyAddress(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        int length;
        if (type==1) length=4;
        else if (type==4) length=16;
        else throw new IOException("Only numeric IPv4/IPv6 SOCKS addresses supported");
        byte[] addr = new byte[length]; in.readFully(addr);
        return InetAddress.getByAddress(addr);
    }

    private void sendRequest(byte[] request) throws IOException {
        if (proxyRelay == null) {
            socket.send(new DatagramPacket(request, request.length, address, port));
            return;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        out.writeShort(0); out.writeByte(0); // RSV, FRAG=0
        out.writeByte(address.getAddress().length==4 ? 1 : 4);
        out.write(address.getAddress()); out.writeShort(port); out.write(request);
        byte[] wrapped = buffer.toByteArray();
        socket.send(new DatagramPacket(wrapped, wrapped.length, proxyRelay));
    }

    private DatagramPacket unwrapProxy(DatagramPacket reply) throws IOException {
        if (!reply.getSocketAddress().equals(proxyRelay)) throw new IOException("UDP relay mismatch");
        ByteArrayInputStream buffer = new ByteArrayInputStream(reply.getData(),0,reply.getLength());
        DataInputStream in = new DataInputStream(buffer);
        if (in.readUnsignedShort()!=0 || in.readUnsignedByte()!=0)
            throw new IOException("Invalid SOCKS UDP reserved/fragment field");
        InetAddress source = readProxyAddress(in);
        int sourcePort = in.readUnsignedShort();
        byte[] payload = new byte[buffer.available()]; in.readFully(payload);
        return new DatagramPacket(payload,payload.length,source,sourcePort);
    }
}
