/*	File			NTP_Client.java
	Purpose			Network Time Protocol (NTP) Client - Library
*/
package TimeServiceClient_Library;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
/**
 * NTP 客户端
 * - 创建并配置 UDP 套接字
 * - 解析时间服务域名 -> 解析为 IP
 * - 发送 NTP 请求并接收响应
 * - 解析时间戳（转换为 Unix 时间与时分秒）
 */
public class NTP_Client
{
    
    private static int NTP_Port = 123;
    private static int NTP_PACKET_SIZE = 48;// NTP time stamp is in the first 48 bytes of the message
    private static long SeventyYears = 2208988800L;  // Number of seconds in 70 years. 
                                                // The raw timestamp is the number of seconds Since 1900
                                                // Unix time starts on Jan 1 1970 (70 years = 2208988800 seconds)
    DatagramSocket m_TimeService_Socket;
    InetAddress m_TimeService_IPAddress;
    Boolean m_bNTP_Client_Started;
    
    public enum NTP_Client_ResultCode {NTP_Success, NTP_ServerAddressNotSet, NTP_SendFailed, NTP_ReceiveFailed};

    public final class NTP_Timestamp_Data
    {		
        public NTP_Client_ResultCode eResultCode;
        public long lUnixTime;	// Seconds since 1970 (secsSince1900 - seventyYears)
        public long lHour;
        public long lMinute;
        public long lSecond;
        public long lOffsetMillis;
        public long lDelayMillis;
        public long lLocalUnixTimeMillis;
        public long lCorrectedUnixTimeMillis;

        NTP_Timestamp_Data()
        {
            eResultCode = NTP_Client_ResultCode.NTP_ServerAddressNotSet; // Use as default for construction (gets overwritten)
            lHour = 0;
            lMinute = 0;
            lSecond = 0;
            lUnixTime = 0;
            lOffsetMillis = 0;
            lDelayMillis = 0;
            lLocalUnixTimeMillis = 0;
            lCorrectedUnixTimeMillis = 0;
        }
    };

    private Boolean m_bTimeServiceAddressSet;

    public NTP_Client()
    {	
        m_bTimeServiceAddressSet = false;
        m_bNTP_Client_Started = false;
    }

    public Boolean CreateSocket()
    {
        try
        {
            m_TimeService_Socket = new DatagramSocket();
            m_TimeService_Socket.setSoTimeout(500); // Timeout = 500ms (i.e. non blocking IO behaviour)
            // The timeout period was chosen so as to prevent the application freezing if the time service does not respond
	    // but that it waits long enough for the reply RTT , so that it mostly avoids missing an actual reply 
            // Tested with the following values: 100ms(can work but unreliable) 
            // 200ms(generally ok but highly dependent on network RTT) 400ms(generally reliable) 500ms(adds margin of safety)
        }
        catch (SocketException Ex)
        {
            return false;	
        }
        return true;
    }

    public InetAddress SetUp_TimeService_AddressStruct(String sURL)
    {         
        String sFullURL = "http://" +sURL;
        try
        {
            m_TimeService_IPAddress = InetAddress.getByName(new URL(sFullURL).getHost());
            m_bTimeServiceAddressSet = true;
        }
        catch (Exception Ex)
        {
            return null;
        }
        return m_TimeService_IPAddress;
    }
    
