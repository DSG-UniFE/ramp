package it.unibo.deis.lia.ramp.service.application;

import it.unibo.deis.lia.ramp.core.internode.sdn.routingPolicy.RoutingPolicy;
import it.unibo.deis.lia.ramp.core.internode.sdn.trafficEngineeringPolicy.TrafficEngineeringPolicy;
import org.graphstream.ui.swingViewer.DefaultView;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitrij David Padalino Montenero
 */
public class SDNControllerServiceJFrame extends JFrame {
    private SDNControllerService SDNControllerService;

    private JFileChooser fileChooser;

    private JPanel trafficEngineeringPolicyPanel;
    private JTextField currentTrafficEngineeringPolicyTextField;
    private JComboBox availableTrafficEngineeringPoliciesComboBox;
    private JButton updateTrafficEngineeringPolicyButton;

    private JPanel routingPolicyPanel;
    private JTextField currentRoutingPolicyTextField;
    private JComboBox availableRoutingPoliciesComboBox;
    private JButton updateRoutingPolicyButton;

    private JPanel activeClientsPanel;
    private JTextArea activeClientsTextArea;
    private JButton getActiveClientsButton;

    private JPanel displayGraphPanel;
    private JButton displayGraphButton;
    private JFrame displayGraphJFrame;

    private JPanel dataTypesPanel;
    private JButton getDataTypesButton;
    private JButton addDataTypeButton;
    private JTextArea dataTypesTextArea;

    private JPanel dataPlaneRulesPanel;
    private JButton getDataPlaneRulesButton;
    private JButton addDataPlaneRuleButton;
    private JTextArea dataPlaneRulesTextArea;
    private JLabel dataPlaneRulesDataTypeLabel;
    private JComboBox dataPlaneRulesAvailableDataTypesComboBox;
    private JLabel dataPlaneRulesRuleLabel;
    private JComboBox dataPlaneRulesAvailableRulesComboBox;
    private JButton addRuleButton;
    private JButton removeRuleButton;

    private JPanel dataPlaneActiveRulesPanel;
    private JScrollPane dataPlaneActiveRulesScrollPane;
    private JTable dataPlaneActiveRulesTable;
    private JButton dataPlaneActiveRulesRefreshButton;

    public SDNControllerServiceJFrame(SDNControllerService SDNControllerService) {
        this.SDNControllerService = SDNControllerService;
        initComponents();
    }

    private void initComponents() {
        fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());

        initTrafficEngineeringPolicyPanel();
        initRoutingPolicyPanel();
        initActiveClientsPanel();
        initDisplayGraphPanel();
        initDataTypesPanel();
        initDataPlaneRulesPanel();
        initDataPlaneActiveRulesPanel();

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
        setTitle("SDNControllerService");
        setLocationByPlatform(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());

