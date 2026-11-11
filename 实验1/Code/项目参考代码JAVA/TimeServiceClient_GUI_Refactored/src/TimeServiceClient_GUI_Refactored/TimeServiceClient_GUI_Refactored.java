/*	File			TimeServiceClient_GUI_Refactored.java
	Purpose			Network Time Protocol (NTP) Client
*/

package TimeServiceClient_GUI_Refactored;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;
import javax.swing.*;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
/**
 * GUI 版 NTP 客户端。
 * - 提供 NTP 服务器列表与位置说明的选择界面
 * - 调用 NTP_Client 解析域名、发送请求并接收时间戳
 * - 定时（Timer）轮询服务器更新时间并在界面显示
 * - 维护统计信息（已发送/已接收次数），并在停止时释放资源
 */    
public class TimeServiceClient_GUI_Refactored extends javax.swing.JFrame implements ActionListener
{
    NTP_Client m_NTP_Client;
    int m_iNumRequestsSent;
    int m_iNumResponsesReceived;
    Timer m_Timer_SendNTPRequests;
    DefaultListModel m_listModel_NTPServerList; // For use with JList
    DefaultListModel m_listModel_LocationList; // For use with JList
    ListSelectionListener m_SelectionListener_NTPServerURLs;

    public TimeServiceClient_GUI_Refactored()
    {
        m_SelectionListener_NTPServerURLs = new ListSelectionListener() // Listener for the 'URL selection changed' event
        {
            public void valueChanged(ListSelectionEvent listSelectionEvent)
            {
                int iSelectionIndex = jList_NTPServerURLs.getSelectedIndex();
                jList_NTPServerLocations.setSelectedIndex(iSelectionIndex);
                Get_ServerURL_listBox_Selection();
                jTextField_UNIX_Time.setText("");
                jTextField_UTC_Time.setText("");
                m_iNumRequestsSent = 0;
                m_iNumResponsesReceived = 0;
                UpdateStatisticsDisplay();
            }
        };
        m_listModel_NTPServerList = new DefaultListModel(); // For use with jList_NTPServerURLs
        m_listModel_LocationList = new DefaultListModel(); // For use with jList_NTPServerLocations
        
        initComponents();
        Populate_NTP_Server_List();

        m_NTP_Client = new NTP_Client();
        Boolean bSocketOpenSuccess = m_NTP_Client.CreateSocket();
        if (false == bSocketOpenSuccess)
        {
            JOptionPane.showMessageDialog(null, "Error creating socket", "NTP client", JOptionPane.PLAIN_MESSAGE);
            CloseSocketAndExit();
        }

        m_iNumRequestsSent = 0;
        m_iNumResponsesReceived = 0;
        UpdateStatisticsDisplay();
        InitialiseControls();
    }

    private void InitialiseControls()    
    {    
        jPanel_NTPServerAddressDetails.setEnabled(false);
        jTextField_URL.setEnabled(false);
        jTextField_Port.setEnabled(false);
        jTextField_ServerIPAddress.setEnabled(false);
        jTextField_UNIX_Time.setEnabled(false);
        jTextField_UTC_Time.setEnabled(false);
        jTextField_NumRequestsSent.setEnabled(false);
        jTextField_NumResponsesReceived.setEnabled(false);
        jList_NTPServerURLs.setEnabled(true);
        JScrollPane_NTPServerURLs.setEnabled(true);
        jList_NTPServerLocations.setEnabled(false);
        JScrollPane_NTPServerLocations.setEnabled(false);
        jButton_StartNTPClient.setEnabled(true);
        jButton_Done.setEnabled(true); 
        Initialise_ServerURL_listBox(); // Selects first item in list boxes, by default
    }

