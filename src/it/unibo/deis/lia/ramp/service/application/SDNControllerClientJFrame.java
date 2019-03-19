package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.e2e.E2EComm;
import it.unibo.deis.lia.ramp.core.internode.*;
import it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy.TrafficEngineeringPolicy;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationRequirements;
import it.unibo.deis.lia.ramp.core.internode.sdn.applicationRequirements.ApplicationType;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.PathSelectionMetric;
import it.unibo.deis.lia.ramp.core.internode.sdn.pathSelection.pathDescriptors.PathDescriptor;
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

    private int localNodeID;
    /**
     * This map keeps track of all flowIds (List<Integer>) obtained
     * for a given destination (Integer) for unicast communications.
     */
    private Map<Integer, List<Integer>> unicastFlowIDs;
    /**
     * This map keeps track for of all flowIds (Integer) to the correspondent
     * destinations (List<Integer>) for multicast communications.
     */
    private Map<Integer, List<Integer>> multicastFlowIDs;

    /**
     * This map keeps track of all routeIds (Integer) obtained
     * for a given destination (Integer) for os level routing communications.
     */
    private Map<String, List<Integer>> routeIDs;

    /**
     * This map keep tracks for each available destination
     * the correspondent service response.
     */
    private Map<String, ServiceResponse> availableClients;

    private JPanel trafficEngineeringPolicyPanel;
    private JTextField currentTrafficEngineeringPolicyTextField;

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

    private JPanel getOSLevelRoutePanel;
    private JLabel getOSLevelRouteLabel;
    private JComboBox getOSLevelRouteDestinationNodeComboBox;
    private JButton getOSLevelRouteButton;

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
    private ActionListener destinationIDComboBoxActionListener;
    private JLabel sendPacketFlowIDLabel;
    private JComboBox sendPacketFlowIDComboBox;
    private JLabel sendPacketRouteIDLabel;
    private JComboBox sendPacketRouteIDComboBox;
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
        destinationIDComboBoxActionListener = null;
        flowIDComboBoxActionListener = null;

        initComponents();
    }

    private void initComponents() {
        initTrafficEngineeringPolicyPanel();
        initApplicationRequirementsPanel();
        initPathSelectionMetricPanel();
        initFindControllerPanel();
        initGetFlowIDPanel();
        initGetOSLevelRoutePanel();
        initDefaultPathTablePanel();
        initFlowPrioritiesTablePanel();
        initSendPacketPanel();
        initReceivePacketPanel();
        initRefreshInfoPanel();

        /*
         * Main Panel Layout
         */
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
                                        .addComponent(trafficEngineeringPolicyPanel, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(applicationRequirementsPanel, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(pathSelectionMetricPanel, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
                                )
                                .addGap(20)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(findControllerClientReceiverPanel, GroupLayout.PREFERRED_SIZE, 550, GroupLayout.PREFERRED_SIZE)
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(getFlowIDPanel, GroupLayout.PREFERRED_SIZE, 245, GroupLayout.PREFERRED_SIZE)
                                                .addComponent(getOSLevelRoutePanel, GroupLayout.PREFERRED_SIZE, 245, GroupLayout.PREFERRED_SIZE)
                                                .addGap(10)
                                                .addComponent(defaultFlowPathPanel, GroupLayout.PREFERRED_SIZE, 295, GroupLayout.PREFERRED_SIZE)
                                                .addComponent(flowPrioritiesPanel, GroupLayout.PREFERRED_SIZE, 295, GroupLayout.PREFERRED_SIZE)
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
                                .addComponent(trafficEngineeringPolicyPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
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
                                        .addComponent(getOSLevelRoutePanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
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

    private void initTrafficEngineeringPolicyPanel() {
        trafficEngineeringPolicyPanel = new JPanel();
        trafficEngineeringPolicyPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Traffic Engineering Policy"));

        currentTrafficEngineeringPolicyTextField = new JTextField(SDNcontrollerClient.getTrafficEngineeringPolicy().toString());
        currentTrafficEngineeringPolicyTextField.setEditable(false);

        GroupLayout trafficEngineeringPolicyLayout = new GroupLayout(trafficEngineeringPolicyPanel);
        trafficEngineeringPolicyPanel.setLayout(trafficEngineeringPolicyLayout);
        trafficEngineeringPolicyLayout.setHorizontalGroup(trafficEngineeringPolicyLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(currentTrafficEngineeringPolicyTextField, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
        trafficEngineeringPolicyLayout.setVerticalGroup(trafficEngineeringPolicyLayout.createSequentialGroup()
                .addComponent(currentTrafficEngineeringPolicyTextField)
        );
    }

    private void initApplicationRequirementsPanel() {
        applicationRequirements = new ApplicationRequirements(ApplicationType.DEFAULT, ApplicationRequirements.UNUSED_FIELD, ApplicationRequirements.UNUSED_FIELD, 0, 300);

        applicationRequirementsPanel = new JPanel();
        applicationRequirementsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Application Requirements"));

        applicationTypeLabel = new JLabel("Application Type");
        applicationTypeComboBox = new JComboBox();
        int count = ApplicationType.values().length;
        String[] applicationTypeItems = new String[count];
        count = 0;
        for (ApplicationType a : ApplicationType.values()) {
            applicationTypeItems[count] = a.toString();
            count++;
        }
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(applicationTypeItems);
        applicationTypeComboBox.setModel(dcm);
        applicationTypeComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jComboBoxApplicationTypeActionPerformed(evt);
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
                jButtonSetApplicationRequirementsActionPerformed(evt);
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
        if (ApplicationType.valueOf(applicationTypeComboBox.getSelectedItem().toString()) == ApplicationType.DEFAULT) {
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
                jButtonFindControllerClientReceiverActionPerformed(evt);
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
        findControllerClientServiceAmountTextField = new JTextField("6");

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
                jButtonGetFlowIDActionPerformed(evt);
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
                jButtonAddDestinationActionPerformed(evt);
            }
        });

        resetDestinationButton = new JButton("Reset");
        resetDestinationButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonResetDestinationActionPerformed(evt);
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

        /*
         * Print again the JFrame to perform layout recalculation
         */
        this.repaint();
        this.revalidate();
    }

    private void jButtonAddDestinationActionPerformed(ActionEvent evt) {
        JComboBox comboBox = new JComboBox();
        int availableClientsSize = availableClients.keySet().size();
        String[] items = new String[availableClientsSize];
        int i = 0;
        for (String key : availableClients.keySet()) {
            items[i] = "" + key;
            i++;
        }

        comboBox.setModel(new DefaultComboBoxModel(items));
        allAdditionalDestinationNodeComboBox.add(comboBox);
        refreshAdditionalComboBoxPanel();
    }

    private void jButtonResetDestinationActionPerformed(ActionEvent evt) {
        this.allAdditionalDestinationNodeComboBox = new Vector<>(0);
        refreshAdditionalComboBoxPanel();
    }

    private void initGetOSLevelRoutePanel() {
        getOSLevelRoutePanel = new JPanel();
        getOSLevelRoutePanel.setBorder(javax.swing.BorderFactory.createTitledBorder("OS Level Routing"));

        getOSLevelRouteLabel = new JLabel("Destination Node ID:");

        getOSLevelRouteDestinationNodeComboBox = new JComboBox();
        String[] empty = new String[0];
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(empty);
        getOSLevelRouteDestinationNodeComboBox.setModel(dcm);

        getOSLevelRouteButton = new JButton("Get Route");
        getOSLevelRouteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonGetOSLevelRouteActionPerformed(evt);
            }
        });
        getOSLevelRouteButton.setEnabled(false);

        GroupLayout getOSLevelRouteLayout = new GroupLayout(getOSLevelRoutePanel);
        getOSLevelRoutePanel.setLayout(getOSLevelRouteLayout);
        getOSLevelRouteLayout.setHorizontalGroup(getOSLevelRouteLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(getOSLevelRouteLabel, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                .addComponent(getOSLevelRouteDestinationNodeComboBox, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                .addComponent(getOSLevelRouteButton, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
        );
        getOSLevelRouteLayout.setVerticalGroup(getOSLevelRouteLayout.createSequentialGroup()
                .addComponent(getOSLevelRouteLabel)
                .addGap(5)
                .addComponent(getOSLevelRouteDestinationNodeComboBox)
                .addGap(5)
                .addComponent(getOSLevelRouteButton)
        );
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
                jButtonSendPacketActionPerformed(evt);
            }
        });
        sendPacketButton.setEnabled(false);

        sendPacketDestinationIDLabel = new JLabel("Destination ID");
        sendMessageDestinationIDComboBox = new JComboBox();

        sendPacketFlowIDLabel = new JLabel("FlowID");
        sendPacketFlowIDComboBox = new JComboBox();

        sendPacketRouteIDLabel = new JLabel("RouteID");
        sendPacketRouteIDComboBox = new JComboBox();

        initMulticastDestinationsPanel();

        sendPacketPayloadLabel = new JLabel("Payload kb");
        sendPacketPayloadTextField = new JTextField("500");

        sendPacketRepetitionLabel = new JLabel("Repeat");
        sendPacketRepetitionTextField = new JTextField("1");

        GroupLayout sendMessageClientLayout = new GroupLayout(sendPacketPanel);
        sendPacketPanel.setLayout(sendMessageClientLayout);

        sendMessageClientLayout.setHorizontalGroup(sendMessageClientLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(sendPacketDestinationIDLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendMessageDestinationIDComboBox, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketFlowIDLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketFlowIDComboBox, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketRouteIDLabel, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addComponent(sendPacketRouteIDComboBox, GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
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
                .addComponent(sendPacketFlowIDComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE).addComponent(sendPacketRouteIDLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
                .addComponent(sendPacketRouteIDComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                .addGap(5)
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
                jButtonReceivePacketActionPerformed(evt);
            }
        });

        deleteReceivedPacketsButton = new JButton("Clear Log");
        deleteReceivedPacketsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonDeletePacketActionPerformed(evt);
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
                jButtonRefreshInfoActionPerformed(evt);
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

    private void jButtonSetApplicationRequirementsActionPerformed(ActionEvent evt) {
        applicationRequirements.setApplicationType(ApplicationType.valueOf(applicationTypeComboBox.getSelectedItem().toString()));

        int bitrate = Integer.parseInt(bitrateTextField.getText());
        if (bitrate < 0) {
            bitrate = ApplicationRequirements.UNUSED_FIELD;
        }
        applicationRequirements.setBitrate(bitrate);

        int trafficAmount = Integer.parseInt(trafficAmountTextField.getText());
        if (trafficAmount < 0) {
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

    private void jButtonGetFlowIDActionPerformed(ActionEvent evt) {
        TrafficEngineeringPolicy trafficEngineeringPolicy = SDNcontrollerClient.getTrafficEngineeringPolicy();

        String selectedPathSelectionMetric = availablePathSelectionMetricComboBox.getSelectedItem().toString();
        PathSelectionMetric pathSelectionMetric = PathSelectionMetric.valueOf(selectedPathSelectionMetric);
        String destinationNode = getFlowIDDestinationNodeComboBox.getSelectedItem().toString();
        int destinationNodeId = availableClients.get(destinationNode).getServerNodeId();
        int[] destNodeIds = null;
        int[] destPorts = null;

        /*
         * Retrieve destNodeIds and destPorts according to the trafficEngineeringPolicy.
         */
        if (trafficEngineeringPolicy == TrafficEngineeringPolicy.MULTICASTING) {
            /*
             * Add 1 in order to take into account also the first ComboBox called getFlowIDDestinationNodeComboBox
             */
            int count = 1 + allAdditionalDestinationNodeComboBox.size();
            destNodeIds = new int[count];
            destPorts = new int[count];

            int index = 0;
            destNodeIds[index] = destinationNodeId;
            destPorts[index] = availableClients.get(destinationNode).getServerPort();
            index++;

            for (int i = 0; i < allAdditionalDestinationNodeComboBox.size(); i++) {
                String additionalDestinationNode = allAdditionalDestinationNodeComboBox.get(i).getSelectedItem().toString();
                destNodeIds[index] = availableClients.get(additionalDestinationNode).getServerNodeId();
                destPorts[index] = availableClients.get(additionalDestinationNode).getServerPort();
                index++;
            }
        } else {
            destNodeIds = new int[]{destinationNodeId};
        }

        /*
         * get the flowId to use
         */
        int flowId = SDNcontrollerClient.getFlowId(applicationRequirements, destNodeIds, destPorts, pathSelectionMetric);

        /*
         * Update the local data structures with the obtained flowId and fill the combo boxes according
         * to the current trafficEngineeringPolicy.
         */
        if (trafficEngineeringPolicy == TrafficEngineeringPolicy.MULTICASTING) {
            multicastFlowIDs.put(flowId, new ArrayList<>());
            multicastFlowIDs.get(flowId).add(destinationNodeId);
            for (int i = 0; i < allAdditionalDestinationNodeComboBox.size(); i++) {
                String additionalDestinationNode = allAdditionalDestinationNodeComboBox.get(i).getSelectedItem().toString();
                int additionalDestinationNodeId = availableClients.get(additionalDestinationNode).getServerNodeId();
                multicastFlowIDs.get(flowId).add(additionalDestinationNodeId);
            }

            /*
             * Remove the action listener active when a TrafficEngineeringPolicy different from MULTICASTING
             * was active.
             */
            if (destinationIDComboBoxActionListener != null) {
                sendMessageDestinationIDComboBox.removeActionListener(destinationIDComboBoxActionListener);
                destinationIDComboBoxActionListener = null;
            }

            flowIDComboBoxActionListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    jComboBoxSendMessageFlowIDActionPerformed(evt);
                }
            };
            sendPacketFlowIDComboBox.addActionListener(flowIDComboBoxActionListener);


            fillMulticastFlowIdComboBox();

            /*
             * Update the destinations available in the send message panel
             * for the current multicast flow id if it is already selected.
             */
            jComboBoxSendMessageFlowIDActionPerformed(null);
        } else {
            if (!unicastFlowIDs.get(destinationNodeId).contains(flowId)) {
                unicastFlowIDs.get(destinationNodeId).add(flowId);
            }

            /*
             * Remove the action listener active when TrafficEngineeringPolicy.MULTICASTING
             * was active.
             */
            if (flowIDComboBoxActionListener != null) {
                sendPacketFlowIDComboBox.removeActionListener(flowIDComboBoxActionListener);
                flowIDComboBoxActionListener = null;
            }

            if (destinationIDComboBoxActionListener != null) {
                sendMessageDestinationIDComboBox.removeActionListener(destinationIDComboBoxActionListener);
                destinationIDComboBoxActionListener = null;
            }

            destinationIDComboBoxActionListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    jComboBoxSendMessageDestinationIDActionPerformed(evt);
                }
            };
            sendMessageDestinationIDComboBox.addActionListener(destinationIDComboBoxActionListener);

            /*
             * Update the flowIds available in the send message panel
             * for the current destination node id if it is already selected.
             */
            jComboBoxSendMessageDestinationIDActionPerformed(null);
        }

        sendPacketButton.setEnabled(true);
    }

    private void jButtonGetOSLevelRouteActionPerformed(ActionEvent evt) {
        /*
         * Retrieve all the info needed to call the getRouteId method.
         */
        String selectedPathSelectionMetric = availablePathSelectionMetricComboBox.getSelectedItem().toString();
        PathSelectionMetric pathSelectionMetric = PathSelectionMetric.valueOf(selectedPathSelectionMetric);
        String destinationNode = getOSLevelRouteDestinationNodeComboBox.getSelectedItem().toString();
        int destinationNodeId = availableClients.get(destinationNode).getServerNodeId();

        int routeId = SDNcontrollerClient.getRouteId(destinationNodeId, -1, applicationRequirements, pathSelectionMetric);

        if (routeId != -1) {
            if (!routeIDs.get(destinationNode).contains(routeId)) {
                routeIDs.get(destinationNode).add(routeId);
            }

            /*
             * Remove the action listener active when TrafficEngineeringPolicy.MULTICASTING
             * was active.
             */
            if (flowIDComboBoxActionListener != null) {
                sendPacketFlowIDComboBox.removeActionListener(flowIDComboBoxActionListener);
                flowIDComboBoxActionListener = null;
            }

            if (destinationIDComboBoxActionListener != null) {
                sendMessageDestinationIDComboBox.removeActionListener(destinationIDComboBoxActionListener);
                destinationIDComboBoxActionListener = null;
            }

            destinationIDComboBoxActionListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    jComboBoxSendMessageRouteIDActionPerformed(evt);
                }
            };
            sendMessageDestinationIDComboBox.addActionListener(destinationIDComboBoxActionListener);

            /*
             * Update the routeIDs available in the send message panel
             * for the current destination node id if it is already selected.
             */
            jComboBoxSendMessageRouteIDActionPerformed(null);

        } else {
            JOptionPane.showMessageDialog(null, "It is not possible to compute a routeId for this destination.");
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

    private void jButtonFindControllerClientReceiverActionPerformed(ActionEvent evt) {
        TrafficEngineeringPolicy trafficEngineeringPolicy = SDNcontrollerClient.getTrafficEngineeringPolicy();

        /*
         * Stop any possible user interaction.
         */
        findControllerClientReceiverTextArea.setText("");
        getFlowIdButton.setEnabled(false);
        getOSLevelRouteButton.setEnabled(false);
        sendPacketButton.setEnabled(false);
        addDestinationButton.setEnabled(false);
        resetDestinationButton.setEnabled(false);

        /*
         * Clear all the comboBox
         */
        String[] emptyList = {};
        DefaultComboBoxModel emptyDcm = new DefaultComboBoxModel(emptyList);
        getFlowIDDestinationNodeComboBox.setModel(emptyDcm);
        getOSLevelRouteDestinationNodeComboBox.setModel(emptyDcm);

        this.repaint();
        this.revalidate();

        /*
         * Retrieve all the info in order to call the findControllerClientReceiver method.
         */
        String selectedProtocol = protocolComboBox.getSelectedItem().toString();
        int protocol;
        if (selectedProtocol.equals("UDP")) {
            protocol = E2EComm.UDP;
        } else {
            protocol = E2EComm.TCP;
        }
        int ttl = Integer.parseInt(findControllerClientTTLTextField.getText());
        int timeout = Integer.parseInt(findControllerClientTimeoutTextField.getText());
        int serviceAmount = Integer.parseInt(findControllerClientServiceAmountTextField.getText());

        /*
         * Find the available services.
         */
        Vector<ServiceResponse> serviceResponses = new Vector<>();
        try {
            serviceResponses = SDNcontrollerClient.findControllerClientReceiver(protocol, ttl, timeout, serviceAmount);
        } catch (Exception e) {
            e.printStackTrace();
        }
        availableClients = new HashMap<>();

        /* TODO Remove me
         * Populate the availableClients structure according to the current trafficEngineeringPolicy
         */
//        if (trafficEngineeringPolicy == TrafficEngineeringPolicy.OS_ROUTING) {
//            for (int i = 0; i < serviceResponses.size(); i++) {
//                /*
//                 * We omit to store the service available at this node.
//                 */
//                if (serviceResponses.elementAt(i).getServerNodeId() != this.localNodeID) {
//                    int getServerDestLen = serviceResponses.elementAt(i).getServerDest().length;
//                    String destinationIP = serviceResponses.elementAt(i).getServerDest()[getServerDestLen - 1];
//                    String key = "";
//                    if (getServerDestLen > 1) {
//                        /*
//                         * In case a service can be reached via different paths add also the viaIP information
//                         * to differentiate the ServiceResponse.
//                         */
//                        String viaIP = serviceResponses.elementAt(i).getServerDest()[0];
//                        key = "" + serviceResponses.elementAt(i).getServerNodeId() + "@" + destinationIP + "via" + viaIP;
//                    } else {
//                        /*
//                         * Only for OS_ROUTING add the destinationIP info in order to let the user
//                         * to understand better which are the reachable destination nodes.
//                         */
//                        key = "" + serviceResponses.elementAt(i).getServerNodeId() + "@" + destinationIP;
//                    }
//                    availableClients.put(key, serviceResponses.elementAt(i));
//                }
//            }
//
//            // TODO Improve routeIDs retrieval for example getting the info from the ControllerService.
//            routeIDs = new HashMap<>();
//        } else {
        for (ServiceResponse serviceResponse : serviceResponses) {
            String key = "" + serviceResponse.getServerNodeId();
            /*
             * We omit to store the service available at this node.
             */
            if (serviceResponse.getServerNodeId() != this.localNodeID && !availableClients.containsKey(key)) {
                availableClients.put(key, serviceResponse);
            }
        }

        switch (trafficEngineeringPolicy) {
            case MULTICASTING:
                // TODO Improve multicastFlowIDs retrieval for example getting the info from the ControllerService.
                multicastFlowIDs = new HashMap<>();
                break;
            case OS_ROUTING:
                // TODO Improve routeIDs retrieval for example getting the info from the ControllerService.
                routeIDs = new HashMap<>();
                break;
            default:
                break;
        }

//            if (trafficEngineeringPolicy == TrafficEngineeringPolicy.MULTICASTING) {
//                // TODO Improve multicastFlowIDs retrieval for example getting the info from the ControllerService.
//                multicastFlowIDs = new HashMap<>();
//            } else {
//                // TODO Improve unicastFlowIDs retrieval for example getting the info from the ControllerService.
//                unicastFlowIDs = new HashMap<>();
//            }
//        }

        String text = "";
        int availableClientsSize = availableClients.keySet().size();
        String[] items = new String[availableClientsSize];

        if (availableClientsSize > 0) {
            int serverNodeId;
            int i = 0;

            for (String key : availableClients.keySet()) {
                ServiceResponse sr = availableClients.get(key);
                text += sr + "\n";
                items[i] = key;
                i++;
                if (trafficEngineeringPolicy == TrafficEngineeringPolicy.OS_ROUTING) {
                    /* TODO Remove me
                     * For OS_ROUTING we still use the key in the form of nodeID@IP
                     * in order to distinguish at which IP we want to reach
                     * the client node.
                     */
                    if (!routeIDs.containsKey(key)) {
                        routeIDs.put(key, new ArrayList<>());
                    }
                } else if (trafficEngineeringPolicy != TrafficEngineeringPolicy.MULTICASTING) {
                    serverNodeId = sr.getServerNodeId();
                    if (!unicastFlowIDs.containsKey(serverNodeId)) {
                        unicastFlowIDs.put(serverNodeId, new ArrayList<>());
                    }
                }
            }

            /*
             * Fill the combo box according to the available reachable clients
             */
            DefaultComboBoxModel dcm = new DefaultComboBoxModel(items);
            DefaultComboBoxModel dcm2 = new DefaultComboBoxModel(items);

            switch (trafficEngineeringPolicy) {
                case OS_ROUTING:
                    getOSLevelRouteDestinationNodeComboBox.setModel(dcm);
                    getOSLevelRouteButton.setEnabled(true);
                    sendMessageDestinationIDComboBox.setModel(dcm2);
                    break;
                case MULTICASTING:
                    getFlowIDDestinationNodeComboBox.setModel(dcm);
                    getFlowIdButton.setEnabled(true);
                    if (availableClientsSize > 1) {
                        addDestinationButton.setEnabled(true);
                    }
                    break;
                default:
                    getFlowIDDestinationNodeComboBox.setModel(dcm);
                    getFlowIdButton.setEnabled(true);
                    sendMessageDestinationIDComboBox.setModel(dcm2);
                    break;
            }
        }
        /*
         * Display the available services.
         */
        findControllerClientReceiverTextArea.setText(text);
    }

    private void jButtonSendPacketActionPerformed(ActionEvent evt) {
        TrafficEngineeringPolicy trafficEngineeringPolicy = SDNcontrollerClient.getTrafficEngineeringPolicy();

        int payload = Integer.parseInt(sendPacketPayloadTextField.getText());
        int repetitions = Integer.parseInt(sendPacketRepetitionTextField.getText());
        String destinationNode;
        ServiceResponse destinationNodeServiceResponse;
        int flowId;
        int routeId;

        switch (trafficEngineeringPolicy) {
            case OS_ROUTING:
                routeId = Integer.parseInt(sendPacketRouteIDComboBox.getSelectedItem().toString());
                destinationNode = sendMessageDestinationIDComboBox.getSelectedItem().toString();
                destinationNodeServiceResponse = availableClients.get(destinationNode);
                String selectedProtocol = protocolComboBox.getSelectedItem().toString();
                if (selectedProtocol.equals("UDP")) {
                    SDNcontrollerClient.sendDatagramSocketMessage(destinationNodeServiceResponse, payload, routeId, repetitions);
                } else {
                    SDNcontrollerClient.sendServiceSocketMessage(destinationNodeServiceResponse, payload, routeId, repetitions);
                }
                break;
            case MULTICASTING:
                flowId = Integer.parseInt(sendPacketFlowIDComboBox.getSelectedItem().toString());
                // TODO improve protocol retrieval
                String genericDestination = multicastFlowIDs.get(flowId).get(0).toString();
                int protocol = availableClients.get(genericDestination).getProtocol();
                SDNcontrollerClient.sendMulticastMessage(multicastFlowIDs.get(flowId), payload, flowId, protocol, repetitions);
                break;
            default:
                flowId = Integer.parseInt(sendPacketFlowIDComboBox.getSelectedItem().toString());
                destinationNode = sendMessageDestinationIDComboBox.getSelectedItem().toString();
                destinationNodeServiceResponse = availableClients.get(destinationNode);
                SDNcontrollerClient.sendUnicastMessage(destinationNodeServiceResponse, payload, flowId, repetitions);
        }
    }

    private void jButtonReceivePacketActionPerformed(ActionEvent evt) {
        String res = "";
        Vector<String> messageList = SDNcontrollerClient.getReceivedMessages();
        for (String mess : messageList) {
            res += mess + "\n";
        }
        receivePacketTextArea.setText(res);
    }

    private void jButtonDeletePacketActionPerformed(ActionEvent evt) {
        SDNcontrollerClient.resetReceivedMessages();
        this.jButtonReceivePacketActionPerformed(null);
    }

    private void jButtonRefreshInfoActionPerformed(ActionEvent evt) {
        /*
         * Update Current TrafficEngineeringPolicy.
         */
        TrafficEngineeringPolicy currentTrafficEngineeringPolicy = SDNcontrollerClient.getTrafficEngineeringPolicy();
        currentTrafficEngineeringPolicyTextField.setText(currentTrafficEngineeringPolicy.toString());

        /*
         * Show tables according to the Current TrafficEngineeringPolicy.
         */
        showAndUpdateTablesPolicy();

        /*
         * Find ControllerClientReceivers according to the new policy.
         */
        jButtonFindControllerClientReceiverActionPerformed(null);
    }

    private void formWindowClosed(java.awt.event.WindowEvent evt) {

    }

    private void formWindowClosing(java.awt.event.WindowEvent evt) {
        System.out.println("SDNControllerClientJFrame: formWindowClosing");
        this.SDNcontrollerClient.stopClient();
    }

    private void showAndUpdateTablesPolicy() {
        defaultFlowPathPanel.setVisible(false);
        flowPrioritiesPanel.setVisible(false);
        getFlowIDPanel.setVisible(false);
        additionalDestinationPanel.setVisible(false);
        getOSLevelRoutePanel.setVisible(false);
        sendPacketDestinationIDLabel.setVisible(false);
        sendMessageDestinationIDComboBox.setVisible(false);
        multicastDestinationsPanel.setVisible(false);
        sendPacketFlowIDLabel.setVisible(false);
        sendPacketFlowIDComboBox.setVisible(false);
        sendPacketRouteIDLabel.setVisible(false);
        sendPacketRouteIDComboBox.setVisible(false);

        TrafficEngineeringPolicy currentTrafficEngineeringPolicy = SDNcontrollerClient.getTrafficEngineeringPolicy();
        DefaultTableModel dtm;
        switch (currentTrafficEngineeringPolicy) {
            case REROUTING:
                getFlowIDPanel.setVisible(true);
                defaultFlowPathPanel.setVisible(true);
                sendPacketDestinationIDLabel.setVisible(true);
                sendMessageDestinationIDComboBox.setVisible(true);
                sendPacketFlowIDLabel.setVisible(true);
                sendPacketFlowIDComboBox.setVisible(true);

                /*
                 * Update Default Flow Path Table
                 */
                dtm = new DefaultTableModel();
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
                break;
            case OS_ROUTING:
                getOSLevelRoutePanel.setVisible(true);
                sendPacketDestinationIDLabel.setVisible(true);
                sendMessageDestinationIDComboBox.setVisible(true);
                sendPacketRouteIDLabel.setVisible(true);
                sendPacketRouteIDComboBox.setVisible(true);
                break;
            case MULTICASTING:
                getFlowIDPanel.setVisible(true);
                additionalDestinationPanel.setVisible(true);
                multicastDestinationsPanel.setVisible(true);
                sendPacketFlowIDLabel.setVisible(true);
                sendPacketFlowIDComboBox.setVisible(true);

                if (availableClients != null && availableClients.keySet().size() > 1) {
                    addDestinationButton.setEnabled(true);
                }
                multicastFlowIDs = new HashMap<>();
            default:
                getFlowIDPanel.setVisible(true);
                flowPrioritiesPanel.setVisible(true);
                sendPacketDestinationIDLabel.setVisible(true);
                sendMessageDestinationIDComboBox.setVisible(true);
                sendPacketFlowIDLabel.setVisible(true);
                sendPacketFlowIDComboBox.setVisible(true);

                /*
                 * Update Default Flow Path Table
                 */
                dtm = new DefaultTableModel();
                dtm.addColumn("FlowID");
                dtm.addColumn("Priority");
                ConcurrentHashMap<Integer, Integer> flowPriorities = SDNcontrollerClient.getFlowPriorities();

                for (Map.Entry<Integer, Integer> entry : flowPriorities.entrySet()) {
                    String flowId = entry.getKey().toString();
                    String priority = entry.getValue().toString();
                    dtm.addRow(new String[]{flowId, priority});
                }
                defaultFlowPathTable.setModel(dtm);
                break;
        }
    }

    private void jComboBoxApplicationTypeActionPerformed(ActionEvent evt) {
        showApplicationRequirementsFieldsPolicy();
    }

    /**
     * This method working only when TrafficEngineeringPolicy.REROUTING || SINGLE_FLOW
     * || QUEUES || TRAFFIC_SHAPING is active
     * shows in the sendPacketFlowIDComboBox the flowID according
     * to the destinationId selected by the user.
     *
     * @param evt
     */
    private void jComboBoxSendMessageDestinationIDActionPerformed(ActionEvent evt) {
        String selectedDestinationNode = sendMessageDestinationIDComboBox.getSelectedItem().toString();
        int selectedDestinationNodeId = availableClients.get(selectedDestinationNode).getServerNodeId();

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

    /**
     * This method working only when TrafficEngineeringPolicy.MULTICASTING is active
     * shows in the multicastDestinationsTextArea the destination according
     * to the flowId selected by the user.
     *
     * @param evt
     */
    private void jComboBoxSendMessageFlowIDActionPerformed(ActionEvent evt) {
        int selectedMulticastFlowId = Integer.parseInt(sendPacketFlowIDComboBox.getSelectedItem().toString());
        String text = "";
        for (Integer d : multicastFlowIDs.get(selectedMulticastFlowId)) {
            text += d + "\n";
        }
        multicastDestinationsTextArea.setText(text);
    }

    /**
     * This method working only when TrafficEngineeringPolicy.OS_ROUTING is active
     * shows in the sendPacketRouteIDComboBox the routeID according
     * to the destinationId@IP selected by the user.
     *
     * @param evt
     */
    private void jComboBoxSendMessageRouteIDActionPerformed(ActionEvent evt) {
        String selectedDestinationNode = sendMessageDestinationIDComboBox.getSelectedItem().toString();

        int count = routeIDs.get(selectedDestinationNode).size();
        String[] routeIdsItems = new String[count];
        count = 0;
        for (Integer f : routeIDs.get(selectedDestinationNode)) {
            routeIdsItems[count] = f.toString();
            count++;
        }
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(routeIdsItems);
        sendPacketRouteIDComboBox.setModel(dcm);
    }
}
