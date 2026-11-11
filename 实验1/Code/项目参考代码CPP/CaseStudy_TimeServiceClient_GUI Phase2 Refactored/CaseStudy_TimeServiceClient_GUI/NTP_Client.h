/*	File			NTP_Client.h
	Purpose			Network Time Protocol (NTP) Client header code
	Author			Richard Anthony	(ar26@gre.ac.uk)
	Date			September 2014
*/

#pragma once
#define NTP_Port 123
#define NTP_PACKET_SIZE 48		// NTP time stamp is in the first 48 bytes of the message

struct NTP_Timestamp_Data {		
	unsigned long lUnixTime;	// Seconds since 1970 (secsSince1900 - seventyYears)
	unsigned long lHour;
	unsigned long lMinute;
	unsigned long lSecond;
	};

enum NTP_Client_ResultCode { NTP_Success = 0, NTP_ServerAddressNotSet = 1, NTP_SendFailed = 2, NTP_ReceiveFailed = 3};

class CNTP_Client
{
	byte SendPacketBuffer[NTP_PACKET_SIZE];		//buffer to hold outgoing packets (NTP service requests)
	byte ReceivePacketBuffer[NTP_PACKET_SIZE];	//buffer to hold incoming packets (UTC time value)

public:
	CNTP_Client(bool* pbSocketOpenSuccess);
	~CNTP_Client(void);
	SOCKADDR_IN ResolveURLtoIPaddress(CString cStrURL);
	unsigned int Get_NTP_Timestamp(NTP_Timestamp_Data* pNTP_Timestamp);

private:
	bool CreateSocket(SOCKET* sock);
	bool SetSocketToNonBlockingMode(SOCKET* sock);
	bool Send_TimeService_Request();
	bool Receive(NTP_Timestamp_Data* pNTP_Timestamp);

	bool m_bTimeServiceAddressSet;
	SOCKET m_TimeService_Socket;
	SOCKADDR_IN m_TimeService_SockAddr;
	int m_iSendLen;
	int m_iReceiveLen;
	NTP_Timestamp_Data m_NTP_Timestamp;
};