    void Start_Timer_SendNTPRequests()
    {
        m_Timer_SendNTPRequests = new Timer();
        m_Timer_SendNTPRequests.scheduleAtFixedRate(
                new Get_NTP_Timestamp(), 100, 10000); // Initial timeout ocurs after 100 ms (sends first NTP request)
                                                      // Subsequent NTP request occur at 10-second intervals) 
    }
    /**
     * 定时任务：发起一次 NTP 请求，按结果更新界面与统计。
     */
    class Get_NTP_Timestamp extends TimerTask
    {
        public void run()
        {
            NTP_Client.NTP_Timestamp_Data NTP_Timestamp = m_NTP_Client.Get_NTP_Timestamp();
            switch (NTP_Timestamp.eResultCode)
            {
                case NTP_Success:
                    m_iNumRequestsSent++;
                    m_iNumResponsesReceived++;
                    jTextField_UNIX_Time.setText(Long.toString(NTP_Timestamp.lUnixTime));
                    String sUTC_Time = String.format("%02d:%02d:%02d", NTP_Timestamp.lHour, NTP_Timestamp.lMinute, NTP_Timestamp.lSecond);
                    jTextField_UTC_Time.setText(sUTC_Time);
                    UpdateStatisticsDisplay();
                    break;
                case NTP_ServerAddressNotSet: // 未设置服务器地址，忽略
                    break;
                case NTP_SendFailed:// 发送失败，忽略（不计数）
                    break;
                case NTP_ReceiveFailed: // 接收失败，仅递增发送计数
                    m_iNumRequestsSent++;
                    UpdateStatisticsDisplay();
                    break;
            }
        }
    }

    void Populate_NTP_Server_List()
    {
        m_listModel_NTPServerList.addElement("time.nist.gov");
        m_listModel_LocationList.addElement("NIST round robin load equalisation");
        m_listModel_NTPServerList.addElement("time.windows.com");
        m_listModel_LocationList.addElement("Windows Time service");
        m_listModel_NTPServerList.addElement("nist1-atl.ustiming.org");
        m_listModel_LocationList.addElement("Atlanta, Georgia");
        m_listModel_NTPServerList.addElement("wolfnisttime.com");
        m_listModel_LocationList.addElement("Birmingham, Alabama");
        m_listModel_NTPServerList.addElement("nist1-chi.ustiming.org");
        m_listModel_LocationList.addElement("Chicago, Illinois");
        m_listModel_NTPServerList.addElement("nist1-lnk.binary.net");
        m_listModel_LocationList.addElement("Lincoln, Nebraska");
        m_listModel_NTPServerList.addElement("time-a.timefreq.bldrdoc.gov");
        m_listModel_LocationList.addElement("NIST, Boulder, Colorado");
        m_listModel_NTPServerList.addElement("ntp-nist.ldsbc.edu");
        m_listModel_LocationList.addElement("LDSBC, Salt Lake City, Utah");
        m_listModel_NTPServerList.addElement("nist1-lv.ustiming.org");
        m_listModel_LocationList.addElement("Las Vegas, Nevada");  
        m_listModel_NTPServerList.addElement("nist1-la.ustiming.org");
        m_listModel_LocationList.addElement("Los Angeles, California");
        m_listModel_NTPServerList.addElement("nist1-ny.ustiming.org");
        m_listModel_LocationList.addElement("New York City, NY");
        m_listModel_NTPServerList.addElement("nist1-nj.ustiming.org");
        m_listModel_LocationList.addElement("Bridgewater, NJ");
    }

    private void Initialise_ServerURL_listBox()
    {
        jList_NTPServerURLs.setSelectedIndex(0);
        jList_NTPServerLocations.setSelectedIndex(0);
        Get_ServerURL_listBox_Selection();
    }

    private void Get_ServerURL_listBox_Selection()
    {
        String sSelectedURL = jList_NTPServerURLs.getSelectedValue().toString();
        jTextField_URL.setText(sSelectedURL);
        SetUp_TimeService_AddressStruct(sSelectedURL);
    }

    void SetUp_TimeService_AddressStruct(String sURL)
    {
        InetAddress TimeService_IPAddress = m_NTP_Client.SetUp_TimeService_AddressStruct(sURL);
        if(null != TimeService_IPAddress)
        {
            jTextField_ServerIPAddress.setText(TimeService_IPAddress.getHostAddress());
            jTextField_Port.setText(Integer.toString(m_NTP_Client.GetPort()));
        }
        else
        {
            jTextField_ServerIPAddress.setText("Not found");
            jTextField_Port.setText("");
        }
    }

    void UpdateStatisticsDisplay()
    {
        jTextField_NumRequestsSent.setText(Integer.toString(m_iNumRequestsSent));
        jTextField_NumResponsesReceived.setText(Integer.toString(m_iNumResponsesReceived));
    }

    void StopTimer()
    {
        if (null != m_Timer_SendNTPRequests)
        {
            m_Timer_SendNTPRequests.cancel();
        }
    }

