
// CaseStudy_TimeServiceClient_GUI.h : main header file for the PROJECT_NAME application
//

#pragma once

#ifndef __AFXWIN_H__
	#error "include 'stdafx.h' before including this file for PCH"
#endif

#include "resource.h"		// main symbols


// CCaseStudy_TimeServiceClient_GUIApp:
// See CaseStudy_TimeServiceClient_GUI.cpp for the implementation of this class
//

class CCaseStudy_TimeServiceClient_GUIApp : public CWinApp
{
public:
	CCaseStudy_TimeServiceClient_GUIApp();

// Overrides
public:
	virtual BOOL InitInstance();

// Implementation

	DECLARE_MESSAGE_MAP()
};

extern CCaseStudy_TimeServiceClient_GUIApp theApp;