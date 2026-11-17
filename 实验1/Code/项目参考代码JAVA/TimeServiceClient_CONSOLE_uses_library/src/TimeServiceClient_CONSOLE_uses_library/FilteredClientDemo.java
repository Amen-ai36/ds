package TimeServiceClient_CONSOLE_uses_library;

import TimeServiceClient_Library.NTP_Client;
import TimeServiceClient_Library.NtpSampleFilter;
import java.util.OptionalLong;

/** Application-layer adapter; the original NTP core is unchanged. */
public final class FilteredClientDemo {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 5)
            throw new IllegalArgumentException("Usage: FilteredClientDemo <host> [port] [count] [intervalMs] [timeoutMs]");
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 123;
        int count = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        int interval = args.length > 3 ? Integer.parseInt(args[3]) : 5000;
        int timeout = args.length > 4 ? Integer.parseInt(args[4]) : 2000;
        if (count < 1 || interval < 0) throw new IllegalArgumentException("Invalid polling settings");
        NTP_Client client = new NTP_Client(port, timeout);
        NtpSampleFilter filter = new NtpSampleFilter();
        try {
            if (!client.CreateSocket()) throw new IllegalStateException("Socket setup failed");
            client.SetUp_TimeService_AddressStruct(args[0]);
            for (int i = 1; i <= count; i++) {
                NTP_Client.NTP_Timestamp_Data r = client.Get_NTP_Timestamp();
                if (r.eResultCode == NTP_Client.NTP_Client_ResultCode.NTP_Success) {
                    OptionalLong offset = filter.add(r.lOffsetMillis, r.lDelayMillis,
                        System.nanoTime() / 1_000_000L);
                    if (offset.isPresent())
                        System.out.printf("%d raw=%d filtered=%d correctedAtT4=%d%n", i,
                            r.lOffsetMillis, offset.getAsLong(), r.t4 + offset.getAsLong());
                    else System.out.println(i + " WARMING_UP_OR_REJECTED");
                } else System.out.println(i + " " + r.sReason);
                if (i < count) Thread.sleep(interval);
            }
        } finally { client.CloseSocket(); }
    }
}
