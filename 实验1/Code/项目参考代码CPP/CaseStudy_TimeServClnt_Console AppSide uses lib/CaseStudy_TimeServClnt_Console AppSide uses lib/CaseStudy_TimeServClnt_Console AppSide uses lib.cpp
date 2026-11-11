/*	File			CaseStudy_TimeServClnt_Console AppSide uses lib.cpp
	Purpose			Network Time Protocol (NTP) Client console application
	Author			Richard Anthony	(ar26@gre.ac.uk)
	Date			September 2014
*/
/*
 * 控制台版 NTP 客户端
 * 解析命令行 -> 初始化 Winsock 与客户端库 -> 解析域名到 IP -> 多次请求时间戳 -> 打印结果并退出
 */
#include "stdafx.h"
#include <iostream>
#include <string.h>
#include "CaseStudy_TimeServClnt_Console AppSide uses lib.h"
using namespace std;

void _tmain(int argc, _TCHAR* argv[])
{
	cout << "Time Service Client (Console Application - uses library)" << endl;
    cout << "--------------------------------------------------------" << endl << endl;
	
	char szDomain[100];
	strcpy_s(szDomain,Parse_CommandLineArguments(argc, (const char**) argv));

	InitialiseWindowsSocketsMechanism();

	InitialiseLibrary();

	cout << "Time Service Domain: " << szDomain << endl;
	SOCKADDR_IN TimeService_SockAddr; 
	TimeService_SockAddr = ResolveTimeServiceDomainNameTOIPAddress((char*) szDomain);
	char szIPaddress[16];
	char szIPAddressDigit[4];
	_itoa_s(TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b1, szIPAddressDigit, 10);
	strcpy_s(szIPaddress,szIPAddressDigit);
	strcat_s(szIPaddress,".");
	_itoa_s(TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b2, szIPAddressDigit, 10);
	strcat_s(szIPaddress,szIPAddressDigit);
	strcat_s(szIPaddress,".");
	_itoa_s(TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b3, szIPAddressDigit, 10);
	strcat_s(szIPaddress,szIPAddressDigit);
	strcat_s(szIPaddress,".");
	_itoa_s(TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b4, szIPAddressDigit, 10);
	strcat_s(szIPaddress,szIPAddressDigit);
	cout << "Time Service IP address (resolved by the library): " << szIPaddress << endl << endl;
	
	NTP_Timestamp_Data m_NTP_Timestamp;
	char szUnixTime[16];
	char szHour[4];
	char szMinute[4];
	char szSecond[4];	
	cout << "Ask Time Service for timestamp 5 times, at 5-second intervals" << endl << endl;
	for(int iLoop = 0; iLoop < 5; iLoop++)
	{
		int iTimeServiceResult = m_pNTP_Client->Get_NTP_Timestamp(&m_NTP_Timestamp);
		// iTimeServiceResult codes:   NTP_Success = 0, NTP_ServerAddressNotSet = 1, NTP_SendFailed = 2, NTP_ReceiveFailed = 3
		switch(iTimeServiceResult)	{
			case 0: // NTP_Success		
				_itoa_s(m_NTP_Timestamp.lUnixTime, szUnixTime, 10);
				_itoa_s(m_NTP_Timestamp.lHour, szHour, 10);
				_itoa_s(m_NTP_Timestamp.lMinute, szMinute, 10);
				_itoa_s(m_NTP_Timestamp.lSecond, szSecond, 10);
				cout << "Unix Time (seconds sine 1970):" << szUnixTime << endl;
				cout << "Current time   Hour:" << szHour << "  Minute:" << szMinute << "  Second:" << szSecond << endl << endl;
				// NTP does not indicate information specifcally related to local time zones such as BST or other daylight savings time offsets
				break;
			case 1: // NTP_ServerAddressNotSet	
				cout << "!!! Time Service Client Library not initialised with Time server Address !!!" << endl << endl;
				break;
			case 2: // NTP_SendFailed		
				cout << "!!! NTP Send Failed in Time Service Client Library !!!" << endl << endl;
				break;
			case 3: // NTP_ReceiveFailed		
				cout << "!!! NTP Receive Failed in Time Service Client Library !!!" << endl << endl;
				break;
		}	
		Sleep(5000); // 5 second delay between loop iterations
	}
	Quit(0);
}

