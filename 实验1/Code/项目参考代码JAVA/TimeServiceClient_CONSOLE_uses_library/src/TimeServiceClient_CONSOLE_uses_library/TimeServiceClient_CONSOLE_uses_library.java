package TimeServiceClient_CONSOLE_uses_library;

import TimeServiceClient_Library.NTP_Client;
import TimeServiceClient_Library.NTP_Client.NTP_Timestamp_Data;
import TimeServiceClient_Library.NTP_Client.NTP_Client_ResultCode;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** 保留参考控制台程序的服务器编号入口，新增主机、测试参数及完整 CSV。 */
public class TimeServiceClient_CONSOLE_uses_library {
    private static final String[] SERVERS = {
        "time.nist.gov", "nist1-ny.ustiming.org", "nist1-nj.ustiming.org",
        "nist1-atl.ustiming.org", "nist1-chi.ustiming.org", "nist1-lnk.binary.net",
        "time-a.timefreq.bldrdoc.gov", "ntp-nist.ldsbc.edu", "nist1-lv.ustiming.org",
        "nist1-la.ustiming.org", "time.windows.com", "wolfnisttime.com"
    };

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 6) {
            System.err.println("Usage: <server-number|host> [count=5] [interval_ms=5000] [port=123] [timeout_ms=500] [csv=ntp_measurements.csv]");
            System.exit(2);
        }
        NTP_Client client = null;
        try {
            String host = args[0];
            if (host.matches("[0-9]+")) {
                int index = Integer.parseInt(host);
                if (index < 1 || index > SERVERS.length) throw new IllegalArgumentException("Server number must be 1..12");
                host = SERVERS[index-1];
            }
            int count = args.length > 1 ? Integer.parseInt(args[1]) : 5;
            int interval = args.length > 2 ? Integer.parseInt(args[2]) : 5000;
            int port = args.length > 3 ? Integer.parseInt(args[3]) : 123;
            int timeout = args.length > 4 ? Integer.parseInt(args[4]) : 500;
            String logPath = args.length > 5 ? args[5] : "ntp_measurements.csv";
            if (count < 1 || interval < 0) throw new IllegalArgumentException("Invalid count or interval");
            client = new NTP_Client(port, timeout);
            boolean opened = client.CreateSocket();
            client.SetUp_TimeService_AddressStruct(host);
            System.out.printf("NTP server: %s:%d; timeout=%d ms; socket=%s%n", host, port, timeout, opened);
            // 不静默吞掉日志错误；调用者可从退出码确认记录是否成功。
            try (PrintWriter log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(logPath), StandardCharsets.UTF_8))) {
                log.println("request,server,status,reason,t1_unix_ms,t2_unix_ms,t3_unix_ms,t4_unix_ms,local_unix_ms,corrected_unix_ms,offset_ms,delay_ms,wait_ms,resolve_ms,sent,discarded_packets,origin_mismatch_packets,last_discard_reason,request_timestamp_hex,transport");
                int sent = 0, received = 0;
                for (int attempt = 1; attempt <= count; attempt++) {
                    NTP_Timestamp_Data r = client.Get_NTP_Timestamp();
                    if (r.bSent) sent++;
                    boolean ok = r.eResultCode == NTP_Client_ResultCode.NTP_Success;
                    if (ok) received++;
                    writeMeasurement(log, attempt, host, r);
                    log.flush();
                    if (log.checkError()) throw new IOException("CSV write failed: " + logPath);
                    System.out.printf("Request %d: %s reason=%s wait=%d ms discarded=%d (%s)%n",
                        attempt, r.eResultCode, r.sReason, r.lWaitMillis, r.iDiscardedPackets, r.sLastDiscardReason);
                    if (ok) {
                        System.out.printf("  T1=%d T2=%d T3=%d T4=%d%n", r.t1, r.t2, r.t3, r.t4);
                        System.out.printf("  offset=%d ms delay=%d ms local=%d corrected=%d (UTC %02d:%02d:%02d)%n",
                            r.lOffsetMillis, r.lDelayMillis, r.lLocalUnixTimeMillis, r.lCorrectedUnixTimeMillis,
                            r.lHour, r.lMinute, r.lSecond);
                    }
                    if (attempt < count) Thread.sleep(interval);
                }
                System.out.printf("Attempts=%d Sent=%d Success=%d CSV=%s%n", count, sent, received, logPath);
            }
        } catch (Exception ex) {
            System.err.println(ex.toString());
            if (client != null) client.CloseSocket();
            System.exit(1);
        } finally { if (client != null) client.CloseSocket(); }
    }

    /** 所有尝试（包括解析失败、发送失败）共用同一记录入口。 */
    public static void writeMeasurement(PrintWriter log, int attempt, String host, NTP_Timestamp_Data r) {
        boolean ok = r.eResultCode == NTP_Client_ResultCode.NTP_Success;
        log.println(csv(attempt, host, r.eResultCode, r.sReason, r.t1, r.t2, r.t3, r.t4,
            ok ? r.lLocalUnixTimeMillis : null, ok ? r.lCorrectedUnixTimeMillis : null,
            ok ? r.lOffsetMillis : null, ok ? r.lDelayMillis : null,
            r.lWaitMillis, r.lResolveMillis, r.bSent, r.iDiscardedPackets, r.iOriginMismatchPackets,
            r.sLastDiscardReason, r.sRequestTimestampHex, r.sTransport));
    }

    private static String csv(Object... fields) {
        StringBuilder line = new StringBuilder();
        for (Object field : fields) {
            if (line.length() > 0) line.append(',');
            String value = field == null ? "" : field.toString();
            line.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        return line.toString();
    }
}
