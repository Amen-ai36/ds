/*	File			NTP_Client.cpp
	Purpose			Network Time Protocol (NTP) Client header code
	Author			Richard Anthony	(ar26@gre.ac.uk)
	Date			September 2014
*/

#include "RJA_stdafx.h"	// Added for compatibility with Windows 7
#include <afxsock.h>	// MFC socket extensions
#include "NTP_Client.h"

CNTP_Client::CNTP_Client(bool* pbSocketOpenSuccess)
{	
	m_bTimeServiceAddressSet = false;

	// Create a socket, and signal whether successful or not
	*pbSocketOpenSuccess = CreateSocket(&m_TimeService_Socket);
	
	if(true == *pbSocketOpenSuccess)
	{
		// Set Socket to Non-Blocking Mode
		*pbSocketOpenSuccess = SetSocketToNonBlockingMode(&m_TimeService_Socket);
		if(false == *pbSocketOpenSuccess)
		{
			closesocket(m_TimeService_Socket);
		}
	}
}

CNTP_Client::~CNTP_Client(void)
{	
	closesocket(m_TimeService_Socket);
}

bool CNTP_Client::CreateSocket(SOCKET* sock)
{
	*sock = socket(AF_INET, SOCK_DGRAM, PF_UNSPEC);
	if(INVALID_SOCKET == *sock)
	{
		return false;
	}
	return true;
}

bool CNTP_Client::SetSocketToNonBlockingMode(SOCKET* sock)
{
	unsigned long lNonBlocking = 1;
	int iError = ioctlsocket(*sock, FIONBIO, &lNonBlocking);
	if(SOCKET_ERROR == iError)
	{
		return false;
	}
	return true;
}

SOCKADDR_IN CNTP_Client::ResolveURLtoIPaddress(char* cStrURL)
{
    DWORD dwError;
    struct hostent *TimeServiceHost;
 	TimeServiceHost = gethostbyname((const char*)cStrURL);

    if(NULL == TimeServiceHost) 
    {   // gethostbyname failed to resove time service domain name
        dwError = WSAGetLastError();
		m_TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b1 = 0;
		m_TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b2 = 0;
		m_TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b3 = 0;
		m_TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b4 = 0;
		m_bTimeServiceAddressSet = false;
    } 
    else 
    {   // gethostbyname successfully resoved time service domain name
		m_TimeService_SockAddr.sin_addr.S_un.S_addr = *(u_long *) TimeServiceHost->h_addr_list[0]; // Get first address from host's list of addresses
		m_TimeService_SockAddr.sin_family = AF_INET;		// Set the address type 
		m_TimeService_SockAddr.sin_port = htons(NTP_Port);   // Set the NTP port (Decimal 123)
		m_bTimeServiceAddressSet = true;
	}
	return m_TimeService_SockAddr;
}
/**
 * 获取一次 NTP 时间戳（核心流程）。
 * 步骤：检查是否已设置地址 -> 发送请求 -> 接收并解析 -> 返回结果码。
 * @param pNTP_Timestamp 输出：Unix 时间与时分秒（UTC）
 * @return 结果码：NTP_Success / NTP_ServerAddressNotSet / NTP_SendFailed / NTP_ReceiveFailed
 */
unsigned int CNTP_Client::Get_NTP_Timestamp(NTP_Timestamp_Data* pNTP_Timestamp)
{
	pNTP_Timestamp->lHour = 0;
	pNTP_Timestamp->lMinute = 0;
	pNTP_Timestamp->lSecond = 0;
	pNTP_Timestamp->lUnixTime = 0;

	if(true == m_bTimeServiceAddressSet)
	{
		if(true == Send_TimeService_Request())
		{   // Send operation succeeded 
			if(true == Receive(pNTP_Timestamp))
			{		
				return (unsigned int) NTP_Success; // Signal that the NTP_Timestamp has been updated with valid content
			}
			return (unsigned int) NTP_ReceiveFailed; // Signal that the receive operation failed (Time server did not reply)
		}
		return (unsigned int) NTP_SendFailed; // Signal that the send operation failed (Time server was not contacted)
	}
	return (unsigned int) NTP_ServerAddressNotSet; // Signal that Time server address has not been set, cannot get NTP timestamp
}

bool CNTP_Client::Send_TimeService_Request()
{
	memset(SendPacketBuffer, 0, NTP_PACKET_SIZE);	// Zero-out entire 48-byte array
	// Initialize values needed to form NTP request
	SendPacketBuffer[0] = 0xE3;	// 0b11100011;   
								// LI bits 7,6			= 3 (Clock not synchronised), 
								// Version bits 5,4,3	= 4 (The current version of NTP)
								// Mode bits 2,1,0		= 3 (Sent by client)

	m_iSendLen = sizeof(SendPacketBuffer);

	int iBytesSent = sendto(m_TimeService_Socket, (char FAR *)SendPacketBuffer, m_iSendLen, 0,
		(const struct sockaddr FAR *)&m_TimeService_SockAddr, sizeof(m_TimeService_SockAddr));
	if(INVALID_SOCKET == iBytesSent)
	{
		return false;
	}
	return true;
}

bool CNTP_Client::Receive(NTP_Timestamp_Data* pNTP_Timestamp)
{
	Sleep(500);	// Wait for a short time for the time service response.
	// In combination with non-blocking receive this prevents the application freezing if the time service does not respond
	// but waits long enough for the reply RTT so mostly avoids missing an actual reply 
	// and avoids the need for a timer within the NTP_Client class
	// Tested with the following values:
	// 100ms(can work but unreliable) 200ms(generally ok but highly dependent on network RTT) 400ms(generally reliable) 500ms(adds margin of safety)

	// The process inspects its buffer to see if any messages have arrived
	int iBytesRecd = recvfrom(m_TimeService_Socket, (char FAR*)ReceivePacketBuffer, NTP_PACKET_SIZE, 0, NULL, NULL);
	if(SOCKET_ERROR == iBytesRecd)
	{
		return false;
	}
	// Receive succeeded (response received from Time server)
	// The timestamp starts at byte 40 of the received packet and is four bytes,
	unsigned long secsSince1900 = (ReceivePacketBuffer[40] << 24) + (ReceivePacketBuffer[41] << 16) + (ReceivePacketBuffer[42] << 8) + ReceivePacketBuffer[43];  
	const unsigned long seventyYears = 2208988800UL; 		// Unix time starts on Jan 1 1970 (2208988800 seconds)
	pNTP_Timestamp->lUnixTime = secsSince1900 - seventyYears;	// Subtract seventy years:
	pNTP_Timestamp->lHour = (pNTP_Timestamp->lUnixTime  % 86400L) / 3600;
	pNTP_Timestamp->lMinute = (pNTP_Timestamp->lUnixTime  % 3600) / 60;
	pNTP_Timestamp->lSecond = pNTP_Timestamp->lUnixTime  % 60;
	return true;
}


