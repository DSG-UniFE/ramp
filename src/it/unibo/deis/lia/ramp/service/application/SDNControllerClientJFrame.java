package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.internode.*;
import it.unibo.deis.lia.ramp.service.management.ServiceResponse;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class SDNControllerClientJFrame extends JFrame {
    private SDNControllerClient SDNcontrollerClient;
    private Vector<ServiceResponse> availableServices;
    private int localNodeID;
    // this map keeps track of all flowIds (List<Integer>) obtained for a given destination (Integer) for unicast communications
    private Map<Integer, List<Integer>> unicastFlowIDs;
    // this map keeps track for of all flowIds (Integer)the correspondent destinations (List<Integer>) for multicast communications
    private Map<Integer, List<Integer>> multicastFlowIDs;
    // this map keep tracks for each available destination the correspondent service response
    private Map<Integer, ServiceResponse> availableClients;

    private JPanel flowPolicyPanel;
    private JLabel currentPolicyLabel;
    private JTextField currentPolicyTextField;

    private JPanel refreshInfoPanel;
    private JButton refreshInfoButton;

    private ApplicationRequirements applicationRequirements;
    private JPanel applicationRequirementsPanel;
    private JLabel applicationTypeLabel;
    private JComboBox applicationTypeComboBox;
    private JLabel bitrateLabel;
    private JTextField bitrateTextField;
    private JLabel trafficAmountLabel;
    private JTextField trafficAmountTextField;
    private JLabel secondsToStartLabel;
    private JTextField secondsToStartTextField;
    private JLabel durationLabel;
    private JTextField durationTextField;
    private JButton setApplicationRequirementsButton;

    private JPanel pathSelectionMetricPanel;
    private JComboBox availablePathSelectionMetricComboBox;

    private JPanel findControllerClientReceiverPanel;
    private JButton findControllerClientReceiverButton;
    private JComboBox protocolComboBox;
    private JLabel findControllerClientTTLLabel;
    private JTextField findControllerClientTTLTextField;
    private JLabel findControllerClientTimeoutLabel;
    private JTextField findControllerClientTimeoutTextField;
    private JLabel findControllerClientServiceAmountLabel;
    private JTextField findControllerClientServiceAmountTextField;
    private JScrollPane findControllerClientScrollPane;
    private JTextArea findControllerClientReceiverTextArea;

    private JPanel getFlowIDPanel;
    private JLabel getFlowIDLabel;
    private JComboBox getFlowIDDestinationNodeComboBox;
    private Vector<JComboBox> allAdditionalDestinationNodeComboBox;
    private JPanel additionalDestinationPanel;
    private JPanel additionalDestinationComboBoxPanel;
    private JButton addDestinationButton;
    private JButton resetDestinationButton;
    private JButton getFlowIdButton;

    private JPanel defaultFlowPathPanel;
    private JScrollPane defaultFlowPathScrollPane;
    private JTable defaultFlowPathTable;

    //TODO
//    private JPanel flowPathPanel;
//    private JScrollPane flowPathScrollPane;
//    private JTable flowPathTable;

    private JPanel flowPrioritiesPanel;
    private JScrollPane flowPrioritiesScrollPane;
    private JTable flowPrioritiesTable;

    private JPanel sendPacketPanel;
    private JButton sendPacketButton;
    private JLabel sendPacketDestinationIDLabel;
    private JComboBox sendMessageDestinationIDComboBox;
    private ActionListener destionationIDComboBoxActionListener;
    private JLabel sendPacketFlowIDLabel;
    private JComboBox sendPacketFlowIDComboBox;
    private JPanel multicastDestinationsPanel;
    private JLabel multicastDestinationsLabel;
    private JScrollPane multicastDestinationsScrollPane;
    private JTextArea multicastDestinationsTextArea;
    private ActionListener flowIDComboBoxActionListener;
    private JLabel sendPacketPayloadLabel;
    private JTextField sendPacketPayloadTextField;
    private JLabel sendPacketRepetitionLabel;
    private JTextField sendPacketRepetitionTextField;

    private JPanel receivePacketPanel;
    private JButton receivePacketButton;
    private JButton deleteReceivedPacketsButton;
    private JScrollPane receivePacketScrollPane;
    private JTextArea receivePacketTextArea;

    public SDNControllerClientJFrame(SDNControllerClient SDNcontrollerClient) {
        this.SDNcontrollerClient = SDNcontrollerClient;
        this.localNodeID = Dispatcher.getLocalRampId();
        this.allAdditionalDestinationNodeComboBox = new Vector<JComboBox>(0);
        destionationIDComboBoxActionListener = null;
        flowIDComboBoxActionListener = null;

        initComponents();
    }

    private void initComponents() {
        initFlowPolicyPanel();
        initApplicationRequirementsPanel();
        initPathSelectionMetricPanel();
        initFindControllerPanel();
        initGetFlowIDPanel();
        initDefaultPathTablePanel();
        initFlowPrioritiesTablePanel();
        initSendPacketPanel();
        initReceivePacketPanel();
        initRefreshInfoPanel();

        /* Main Panel Layout */
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }

            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("SDNControllerClient");
        setLocationByPlatform(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());

        layout.setHorizontalGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup()
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(flowPolicyPanel, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(applicationRequirementsPanel, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(pathSelectionMetricPanel, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
                                )
                                .addGap(20)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(findControllerClientReceiverPanel, GroupLayout.PREFERRED_SIZE, 500, GroupLayout.PREFERRED_SIZE)
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(getFlowIDPanel, GroupLayout.PREFERRED_SIZE, 220, GroupLayout.PREFERRED_SIZE)
                                                .addGap(10)
                                                .addComponent(defaultFlowPathPanel, GroupLayout.PREFERRED_SIZE, 270, GroupLayout.PREFERRED_SIZE)
                                                .addComponent(flowPrioritiesPanel, GroupLayout.PREFERRED_SIZE, 270, GroupLayout.PREFERRED_SIZE)
                                        )
                                )
                                .addGap(20)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(sendPacketPanel, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(receivePacketPanel, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
                                )
                        )
                        .addComponent(refreshInfoPanel, GroupLayout.PREFERRED_SIZE, 238, GroupLayout.PREFERRED_SIZE)
                )
                .addContainerGap()
        );

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup()
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(flowPolicyPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(15)
                                .addComponent(applicationRequirementsPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(15)
                                .addComponent(pathSelectionMetricPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        )
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(findControllerClientReceiverPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(15)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(getFlowIDPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(defaultFlowPathPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(flowPrioritiesPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                )
                        )
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(sendPacketPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(15)
                                .addComponent(receivePacketPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        )
                )
                .addGap(15)
                .addComponent(refreshInfoPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addContainerGap()
        );

        getContentPane().setLayout(layout);
        pack();

        showAndUpdateTablesPolicy();
    }

    private void initFlowPolicyPanel() {
        flowPolicyPanel = new JPanel();
        flowPolicyPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Flow Policy"));

        currentPolicyLabel = new JLabel("Current Flow Policy");
        currentPolicyTextField = new JTextField(SDNcontrollerClient.getFlowPolicy().toString());
        currentPolicyTextField.setEditable(false);

        GroupLayout flowPoliciesLayout = new GroupLayout(flowPolicyPanel);
        flowPolicyPanel.setLayout(flowPoliciesLayout);
        flowPoliciesLayout.setHorizontalGroup(flowPoliciesLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(currentPolicyLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(currentPolicyTextField, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
        flowPoliciesLayout.setVerticalGroup(flowPoliciesLayout.createSequentialGroup()
                .addComponent(currentPolicyLabel)
                .addGap(5)
                .addComponent(currentPolicyTextField)
        );
    }

    private void initApplicationRequirementsPanel() {
        applicationRequirements = new ApplicationRequirements(ApplicationRequirements.ApplicationType.DEFAULT, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 300);

        applicationRequirementsPanel = new JPanel();
        applicationRequirementsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Application Requirements"));

        applicationTypeLabel = new JLabel("Application Type");
        applicationTypeComboBox = new JComboBox();
        int count = ApplicationRequirements.ApplicationType.values().length;
        String[] applicationTypeItems = new String[count];
        count = 0;
        for (ApplicationRequirements.ApplicationType a : ApplicationRequirements.ApplicationType.values()) {
            applicationTypeItems[count] = a.toString();
            count++;
        }
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(applicationTypeItems);
        applicationTypeComboBox.setModel(dcm);
        applicationTypeComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jComboBoxApplicationTypeActionPerfomed(evt);
            }
        });

        bitrateLabel = new JLabel("Bitrate kb/s (-1 for unused field):");
        bitrateTextField = new JTextField("-1");

        trafficAmountLabel = new JLabel("Traffic Amount kb (-1 for unused field):");
        trafficAmountTextField = new JTextField("-1");

        secondsToStartLabel = new JLabel("Seconds to start:");
        secondsToStartTextField = new JTextField("0");

        durationLabel = new JLabel("Duration (seconds):");
        durationTextField = new JTextField("300");

        setApplicationRequirementsButton = new JButton("Set Application Requirements");
        setApplicationRequirementsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonSetApplicationRequirementsActionPerfomed(evt);
            }
        });

        GroupLayout applicationRequirementsLayout = new GroupLayout(applicationRequirementsPanel);
        applicationRequirementsPanel.setLayout(applicationRequirementsLayout);
        applicationRequirementsLayout.setHorizontalGroup(applicationRequirementsLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(applicationTypeLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(applicationTypeComboBox, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(bitrateLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(bitrateTextField, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(trafficAmountLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(trafficAmountTextField, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(secondsToStartLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(secondsToStartTextField, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(durationLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(durationTextField, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(setApplicationRequirementsButton, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
        applicationRequirementsLayout.setVerticalGroup(applicationRequirementsLayout.createSequentialGroup()
                .addComponent(applicationTypeLabel)
                .addGap(5)
                .addComponent(applicationTypeComboBox)
                .addGap(5)
                .addComponent(bitrateLabel)
                .addGap(5)
                .addComponent(bitrateTextField)
                .addGap(5)
                .addComponent(trafficAmountLabel)
                .addGap(5)
                .addComponent(trafficAmountTextField)
                .addGap(5)
                .addComponent(secondsToStartLabel)
                .addGap(5)
                .addComponent(secondsToStartTextField)
                .addGap(5)
                .addComponent(durationLabel)
                .addGap(5)
                .addComponent(durationTextField)
                .addGap(5)
                .addComponent(setApplicationRequirementsButton)
        );

        showApplicationRequirementsFieldsPolicy();
    }

    private void showApplicationRequirementsFieldsPolicy() {
        boolean show = true;
        if (ApplicationRequirements.ApplicationType.valueOf(applicationTypeComboBox.getSelectedItem().toString()) == ApplicationRequirements.ApplicationType.DEFAULT) {
            show = false;
        }

        bitrateTextField.setEditable(show);
        trafficAmountTextField.setEditable(show);
        secondsToStartTextField.setEditable(show);
        durationTextField.setEditable(show);
    }

    private void initPathSelectionMetricPanel() {
        pathSelectionMetricPanel = new JPanel();
        pathSelectionMetricPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Path Selection Metric"));

        availablePathSelectionMetricComboBox = new JComboBox();
        int count = TopologyGraphSelector.PathSelectionMetric.values().length;
        String[] pathSelectionMetricItems = new String[count];
        count = 0;
        for (TopologyGraphSelector.PathSelectionMetric p : TopologyGraphSelector.PathSelectionMetric.values()) {
            pathSelectionMetricItems[count] = p.toString();
            count++;
        }
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(pathSelectionMetricItems);
        availablePathSelectionMetricComboBox.setModel(dcm);

        GroupLayout pathSelectionMetricLayout = new GroupLayout(pathSelectionMetricPanel);
        pathSelectionMetricPanel.setLayout(pathSelectionMetricLayout);
        pathSelectionMetricLayout.setHorizontalGroup(pathSelectionMetricLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(availablePathSelectionMetricComboBox, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
        pathSelectionMetricLayout.setVerticalGroup(pathSelectionMetricLayout.createSequentialGroup()
                .addComponent(availablePathSelectionMetricComboBox)
        );
    }

    private void initFindControllerPanel() {
        findControllerClientReceiverPanel = new JPanel();
        findControllerClientReceiverPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Available SDN Controller Client Receivers"));

        findControllerClientReceiverButton = new JButton("Find Nodes");
        findControllerClientReceiverButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonFindControllerClientReceiverActionPerfomed(evt);
            }
        });

        protocolComboBox = new JComboBox();
        String[] protocols = new String[]{"UDP", "TCP"};
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(protocols);
        protocolComboBox.setModel(dcm);

        findControllerClientTTLLabel = new JLabel("TTL:");
        findControllerClientTTLTextField = new JTextField("5");

        findControllerClientTimeoutLabel = new JLabel("Timeout:");
        findControllerClientTimeoutTextField = new JTextField("2500");

        findControllerClientServiceAmountLabel = new JLabel("Amount:");
        findControllerClientServiceAmountTextField = new JTextField("4");

        findControllerClientReceiverTextArea = new JTextArea();
        findControllerClientReceiverTextArea.setColumns(20);
        findControllerClientReceiverTextArea.setEditable(false);
        findControllerClientReceiverTextArea.setRows(5);

        findControllerClientScrollPane = new JScrollPane();
        findControllerClientScrollPane.setViewportView(findControllerClientReceiverTextArea);

        GroupLayout findControllerClientLayout = new GroupLayout(findControllerClientReceiverPanel);
        findControllerClientReceiverPanel.setLayout(findControllerClientLayout);
        findControllerClientLayout.setHorizontalGroup(findControllerClientLayout.createParallelGroup()
                .addGroup(findControllerClientLayout.createSequentialGroup()
                        .addComponent(findControllerClientReceiverButton, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 186, Short.MAX_VALUE)
                        .addComponent(protocolComboBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(findControllerClientTTLLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(findControllerClientTTLTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(findControllerClientTimeoutLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(findControllerClientTimeoutTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(findControllerClientServiceAmountLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(findControllerClientServiceAmountTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                )
                .addComponent(findControllerClientScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
        );
        findControllerClientLayout.setVerticalGroup(findControllerClientLayout.createSequentialGroup()
                .addGroup(findControllerClientLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(findControllerClientReceiverButton)
                        .addComponent(protocolComboBox)
                        .addComponent(findControllerClientTTLLabel)
                        .addComponent(findControllerClientTTLTextField)
                        .addComponent(findControllerClientTimeoutLabel)
                        .addComponent(findControllerClientTimeoutTextField)
                        .addComponent(findControllerClientServiceAmountLabel)
                        .addComponent(findControllerClientServiceAmountTextField)
                )
                .addGap(5)
                .addComponent(findControllerClientScrollPane)
        );
    }

    private void initGetFlowIDPanel() {
        getFlowIDPanel = new JPanel();
        getFlowIDPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Flow ID"));

        getFlowIDLabel = new JLabel("Destination Node ID:");

        getFlowIDDestinationNodeComboBox = new JComboBox();
        String[] empty = new String[0];
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(empty);
        getFlowIDDestinationNodeComboBox.setModel(dcm);

        initAdditionalDestinationPanel();

        getFlowIdButton = new JButton("Get Flow ID");
        getFlowIdButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonGetFlowIDActionPerfomed(evt);
            }
        });
        getFlowIdButton.setEnabled(false);

        GroupLayout getFlowIdLayout = new GroupLayout(getFlowIDPanel);
        getFlowIDPanel.setLayout(getFlowIdLayout);
        getFlowIdLayout.setHorizontalGroup(getFlowIdLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(getFlowIDLabel, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                .addComponent(getFlowIDDestinationNodeComboBox, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                .addComponent(additionalDestinationPanel, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                .addComponent(getFlowIdButton, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
        );
        getFlowIdLayout.setVerticalGroup(getFlowIdLayout.createSequentialGroup()
                .addComponent(getFlowIDLabel)
                .addGap(5)
                .addComponent(getFlowIDDestinationNodeComboBox)
                .addComponent(additionalDestinationPanel)
                .addGap(5)
                .addComponent(getFlowIdButton)
        );
    }

    private void initAdditionalDestinationPanel() {
        additionalDestinationPanel = new JPanel();

        additionalDestinationComboBoxPanel = new JPanel();

        addDestinationButton = new JButton("Add Dest");
        addDestinationButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonAddDestinationActionPerfomed(evt);
            }
        });

        resetDestinationButton = new JButton("Reset");
        resetDestinationButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonResetDestinationActionPerfomed(evt);
            }
        });

        GroupLayout layout = new GroupLayout(additionalDestinationPanel);
        additionalDestinationPanel.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(additionalDestinationComboBoxPanel, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                .addGroup(layout.createSequentialGroup()
                        .addComponent(addDestinationButton, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                        .addComponent(resetDestinationButton, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                )
        );
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addComponent(additionalDestinationComboBoxPanel)
                .addGap(5)
                .addGroup(layout.createParallelGroup()
                        .addComponent(addDestinationButton)
                        .addComponent(resetDestinationButton)
                )
        );

        refreshAdditionalComboBoxPanel();

        if (SDNcontrollerClient.getFlowPolicy() != FlowPolicy.MULTICASTING) {
            additionalDestinationPanel.setVisible(false);
            addDestinationButton.setEnabled(false);
            resetDestinationButton.setEnabled(false);
        }
    }

    private void refreshAdditionalComboBoxPanel() {
        GroupLayout layout = new GroupLayout(additionalDestinationComboBoxPanel);
        additionalDestinationComboBoxPanel.setLayout(layout);

        if (allAdditionalDestinationNodeComboBox.size() > 0) {
            layout = new GroupLayout(additionalDestinationComboBoxPanel);
            additionalDestinationComboBoxPanel.setLayout(layout);
            GroupLayout.ParallelGroup parallelGroup = layout.createParallelGroup(GroupLayout.Alignment.LEADING);
            GroupLayout.SequentialGroup sequentialGroup = layout.createSequentialGroup();
            for (int i = 0; i < allAdditionalDestinationNodeComboBox.size(); i++) {
                JComboBox element = allAdditionalDestinationNodeComboBox.get(i);
                parallelGroup.addComponent(element, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE);
                sequentialGroup.addGap(5);
                sequentialGroup.addComponent(element);
            }
            layout.setHorizontalGroup(parallelGroup);
            layout.setVerticalGroup(sequentialGroup);

            resetDestinationButton.setEnabled(true);
        }

        // Print again the JFrame to perform layout recalculation
        this.repaint();
        this.revalidate();
    }

    private void jButtonAddDestinationActionPerfomed(ActionEvent evt) {
        JComboBox comboBox = new JComboBox();
        String[] items = new String[availableServices.size()];
        for (int i = 0; i < availableServices.size(); i++) {
            items[i] = "" + availableServices.elementAt(i).getServerNodeId();
        }
        comboBox.setModel(new DefaultComboBoxModel(items));
        allAdditionalDestinationNodeComboBox.add(comboBox);
        refreshAdditionalComboBoxPanel();
    }

    private void jButtonResetDestinationActionPerfomed(ActionEvent evt) {
        this.allAdditionalDestinationNodeComboBox = new Vector<JComboBox>(0);
        refreshAdditionalComboBoxPanel();
    }

    private void initDefaultPathTablePanel() {
        defaultFlowPathPanel = new JPanel();
        defaultFlowPathPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Default Flow Path"));

        String[] columnNames = {"FlowID", "Path"};
        String[][] data = new String[0][0];
        defaultFlowPathTable = new JTable(data, columnNames);

        defaultFlowPathScrollPane = new JScrollPane(defaultFlowPathTable);
        defaultFlowPathTable.setFillsViewportHeight(true);

        GroupLayout defaultPathTableLayout = new GroupLayout(defaultFlowPathPanel);
        defaultFlowPathPanel.setLayout(defaultPathTableLayout);

        defaultPathTableLayout.setHorizontalGroup(defaultPathTableLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(defaultFlowPathScrollPane, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
        );
        defaultPathTableLayout.setVerticalGroup(defaultPathTableLayout.createSequentialGroup()
                .addComponent(defaultFlowPathScrollPane, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
        );
        defaultFlowPathPanel.setVisible(false);
    }

    private void initFlowPrioritiesTablePanel() {
        flowPrioritiesPanel = new JPanel();
        flowPrioritiesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Flow Priorities"));

        String[] columnNames = {"FlowID", "Priority"};
        String[][] data = new String[0][0];
        flowPrioritiesTable = new JTable(data, columnNames);

        flowPrioritiesScrollPane = new JScrollPane(flowPrioritiesTable);
        flowPrioritiesTable.setFillsViewportHeight(true);

        GroupLayout flowPrioritiesTableLayout = new GroupLayout(flowPrioritiesPanel);
        flowPrioritiesPanel.setLayout(flowPrioritiesTableLayout);

        flowPrioritiesTableLayout.setHorizontalGroup(flowPrioritiesTableLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(flowPrioritiesScrollPane, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
        );
        flowPrioritiesTableLayout.setVerticalGroup(flowPrioritiesTableLayout.createSequentialGroup()
                .addComponent(flowPrioritiesScrollPane, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
        );
        defaultFlowPathPanel.setVisible(false);
    }

    private void initSendPacketPanel() {
        sendPacketPanel = new JPanel();
        sendPacketPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Send Packet"));

        sendPacketButton = new JButton("Send Packet");
        sendPacketButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonSendPacketActionPerfomed(evt);
            }
        });
        sendPacketButton.setEnabled(false);

        sendPacketDestinationIDLabel = new JLabel("Destination ID");
        sendMessageDestinationIDComboBox = new JComboBox();
//        sendMessageDestinationIDComboBox.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent evt) {
//                jComboBoxSendMessageDestinationIDActionPerfomed(evt);
//            }
//        });

        sendPacketFlowIDLabel = new JLabel("FlowID");
        sendPacketFlowIDComboBox = new JComboBox();

        initMulticastDestinationsPanel();

        sendPacketPayloadLabel = new JLabel("Payload kb");
        sendPacketPayloadTextField = new JTextField("65536");

        sendPacketRepetitionLabel = new JLabel("Repeat");
        sendPacketRepetitionTextField = new JTextField("1");

        GroupLayout sendMessageClientLayout = new GroupLayout(sendPacketPanel);
        sendPacketPanel.setLayout(sendMessageClientLayout);

        sendMessageClientLayout.setHorizontalGroup(sendMessageClientLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(sendPacketDestinationIDLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendMessageDestinationIDComboBox, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketFlowIDLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketFlowIDComboBox, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(multicastDestinationsPanel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketPayloadLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketPayloadTextField, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketRepetitionLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketRepetitionTextField, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketButton, GroupLayout.DEFAULT_SIZE, 120, 120)
        );
        sendMessageClientLayout.setVerticalGroup(sendMessageClientLayout.createSequentialGroup()
                .addComponent(sendPacketDestinationIDLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
                .addComponent(sendMessageDestinationIDComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
                .addComponent(sendPacketFlowIDLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
                .addComponent(sendPacketFlowIDComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addComponent(multicastDestinationsPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
                .addComponent(sendPacketPayloadLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
                .addComponent(sendPacketPayloadTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
                .addComponent(sendPacketRepetitionLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
                .addComponent(sendPacketRepetitionTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
                .addComponent(sendPacketButton, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
        );
        if (SDNcontrollerClient.getFlowPolicy() != FlowPolicy.MULTICASTING) {
            multicastDestinationsPanel.setVisible(false);
        } else {
            sendPacketDestinationIDLabel.setVisible(false);
            sendMessageDestinationIDComboBox.setVisible(false);
        }
    }

    private void initMulticastDestinationsPanel() {
        multicastDestinationsPanel = new JPanel();

        multicastDestinationsLabel = new JLabel("Multicast Destinations");

        multicastDestinationsTextArea = new JTextArea();
        multicastDestinationsTextArea.setLineWrap(true);
        multicastDestinationsTextArea.setColumns(20);
        multicastDestinationsTextArea.setRows(5);

        multicastDestinationsScrollPane = new JScrollPane();
        multicastDestinationsScrollPane.setViewportView(multicastDestinationsTextArea);

        GroupLayout layout = new GroupLayout(multicastDestinationsPanel);
        multicastDestinationsPanel.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(multicastDestinationsLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(multicastDestinationsScrollPane, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGap(5)
                .addComponent(multicastDestinationsLabel)
                .addGap(5)
                .addComponent(multicastDestinationsScrollPane)
        );
    }

    private void initReceivePacketPanel() {
        receivePacketPanel = new JPanel();
        receivePacketPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Received Packets"));

        receivePacketButton = new JButton("Update Log");
        receivePacketButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonReceivePacketActionPerfomed(evt);
            }
        });

        deleteReceivedPacketsButton = new JButton("Clear Log");
        deleteReceivedPacketsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonDeletePacketActionPerfomed(evt);
            }
        });

        receivePacketTextArea = new JTextArea();
        receivePacketTextArea.setLineWrap(true);
        receivePacketTextArea.setColumns(20);
        receivePacketTextArea.setRows(5);

        receivePacketScrollPane = new JScrollPane();
        receivePacketScrollPane.setViewportView(receivePacketTextArea);

        GroupLayout receiveMessageClientLayout = new GroupLayout(receivePacketPanel);
        receivePacketPanel.setLayout(receiveMessageClientLayout);

        receiveMessageClientLayout.setHorizontalGroup(receiveMessageClientLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(receiveMessageClientLayout.createSequentialGroup()
                        .addComponent(receivePacketButton, GroupLayout.DEFAULT_SIZE, 130, Short.MAX_VALUE)
                        .addGap(20)
                        .addComponent(deleteReceivedPacketsButton, GroupLayout.DEFAULT_SIZE, 130, Short.MAX_VALUE)
                )
                .addComponent(receivePacketScrollPane, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
        receiveMessageClientLayout.setVerticalGroup(receiveMessageClientLayout.createSequentialGroup()
                .addGroup(receiveMessageClientLayout.createParallelGroup()
                        .addComponent(receivePacketButton, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        .addComponent(deleteReceivedPacketsButton, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                )
                .addGap(5)
                .addComponent(receivePacketScrollPane, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
        );
    }

    private void initRefreshInfoPanel() {
        refreshInfoPanel = new JPanel();
        refreshInfoPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Info Controller"));

        refreshInfoButton = new JButton("Refresh Info");
        refreshInfoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonRefreshInfoActionPerfomed(evt);
            }
        });

        GroupLayout refreshInfoLayout = new GroupLayout(refreshInfoPanel);
        refreshInfoPanel.setLayout(refreshInfoLayout);
        refreshInfoLayout.setHorizontalGroup(refreshInfoLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(refreshInfoButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
        );
        refreshInfoLayout.setVerticalGroup(refreshInfoLayout.createSequentialGroup()
                .addComponent(refreshInfoButton)
        );
    }

    private void jButtonSetApplicationRequirementsActionPerfomed(ActionEvent evt) {
        applicationRequirements.setApplicationType(ApplicationRequirements.ApplicationType.valueOf(applicationTypeComboBox.getSelectedItem().toString()));

        int bitrate = Integer.parseInt(bitrateTextField.getText());
        if (bitrate == -1) {
            bitrate = ApplicationRequirements.UNUSED_FIELD;
        }
        applicationRequirements.setBitrate(bitrate);

        int trafficAmount = Integer.parseInt(trafficAmountTextField.getText());
        if (trafficAmount == -1) {
            trafficAmount = ApplicationRequirements.UNUSED_FIELD;
        }
        applicationRequirements.setTrafficAmount(trafficAmount);

        int secondsToStart = Integer.parseInt(secondsToStartTextField.getText());
        if (secondsToStart < 0) {
            secondsToStart = 0;
            secondsToStartTextField.setText("" + secondsToStart);
        }
        applicationRequirements.setSecondsToStart(secondsToStart);

        int duration = Integer.parseInt(durationTextField.getText());
        if (duration < 0) {
            duration = 0;
            durationTextField.setText("" + duration);
        }
        applicationRequirements.setDuration(duration);

        System.out.println("SDNControllerClientJFrame: Applications Requirements updated");
        System.out.println(applicationRequirements.toString());
    }

    private void jButtonGetFlowIDActionPerfomed(ActionEvent evt) {
        FlowPolicy flowPolicy = SDNcontrollerClient.getFlowPolicy();

        String selectedPathSelectionMetric = availablePathSelectionMetricComboBox.getSelectedItem().toString();
        TopologyGraphSelector.PathSelectionMetric pathSelectionMetric = TopologyGraphSelector.PathSelectionMetric.valueOf(selectedPathSelectionMetric);
        int destinationNodeId = Integer.parseInt(getFlowIDDestinationNodeComboBox.getSelectedItem().toString());
        int[] destNodeIds = null;
        int[] destPorts = null;

        if (flowPolicy == FlowPolicy.MULTICASTING) {
            int count = 1 + allAdditionalDestinationNodeComboBox.size();
            destNodeIds = new int[count];
            destPorts = new int[count];

            int index = 0;
            destNodeIds[index] = destinationNodeId;
            destPorts[index] = availableClients.get(destinationNodeId).getServerPort();
            index++;

            for (int i = 0; i < allAdditionalDestinationNodeComboBox.size(); i++) {
                int additionalDestinationNodeId = Integer.parseInt(allAdditionalDestinationNodeComboBox.get(i).getSelectedItem().toString());
                destNodeIds[index] = additionalDestinationNodeId;
                destPorts[index] = availableClients.get(additionalDestinationNodeId).getServerPort();
                index++;
            }
        } else {
            destNodeIds = new int[]{destinationNodeId};
        }

        int flowId = SDNcontrollerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, pathSelectionMetric);
        if (SDNcontrollerClient.getFlowPolicy() != FlowPolicy.MULTICASTING) {
            if (!unicastFlowIDs.get(destinationNodeId).contains(flowId)) {
                unicastFlowIDs.get(destinationNodeId).add(flowId);
            }

            if (flowIDComboBoxActionListener != null) {
                sendPacketFlowIDComboBox.removeActionListener(flowIDComboBoxActionListener);
                flowIDComboBoxActionListener = null;
            }

            if (destionationIDComboBoxActionListener != null) {
                destionationIDComboBoxActionListener = new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent evt) {
                        jComboBoxSendMessageDestinationIDActionPerfomed(evt);
                    }
                };
                sendMessageDestinationIDComboBox.addActionListener(destionationIDComboBoxActionListener);
            }

            // Update the flow available in the send message panel for the current destination node id if it is already selected
            jComboBoxSendMessageDestinationIDActionPerfomed(null);
        } else {
            multicastFlowIDs.put(flowId, new ArrayList<Integer>());
            multicastFlowIDs.get(flowId).add(destinationNodeId);
            for (int i = 0; i < allAdditionalDestinationNodeComboBox.size(); i++) {
                int additionalDestinationNodeId = Integer.parseInt(allAdditionalDestinationNodeComboBox.get(i).getSelectedItem().toString());
                multicastFlowIDs.get(flowId).add(additionalDestinationNodeId);
            }

            if (destionationIDComboBoxActionListener != null) {
                sendMessageDestinationIDComboBox.removeActionListener(destionationIDComboBoxActionListener);
                destionationIDComboBoxActionListener = null;
            }

            if (flowIDComboBoxActionListener != null) {
                flowIDComboBoxActionListener = new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent evt) {
                        jComboBoxSendMessageFlowIDActionPerfomed(evt);
                    }
                };
                sendPacketFlowIDComboBox.addActionListener(flowIDComboBoxActionListener);
            }

            fillMulticastFlowIdComboBox();

            // Update the destinations available in the send message panel for the current multicast flow id if it is already selected
            jComboBoxSendMessageFlowIDActionPerfomed(null);
        }

        sendPacketButton.setEnabled(true);
    }

    private void fillMulticastFlowIdComboBox() {
        String[] items = new String[multicastFlowIDs.keySet().size()];
        int count = 0;
        for (Integer flowID : multicastFlowIDs.keySet()) {
            items[count] = "" + flowID;
            count++;
        }
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(items);
        sendPacketFlowIDComboBox.setModel(dcm);
    }

    private void jButtonFindControllerClientReceiverActionPerfomed(ActionEvent evt) {
        FlowPolicy flowPolicy = SDNcontrollerClient.getFlowPolicy();

        findControllerClientReceiverTextArea.setText("");
        getFlowIdButton.setEnabled(false);
        sendPacketButton.setEnabled(false);
        if (flowPolicy == FlowPolicy.MULTICASTING) {
            addDestinationButton.setEnabled(false);
            resetDestinationButton.setEnabled(false);
        }

        String[] emptyList = {};
        DefaultComboBoxModel emptyDcbm = new DefaultComboBoxModel(emptyList);
        getFlowIDDestinationNodeComboBox.setModel(emptyDcbm);

        try {
            String selectedProtocol = protocolComboBox.getSelectedItem().toString();
            int protocol = -1;
            if(selectedProtocol == "UDP") {
                protocol = E2EComm.UDP;
            } else {
                protocol = E2EComm.TCP;
            }
            int ttl = Integer.parseInt(findControllerClientTTLTextField.getText());
            int timeout = Integer.parseInt(findControllerClientTimeoutTextField.getText());
            int serviceAmount = Integer.parseInt(findControllerClientServiceAmountTextField.getText());

            Vector<ServiceResponse> serviceResponses = SDNcontrollerClient.findControllerClientReceiver(protocol, ttl, timeout, serviceAmount);
            // Populate availableServices excluding the currentNode
            int availableResponsesLen = serviceResponses.size() - 1;
            availableServices = new Vector<ServiceResponse>(availableResponsesLen);
            availableClients = new HashMap<Integer, ServiceResponse>();

            // Remove
            int j = 0;
            for (int i = 0; i < serviceResponses.size(); i++) {
                if (serviceResponses.elementAt(i).getServerNodeId() != this.localNodeID) {
                    availableClients.put(serviceResponses.elementAt(i).getServerNodeId(), serviceResponses.elementAt(i));
                    availableServices.add(j, serviceResponses.elementAt(i));
                    j++;
                }
            }

            if (flowPolicy == FlowPolicy.MULTICASTING) {
                multicastFlowIDs = new HashMap<Integer, List<Integer>>();
            } else {
                // Initialise the flowID information for the new current Nodes
                unicastFlowIDs = new HashMap<Integer, List<Integer>>();
            }

            String text = "";
            int serverNodeId;
            String[] items = new String[availableServices.size()];
            for (int i = 0; i < availableServices.size(); i++) {
                ServiceResponse element = availableServices.elementAt(i);
                text += element + "\n";
                serverNodeId = element.getServerNodeId();
                items[i] = "" + serverNodeId;
                if (flowPolicy != FlowPolicy.MULTICASTING) {
                    unicastFlowIDs.put(serverNodeId, new ArrayList<Integer>());
                }
            }
            findControllerClientReceiverTextArea.setText(text);
            DefaultComboBoxModel dcm = new DefaultComboBoxModel(items);

            getFlowIDDestinationNodeComboBox.setModel(dcm);

            if (flowPolicy != FlowPolicy.MULTICASTING) {
                sendMessageDestinationIDComboBox.setModel(dcm);
            }

            if (availableServices.size() > 0) {
                getFlowIdButton.setEnabled(true);
                sendPacketButton.setEnabled(true);
                if (flowPolicy == FlowPolicy.MULTICASTING && availableServices.size() > 1) {
                    addDestinationButton.setEnabled(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void jButtonSendPacketActionPerfomed(ActionEvent evt) {
        int flowId = Integer.parseInt(sendPacketFlowIDComboBox.getSelectedItem().toString());
        int payload = Integer.parseInt(sendPacketPayloadTextField.getText());
        int repetitions = Integer.parseInt(sendPacketRepetitionTextField.getText());
        if (SDNcontrollerClient.getFlowPolicy() != FlowPolicy.MULTICASTING) {
            int destinationNodeIdIndex = sendMessageDestinationIDComboBox.getSelectedIndex();
            ServiceResponse destinationNodeServiceResponse = availableServices.elementAt(destinationNodeIdIndex);
            SDNcontrollerClient.sendUnicastMessage(destinationNodeServiceResponse, payload, flowId, repetitions);
        } else {
            // TODO improve protocol retrieval
            int genericDestination = multicastFlowIDs.get(flowId).get(0);
            int protocol = availableClients.get(genericDestination).getProtocol();
            SDNcontrollerClient.sendMulticastMessage(multicastFlowIDs.get(flowId), payload, flowId, protocol, repetitions);
        }

    }

    private void jButtonReceivePacketActionPerfomed(ActionEvent evt) {
        String res = "";
        Vector<String> messageList = SDNcontrollerClient.getReceivedMessages();
        for (String mess : messageList) {
            res += mess + "\n";
        }
        receivePacketTextArea.setText(res);
    }

    private void jButtonDeletePacketActionPerfomed(ActionEvent evt) {
        SDNcontrollerClient.resetReceivedMessages();
        this.jButtonReceivePacketActionPerfomed(null);
    }

    private void jButtonRefreshInfoActionPerfomed(ActionEvent evt) {
        /* Update Current Flow Policy */
        FlowPolicy currentFlowPolicy = SDNcontrollerClient.getFlowPolicy();
        currentPolicyTextField.setText(currentFlowPolicy.toString());

        if (currentFlowPolicy == FlowPolicy.MULTICASTING) {
            additionalDestinationPanel.setVisible(true);
            multicastDestinationsPanel.setVisible(true);
            sendPacketDestinationIDLabel.setVisible(false);
            sendMessageDestinationIDComboBox.setVisible(false);
            if (availableServices != null && availableServices.size() > 1) {
                addDestinationButton.setEnabled(true);
            }
            multicastFlowIDs = new HashMap<Integer, List<Integer>>();
        } else {
            additionalDestinationPanel.setVisible(false);
            multicastDestinationsPanel.setVisible(false);
            sendPacketDestinationIDLabel.setVisible(true);
            sendMessageDestinationIDComboBox.setVisible(true);
        }

        /* Show tables according to the Current Flow Policy */
        showAndUpdateTablesPolicy();
    }

    private void formWindowClosed(java.awt.event.WindowEvent evt) {

    }

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        System.out.println("SDNControllerClientJFrame: formWindowClosing");
        this.SDNcontrollerClient.stopClient();
    }

    private void showAndUpdateTablesPolicy() {
        defaultFlowPathPanel.setVisible(false);
        flowPrioritiesPanel.setVisible(false);

        FlowPolicy currentFlowPolicy = SDNcontrollerClient.getFlowPolicy();
        if (currentFlowPolicy == FlowPolicy.REROUTING) {
            defaultFlowPathPanel.setVisible(true);
            /* Update Default Flow Path Table */
            DefaultTableModel dtm = new DefaultTableModel();
            dtm.addColumn("FlowID");
            dtm.addColumn("Path");
            ConcurrentHashMap<Integer, PathDescriptor> defaultFlowPath = SDNcontrollerClient.getDefaultFlowPath();

            for (Map.Entry<Integer, PathDescriptor> entry : defaultFlowPath.entrySet()) {
                String flowId = entry.getKey().toString();
                String[] path = entry.getValue().getPath();
                int pathLen = path.length;
                String pathString = "";
                for (int i = 0; i < pathLen; i++) {
                    if (i == pathLen - 1) {
                        pathString += path[i];
                    } else {
                        pathString += path[i] + " - ";
                    }
                }
                dtm.addRow(new String[]{flowId, pathString});
            }

            defaultFlowPathTable.setModel(dtm);
        } else if (currentFlowPolicy != FlowPolicy.MULTICASTING) {
            flowPrioritiesPanel.setVisible(true);
            /* Update Default Flow Path Table */
            DefaultTableModel dtm = new DefaultTableModel();
            dtm.addColumn("FlowID");
            dtm.addColumn("Priority");
            ConcurrentHashMap<Integer, Integer> flowPriorities = SDNcontrollerClient.getFlowPriorities();

            for (Map.Entry<Integer, Integer> entry : flowPriorities.entrySet()) {
                String flowId = entry.getKey().toString();
                String priority = entry.getValue().toString();
                dtm.addRow(new String[]{flowId, priority});
            }
            defaultFlowPathTable.setModel(dtm);
        }
    }

    private void jComboBoxApplicationTypeActionPerfomed(ActionEvent evt) {
        showApplicationRequirementsFieldsPolicy();
    }

    private void jComboBoxSendMessageDestinationIDActionPerfomed(ActionEvent evt) {
        int selectedDestinationNodeId = Integer.parseInt(sendMessageDestinationIDComboBox.getSelectedItem().toString());
        int count = unicastFlowIDs.get(selectedDestinationNodeId).size();
        String[] flowIdsItems = new String[count];
        count = 0;
        for (Integer f : unicastFlowIDs.get(selectedDestinationNodeId)) {
            flowIdsItems[count] = f.toString();
            count++;
        }
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(flowIdsItems);
        sendPacketFlowIDComboBox.setModel(dcm);
    }

    private void jComboBoxSendMessageFlowIDActionPerfomed(ActionEvent evt) {
        int selectedMulticastFlowId = Integer.parseInt(sendPacketFlowIDComboBox.getSelectedItem().toString());
        String text = "";
        for (Integer d : multicastFlowIDs.get(selectedMulticastFlowId)) {
            text += d + "\n";
        }
        multicastDestinationsTextArea.setText(text);
    }
}
