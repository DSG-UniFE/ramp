package it.unibo.deis.lia.ramp.core.internode.sdn.osRoutingManager;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.Heartbeater;
import it.unibo.deis.lia.ramp.core.internode.sdn.osRoutingManager.ipRouteRule.IpRouteRule;
import it.unibo.deis.lia.ramp.core.internode.sdn.osRoutingManager.localRoutingTable.LocalRoutingTable;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This component is able to enable static routing according to decisions taken at
 * the middleware level by using Policy-based routing mechanisms.
 * All the rules added by this manager are ephemeral i.e. they will not survive in case
 * of machine shutdown or restart.
 */
public class OsRoutingManager {
    /**
     * SuperUser password.
     */
    private static String superuserPassword = null;

    /**
     * Last timestamp at which the SuperUser privileges were acquired.
     */
    private static long lastSuperuserPasswordRefresh = -1;

    /**
     * Timeout for SuperUser password validity, every time this timeout
     * expires the SuperUser privileges are acquired again.
     */
    private static final long TIMEOUT_SU_PASSWORD = 180 * 1000;
    /**
     * Data structure to hold current local IP addresses to easily get the src IP address
     * for the "ip route add destIP via hopIP src srcIP".
     * For example given a local IP 192.168.1.100
     * the key is 192.168.1 representing the subnet
     * the value is 192.168.1.100 representing the local IP of that subnet.
     */
    private Map<String, String> localIpAddresses;

    /**
     * Data structure to keep tack of the current ip route added for packets sent from this node
     * The key contains the destination IP.
     * The value contains the list of IP sources already used to reach that destination.
     */
    private Map<String, List<String>> currentDestinationsSourceIps;

    /**
     * Data structure to keep tack of the "ip route add" commands executed for each routeId.
     * This map will be used by the sender node to retrieve the srcIP to use given a routeId
     * and by this manager to delete a route when it expires.
     *
     * The key contains the routeId.
     * The value contains a list of IpRouteRule objects.
     */
    private Map<Integer, List<IpRouteRule>> currentRoutes;

    /**
     * Data structure to keep tack of the current ip rule added
     * The key contains the routeId
     * The value contains the sourceIp associated to the ip rule.
     */
    private Map<Integer, String> currentRules;

    /**
     * Data structure to keep track of the current routing local tables
     * created for the source routing.
     * The key is the srcIP
     * The value is a LocalRoutingTable object.
     */
    private Map<String, LocalRoutingTable> routingLocalTables;

    /**
     * This index is used to create the local routing tables
     * with a unique name. The prefix used is "sndOsRouting".
     */
    private int routingLocalTablesIndex;

    /**
     * OsRoutingManager instance.
     */
    private static OsRoutingManager osRoutingManager = null;

    private OsRoutingManager() {
        this.currentDestinationsSourceIps = new ConcurrentHashMap<>();
        this.currentRoutes = new ConcurrentHashMap<>();
        this.currentRules = new ConcurrentHashMap<>();
        this.routingLocalTables = new ConcurrentHashMap<>();
        this.routingLocalTablesIndex = 1;
        updateLocalIpAddresses();
        getSuperUserPassword();
    }

    synchronized public static OsRoutingManager getInstance() throws Exception {
        if (osRoutingManager == null) {
            if (!RampEntryPoint.os.startsWith("linux")) {
                throw new Exception("OSRoutingManager: Unsupported Operating System: " + System.getProperty("os.name"));
            }

            osRoutingManager = new OsRoutingManager();
        }
        /*
         * Activate packet forwarding.
         */
        try {
            activatePacketForwarding();
        } catch (Exception e) {
            osRoutingManager = null;
            throw e;
        }

        System.out.println("OSRoutingManager ENABLED");

        return osRoutingManager;
    }

    public void deactivate() {
        if (osRoutingManager != null) {
            /*
             * Deactivate packet forwarding.
             */
            deactivatePacketForwarding();

            /*
             * Clean all the routing tables and rules
             * created.
             */
            cleanRoutingTables();

            /*
             * Remove super user authentication for this application
             */
            resetSuperUserCredentials();

            superuserPassword = null;
            this.currentDestinationsSourceIps = null;
            this.currentRoutes = null;
            this.currentRules = null;
            this.routingLocalTables = null;

            osRoutingManager = null;
        }
        System.out.println("OSRoutingManager DISABLED");
    }

