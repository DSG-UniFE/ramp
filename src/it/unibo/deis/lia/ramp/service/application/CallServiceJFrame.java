package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.service.management.ServiceResponse;

import javax.swing.JFrame;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JPanel;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Vector;

import javax.swing.border.TitledBorder;
import javax.swing.BoxLayout;
import javax.swing.border.EmptyBorder;
import java.awt.FlowLayout;
import java.awt.Color;
import javax.swing.border.LineBorder;
import javax.swing.SwingConstants;

public class CallServiceJFrame extends JFrame {
	
	private static final long serialVersionUID = 1L;
	private CallService cs;
	private CallReceiver cr;
	private boolean callIsActive = false;
	private byte localQ = 0;
	private byte remoteQ = 0;

	private Vector<ServiceResponse> vss = null;
	
	private ServiceResponse neighborSelected = null;

	public byte getLocalQ() {
		return localQ;
	}

	public void setLocalQ(byte localQ) {
		this.localQ = localQ;
	}

	public byte getRemoteQ() {
		return remoteQ;
	}

	public void setRemoteQ(byte remoteQ) {
		this.remoteQ = remoteQ;
	}

	public ServiceResponse getNeighborSelected() {
		return neighborSelected;
	}

	private final ButtonGroup radioGroupSend = new ButtonGroup();
	private JTextField inputWebcam;
	private JTextField inputConnectTimeout;
	private JTextField inTTL;
	private JTextField inTimeout;
	private JTextField inServiceAmount;
	private JList neighborsList;
	private JPanel radioPanelSend;
	private JRadioButton rdbtnSendVoice;
	private JRadioButton rdbtnSendVideo;
	private JRadioButton rdbtnSendBoth;
	private JPanel controlPanel;
	private JButton btnChangeWebcam;
	private JLabel lblWebcamName;
	private JButton btnChangeConnectTimeout;
	private JLabel lblConnectTimeout;
	private JButton btnFind;
	private JPanel panelW;
	private JPanel panelCT;
	private JPanel panelService;
	private JPanel panelAD;
	private JLabel lblAudioCaptureDevice;
	private JButton btnAudioDevice;
	private JTextField inputAudioDevice;