char* Parse_CommandLineArguments(int argc, const char* argv[])
{
	if(2 != argc)    // argv[0] is Application exe name argv[1] is a single option parameter
	{   // User did not provide the required option parameter
		DisplayUsage((char*) argv[0]);
		cout << "!!! Command Line Arguments invalid !!!   QUITTING" << endl << endl;
		Quit(-1);
	}
	
	int iOption = atoi(argv[1]);  // Resolves to 0 if characters preseent, therefore options start at 1
	if(1 > iOption || 12 < iOption)
	{   // Option value was out of range
		DisplayUsage((char*) argv[0]);
		cout << "!!! Command Line Arguments invalid !!!   QUITTING" << endl << endl;
		Quit(-2);
	}
	
	switch(iOption) {
		case 1: 
			return "time.nist.gov";
		case 2: 
			return "nist1-ny.ustiming.org";
		case 3: 
			return "nist1-nj.ustiming.org";
		case 4: 
			return "nist1-atl.ustiming.org";
		case 5: 
			return "nist1-chi.ustiming.org";
		case 6: 
			return "nist1-lnk.binary.net";
		case 7: 
			return "time-a.timefreq.bldrdoc.gov";
		case 8: 
			return "ntp-nist.ldsbc.edu";
		case 9: 
			return "nist1-lv.ustiming.org";
		case 10: 
			return "nist1-la.ustiming.org";
		case 11: 
			return "time.windows.com";
		case 12: 
			return "wolfnisttime.com";	
	}
	return "time.nist.gov";
}

void InitialiseWindowsSocketsMechanism(void)
{
	WSADATA wsaData;
	int iResult = WSAStartup( MAKEWORD(2,2), &wsaData );  // Initialize Winsock
	if ( iResult != NO_ERROR )
	{
		cout << "Error at WSAStartup()" << endl;
		Quit(-3);
	}
}

void InitialiseLibrary(void)
{
	bool bLibraryInitialisationSuccess;
	m_pNTP_Client = new CNTP_Client(&bLibraryInitialisationSuccess);
	if(false == bLibraryInitialisationSuccess)
	{   // Library not initialised - could not open socket, or could not set socket to non-blocking mode
		delete m_pNTP_Client;
		cout << "!!! Failed to initialise Time Service Client library !!!   QUITTING" << endl << endl;
		Quit(-4);
	}
}

SOCKADDR_IN ResolveTimeServiceDomainNameTOIPAddress(char* szDomain)
{
	SOCKADDR_IN	SockAddr = m_pNTP_Client->ResolveURLtoIPaddress((char*) szDomain);
	if( 0 == SockAddr.sin_addr.S_un.S_un_b.s_b1 && 0 == SockAddr.sin_addr.S_un.S_un_b.s_b2 &&
		0 == SockAddr.sin_addr.S_un.S_un_b.s_b3 && 0 == SockAddr.sin_addr.S_un.S_un_b.s_b4)
	{
		cout << "!!! Time Service IP address could not be resolved by the library !!!   QUITTING" << endl << endl;
		Quit(-5);
	}
	return SockAddr;
}

void DisplayUsage(char* szProgramName)
{
	cout << "Usage " << szProgramName << " Time-Service-Domain-Name-Option (see below)" << endl;	
	cout << "Select from the following Time Service Domain Names (enter the numerical index on the command line):" << endl;	
	cout << " 1  " << "time.nist.gov" << "(Location: NIST round robin load equalisation" << endl;
	cout << " 2  " << "nist1-ny.ustiming.org" << "(Location: New York City, NY" << endl;	
	cout << " 3  " << "nist1-nj.ustiming.org" << "(Location: Bridgewater, NJ" << endl;	
	cout << " 4  " << "nist1-atl.ustiming.org" << "(Location: Atlanta, Georgia" << endl;	
	cout << " 5  " << "nist1-chi.ustiming.org" << "(Location: Chicago, Illinois" << endl;	
	cout << " 6  " << "nist1-lnk.binary.net" << "(Location: Lincoln, Nebraska" << endl;	
	cout << " 7  " << "time-a.timefreq.bldrdoc.gov" << "(Location: NIST, Boulder, Colorado" << endl;	
	cout << " 8  " << "ntp-nist.ldsbc.edu" << "(Location: LDSBC, Salt Lake City, Utah" << endl;	
	cout << " 9  " << "nist1-lv.ustiming.org" << "(Location: Las Vegas, Nevada" << endl;	  
	cout << "10  " << "nist1-la.ustiming.org" << "(Location: Los Angeles, California" << endl;	
	cout << "11  " << "time.windows.com" << "(Location: Windows Time service" << endl;	
	cout << "12  " << "wolfnisttime.com" << "(Location: Birmingham, Alabama" << endl;	
}

void Quit(int iExitCode)
{
	cout << "Press any key followed by 'enter' to quit" << endl;
	char c;
	cin >> c;
	exit(iExitCode);
}