        layout.setHorizontalGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup()
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(trafficEngineeringPolicyPanel, GroupLayout.PREFERRED_SIZE, 238, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(routingPolicyPanel, GroupLayout.PREFERRED_SIZE, 238, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(activeClientsPanel, GroupLayout.PREFERRED_SIZE, 238, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(displayGraphPanel, GroupLayout.PREFERRED_SIZE, 238, GroupLayout.PREFERRED_SIZE)
                                )
                                .addGap(20)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(dataTypesPanel, GroupLayout.PREFERRED_SIZE, 238, GroupLayout.PREFERRED_SIZE)
                                        .addComponent(dataPlaneRulesPanel, GroupLayout.PREFERRED_SIZE, 238, GroupLayout.PREFERRED_SIZE)
                                )
                                .addGap(20)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(dataPlaneActiveRulesPanel, GroupLayout.PREFERRED_SIZE, 270, GroupLayout.PREFERRED_SIZE)
                                )
                        )
                )
                .addContainerGap()
        );

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup()
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(trafficEngineeringPolicyPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(15)
                                .addComponent(routingPolicyPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(15)
                                .addComponent(activeClientsPanel, GroupLayout.PREFERRED_SIZE, 264, GroupLayout.PREFERRED_SIZE)
                                .addGap(15)
                                .addComponent(displayGraphPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        )
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(dataTypesPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGap(15)
                                .addComponent(dataPlaneRulesPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        )
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(dataPlaneActiveRulesPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                        )
                )
                .addContainerGap()
        );

        getContentPane().setLayout(layout);
        pack();
    }

    private void initTrafficEngineeringPolicyPanel() {
        trafficEngineeringPolicyPanel = new JPanel();
        trafficEngineeringPolicyPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Traffic Engineering Policy"));

        currentTrafficEngineeringPolicyTextField = new JTextField(SDNControllerService.getTrafficEngineeringPolicy().toString());
        currentTrafficEngineeringPolicyTextField.setEditable(false);

        availableTrafficEngineeringPoliciesComboBox = new JComboBox();
        int count = TrafficEngineeringPolicy.values().length;
        String[] trafficEngineeringPolicyItems = new String[count];
        count = 0;
        for (TrafficEngineeringPolicy tef : TrafficEngineeringPolicy.values()) {
            trafficEngineeringPolicyItems[count] = tef.toString();
            count++;
        }
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(trafficEngineeringPolicyItems);
        availableTrafficEngineeringPoliciesComboBox.setModel(dcm);

        updateTrafficEngineeringPolicyButton = new JButton("Update Traffic Policy");
        updateTrafficEngineeringPolicyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonUpdateTrafficEngineeringPolicyActionPerformed(evt);
            }
        });

        GroupLayout trafficEngineeringPolicyLayout = new GroupLayout(trafficEngineeringPolicyPanel);
        trafficEngineeringPolicyPanel.setLayout(trafficEngineeringPolicyLayout);
        trafficEngineeringPolicyLayout.setHorizontalGroup(trafficEngineeringPolicyLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(currentTrafficEngineeringPolicyTextField, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(availableTrafficEngineeringPoliciesComboBox, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(updateTrafficEngineeringPolicyButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
        );
        trafficEngineeringPolicyLayout.setVerticalGroup(trafficEngineeringPolicyLayout.createSequentialGroup()
                .addComponent(currentTrafficEngineeringPolicyTextField)
                .addGap(5)
                .addComponent(availableTrafficEngineeringPoliciesComboBox)
                .addGap(5)
                .addComponent(updateTrafficEngineeringPolicyButton)
        );
    }

    private void initRoutingPolicyPanel() {
        routingPolicyPanel = new JPanel();
        routingPolicyPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Routing Policy"));

        currentRoutingPolicyTextField = new JTextField(SDNControllerService.getRoutingPolicy().toString());
        currentRoutingPolicyTextField.setEditable(false);

        availableRoutingPoliciesComboBox = new JComboBox();
        int count = RoutingPolicy.values().length;
        String[] routingPolicyItems = new String[count];
        count = 0;
        for (RoutingPolicy rp : RoutingPolicy.values()) {
            routingPolicyItems[count] = rp.toString();
            count++;
        }
        DefaultComboBoxModel dcm = new DefaultComboBoxModel(routingPolicyItems);
        availableRoutingPoliciesComboBox.setModel(dcm);

        updateRoutingPolicyButton = new JButton("Update Routing Policy");
        updateRoutingPolicyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonUpdateRoutingPolicyActionPerformed(evt);
            }
        });

        GroupLayout routingPolicyLayout = new GroupLayout(routingPolicyPanel);
        routingPolicyPanel.setLayout(routingPolicyLayout);
        routingPolicyLayout.setHorizontalGroup(routingPolicyLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(currentRoutingPolicyTextField, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(availableRoutingPoliciesComboBox, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(updateRoutingPolicyButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
        );
        routingPolicyLayout.setVerticalGroup(routingPolicyLayout.createSequentialGroup()
                .addComponent(currentRoutingPolicyTextField)
                .addGap(5)
                .addComponent(availableRoutingPoliciesComboBox)
                .addGap(5)
                .addComponent(updateRoutingPolicyButton)
        );
    }

    private void initActiveClientsPanel() {
        activeClientsPanel = new JPanel();
        activeClientsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Active Clients"));

        getActiveClientsButton = new JButton("Get Active Clients");
        getActiveClientsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonRefreshActiveClientsActionPerfomed(evt);
            }
        });

        activeClientsTextArea = new JTextArea();
        activeClientsTextArea.setColumns(20);
        activeClientsTextArea.setEditable(false);
        activeClientsTextArea.setRows(5);

        GroupLayout activeClientsLayout = new GroupLayout(activeClientsPanel);
        activeClientsPanel.setLayout(activeClientsLayout);
        activeClientsLayout.setHorizontalGroup(activeClientsLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(getActiveClientsButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(activeClientsTextArea, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
        );
        activeClientsLayout.setVerticalGroup(activeClientsLayout.createSequentialGroup()
                .addComponent(getActiveClientsButton)
                .addGap(5)
                .addComponent(activeClientsTextArea)
        );
    }

    private void initDisplayGraphPanel() {
        displayGraphPanel = new JPanel();
        displayGraphPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Topology Graph"));

        displayGraphButton = new JButton("Show Topology Graph");
        displayGraphButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonDisplayActionPerformed(evt);
            }
        });

        GroupLayout displayGraphLayout = new GroupLayout(displayGraphPanel);
        displayGraphPanel.setLayout(displayGraphLayout);
        displayGraphLayout.setHorizontalGroup(displayGraphLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(displayGraphButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
        );
        displayGraphLayout.setVerticalGroup(displayGraphLayout.createSequentialGroup()
                .addComponent(displayGraphButton)
        );
    }

    private void initDataTypesPanel() {
        dataTypesPanel = new JPanel();
        dataTypesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Current Data Types"));

        getDataTypesButton = new JButton("Get Data Types");
        getDataTypesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonGetDataTypesActionPerformed(evt);
            }
        });

        dataTypesTextArea = new JTextArea();
        dataTypesTextArea.setColumns(20);
        dataTypesTextArea.setEditable(false);
        dataTypesTextArea.setRows(5);

        fillDataTypesTextArea();

        addDataTypeButton = new JButton("Add Data Type");
        addDataTypeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonAddDataTypesActionPerformed(evt);
            }
        });

        GroupLayout dataTypesLayout = new GroupLayout(dataTypesPanel);
        dataTypesPanel.setLayout(dataTypesLayout);
        dataTypesLayout.setHorizontalGroup(dataTypesLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(getDataTypesButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(dataTypesTextArea, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(addDataTypeButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
        );
        dataTypesLayout.setVerticalGroup(dataTypesLayout.createSequentialGroup()
                .addComponent(getDataTypesButton)
                .addGap(5)
                .addComponent(dataTypesTextArea)
                .addGap(5)
                .addComponent(addDataTypeButton)
        );
    }

    private void fillDataTypesTextArea() {
        try {
            String text = "";
            int i = 0;
            Iterator<String> currentDataTypes = this.SDNControllerService.getAvailableDataTypes().iterator();
            while (currentDataTypes.hasNext()) {
                text = text + currentDataTypes.next() + "\n";
                i++;
            }
            dataTypesTextArea.setText(text);
        } catch (Exception e) {
            dataTypesTextArea.setText(e.toString());
        }
    }

    private void initDataPlaneRulesPanel() {
        dataPlaneRulesPanel = new JPanel();
        dataPlaneRulesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Plane Rules Manager"));

        getDataPlaneRulesButton = new JButton("Get Data Plane Rules");
        getDataPlaneRulesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonGetDataPlaneRulesActionPerformed(evt);
            }
        });

        dataPlaneRulesTextArea = new JTextArea();
        dataPlaneRulesTextArea.setColumns(20);
        dataPlaneRulesTextArea.setEditable(false);
        dataPlaneRulesTextArea.setRows(5);

        fillDataPlaneRulesTextArea();

        addDataPlaneRuleButton = new JButton("Insert Data Plane Rule");
        addDataPlaneRuleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonInsertDataPlaneRuleFileActionPerformed(evt);
            }
        });

        dataPlaneRulesDataTypeLabel = new JLabel("Data Type:");
        dataPlaneRulesAvailableDataTypesComboBox = new JComboBox();

        dataPlaneRulesRuleLabel = new JLabel("Rule:");
        dataPlaneRulesAvailableRulesComboBox = new JComboBox();

        fillDataPlaneRulesComboBoxes();

        addRuleButton = new JButton("Add Data Plane Rule");
        addRuleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonAddRuleActionPerformed(evt);
            }
        });

        removeRuleButton = new JButton("Remove Data Plane Rule");
        removeRuleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonRemoveRuleActionPerformed(evt);
            }
        });

        GroupLayout dataPlaneRulesLayout = new GroupLayout(dataPlaneRulesPanel);
        dataPlaneRulesPanel.setLayout(dataPlaneRulesLayout);
        dataPlaneRulesLayout.setHorizontalGroup(dataPlaneRulesLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(getDataPlaneRulesButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(dataPlaneRulesTextArea, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(addDataPlaneRuleButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(dataPlaneRulesDataTypeLabel, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(dataPlaneRulesAvailableDataTypesComboBox, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(dataPlaneRulesRuleLabel, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(dataPlaneRulesAvailableRulesComboBox, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(addRuleButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
                .addComponent(removeRuleButton, GroupLayout.DEFAULT_SIZE, 238, Short.MAX_VALUE)
        );
        dataPlaneRulesLayout.setVerticalGroup(dataPlaneRulesLayout.createSequentialGroup()
                .addComponent(getDataPlaneRulesButton)
                .addGap(5)
                .addComponent(dataPlaneRulesTextArea)
                .addGap(5)
                .addComponent(addDataPlaneRuleButton)
                .addGap(5)
                .addComponent(dataPlaneRulesDataTypeLabel)
                .addGap(5)
                .addComponent(dataPlaneRulesAvailableDataTypesComboBox)
                .addGap(5)
                .addComponent(dataPlaneRulesRuleLabel)
                .addGap(5)
                .addComponent(dataPlaneRulesAvailableRulesComboBox)
                .addGap(5)
                .addComponent(addRuleButton)
                .addGap(5)
                .addComponent(removeRuleButton)
        );
    }

    private void fillDataPlaneRulesTextArea() {
        try {
            String text = "";
            int i = 0;
            Iterator<String> currentDataPlaneRules = this.SDNControllerService.getAvailableDataPlaneRules().iterator();
            while (currentDataPlaneRules.hasNext()) {
                text = text + currentDataPlaneRules.next() + "\n";
                i++;
            }
            dataPlaneRulesTextArea.setText(text);
        } catch (Exception e) {
            dataPlaneRulesTextArea.setText(e.toString());
        }
    }

    private void fillDataPlaneRulesComboBoxes() {
        Set<String> currentDataTypes = SDNControllerService.getAvailableDataTypes();
        String[] dataTypeItems = new String[currentDataTypes.size()];
        int count = 0;
        for (String dataType : currentDataTypes) {
            dataTypeItems[count] = "" + dataType;
            count++;
        }
        DefaultComboBoxModel dataTypeDcm = new DefaultComboBoxModel(dataTypeItems);
        dataPlaneRulesAvailableDataTypesComboBox.setModel(dataTypeDcm);

        Set<String> currentRules = SDNControllerService.getAvailableDataPlaneRules();
        String[] ruleItems = new String[currentRules.size()];
        count = 0;
        for (String rule : currentRules) {
            ruleItems[count] = "" + rule;
            count++;
        }
        DefaultComboBoxModel rulesDcm = new DefaultComboBoxModel(ruleItems);
        dataPlaneRulesAvailableRulesComboBox.setModel(rulesDcm);
    }

    private void initDataPlaneActiveRulesPanel() {
        dataPlaneActiveRulesPanel = new JPanel();
        dataPlaneActiveRulesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Data Plane Active Rules"));

        String[] columnNames = {"Data Type", "Rule"};
        String[][] data = new String[0][0];
        dataPlaneActiveRulesTable = new JTable(data, columnNames);

        dataPlaneActiveRulesScrollPane = new JScrollPane(dataPlaneActiveRulesTable);
        dataPlaneActiveRulesTable.setFillsViewportHeight(true);


        dataPlaneActiveRulesRefreshButton = new JButton("Refresh Active Rules Info");
        dataPlaneActiveRulesRefreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                jButtonRefreshActiveRulesActionPerformed(evt);
            }
        });

        GroupLayout dataPlaneActiveRulesLayout = new GroupLayout(dataPlaneActiveRulesPanel);
        dataPlaneActiveRulesPanel.setLayout(dataPlaneActiveRulesLayout);

        dataPlaneActiveRulesLayout.setHorizontalGroup(dataPlaneActiveRulesLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(dataPlaneActiveRulesScrollPane, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
                .addComponent(dataPlaneActiveRulesRefreshButton, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
        );
        dataPlaneActiveRulesLayout.setVerticalGroup(dataPlaneActiveRulesLayout.createSequentialGroup()
                .addComponent(dataPlaneActiveRulesScrollPane, GroupLayout.DEFAULT_SIZE, 270, Short.MAX_VALUE)
                .addGap(5)
                .addComponent(dataPlaneActiveRulesRefreshButton)
        );
    }

    private void jButtonRefreshActiveClientsActionPerfomed(ActionEvent evt) {
        try {
            String text = "";
            int i = 0;
            Iterator<Integer> activeClients = this.SDNControllerService.getActiveClients();
            while (activeClients.hasNext()) {
                text = text + i + " " + activeClients.next() + "\n";
                i++;
            }
            activeClientsTextArea.setText(text);
        } catch (Exception e) {
            activeClientsTextArea.setText(e.toString());
        }
    }

    private void jButtonUpdateTrafficEngineeringPolicyActionPerformed(ActionEvent evt) {
        try {
            String selectedTrafficEngineeringPolicy = availableTrafficEngineeringPoliciesComboBox.getSelectedItem().toString();
            TrafficEngineeringPolicy trafficEngineeringPolicy = TrafficEngineeringPolicy.valueOf(selectedTrafficEngineeringPolicy);
            SDNControllerService.updateTrafficEngineeringPolicy(trafficEngineeringPolicy);
            currentTrafficEngineeringPolicyTextField.setText(SDNControllerService.getTrafficEngineeringPolicy().toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void jButtonUpdateRoutingPolicyActionPerformed(ActionEvent evt) {
        try {
            String selectedRoutingPolicy = availableRoutingPoliciesComboBox.getSelectedItem().toString();
            RoutingPolicy routingPolicy = RoutingPolicy.valueOf(selectedRoutingPolicy);
            SDNControllerService.updateRoutingPolicy(routingPolicy);
            currentRoutingPolicyTextField.setText(SDNControllerService.getRoutingPolicy().toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void jButtonDisplayActionPerformed(ActionEvent evt) {
        DefaultView view = SDNControllerService.getGraph();

        displayGraphJFrame = new JFrame();
        displayGraphJFrame.setTitle("TopologyGraph");
        displayGraphJFrame.setLocationByPlatform(true);
        displayGraphJFrame.add(view);
        displayGraphJFrame.setSize(new Dimension(500, 500));

        displayGraphJFrame.setVisible(true);
    }

    private void jButtonGetDataTypesActionPerformed(ActionEvent evt) {
        fillDataTypesTextArea();
        fillDataPlaneRulesComboBoxes();
    }

    private void jButtonAddDataTypesActionPerformed(ActionEvent evt) {
        File dataTypeFile = null;
        String fileName = "";
        int returnVal = fileChooser.showOpenDialog((Component) evt.getSource());
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            dataTypeFile = fileChooser.getSelectedFile();
            try {
                fileName = dataTypeFile.getName();
            } catch (Exception ex) {
                System.out.println("Problem accessing file " + dataTypeFile.getAbsolutePath());
            }
        } else {
            System.out.println("File access cancelled by user.");
        }
        boolean result = SDNControllerService.addUserDefinedDataType(fileName, dataTypeFile);

        if (result) {
            jButtonGetDataTypesActionPerformed(null);
        } else {
            JOptionPane.showMessageDialog(null, "It is not possible to add the specified DataType.");
        }
    }

    private void jButtonGetDataPlaneRulesActionPerformed(ActionEvent evt) {
        fillDataPlaneRulesTextArea();
        fillDataPlaneRulesComboBoxes();
    }

    private void jButtonInsertDataPlaneRuleFileActionPerformed(ActionEvent evt) {
        File dataPlaneRuleFile = null;
        String fileName = "";
        int returnVal = fileChooser.showOpenDialog((Component) evt.getSource());
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            dataPlaneRuleFile = fileChooser.getSelectedFile();
            try {
                fileName = dataPlaneRuleFile.getName();
            } catch (Exception ex) {
                System.out.println("Problem accessing file " + dataPlaneRuleFile.getAbsolutePath());
            }
        } else {
            System.out.println("File access cancelled by user.");
        }
        boolean result = SDNControllerService.addUserDefinedDataPlaneRule(fileName, dataPlaneRuleFile);

        if (result) {
            jButtonGetDataPlaneRulesActionPerformed(null);
        } else {
            JOptionPane.showMessageDialog(null, "It is not possible to add the specified DataPlaneRule.");
        }
    }

    private void jButtonAddRuleActionPerformed(ActionEvent evt) {
        String dataType = dataPlaneRulesAvailableDataTypesComboBox.getSelectedItem().toString();
        String dataPlaneRule = dataPlaneRulesAvailableRulesComboBox.getSelectedItem().toString();

        boolean result = SDNControllerService.addDataPlaneRule(dataType, dataPlaneRule);

        if (!result) {
            JOptionPane.showMessageDialog(null, "It is not possible to add the data plane rule for this data type.");
        }
    }

    private void jButtonRemoveRuleActionPerformed(ActionEvent evt) {
        String dataType = dataPlaneRulesAvailableDataTypesComboBox.getSelectedItem().toString();
        String dataPlaneRule = dataPlaneRulesAvailableRulesComboBox.getSelectedItem().toString();

        SDNControllerService.removeDataPlaneRule(dataType, dataPlaneRule);
    }

    private void jButtonRefreshActiveRulesActionPerformed(ActionEvent evt) {
        DefaultTableModel dtm = new DefaultTableModel();

        dtm.addColumn("Data Type");
        dtm.addColumn("Rule");

        ConcurrentHashMap<String, List<String>> activeRules = (ConcurrentHashMap<String, List<String>>) SDNControllerService.getActiveDataPlaneRules();

        for (Map.Entry<String, List<String>> entry : activeRules.entrySet()) {
            String dataType = entry.getKey();
            ArrayList<String> rules = (ArrayList<String>) entry.getValue();

            int rulesLen = rules.size();

            for (int i = 0; i < rulesLen; i++) {
                if (i == 0) {
                    dtm.addRow(new String[]{dataType, rules.get(i)});
                } else {
                    dtm.addRow(new String[]{"", rules.get(i)});
                }
            }
        }

        dataPlaneActiveRulesTable.setModel(dtm);
    }

    private void formWindowClosed(java.awt.event.WindowEvent evt) {

    }

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        System.out.println("SDNControllerServiceJFrame: formWindowClosing");
        SDNControllerService.stopService();
    }
}
