/*	File			CaseStudy_TimeServClnt_Console AppSide uses lib.h
	Purpose			Network Time Protocol (NTP) Client console application
	Author			Richard Anthony	(ar26@gre.ac.uk)
	Date			September 2014
*/

#pragma once

#include <windows.h>
#include "..\..\CaseStudy_TimeServiceClient Phase3 Library\CaseStudy_TimeServiceClient Phase3 Library\NTP_Client.h"

char* Parse_CommandLineArguments(int argc, const char* argv[]);
void InitialiseWindowsSocketsMechanism(void);
void InitialiseLibrary(void);
SOCKADDR_IN ResolveTimeServiceDomainNameTOIPAddress(char* szDomain);
void DisplayUsage(char* szProgramName);
void Quit(int iExitCode);

CNTP_Client* m_pNTP_Client;
