import TimeServiceClient_Library.NTP_Client;
import TimeServiceClient_Library.NTP_Client.NTP_Timestamp_Data;
import TimeServiceClient_Library.NTP_Client.NTP_Client_ResultCode;
import java.net.*;
import java.nio.file.*;
import java.io.PrintWriter;
import TimeServiceClient_CONSOLE_uses_library.TimeServiceClient_CONSOLE_uses_library;
import java.util.concurrent.*;

/** 使用真实本地 UDP 收发复现迟到包、无效包、总超时及发送失败，无外网依赖。 */
public class NtpRegressionTest {
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static DatagramPacket receive(DatagramSocket socket) throws Exception {
        DatagramPacket p = new DatagramPacket(new byte[512], 512);
        socket.receive(p);
        return p;
    }
    static void timestamp(byte[] b, int offset, long millis) {
        long seconds = millis / 1000 + 2208988800L;
        long fraction = millis % 1000 * 0x100000000L / 1000;
        for (int i=3;i>=0;i--) { b[offset+i]=(byte)seconds; seconds>>>=8; }
        for (int i=3;i>=0;i--) { b[offset+4+i]=(byte)fraction; fraction>>>=8; }
    }
    static byte[] response(DatagramPacket request) {
        byte[] b = new byte[48]; b[0]=0x24; b[1]=2;
        System.arraycopy(request.getData(),40,b,24,8);
        timestamp(b,32,System.currentTimeMillis()+100);
        timestamp(b,40,System.currentTimeMillis()+100);
        return b;
    }
    static void send(DatagramSocket server, byte[] b, DatagramPacket target) throws Exception {
        server.send(new DatagramPacket(b,b.length,target.getAddress(),target.getPort()));
    }
    static void verifyMath(NTP_Timestamp_Data r) {
        require(r.eResultCode == NTP_Client_ResultCode.NTP_Success,"not successful");
        require(r.lOffsetMillis == ((r.t2-r.t1)+(r.t3-r.t4))/2,"offset mismatch");
        require(r.lDelayMillis == (r.t4-r.t1)-(r.t3-r.t2),"delay mismatch");
        require(r.lCorrectedUnixTimeMillis == r.t4+r.lOffsetMillis,"correction mismatch");
    }
    public static void main(String[] args) throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            try (DatagramSocket server = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
                server.setSoTimeout(3000);
                Future<?> peer=executor.submit(() -> {
                    try {
                        DatagramPacket old=receive(server);
                        DatagramPacket current=receive(server);
                        // 收到第二次请求后，才将第一次回包发给同一客户端套接字。
                        send(server,response(old),current);
                        send(server,new byte[8],current);
                        byte[] unsync=response(current); unsync[0]=(byte)0xe4;
                        send(server,unsync,current);
                        send(server,response(current),current);
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                NTP_Client client=new NTP_Client(server.getLocalPort(),150);
                require(client.CreateSocket(),"socket failed");
                client.SetUp_TimeService_AddressStruct("127.0.0.1");
                NTP_Timestamp_Data first=client.Get_NTP_Timestamp();
                require(first.sReason.equals("TIMEOUT"),"first should time out");
                NTP_Timestamp_Data second=client.Get_NTP_Timestamp();
                verifyMath(second);
                require(second.iOriginMismatchPackets==1,"old response not identified");
                require(second.iDiscardedPackets==3,"old/short/unsynchronized packets not rejected");
                require(second.t1>first.t1,"wrong request T1 used");
                peer.get(3,TimeUnit.SECONDS);
                System.out.println("PASS delayed response: previous request timed out; old packet + short packet + unsynchronized packet rejected; current response accepted");
                System.out.printf("RECOMPUTE T1=%d T2=%d T3=%d T4=%d offset=%d delay=%d corrected=%d%n",
                    second.t1,second.t2,second.t3,second.t4,second.lOffsetMillis,second.lDelayMillis,second.lCorrectedUnixTimeMillis);
                client.CloseSocket();
                NTP_Timestamp_Data failed=client.Get_NTP_Timestamp();
                require(failed.eResultCode==NTP_Client_ResultCode.NTP_SendFailed && failed.sReason.startsWith("SEND_FAILED"),"send failure reason lost");
                System.out.println("PASS closed socket: SEND_FAILED with specific exception and elapsed time");
                client.SetUp_TimeService_AddressStruct("[invalid]");
                NTP_Timestamp_Data dns=client.Get_NTP_Timestamp();
                require(dns.eResultCode==NTP_Client_ResultCode.NTP_ServerAddressNotSet && dns.sReason.startsWith("DNS_FAILED"),"stale DNS address retained");
                System.out.println("PASS DNS failure: previous server cleared; specific reason recorded");
                try (PrintWriter log=new PrintWriter("实验1/验证记录/local_regression_rows.csv", "UTF-8")) {
                    log.println("request,server,status,reason,t1_unix_ms,t2_unix_ms,t3_unix_ms,t4_unix_ms,local_unix_ms,corrected_unix_ms,offset_ms,delay_ms,wait_ms,resolve_ms,sent,discarded_packets,origin_mismatch_packets,last_discard_reason,request_timestamp_hex,transport");
                    TimeServiceClient_CONSOLE_uses_library.writeMeasurement(log,1,"127.0.0.1",first);
                    TimeServiceClient_CONSOLE_uses_library.writeMeasurement(log,2,"127.0.0.1",second);
                    TimeServiceClient_CONSOLE_uses_library.writeMeasurement(log,3,"127.0.0.1",failed);
                    TimeServiceClient_CONSOLE_uses_library.writeMeasurement(log,4,"[invalid]",dns);
                }
            }
            try (DatagramSocket server=new DatagramSocket(0,InetAddress.getByName("127.0.0.1"))) {
                server.setSoTimeout(3000);
                Future<?> peer=executor.submit(() -> {
                    try {
                        DatagramPacket p=receive(server);
                        for(int i=0;i<20;i++) { send(server,new byte[8],p); Thread.sleep(15); }
                    } catch(Exception e) { throw new RuntimeException(e); }
                });
                NTP_Client client=new NTP_Client(server.getLocalPort(),150);
                require(client.CreateSocket(),"socket failed"); client.SetUp_TimeService_AddressStruct("127.0.0.1");
                NTP_Timestamp_Data r=client.Get_NTP_Timestamp();
                require(r.sReason.equals("TIMEOUT") && r.iDiscardedPackets>0 && r.lWaitMillis<280,"discarded packets extended deadline");
                require(r.t2==null && r.t3==null && r.t4==null,"failure has fabricated timestamps");
                System.out.printf("PASS total timeout: %d ms; discarded=%d; invalid packet stream does not reset deadline%n",r.lWaitMillis,r.iDiscardedPackets);
                client.CloseSocket(); peer.get(3,TimeUnit.SECONDS);
            }
            System.out.println("ALL REGRESSION TESTS PASSED");
        } finally { executor.shutdownNow(); }
    }
}
