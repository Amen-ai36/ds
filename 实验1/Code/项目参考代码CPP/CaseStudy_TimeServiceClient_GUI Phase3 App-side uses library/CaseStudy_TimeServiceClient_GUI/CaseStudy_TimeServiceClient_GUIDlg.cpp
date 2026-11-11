
/*	File			CaseStudy_TimeServiceClient_GUIDlg - Refactored.cpp
	Purpose			Network Time Protocol (NTP) Client header code
	Author			Richard Anthony	(ar26@gre.ac.uk)
	Date			September 2014
*/

#include "stdafx.h"
#include "CaseStudy_TimeServiceClient_GUI.h"
#include "CaseStudy_TimeServiceClient_GUIDlg.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#endif

CCaseStudy_TimeServiceClient_GUIDlg::CCaseStudy_TimeServiceClient_GUIDlg(CWnd* pParent /*=NULL*/)
	: CDialog(CCaseStudy_TimeServiceClient_GUIDlg::IDD, pParent)
{
	bool bSocketOpenSuccess;
	m_pNTP_Client = new CNTP_Client(&bSocketOpenSuccess);
	if(false == bSocketOpenSuccess)
	{   // Could not open socket, or could not set socket to non-blocking mode
		delete m_pNTP_Client;
		exit(-1);
	}
	
	m_bNTP_Client_Started = false;
	m_iNumRequestsSent = 0;
	m_iNumResponsesReceived = 0;	
}

CCaseStudy_TimeServiceClient_GUIDlg::~CCaseStudy_TimeServiceClient_GUIDlg()
{
	delete m_pNTP_Client;
}

void CCaseStudy_TimeServiceClient_GUIDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	
	CString csStr;
	m_pUTC_Time = (CEdit*) GetDlgItem(IDC_UTC_Time_EDIT);
	m_pNTP_StartButton = (CButton*) GetDlgItem(IDC_NTP_Start_BUTTON);
	m_pNTP_URL_List = (CListBox*) GetDlgItem(IDC_NTP_SERVER_URL_LIST);
	m_pNTP_Location_List = (CListBox*) GetDlgItem(IDC_NTP_SERVER_Location_LIST);
	m_pNTP_Location_List->EnableWindow(false);
	Populate_NTP_Server_List();
	m_pNTP_Address_1 = (CEdit*) GetDlgItem(IDC_NTP_ADD_1);
	m_pNTP_Address_2 = (CEdit*) GetDlgItem(IDC_NTP_ADD_2);
	m_pNTP_Address_3 = (CEdit*) GetDlgItem(IDC_NTP_ADD_3);
	m_pNTP_Address_4 = (CEdit*) GetDlgItem(IDC_NTP_ADD_4);
	m_pNTP_PORT = (CEdit*) GetDlgItem(IDC_NTP_PORT);
	m_pSelectedURL = (CEdit*) GetDlgItem(IDC_Selected_URL_EDIT);
	m_pSecondsSince1900 = (CEdit*) GetDlgItem(IDC_SecondsSince1900_EDIT);
	m_pNumNTPRequestsSent = (CEdit*) GetDlgItem(IDC_NUM_NTP_REQUESTS_EDIT);
	m_pNumNTPResponsesReceived = (CEdit*) GetDlgItem(IDC_NUM_NTP_RESPONSES_EDIT);
	m_pNTP_PORT->EnableWindow(false);
	m_pSelectedURL->EnableWindow(false);
	m_pNumNTPRequestsSent->EnableWindow(false);
	m_pNumNTPResponsesReceived->EnableWindow(false);
	m_pNTP_URL_List->SetCurSel(0);		// Select first entry in the list
	OnLbnSelchangeNtpServerUrlList();	// Highlight selection across both list boxes, set the server address structure contents and populate address edit boxes
}

BEGIN_MESSAGE_MAP(CCaseStudy_TimeServiceClient_GUIDlg, CDialog)
	ON_WM_TIMER()
	ON_BN_CLICKED(IDC_NTP_Start_BUTTON, &CCaseStudy_TimeServiceClient_GUIDlg::OnBnClickedNtpStartButton)
	ON_LBN_SELCHANGE(IDC_NTP_SERVER_URL_LIST, &CCaseStudy_TimeServiceClient_GUIDlg::OnLbnSelchangeNtpServerUrlList)
	ON_BN_CLICKED(IDCANCEL, &CCaseStudy_TimeServiceClient_GUIDlg::OnBnClickedCancel)
