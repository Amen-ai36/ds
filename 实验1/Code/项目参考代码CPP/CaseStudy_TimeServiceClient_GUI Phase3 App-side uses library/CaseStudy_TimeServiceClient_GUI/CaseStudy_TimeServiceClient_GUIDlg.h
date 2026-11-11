/*	File			CaseStudy_TimeServiceClient_GUIDlg - Refactored.h
	Purpose			Network Time Protocol (NTP) Client header code
	Author			Richard Anthony	(ar26@gre.ac.uk)
	Date			September 2014
*/

#pragma once
#include "..\..\CaseStudy_TimeServiceClient Phase3 Library\CaseStudy_TimeServiceClient Phase3 Library\NTP_Client.h"

class CCaseStudy_TimeServiceClient_GUIDlg : public CDialog
{
public:
	CCaseStudy_TimeServiceClient_GUIDlg(CWnd* pParent = NULL);
	virtual ~CCaseStudy_TimeServiceClient_GUIDlg();
	IN_ADDR GetConnectAddress();
	void InitiateTimer();
	void StopTimer();
	void OnTimer(UINT nIDEvent); 
	void StartPeriodic_NTP_requests();
	void StopPeriodic_NTP_requests();
	void Populate_NTP_Server_List();
	void UpdateStatisticsDisplay();

	enum { IDD = IDD_CASESTUDY_TIMESERVICECLIENT_GUI_DIALOG };

	CEdit* m_pNTP_Address_1;
	CEdit* m_pNTP_Address_2;
	CEdit* m_pNTP_Address_3;
	CEdit* m_pNTP_Address_4;
	CEdit* m_pNTP_PORT;
	CEdit* m_pSelectedURL;
	CEdit* m_pUTC_Time;
	CEdit* m_pSecondsSince1900;
	CEdit* m_pNumNTPRequestsSent;
	CEdit* m_pNumNTPResponsesReceived;
	CButton* m_pNTP_StartButton;
	CListBox* m_pNTP_URL_List;
	CListBox* m_pNTP_Location_List;

private:
	CNTP_Client* m_pNTP_Client;
	NTP_Timestamp_Data m_NTP_Timestamp;
	
	UINT_PTR m_nTimer;
	int m_iDelay;
	bool m_bNTP_Client_Started;
	int m_iNumRequestsSent;
	int m_iNumResponsesReceived;

protected:
	virtual void DoDataExchange(CDataExchange* pDX);

	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnBnClickedNtpStartButton();
	afx_msg void OnLbnSelchangeNtpServerUrlList();
	afx_msg void OnBnClickedCancel();
};