	private void initComponents() {
		
	// window
		setTitle("CallService");
		setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		getContentPane().setLayout(new BorderLayout(0, 0));
	// end window
		
	// list
		neighborsList = new JList();
		neighborsList.setBorder(new TitledBorder(null, "", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		getContentPane().add(neighborsList, BorderLayout.CENTER);
	// end list
		
	// radio panel send	
		radioPanelSend = new JPanel();
		radioPanelSend.setBorder(new TitledBorder(new LineBorder(new Color(184, 207, 229)), "send >", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		getContentPane().add(radioPanelSend, BorderLayout.WEST);
		radioPanelSend.setLayout(new BoxLayout(radioPanelSend, BoxLayout.PAGE_AXIS));

		rdbtnSendVoice = new JRadioButton("voice");
		rdbtnSendVoice.setSelected(true);
		rdbtnSendVoice.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				jRadioSendActionPerformed(e);
			}
		});
		radioGroupSend.add(rdbtnSendVoice);
		radioPanelSend.add(rdbtnSendVoice);

		rdbtnSendVideo = new JRadioButton("video");
		rdbtnSendVideo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				jRadioSendActionPerformed(e);
			}
		});
		radioGroupSend.add(rdbtnSendVideo);
		radioPanelSend.add(rdbtnSendVideo);

		rdbtnSendBoth = new JRadioButton("both");
		rdbtnSendBoth.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				jRadioSendActionPerformed(e);
			}
		});
		radioGroupSend.add(rdbtnSendBoth);
		radioPanelSend.add(rdbtnSendBoth);
	// end radio panel send
		
	// control panel
		controlPanel = new JPanel();
		controlPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		getContentPane().add(controlPanel, BorderLayout.SOUTH);
		controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
	// end panel rp

	// panel service
		panelService = new JPanel();
		panelService.setBorder(new TitledBorder(null, "service settings", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		controlPanel.add(panelService);
		panelService.setLayout(new BoxLayout(panelService, BoxLayout.Y_AXIS));
	// end panel service
		
	// panel w
		panelW = new JPanel();
		panelService.add(panelW);
		FlowLayout flowLayout_2 = (FlowLayout) panelW.getLayout();
		flowLayout_2.setAlignment(FlowLayout.LEADING);

		btnChangeWebcam = new JButton("change webcam");
		panelW.add(btnChangeWebcam);

		lblWebcamName = new JLabel("  webcam number");
		panelW.add(lblWebcamName);

		inputWebcam = new JTextField();
		panelW.add(inputWebcam);
		inputWebcam.setColumns(35);
		btnChangeWebcam.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				jButtonChangeWebcamActionPerformed(e);
			}
		});
	// end radio panel receive

	// panel device discovery
		JPanel panelServiceDiscovery = new JPanel();
		panelServiceDiscovery.setBorder(new EmptyBorder(10, 10, 10, 10));
		getContentPane().add(panelServiceDiscovery, BorderLayout.NORTH);
		GridBagLayout gbl_panelServiceDiscovery = new GridBagLayout();
		gbl_panelServiceDiscovery.columnWidths = new int[] { 63, 63, 63, 63, 63, 63, 63, 0 };
		gbl_panelServiceDiscovery.rowHeights = new int[] { 25, 0 };
		gbl_panelServiceDiscovery.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
		gbl_panelServiceDiscovery.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
		panelServiceDiscovery.setLayout(gbl_panelServiceDiscovery);

		btnFind = new JButton("find neighbors");
		btnFind.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				jButtonRefreshStreamListActionPerformed(e);
			}
		});
		GridBagConstraints gbc_btnFind = new GridBagConstraints();
		gbc_btnFind.fill = GridBagConstraints.BOTH;
		gbc_btnFind.insets = new Insets(0, 0, 0, 5);
		gbc_btnFind.gridx = 0;
		gbc_btnFind.gridy = 0;
		panelServiceDiscovery.add(btnFind, gbc_btnFind);

		JLabel lblTTL = new JLabel("    TTL");
		GridBagConstraints gbc_lblTTL = new GridBagConstraints();
		gbc_lblTTL.anchor = GridBagConstraints.EAST;
		gbc_lblTTL.fill = GridBagConstraints.VERTICAL;
		gbc_lblTTL.insets = new Insets(0, 0, 0, 5);
		gbc_lblTTL.gridx = 1;
		gbc_lblTTL.gridy = 0;
		panelServiceDiscovery.add(lblTTL, gbc_lblTTL);

		inTTL = new JTextField();
		inTTL.setText("3");
		GridBagConstraints gbc_inTTL = new GridBagConstraints();
		gbc_inTTL.fill = GridBagConstraints.BOTH;
		gbc_inTTL.insets = new Insets(0, 0, 0, 5);
		gbc_inTTL.gridx = 2;
		gbc_inTTL.gridy = 0;
		panelServiceDiscovery.add(inTTL, gbc_inTTL);
		inTTL.setColumns(6);

		JLabel lblTimeout = new JLabel("    timeout");
		GridBagConstraints gbc_lblTimeout = new GridBagConstraints();
		gbc_lblTimeout.anchor = GridBagConstraints.EAST;
		gbc_lblTimeout.fill = GridBagConstraints.VERTICAL;
		gbc_lblTimeout.insets = new Insets(0, 0, 0, 5);
		gbc_lblTimeout.gridx = 3;
		gbc_lblTimeout.gridy = 0;
		panelServiceDiscovery.add(lblTimeout, gbc_lblTimeout);

		inTimeout = new JTextField();
		inTimeout.setText("1000");
		GridBagConstraints gbc_inTimeout = new GridBagConstraints();
		gbc_inTimeout.fill = GridBagConstraints.BOTH;
		gbc_inTimeout.insets = new Insets(0, 0, 0, 5);
		gbc_inTimeout.gridx = 4;
		gbc_inTimeout.gridy = 0;
		panelServiceDiscovery.add(inTimeout, gbc_inTimeout);
		inTimeout.setColumns(10);

		JLabel lblServiceAmount = new JLabel("    serviceAmount 1+");
		GridBagConstraints gbc_lblServiceAmount = new GridBagConstraints();
		gbc_lblServiceAmount.anchor = GridBagConstraints.EAST;
		gbc_lblServiceAmount.fill = GridBagConstraints.VERTICAL;
		gbc_lblServiceAmount.insets = new Insets(0, 0, 0, 5);
		gbc_lblServiceAmount.gridx = 5;
		gbc_lblServiceAmount.gridy = 0;
		panelServiceDiscovery.add(lblServiceAmount, gbc_lblServiceAmount);

		inServiceAmount = new JTextField();
		inServiceAmount.setText("1");
		GridBagConstraints gbc_inServiceAmount = new GridBagConstraints();
		gbc_inServiceAmount.fill = GridBagConstraints.BOTH;
		gbc_inServiceAmount.gridx = 6;
		gbc_inServiceAmount.gridy = 0;
		panelServiceDiscovery.add(inServiceAmount, gbc_inServiceAmount);
		inServiceAmount.setColumns(6);
	// end panel device discovery

	// panel ad
		panelAD = new JPanel();
		panelService.add(panelAD);
		panelAD.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));

		btnAudioDevice = new JButton("change audio device");
		btnAudioDevice.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				jButtonChangeAudioDeviceActionPerformed(e);
			}
		});
		btnAudioDevice.setHorizontalAlignment(SwingConstants.LEFT);
		panelAD.add(btnAudioDevice);

		lblAudioCaptureDevice = new JLabel("  audio capture device");
		panelAD.add(lblAudioCaptureDevice);

		inputAudioDevice = new JTextField();
		panelAD.add(inputAudioDevice);
		inputAudioDevice.setColumns(20);
	// end panel vd

	// panel ct
		panelCT = new JPanel();
		panelService.add(panelCT);
		FlowLayout flowLayout_4 = (FlowLayout) panelCT.getLayout();
		flowLayout_4.setAlignment(FlowLayout.LEADING);

		btnChangeConnectTimeout = new JButton("change connect timeout");
		panelCT.add(btnChangeConnectTimeout);

		lblConnectTimeout = new JLabel("  connect timeout");
		panelCT.add(lblConnectTimeout);

		inputConnectTimeout = new JTextField();
		panelCT.add(inputConnectTimeout);
		inputConnectTimeout.setColumns(10);
		btnChangeConnectTimeout.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String newConnectTimeout = inputConnectTimeout.getText();
				cs.setTimeoutConnect(Short.parseShort(newConnectTimeout));
			}
		});
	// end panel ct

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				formWindowClosing(e);
			}
		});

		MouseListener mouseListener = new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				mouseDoubleClicked(e);
			}
		};
		neighborsList.addMouseListener(mouseListener);

		pack();
	}

	private void TODOfeaturesCompontentsDisable() {
		inputAudioDevice.setEnabled(false);
		btnAudioDevice.setEnabled(false);
		rdbtnSendBoth.setEnabled(false);
		rdbtnSendVideo.setEnabled(false);
		rdbtnSendVoice.setEnabled(false);
	}
	
	public CallServiceJFrame(CallService callSender) {

		this.cs = callSender;
		cr=CallReceiver.getInstance(this);
		initComponents();
		jButtonRefreshStreamListActionPerformed(null);
		inputWebcam.setText(cs.getWebcam());
		inputAudioDevice.setText(cs.getAudioDev());
		inputConnectTimeout.setText("" + cs.getConnectTimeout());
		
		TODOfeaturesCompontentsDisable();
	}

	private void jButtonRefreshStreamListActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jButtonRefreshStreamListActionPerformed
		System.out.println("StreamService: jButtonRefreshFileListActionPerformed");
		String[] nbrs;

		if (evt == null) {
			nbrs = new String[1];
			nbrs[0] = "push \"find neighbors\" to fill, double clic to start call";
			neighborsList.setListData(nbrs);
			neighborsList.repaint();
			neighborsList.setEnabled(false);
			return;
		}

		String localhost = null;
		try {
			localhost = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e1) {
			System.out.println("jButtonRefreshStreamListActionPerformed error: unknown localhost");
			nbrs = new String[1];
			nbrs[0] = "no localhost found";
			neighborsList.setEnabled(false);
			e1.printStackTrace();
		}
		try {
			vss = cr.findCallService(Integer.parseInt(inTTL.getText()), Integer.parseInt(inTimeout.getText()), Integer.parseInt(inServiceAmount.getText() + 1));
		} catch (Exception e) {
			nbrs = new String[1];
			nbrs[0] = "no neighbors found (findCallService error)";
			neighborsList.setEnabled(false);
			e.printStackTrace();
		}
		if (vss != null) {
			boolean localFound = false;
			nbrs = new String[vss.size()];
			for (int i = 0; i < nbrs.length; i++) {
				ServiceResponse current = vss.elementAt(i);
				nbrs[i] = i + "     " + current + " {sends: " + current.getQos() + "}";
				if (!localFound) {
					String[] hosts = current.getServerDest();
					for (int j = 0; j < hosts.length; j++)
						if (hosts[j].equals(localhost)) {
							nbrs[i] += " (myself)";
							localFound = true;
							break;
						}
				}
			}
			neighborsList.setEnabled(true);
		} else {
			nbrs = new String[1];
			nbrs[0] = "no neighbors found";
			neighborsList.setEnabled(false);
		}
		neighborsList.setListData(nbrs);
		neighborsList.repaint();
	}// GEN-LAST:event_jButtonRefreshStreamListActionPerformed

	private void jButtonChangeWebcamActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jButtonChangeWebcamActionPerformed
		cs.setWebcam(inputWebcam.getText());
	}// GEN-LAST:event_jButtonChangeWebcamActionPerformed

	private void jButtonChangeAudioDeviceActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jButtonChangeAudioDeviceActionPerformed
		cs.setAudioDev(inputAudioDevice.getText());
	}// GEN-LAST:event_jButtonChangeWebcamActionPerformed

	private void formWindowClosing(java.awt.event.WindowEvent evt) {// GEN-FIRST:event_formWindowClosing
		System.out.println("CallService: formWindowClosing");
		cs.stopService();
		if(cr!=null) cr.stopClient();
	}// GEN-LAST:event_formWindowClosing

	private void jRadioSendActionPerformed(ActionEvent evt) {
		if (rdbtnSendVoice.isSelected())
			cs.setsType("voice");
		else if (rdbtnSendVideo.isSelected())
			cs.setsType("video");
		else if (rdbtnSendBoth.isSelected())
			cs.setsType("both");
	}
	
	private int emu = -1;

	private void mouseDoubleClicked(MouseEvent e) {
		
		if (e == null && emu > 0) {
			int port = emu;
			emu = -1;
			
			for (ServiceResponse ss : vss) {
				if (ss.getServerPort() == port) {
					neighborSelected = ss;
					break;
				}
			}
			if( neighborSelected == null) {
				System.out.println("Emulate double click button error, neighbor selected = null");
				return;
			}
			
			System.out.println("Neighbor selected from CallService: " + neighborSelected + " {Sends: " + neighborSelected.getQos() + "}");
			
			Thread t=new Thread(
						new Runnable() {
							@Override
							public void run() {
								try {
									cr.getStream(neighborSelected, "call", cr.getStreamProtocol(), cr.getRampProtocol(), -1);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
					);
			t.start();
			callIsActive = true;
			return;
		}
		
		JList list = (JList) e.getSource();
		if (e.getClickCount() == 2) {
			int index = list.locationToIndex(e.getPoint());
			if (index >= 0) {
				String sel = (String)list.getModel().getElementAt(index);
				System.out.println("In neighbors list: double-clicked on: " + sel);
				if (sel.endsWith("(myself)")) {
					System.out.println("can't call myself");
					neighborSelected = null;
					return;
				}
				int vindex;
				try {
					vindex = Integer.parseInt(sel.split(" ")[0]);
				} catch (Exception e1) {
					System.out.println("ParseInt error");
					neighborSelected = null;
					return;
				}
				neighborSelected = vss.elementAt(vindex);
				System.out.println("Neighbor selected: " + neighborSelected + " {Sends: " + neighborSelected.getQos() + "}");
				
				Thread t=new Thread(
							new Runnable() {
								@Override
								public void run() {
									try {
										cr.getStream(neighborSelected, "call", cr.getStreamProtocol(), cr.getRampProtocol(), cs.getServicePort());
									} catch (Exception e) {
										e.printStackTrace();
									}
								}
							}
						);
				t.start();
				callIsActive = true;
			}
		}
	}
	
	public void emulateMouseDoubleClickedSignal(int emu) {
		System.out.println("Trying to send mouse double clicked signal to receive");
		this.emu = emu;
		this.mouseDoubleClicked(null);
	}
	
	public boolean isCallActive() {
		return callIsActive;
	}
	
	public void closeCall() {
		callIsActive = false;
	}
}