END_MESSAGE_MAP()

void CCaseStudy_TimeServiceClient_GUIDlg::OnBnClickedNtpStartButton()
{
	if(false == m_bNTP_Client_Started)
	{
		m_pNTP_StartButton->SetWindowText("STOP NTP requests");
		m_bNTP_Client_Started = true;
		m_pNTP_Address_1->EnableWindow(false);
		m_pNTP_Address_2->EnableWindow(false);
		m_pNTP_Address_3->EnableWindow(false);
		m_pNTP_Address_4->EnableWindow(false);
		m_pNTP_URL_List->EnableWindow(false);	
		m_iNumRequestsSent = 0;
		m_iNumResponsesReceived = 0;
		UpdateStatisticsDisplay();
		StartPeriodic_NTP_requests();
	}
	else
	{
		m_pNTP_StartButton->SetWindowText("START NTP requests");
		m_bNTP_Client_Started = false;
		m_pNTP_Address_1->EnableWindow(true);
		m_pNTP_Address_2->EnableWindow(true);
		m_pNTP_Address_3->EnableWindow(true);
		m_pNTP_Address_4->EnableWindow(true);
		m_pNTP_URL_List->EnableWindow(true);
		StopPeriodic_NTP_requests();
	}
}

void CCaseStudy_TimeServiceClient_GUIDlg::StartPeriodic_NTP_requests()
{
	m_pNTP_Client->Get_NTP_Timestamp(&m_NTP_Timestamp);
	InitiateTimer();				// Subsequent NTP requests are made every 5 seconds
}

void CCaseStudy_TimeServiceClient_GUIDlg::StopPeriodic_NTP_requests()
{
	StopTimer();
}

void CCaseStudy_TimeServiceClient_GUIDlg::InitiateTimer()
{	
	// Do not make NTP requests faster than once every 4 seconds or NIST will treat it as a DoS attack
	// This really happens (for example try sending at 200ms intervals :- refused after 22 requests)
	m_nTimer = SetTimer(1, 5000, 0);    // 5 second period for send NTP requests
}

void CCaseStudy_TimeServiceClient_GUIDlg::StopTimer()
{
	KillTimer(m_nTimer);
}
/**
 * 定时器回调：执行一次 NTP 请求，按结果更新界面与统计。
 * 结果码：0 成功；1 未设置地址；2 发送失败；3 接收失败。
 */
void CCaseStudy_TimeServiceClient_GUIDlg::OnTimer(UINT nIDEvent) 
{	
	int iResult = m_pNTP_Client->Get_NTP_Timestamp(&m_NTP_Timestamp);
	CString csStrUTC_Time;
	
	// iResult codes:   NTP_Success = 0, NTP_ServerAddressNotSet = 1, NTP_SendFailed = 2, NTP_ReceiveFailed = 3
	switch(iResult)	{
		case 0: // NTP_Success		
			m_iNumRequestsSent++;
			m_iNumResponsesReceived++;
			csStrUTC_Time.Format("%d", m_NTP_Timestamp.lUnixTime);
			m_pSecondsSince1900->SetWindowTextA(csStrUTC_Time.GetString());
			csStrUTC_Time.Format("%02d:%02d:%02d", m_NTP_Timestamp.lHour, m_NTP_Timestamp.lMinute, m_NTP_Timestamp.lSecond);
			m_pUTC_Time->SetWindowTextA(csStrUTC_Time.GetString());
			// NTP does not indicate information specifcally related to local time zones such as BST or other daylight savings time offsets
			UpdateStatisticsDisplay();
			break;
		case 1: // NTP_ServerAddressNotSet	
			break;
		case 2: // NTP_SendFailed		
			break;
		case 3: // NTP_ReceiveFailed		
			m_iNumRequestsSent++;
			UpdateStatisticsDisplay();
			break;
	}	
}