    void CloseSocketAndExit()
    {
        StopTimer();
        m_NTP_Client.CloseSocket();
        System.exit(0);
    }
    
    public static void main(String args[]) throws Exception
    {   // Initialise the GUI libraries
        try 
        {
            javax.swing.UIManager.LookAndFeelInfo[] installedLookAndFeels=javax.swing.UIManager.getInstalledLookAndFeels();
            for (int idx=0; idx<installedLookAndFeels.length; idx++)
            {
                if ("Nimbus".equals(installedLookAndFeels[idx].getName())) {
                    javax.swing.UIManager.setLookAndFeel(installedLookAndFeels[idx].getClassName());
                    break;
                }
            }
        } 
        catch (Exception Ex)
        {
            System.exit(0);
        }

        java.awt.EventQueue.invokeAndWait(new Runnable() // Create and display the GUI form
        { 
            public void run() 
            { 
                new TimeServiceClient_GUI_Refactored().setVisible(true);
            }
        } );
    }    

    public void actionPerformed(ActionEvent e)
    {
        if(jButton_Done == e.getSource())
        {
            CloseSocketAndExit();
        }
        if(jButton_StartNTPClient == e.getSource())
        {
            if(false == m_NTP_Client.Get_ClientStarted_Flag())
            {
                jButton_StartNTPClient.setText("STOP NTP requests");
                jList_NTPServerURLs.setEnabled(false);
                m_iNumRequestsSent = 0;
                m_iNumResponsesReceived = 0;
                UpdateStatisticsDisplay();
                Start_Timer_SendNTPRequests();
                m_NTP_Client.Set_ClientStarted_Flag(true);
            }
            else
            {
                jButton_StartNTPClient.setText("START NTP requests");
                jList_NTPServerURLs.setEnabled(true);
                m_NTP_Client.Set_ClientStarted_Flag(false);
                StopTimer();
            }
        }
    }
    // ---------------- 以下为 UI 组件字段（界面元素） ----------------   
    private JPanel jPanel_NTPServerAddressDetails;
    private JLabel jLabel_URL;
    private JTextField jTextField_URL;
    private JLabel jLabel_Port;
    private JTextField jTextField_Port;
    private JLabel jLabel_ServerIPAddress;
    private JTextField jTextField_ServerIPAddress;
    private JPanel jPanel_Time_Status;
    private JLabel jLabel_UNIX_Time;
    private JTextField jTextField_UNIX_Time;
    private JLabel jLabel_UTC_Time;
    private JTextField jTextField_UTC_Time;
    private JLabel jLabel_NumRequestsSent;
    private JTextField jTextField_NumRequestsSent;
    private JLabel jLabel_NumResponsesReceived;
    private JTextField jTextField_NumResponsesReceived;
    private JPanel jPanel_NTPServerSelection;
    private JLabel jLabel_NIST_Servers;
    private JLabel jLabel_NTPServerURLs;
    private JLabel jLabel_NTPServerLocations;
    private JList jList_NTPServerURLs;
    private JScrollPane JScrollPane_NTPServerURLs;
    private JList jList_NTPServerLocations;
    private JScrollPane JScrollPane_NTPServerLocations;
    private JPanel jPanel_Controls;
    private JButton jButton_StartNTPClient;
    private JButton jButton_Done;