    private static void activatePacketForwarding() {
        System.out.println("OSRoutingManager activatePacketForwarding");
        if (RampEntryPoint.os.startsWith("windows")) {
            System.out.println("OSRoutingManager: remember to activate the IPEnableRouter registry key");
            System.out.println("OSRoutingManager: windows users must be administrators");
            /*
             * Future work: Check if it is possible to change the Windows Registry from here
             */
        } else if (RampEntryPoint.os.startsWith("linux")) {
            try {
                sudoCommand("sysctl -w net.ipv4.ip_forward=1");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                throw new Exception("Unsupported Operating System: " + System.getProperty("os.name"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void deactivatePacketForwarding() {
        if (RampEntryPoint.os.startsWith("windows")) {
            System.out.println("OSRoutingManager: remember to deactivate the IPEnableRouter registry key");
        } else if (RampEntryPoint.os.startsWith("linux")) {
            try {
                sudoCommand("sysctl -w net.ipv4.ip_forward=0");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                throw new Exception("OSRoutingManager: Unsupported Operating System: " + System.getProperty("os.name"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateLocalIpAddresses() {
        Vector<String> localInterfaces = null;
        int localInterfacesLength = 0;

        try {
            localInterfaces = Dispatcher.getLocalNetworkAddresses(false);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        if (localInterfaces != null) {
            localInterfacesLength = localInterfaces.size();
        }

        if (localInterfacesLength > 0) {
            this.localIpAddresses = new ConcurrentHashMap<>();
            for (int i = 0; i < localInterfacesLength; i++) {
                String currentLocalInterface = localInterfaces.get(i);
                String[] splitKey = currentLocalInterface.split("\\.");
                String key = splitKey[0] + "." + splitKey[1] + "." + splitKey[2];
                this.localIpAddresses.put(key, currentLocalInterface);
            }
        }
    }

    public boolean addRoute(String sourceIP, String destinationIP, String viaIP, int routeId) {
        if (RampEntryPoint.os.startsWith("windows")) {
            /*
             * Future Work: implements this feature for Windows. Since iproute2 is not
             * available for Windows at the moment we should use another Policy-based
             * routing library.
             * Tips: remove from here the functions related to sudoCommand and create a factory
             * that depending on the operating systems returns an object able to perform the Policy-based
             * routing operations.
             * So functions like addRoute, deleteRoute and so on will become the methods of an interface or
             * an abstract class and each os-dependent object returned by the factory will implement
             * these methods according to the Policy-based routing mechanism available for that
             * particular operating system.
             */
            System.out.println("OSRoutingManager: Unsupported Operating System: " + System.getProperty("os.name"));
            return false;
        } else if (RampEntryPoint.os.startsWith("linux")) {
            try {
                boolean isSender = (sourceIP == null);
                /*
                 * In case of one hop route if the sender node has to find a new viaIP
                 * it needs to update also the destinationIP.
                 */
                boolean isOneHopRoute = isSender && destinationIP.equals(viaIP);

                /*
                 * iproute2 has reserved values for these tables
                 * 255     local
                 * 254     main
                 * 253     default
                 * 0       unspec
                 * so the routingLocalTablesIndex must start from 1 and be less than 253.
                 * If this range is exceeded it is not possible to add a new local routing table.
                 */
                if(this.routingLocalTablesIndex >= 253 ) {
                    return false;
                }

                String sudoCommandResult;
                String ipRouteCommand = "ip route";
                System.out.println("OSRoutingManager " + ipRouteCommand);
                sudoCommandResult = sudoCommand(ipRouteCommand);
                System.out.println("OSRoutingManager " + ipRouteCommand + " result: " + sudoCommandResult);

                /*
                 * if sourceIP is null this node is the sender, so we need
                 * to identify the srcIP address to use.
                 */
                if (isSender) {
                    updateLocalIpAddresses();

                    String[] splitKey = viaIP.split("\\.");
                    String key = splitKey[0] + "." + splitKey[1] + "." + splitKey[2];
                    sourceIP = this.localIpAddresses.get(key);
                }

                /*
                 * Check if this destIP has already this srcIP associated,
                 * if the association exists it is not possible to select
                 * this route so let's discover if exists another interface
                 * to reach the next hop.
                 */
                if (this.currentDestinationsSourceIps.containsKey(destinationIP)) {
                    List<String> existingSources = this.currentDestinationsSourceIps.get(destinationIP);
                    if (existingSources.contains(sourceIP)) {
                        /*
                         * There is already an ip route add assigned for this destination.
                         * Let's check if it is possible to reach the destination by using another
                         * network interface.
                         */
                        boolean foundNewViaIP = false;
                        List<InetAddress> hopAvailableAddresses = Heartbeater.getInstance(false).getNeighborAvailableAddressesByInetAddress(InetAddress.getByName(viaIP));

                        for (InetAddress a : hopAvailableAddresses) {
                            String candidateViaIP = a.getHostAddress();
                            if (!candidateViaIP.equals(viaIP) && !this.currentDestinationsSourceIps.containsKey(candidateViaIP)) {
                                viaIP = candidateViaIP;
                                foundNewViaIP = true;
                                break;
                            }
                        }
                        /*
                         * If a new interface is not found it is not possible
                         * to create the route.
                         */
                        if (foundNewViaIP) {
                            /*
                             * Update the sourceIP if this node is the sender according
                             * to the new interface discovered to reach next hop.
                             */
                            if(isSender) {
                                String[] splitKey = viaIP.split("\\.");
                                String key = splitKey[0] + "." + splitKey[1] + "." + splitKey[2];
                                sourceIP = this.localIpAddresses.get(key);

                                if(isOneHopRoute) {
                                    destinationIP = viaIP;
                                }
                            }
                        } else {
                            return false;
                        }
                    }
                }

                /*
                 * Check if a table name for the current sourceIP exists otherwise create it.
                 */
                if (!this.routingLocalTables.containsKey(sourceIP)) {
                    int localIpTableIndex = this.routingLocalTablesIndex;
                    String localIpTableName = "sdnOsRouting" + localIpTableIndex;

                    String addRoutingTableShellCommand = "echo " + localIpTableIndex + " " + localIpTableName + " >> /etc/iproute2/rt_tables";
                    System.out.println("OSRoutingManager " + addRoutingTableShellCommand);
                    sudoShellCommand(addRoutingTableShellCommand);
                    this.routingLocalTables.put(sourceIP, new LocalRoutingTable(localIpTableIndex, localIpTableName));
                    /*
                     * For this source we have to look up the table just created that
                     * will contain all the routes, so we add a rule for all the packets
                     * coming from this sourceIP.
                     */
                    String addRuleCommand = "ip rule add from " + sourceIP + " lookup " + localIpTableName;
                    System.out.println("OSRoutingManager " + addRuleCommand);
                    sudoCommand(addRuleCommand);

                    /*
                     * Increment the index for the next local routing table to create.
                     */
                    this.routingLocalTablesIndex++;
                }

                String addRouteCommand;
                IpRouteRule addRouteRule;
                String localTableName = this.routingLocalTables.get(sourceIP).getTableName();

                if(isSender) {
                    addRouteCommand = "ip route add " + destinationIP + " via " + viaIP + " src " + sourceIP + " table " + localTableName;
                    addRouteRule = new IpRouteRule(sourceIP, viaIP, destinationIP);
                } else {
                    addRouteCommand = "ip route add " + destinationIP + " via " + viaIP + " table " + localTableName;
                    addRouteRule = new IpRouteRule(viaIP, destinationIP);
                }

                System.out.println("OSRoutingManager " + addRouteCommand);
                sudoCommandResult = sudoCommand(addRouteCommand);
                /*
                 * It is not possible to add a route for this routeId because a route for the same
                 * destination already exists.
                 */
                if (sudoCommandResult.contains("File exists")) {
                    return false;
                } else {
                    /*
                     * Everything is fine so we can keep track of this routeId.
                     */
                    if(isSender) {
                        this.currentRules.put(routeId, sourceIP);
                    }
                }

                /*
                 * Flush the cache
                 */
                ipRouteFlushCache();

                if (!this.currentDestinationsSourceIps.containsKey(destinationIP)) {
                    List<String> srcList = new ArrayList<>();
                    srcList.add(sourceIP);
                    this.currentDestinationsSourceIps.put(destinationIP, srcList);
                } else {
                    this.currentDestinationsSourceIps.get(destinationIP).add(sourceIP);
                }

                if(!this.currentRoutes.containsKey(routeId)) {
                    List<IpRouteRule> rulesList = new ArrayList<>();
                    rulesList.add(addRouteRule);
                    this.currentRoutes.put(routeId, rulesList);
                } else {
                    this.currentRoutes.get(routeId).add(addRouteRule);
                }

                return true;
            } catch (Exception e) {
                //e.printStackTrace();
                return false;
            }
        } else {
            System.out.println("OSRoutingManager: Unsupported Operating System: " + System.getProperty("os.name"));
            return false;
        }
    }

    /**
     * Deletes the route associated to the given routeID.
     *
     * @param routeId actually stored
     */
    public void deleteRoute(int routeId) {
        if (RampEntryPoint.os.startsWith("windows")) {
            System.out.println("OSRoutingManager: Unsupported Operating System: " + System.getProperty("os.name"));
        } else if (RampEntryPoint.os.startsWith("linux")) {
            String deleteRouteCommand;

            /*
             * Retrieve the info used for the "ip route add" command
             * associated to this routeId.
             */
            List<IpRouteRule> addRouteRules = this.currentRoutes.get(routeId);

            for(IpRouteRule ipRouteRule : addRouteRules) {
                String sourceIP = ipRouteRule.getSourceIP();
                String viaIP = ipRouteRule.getViaIP();
                String destinationIP = ipRouteRule.getDestinationIP();

                /*
                 * Retrieve the routing table name containing this route.
                 */
                String ruleSourceIP = this.currentRules.get(routeId);
                String localTableName = this.routingLocalTables.get(ruleSourceIP).getTableName();


                if(sourceIP != null) {
                    /*
                     * This node is the sender of this routeId.
                     */
                    deleteRouteCommand = "ip route del " + destinationIP + " via " + viaIP + " src " + sourceIP + " table " + localTableName;
                } else {
                    /*
                     * This node is an intermediate node for this routeId.
                     */
                    deleteRouteCommand = "ip route del " + destinationIP + " via " + viaIP + " table " + localTableName;
                }

                System.out.println("OSRoutingManager " + deleteRouteCommand);
                try {
                    sudoCommand(deleteRouteCommand);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                this.currentDestinationsSourceIps.get(destinationIP).remove(sourceIP);
            }

            /*
             * Flush the cache
             */
            ipRouteFlushCache();

            this.currentRules.remove(routeId);
            this.currentRoutes.remove(routeId);


        } else {
            System.out.println("OSRoutingManager: Unsupported Operating System: " + System.getProperty("os.name"));
        }
    }

    /**
     * This method, called when the OsRoutingManager is deactivated,
     * is in charge to clean all rules, routes and routing tables
     * created so far.
     */
    private void cleanRoutingTables() {
        System.out.println("OSRoutingManager: Starting cleaning.");

        for (String sourceIP : this.routingLocalTables.keySet()) {
            /*
             * Remove the rule created for the current sourceIP.
             */
            String removeRuleCommand = "ip rule del from " + sourceIP;
            System.out.println("OSRoutingManager " + removeRuleCommand);
            try {
                sudoCommand(removeRuleCommand);
            } catch (Exception e) {
                e.printStackTrace();
            }

            LocalRoutingTable currentTable = this.routingLocalTables.get(sourceIP);

            /*
             * Flush the table related to the above rule.
             */
            String localTableName = currentTable.getTableName();
            String flushTableCommand = "ip route flush table " + localTableName;
            System.out.println("OSRoutingManager " + flushTableCommand);
            try {
                sudoCommand(flushTableCommand);
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
             * Remove the entry related to the above table in /etc/iproute2/rt_tables
             * Credits: https://github.com/napalm255/checkpoint-routing-tables/blob/master/route-clean.sh
             */
            int localTableIndex = currentTable.getIndex();
            String commandResult = "";
            String grepCommand = "grep " + localTableName + " /etc/iproute2/rt_tables";
            try {
                commandResult = sudoCommand(grepCommand);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!commandResult.equals("")) {
                System.out.println("OSRoutingManager: removing table " + localTableName);
                String sedCommand = "sed -i~ -e s/\"" + localTableIndex + "\".*$// /etc/iproute2/rt_tables";
                try {
                    sudoCommand(sedCommand);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    commandResult = sudoCommand(grepCommand, 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (commandResult.equals("")) {
                    System.out.println("OSRoutingManager: table " + localTableName + " removed.");
                } else {
                    System.out.println("OSRoutingManager: table " + localTableName + " not removed.");
                }
            } else {
                System.out.println("OSRoutingManager: table " + localTableName + " not found.");
            }
        }

        /*
         * Remove empty lines from /etc/iproute2/rt_tables
         */
        String sedCommand = "sed -i \'/^$/d\' /etc/iproute2/rt_tables";
        try {
            sudoCommand(sedCommand);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Flush the cache
         */
        ipRouteFlushCache();

        System.out.println("OSRoutingManager: Cleaning completed.");
    }

    /**
     * Every time a route, a rule or a local table is added into the system
     * a good practice is to flush the cache in order to avoid
     * inconsistencies.
     */
    private void ipRouteFlushCache() {
        String flushCacheCommand = "ip route flush cache";
        System.out.println("OSRoutingManager " + flushCacheCommand);
        try {
            sudoCommand(flushCacheCommand);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Given a routeID this method returns the sourceIP to be used
     * according to routing decisions made by the OsRoutingManager.
     *
     * @param routeId given by the client
     * @return sourceIP to be used by the client. It is different from null
     * only if this node is the true sender of this route.
     */
    public String getRouteIdSourceIpAddress(int routeId) {
        String result = null;
        if (this.currentRules.containsKey(routeId)) {
            result = this.currentRules.get(routeId);
        }
        return result;
    }

    /**
     * Given a routeID this method returns the destinationIP to be used
     * according to routing decisions made by the OsRoutingManager.
     *
     * @param routeId given by the client
     * @return destinationIP to be used by the client.
     */
    public String getRouteIdDestinationIpAddress(int routeId) {
        String result = null;
        if (this.currentRules.containsKey(routeId)) {
            String sourceIp = this.currentRules.get(routeId);
            for(IpRouteRule ipRouteRule : this.currentRoutes.get(routeId)) {
                if(ipRouteRule.getSourceIP().equals(sourceIp)) {
                    result = ipRouteRule.getDestinationIP();
                    break;
                }
            }
        }
        return result;
    }

    private void getSuperUserPassword() {
        if (superuserPassword == null) {
            /*
             * Reset super user authentication from the last session in case
             * the ControllerClient goes down.
             */
            resetSuperUserCredentials();
            /*
             * Get Super User Password
             */
            String candidateSuperUserPassword = showSuperUserPasswordDialog("Insert root password");
            while(candidateSuperUserPassword == null || !setupSuperUserCredentials(candidateSuperUserPassword)) {
                candidateSuperUserPassword = showSuperUserPasswordDialog("Wrong password. Insert root password");
            }
            superuserPassword = candidateSuperUserPassword;
        }
    }

    private String showSuperUserPasswordDialog(String message) {
        javax.swing.JPasswordField passwordField = new javax.swing.JPasswordField();
        Object[] userMessage = {message, passwordField};
        int res = javax.swing.JOptionPane.showConfirmDialog(null, userMessage, "OSRoutingManager: granting root privileges", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE);
        if (res == javax.swing.JOptionPane.OK_OPTION) {
            return new String(passwordField.getPassword());
        } else {
            return null;
        }
    }

    private static boolean setupSuperUserCredentials(String password) {
        CommandLine commandLine = new CommandLine("sudo");
        commandLine.addArgument("--validate", false);
        commandLine.addArgument("--stdin", false);
        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
        ByteArrayInputStream stdIn = new ByteArrayInputStream((password + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        executor.setStreamHandler(new PumpStreamHandler(stdOut, stdErr, stdIn));

        try {
            executor.execute(commandLine);
        } catch (IOException e) {
            return false;
        }

        lastSuperuserPasswordRefresh = System.currentTimeMillis();

        return true;
    }

    private static void resetSuperUserCredentials() {
        CommandLine commandLine = new CommandLine("sudo");
        commandLine.addArgument("--remove-timestamp", false);
        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        executor.setStreamHandler(new PumpStreamHandler(stdout));

        try {
            executor.execute(commandLine);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void refreshSuperUserCredentials() {
        if (System.currentTimeMillis() - lastSuperuserPasswordRefresh > TIMEOUT_SU_PASSWORD) {
            /*
             * Setup again the user credentials to avoid the sudo timeout.
             */
            resetSuperUserCredentials();
            setupSuperUserCredentials(superuserPassword);
        }
    }
    private static String sudoCommand(String command) {
        return sudoCommand(command, 0);
    }

    private static String sudoCommand(String command, int exitValue) {
        refreshSuperUserCredentials();

        String sudoCommand = "sudo " + command;
        CommandLine commandLine = CommandLine.parse(sudoCommand);

        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

        DefaultExecutor exec = new DefaultExecutor();
        if(exitValue != 0) {
            exec.setExitValue(exitValue);
        }
        PumpStreamHandler streamHandler = new PumpStreamHandler(stdOut, stdErr);
        exec.setStreamHandler(streamHandler);
        try {
            exec.execute(commandLine);
        } catch (IOException e) {
            System.out.println("OSRoutingManager: sudoCommand error " + stdErr.toString());
            e.printStackTrace();
        }

        return stdOut.toString();
    }

    private static String sudoShellCommand(String command) {
        refreshSuperUserCredentials();

        CommandLine shellCommandLine = new CommandLine("sudo").addArgument("sh").addArgument("-c");
        shellCommandLine.addArgument(command, false);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        DefaultExecutor exec = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(stdout, stderr);
        exec.setStreamHandler(streamHandler);
        try {
            exec.execute(shellCommandLine);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stdout.toString();
    }
}