void CCaseStudy_TimeServiceClient_GUIDlg::Populate_NTP_Server_List()
{
	m_pNTP_URL_List->InsertString(0,"nist1-ny.ustiming.org");
	m_pNTP_Location_List->InsertString(0,"New York City, NY");

	m_pNTP_URL_List->InsertString(0,"nist1-nj.ustiming.org");
	m_pNTP_Location_List->InsertString(0,"Bridgewater, NJ");

	m_pNTP_URL_List->InsertString(0,"nist1-atl.ustiming.org");
	m_pNTP_Location_List->InsertString(0,"Atlanta, Georgia");

	m_pNTP_URL_List->InsertString(0,"wolfnisttime.com");
	m_pNTP_Location_List->InsertString(0,"Birmingham, Alabama");

	m_pNTP_URL_List->InsertString(0,"nist1-chi.ustiming.org");
	m_pNTP_Location_List->InsertString(0,"Chicago, Illinois");

	m_pNTP_URL_List->InsertString(0,"nist1-lnk.binary.net");
	m_pNTP_Location_List->InsertString(0,"Lincoln, Nebraska");

	m_pNTP_URL_List->InsertString(0,"time-a.timefreq.bldrdoc.gov");
	m_pNTP_Location_List->InsertString(0,"NIST, Boulder, Colorado");

	m_pNTP_URL_List->InsertString(0,"ntp-nist.ldsbc.edu");
	m_pNTP_Location_List->InsertString(0,"LDSBC, Salt Lake City, Utah");
	
	m_pNTP_URL_List->InsertString(0,"nist1-lv.ustiming.org");
	m_pNTP_Location_List->InsertString(0,"Las Vegas, Nevada");  

	m_pNTP_URL_List->InsertString(0,"nist1-la.ustiming.org");
	m_pNTP_Location_List->InsertString(0,"Los Angeles, California");

	m_pNTP_URL_List->InsertString(0,"time.nist.gov");
	m_pNTP_Location_List->InsertString(0,"NIST round robin load equalisation");

	m_pNTP_URL_List->InsertString(0,"time.windows.com");
	m_pNTP_Location_List->InsertString(0,"Windows Time service");
}

void CCaseStudy_TimeServiceClient_GUIDlg::OnLbnSelchangeNtpServerUrlList()
{
	int iSelectionIndex = m_pNTP_URL_List->GetCurSel();
	CString cStrURL;
	m_pNTP_Location_List->SetCurSel(iSelectionIndex);
	m_pNTP_URL_List->GetText(iSelectionIndex,cStrURL);
	m_pSelectedURL->SetWindowTextA(cStrURL.GetString());
	
	SOCKADDR_IN TimeService_SockAddr = m_pNTP_Client->ResolveURLtoIPaddress((char*) cStrURL.GetString());
	
	CString csIPAddressByte;
	csIPAddressByte.Format("%d",TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b1);
	m_pNTP_Address_1->SetWindowText(csIPAddressByte.GetString());
	csIPAddressByte.Format("%d",TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b2);
	m_pNTP_Address_2->SetWindowText(csIPAddressByte.GetString());
	csIPAddressByte.Format("%d",TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b3);
	m_pNTP_Address_3->SetWindowText(csIPAddressByte.GetString());
	csIPAddressByte.Format("%d",TimeService_SockAddr.sin_addr.S_un.S_un_b.s_b4);
	m_pNTP_Address_4->SetWindowText(csIPAddressByte.GetString());

	CString csPort;
	csPort.Format("%d",TimeService_SockAddr.sin_port);
	m_pNTP_PORT->SetWindowText(csPort.GetString());
}

void CCaseStudy_TimeServiceClient_GUIDlg::UpdateStatisticsDisplay()
{
	CString csStr;
	csStr.Format("%d",m_iNumRequestsSent);
	m_pNumNTPRequestsSent->SetWindowTextA(csStr.GetString());
	csStr.Format("%d",m_iNumResponsesReceived);
	m_pNumNTPResponsesReceived->SetWindowTextA(csStr.GetString());
}

void CCaseStudy_TimeServiceClient_GUIDlg::OnBnClickedCancel()
{
	CDialog::OnCancel();
}