    private void initComponents()
    {
        jLabel_URL = new JLabel();
        jLabel_URL.setText("URL");
        jTextField_URL = new JTextField();
        jTextField_URL.setMaximumSize(new Dimension(250, 4));
        jTextField_URL.setHorizontalAlignment(JTextField.CENTER);
        jLabel_Port = new JLabel();
        jLabel_Port.setText("Port");
        jTextField_Port = new JTextField();
        jTextField_Port.setMaximumSize(new Dimension(120, 4));
        jTextField_Port.setHorizontalAlignment(JTextField.CENTER);
        jLabel_ServerIPAddress = new JLabel();
        jLabel_ServerIPAddress.setText("Server IP address");
        jTextField_ServerIPAddress = new JTextField();
        jTextField_ServerIPAddress.setMaximumSize(new Dimension(120, 4));
        jTextField_ServerIPAddress.setHorizontalAlignment(JTextField.CENTER);

        jPanel_NTPServerAddressDetails = new JPanel();
        jPanel_NTPServerAddressDetails.setPreferredSize(new Dimension(400, 300));
        jPanel_NTPServerAddressDetails.setBorder(BorderFactory.createTitledBorder("Selected NTP Server Address"));
        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel_NTPServerAddressDetails);
        jPanel_NTPServerAddressDetails.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel1Layout.createSequentialGroup()
                    .add(jLabel_URL)
                    .addContainerGap(10, 10)
                    .add(jTextField_URL))
                .add(jPanel1Layout.createSequentialGroup()
                    .add(jLabel_Port)
                    .addContainerGap(10, 10)
                    .add(jTextField_Port))
                .add(jPanel1Layout.createSequentialGroup()
                    .add(jLabel_ServerIPAddress)
                    .addContainerGap(10, 10)
                    .add(jTextField_ServerIPAddress)));
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel1Layout.createSequentialGroup()
                    .add(jPanel1Layout.createParallelGroup()
                        .add(jLabel_URL)
                        .add(jTextField_URL))
                    .addContainerGap(20, 20)
                    .add(jPanel1Layout.createParallelGroup()
                        .add(jLabel_Port)
                        .add(jTextField_Port))
                    .addContainerGap(20, 20)
                    .add(jPanel1Layout.createParallelGroup()
                        .add(jLabel_ServerIPAddress)
                        .add(jTextField_ServerIPAddress))));
                
        jLabel_UNIX_Time = new JLabel();
        jLabel_UNIX_Time.setText("UNIX time");
        jTextField_UNIX_Time = new JTextField();
        jTextField_UNIX_Time.setMaximumSize(new Dimension(120, 4));
        jTextField_UNIX_Time.setHorizontalAlignment(JTextField.CENTER);
        jLabel_UTC_Time = new JLabel();
        jLabel_UTC_Time.setText("UTC time");
        jTextField_UTC_Time = new JTextField();
        jTextField_UTC_Time.setMaximumSize(new Dimension(120, 4));
        jTextField_UTC_Time.setHorizontalAlignment(JTextField.CENTER);
        jLabel_NumRequestsSent = new JLabel();
        jLabel_NumRequestsSent.setText("Number of NTP time requests sent");
        jTextField_NumRequestsSent = new JTextField();
        jTextField_NumRequestsSent.setMaximumSize(new Dimension(60, 4));
        jTextField_NumRequestsSent.setHorizontalAlignment(JTextField.CENTER);
        jLabel_NumResponsesReceived = new JLabel();
        jLabel_NumResponsesReceived.setText("Number of NTP time responses received");
        jTextField_NumResponsesReceived = new JTextField();
        jTextField_NumResponsesReceived.setMaximumSize(new Dimension(60, 4));
        jTextField_NumResponsesReceived.setHorizontalAlignment(JTextField.CENTER);

        jPanel_Time_Status = new JPanel();
        jPanel_Time_Status.setPreferredSize(new Dimension(400, 300));
        jPanel_Time_Status.setBorder(BorderFactory.createTitledBorder("Time and Status"));
        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel_Time_Status);
        jPanel_Time_Status.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel2Layout.createSequentialGroup()
                    .add(jLabel_UNIX_Time)
                    .addContainerGap(10, 10)
                    .add(jTextField_UNIX_Time))
                .add(jPanel2Layout.createSequentialGroup()
                    .add(jLabel_UTC_Time)
                    .addContainerGap(10, 10)
                    .add(jTextField_UTC_Time))
                .add(jLabel_NumRequestsSent)
                .add(jTextField_NumRequestsSent)
                .add(jLabel_NumResponsesReceived)
                .add(jTextField_NumResponsesReceived));
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel2Layout.createSequentialGroup()
                    .add(jPanel2Layout.createParallelGroup()
                        .add(jLabel_UNIX_Time)
                        .add(jTextField_UNIX_Time))
                    .addContainerGap(20, 20)
                    .add(jPanel2Layout.createParallelGroup()
                        .add(jLabel_UTC_Time)
                        .add(jTextField_UTC_Time))
                    .addContainerGap(20, 20)
                    .add(jLabel_NumRequestsSent)
                    .add(jTextField_NumRequestsSent)
                    .addContainerGap(20, 20)
                    .add(jLabel_NumResponsesReceived)
                    .add(jTextField_NumResponsesReceived)));
                
        jLabel_NIST_Servers = new JLabel();
        jLabel_NIST_Servers.setText("A selection of NIST servers are provided (availability may change over time)");
        jLabel_NTPServerURLs = new JLabel();
        jLabel_NTPServerURLs.setText("NTP Server URLs");
        jLabel_NTPServerLocations = new JLabel();
        jLabel_NTPServerLocations.setText("Location / description");
        jList_NTPServerURLs = new JList(m_listModel_NTPServerList);
        jList_NTPServerURLs.setMaximumSize(new Dimension(300, 250));
        jList_NTPServerURLs.setSelectedIndex(0);
        jList_NTPServerURLs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jList_NTPServerURLs.addListSelectionListener(m_SelectionListener_NTPServerURLs);
        JScrollPane_NTPServerURLs = new javax.swing.JScrollPane(jList_NTPServerURLs,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, 
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jList_NTPServerLocations = new JList(m_listModel_LocationList);
        jList_NTPServerLocations.setMaximumSize(new Dimension(300, 250));
        jList_NTPServerLocations.setSelectedIndex(0);
        jList_NTPServerLocations.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane_NTPServerLocations = new javax.swing.JScrollPane(jList_NTPServerLocations,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, 
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        jPanel_NTPServerSelection = new JPanel();
        jPanel_NTPServerSelection.setPreferredSize(new Dimension(500, 300));
        jPanel_NTPServerSelection.setBorder(BorderFactory.createTitledBorder("NIST / NTP Server Selection"));
        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel_NTPServerSelection);
        jPanel_NTPServerSelection.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel3Layout.createSequentialGroup()
                    .add(jLabel_NIST_Servers))
                .add(jPanel3Layout.createSequentialGroup()
                    .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel_NTPServerURLs)
                    .add(JScrollPane_NTPServerURLs))
                    .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                    .add(jLabel_NTPServerLocations)
                    .add(JScrollPane_NTPServerLocations))));
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel3Layout.createSequentialGroup()
                    .add(jLabel_NIST_Servers)
                    .addContainerGap(20, 20)
                    .add(jPanel3Layout.createParallelGroup()
                        .add(jPanel3Layout.createSequentialGroup()
                            .add(jLabel_NTPServerURLs)
                            .add(JScrollPane_NTPServerURLs))
                        .add(jPanel3Layout.createSequentialGroup()
                            .add(jLabel_NTPServerLocations)
                            .add(JScrollPane_NTPServerLocations)))));

        jButton_StartNTPClient = new JButton();
        jButton_StartNTPClient.setText("START NTP requests");
        jButton_StartNTPClient.setMaximumSize(new Dimension(100, 4));
        jButton_StartNTPClient.addActionListener(this);
        jButton_Done = new JButton();
        jButton_Done.setText("Done");
        jButton_Done.setMaximumSize(new Dimension(100, 4));
        jButton_Done.addActionListener(this);

        jPanel_Controls = new JPanel();
        jPanel_Controls.setPreferredSize(new Dimension(500, 100));
        jPanel_Controls.setBorder(BorderFactory.createTitledBorder("Controls"));
        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel_Controls);
        jPanel_Controls.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel4Layout.createSequentialGroup()
                    .addContainerGap(200, 200)
                    .add(jButton_StartNTPClient)
                    .addContainerGap(200, 200)
                    .add(jButton_Done)
                    .addContainerGap(200, 200)));
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
                .add(jPanel4Layout.createParallelGroup()
                    .add(jButton_StartNTPClient)
                    .add(jButton_Done)));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        getContentPane().setPreferredSize(new Dimension(700, 450));
         
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
            .add(layout.createSequentialGroup()
                .add(layout.createParallelGroup()            
                    .add(jPanel_NTPServerAddressDetails)
                    .add(jPanel_Time_Status))
                .add(layout.createParallelGroup()            
                    .add(jPanel_NTPServerSelection)
                    .add(jPanel_Controls))));
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.CENTER)
            .add(layout.createSequentialGroup()
                .add(jPanel_NTPServerAddressDetails)
                .addContainerGap(20, 20)
                .add(jPanel_Time_Status))
            .add(layout.createSequentialGroup()
                .add(jPanel_NTPServerSelection)
                .addContainerGap(20, 20)
                .add(jPanel_Controls)));

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Network Time Protocol client");
        pack();
    }                                             
}