    public int GetPort()
    {
        return NTP_Port;
    }
    /**
    * 获取一次 NTP 时间戳。
    * 流程：检查地址 -> 发送请求 -> 接收并解析 -> 设置结果码。
    * @return 携带结果码与时间数据的 NTP_Timestamp_Data
    */       
    public NTP_Timestamp_Data Get_NTP_Timestamp()
    {
        NTP_Timestamp_Data NTP_Timestamp = new NTP_Timestamp_Data();
        if(true == m_bTimeServiceAddressSet)
        {
            if(true == Send_TimeService_Request())
            {   // Send operation succeeded 
            NTP_Timestamp = Receive(NTP_Timestamp);
                if(0 != NTP_Timestamp.lUnixTime)
                {
                    NTP_Timestamp.eResultCode = NTP_Client_ResultCode.NTP_Success; // Signal that the NTP_Timestamp has been updated with valid content
                    return NTP_Timestamp;
                }
                NTP_Timestamp.eResultCode = NTP_Client_ResultCode.NTP_ReceiveFailed; // Signal that the receive operation failed (Time server did not reply)
                return NTP_Timestamp;
            }
            NTP_Timestamp.eResultCode = NTP_Client_ResultCode.NTP_SendFailed; // Signal that the send operation failed (Time server was not contacted)
            return NTP_Timestamp;
        }
        NTP_Timestamp.eResultCode = NTP_Client_ResultCode.NTP_ServerAddressNotSet; // Signal that Time server address has not been set, cannot get NTP timestamp
        return NTP_Timestamp;
    }
    /**
    * 发送 NTP 请求报文（客户端模式）
    * 报文前 1 字节包含 LI/Version/Mode：
    * @return true 表示发送成功；false 表示失败
    */
    Boolean Send_TimeService_Request()
    {
        byte[] bSendBuf = new byte [NTP_PACKET_SIZE]; // Zero-out entire 48-byte buffer to hold incoming packets (UTC time value)
        // Initialize values needed to form NTP request
        bSendBuf[0] = (byte) 0x23;// LI=0, version=4, client mode=3
        long t1 = System.currentTimeMillis();
        putTimestamp(bSendBuf, 40, t1);
        // LI bits 7,6		= 3 (Clock not synchronised), 
        // Version bits 5,4,3	= 4 (The current version of NTP)
        // Mode bits 2,1,0	= 3 (Sent by client)

        try
        {
            DatagramPacket SendPacket = new DatagramPacket(bSendBuf, bSendBuf.length,
                            m_TimeService_IPAddress /*The address to send to*/, NTP_Port); 
            m_TimeService_Socket.send(SendPacket);
        }
        catch(SocketTimeoutException Ex)
        {
             return false;
        }
        catch(Exception Ex)
        {
            System.out.printf("Send failed: %s\n", Ex.toString());
            return false;
        }
        return true;
    }
    /**
    * 接收并解析 NTP 响应，将 1900 纪元时间转换为 Unix 时间（1970 起）。
    * 解析规则：时间戳起始于字节偏移 40，长度 4 字节（秒）。
    * @param NTP_Timestamp 输出对象
    * @return 传入的 NTP_Timestamp（已填充），若失败则 lUnixTime=0
    */
    private NTP_Timestamp_Data Receive(NTP_Timestamp_Data NTP_Timestamp)
    {
        byte[] bRecvBuf = new byte [NTP_PACKET_SIZE]; // buffer to hold incoming packets (UTC time value)
        DatagramPacket RecvPacket = new DatagramPacket(bRecvBuf, NTP_PACKET_SIZE);
        try
        {
            m_TimeService_Socket.receive(RecvPacket);
        }
        catch(Exception ex)
        {
            NTP_Timestamp.lUnixTime = 0; // Signal that an error occurred
            return NTP_Timestamp;
        }

        if (RecvPacket.getLength() >= NTP_PACKET_SIZE)
        {   // The timestamp starts at byte 40 of the received packet and is four bytes,
            long t4 = System.currentTimeMillis();
            long t1 = readTimestampMillis(bRecvBuf, 24);
            long t2 = readTimestampMillis(bRecvBuf, 32);
            long t3 = readTimestampMillis(bRecvBuf, 40);
            NTP_Timestamp.lLocalUnixTimeMillis = t4;
            NTP_Timestamp.lOffsetMillis = ((t2 - t1) + (t3 - t4)) / 2;
            NTP_Timestamp.lDelayMillis = (t4 - t1) - (t3 - t2);
            NTP_Timestamp.lCorrectedUnixTimeMillis = t4 + NTP_Timestamp.lOffsetMillis;
            NTP_Timestamp.lUnixTime = NTP_Timestamp.lCorrectedUnixTimeMillis / 1000L;
            NTP_Timestamp.lHour = (long) (NTP_Timestamp.lUnixTime  % 86400L) / 3600;
            NTP_Timestamp.lMinute = (long) (NTP_Timestamp.lUnixTime % 3600) / 60;
            NTP_Timestamp.lSecond = (long) NTP_Timestamp.lUnixTime % 60;
        }
        else
        {
            NTP_Timestamp.lUnixTime = 0; // Signal that an error occurred
        }
        return NTP_Timestamp;
    }

    private static void putTimestamp(byte[] b, int offset, long unixMillis) {
        long seconds = unixMillis / 1000L + SeventyYears;
        long fraction = ((unixMillis % 1000L) * 0x100000000L) / 1000L;
        for (int i = 3; i >= 0; i--) { b[offset + i] = (byte)(seconds & 0xff); seconds >>>= 8; }
        for (int i = 3; i >= 0; i--) { b[offset + 4 + i] = (byte)(fraction & 0xff); fraction >>>= 8; }
    }

    private static long readTimestampMillis(byte[] b, int offset) {
        long seconds = 0, fraction = 0;
        for (int i = 0; i < 4; i++) seconds = (seconds << 8) | (b[offset+i] & 0xffL);
        for (int i = 0; i < 4; i++) fraction = (fraction << 8) | (b[offset+4+i] & 0xffL);
        return (seconds - SeventyYears) * 1000L + (fraction * 1000L) / 0x100000000L;
    }

    public Boolean Get_ClientStarted_Flag()
    {
        return m_bNTP_Client_Started;
    }

    public void Set_ClientStarted_Flag(Boolean bClient_Started)
    {
        m_bNTP_Client_Started = bClient_Started;
    }

    public void CloseSocket()
    {
        try
        {
            m_TimeService_Socket.close();
        }
        catch (Exception Ex)
        {   // Generic approach to dealing with situations such as socket not created
        }
    }
}
