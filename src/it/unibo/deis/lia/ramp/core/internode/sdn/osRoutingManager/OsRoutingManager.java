package it.unibo.deis.lia.ramp.core.internode.sdn.osRoutingManager;

import it.unibo.deis.lia.ramp.RampEntryPoint;
import it.unibo.deis.lia.ramp.core.internode.Dispatcher;
import it.unibo.deis.lia.ramp.core.internode.Heartbeater;
import it.unibo.deis.lia.ramp.core.internode.sdn.osRoutingManager.ipRouteRule.IpRouteRule;
import it.unibo.deis.lia.ramp.core.internode.sdn.osRoutingManager.localRoutingTable.LocalRoutingTable;


import java.io.*;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Dmitrij David Padalino Montenero
 *
 * This class is a temporary copy of Layer3RoutingManager to be used by ControllerClient.
 * This component is able to enable static routing according to decisions taken at
 * the middleware level.
 * All the rules added by this manager are ephemeral i.e. they will not survive in case
 * of machine shutdown or restart.
 */
public class OsRoutingManager {
    private static String superuserPassword = null;
    private static long lastSuperuserPasswordRefresh = -1;
    private static final long TIMEOUT_SU_PASSWORD = 180 * 1000; //Previously 10 * 1000
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
     * The value contains an IpRouteRule object.
     */
    private Map<Integer, IpRouteRule> currentRoutes;

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
            // TODO Check if it is possible to change the Windows Registry from here
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
            // TODO Windows
            System.out.println("OSRoutingManager: Unsupported Operating System: " + System.getProperty("os.name"));
            return false;
        } else if (RampEntryPoint.os.startsWith("linux")) {
            try {
                boolean isSender = (sourceIP == null);
                /*
                 * In case of one hop route the sender node if it has to find a new viaIP
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
                 * Check if this destIP has already this srcIP associated, if so
                 * it is not possible to select this route so let's discover
                 * if exists another interface to reach the next hop.
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

                    String addRoutingTableCommand = "sh -c \"echo " + localIpTableIndex + " " + localIpTableName + " >> /etc/iproute2/rt_tables\"";
                    System.out.println("OSRoutingManager " + addRoutingTableCommand);
                    sudoCommand(addRoutingTableCommand);
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
                    this.currentRules.put(routeId, sourceIP);
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

                this.currentRoutes.put(routeId, addRouteRule);

                return true;

//                if (destinationIP != null) {
//                    // 1) enable NAT
//                    String com1 = "iptables -t nat -A POSTROUTING -s " + from + " -j MASQUERADE";
//                    // System.out.println("OSRoutingManager "+com1);
//                    sudoCommand(com1);
//                }
//                if (to != null) {
//                    // 2) default gateway
//                    // 2a) delete previous default gateways
//                    Process pShow = Runtime.getRuntime().exec("ip route show");
//                    BufferedReader is = new BufferedReader(new InputStreamReader(pShow.getInputStream()));
//                    String line;
//                    while ((line = is.readLine()) != null) {
//                        if (line.contains("default")) {
//                            String[] delTokens = line.split(" ");
//                            String delCom = "ip route del default via " + delTokens[2] + " dev " + delTokens[4];
//                            // System.out.println("OSRoutingManager delCom "+delCom);
//                            sudoCommand(delCom);
//                        }
//                    }
//
//                    // 2b) add new default gateway
//                    String interf = OSRoutingManager.fromIpToName(to);
//                    String addGateway = "ip route add default via " + to + " dev " + interf;
//                    // System.out.println("OSRoutingManager addGateway "+addGateway);
//                    sudoCommand(addGateway);
//                }
//
//                // 3) add DNS
//                FileWriter fwDns = new FileWriter("./temp/resolv.conf");
//                BufferedWriter writerDns = new BufferedWriter(fwDns);
//                writerDns.write("nameserver 137.204.58.1");
//                writerDns.newLine();
//                writerDns.write("nameserver 137.204.59.1");
//                writerDns.newLine();
//                writerDns.flush();
//                writerDns.close();
//                sudoCommand("mv ./temp/resolv.conf /etc/resolv.conf");
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
            // TODO Windows
            System.out.println("OSRoutingManager: Unsupported Operating System: " + System.getProperty("os.name"));
        } else if (RampEntryPoint.os.startsWith("linux")) {
            String sudoCommandResult = "";
            String deleteRouteCommand;

            /*
             * Retrieve the info used for the "ip route add" command
             * associated to this routeId.
             */
            IpRouteRule addRouteRule = this.currentRoutes.get(routeId);

            String sourceIP = addRouteRule.getSourceIP();
            String viaIP = addRouteRule.getViaIP();
            String destinationIP = addRouteRule.getDestinationIP();

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
                sudoCommandResult = sudoCommand(deleteRouteCommand);
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
             * Flush the cache
             */
            ipRouteFlushCache();

            this.currentRules.remove(routeId);
            this.currentRoutes.remove(routeId);
            this.currentDestinationsSourceIps.get(destinationIP).remove(sourceIP);

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
                    commandResult = sudoCommand(grepCommand);
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
        if (this.currentRoutes.containsKey(routeId)) {
            result = this.currentRoutes.get(routeId).getSourceIP();
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
        if (this.currentRoutes.containsKey(routeId)) {
            result = this.currentRoutes.get(routeId).getDestinationIP();
        }
        return result;
    }

    private static String fromIpToName(String ip) throws Exception {
        String interf = null;

        if (RampEntryPoint.os.startsWith("windows")) {
            Process p = null;
            p = Runtime.getRuntime().exec("ipconfig");
            BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String previousLine = null;
            String line;

            String ipAddress = ip.replaceAll("/", "");
            // System.out.println("OSRoutingForwarder toIp "+toIp);
            String[] tokens = ipAddress.split("[.]");
            // System.out.println("OSRoutingForwarder tokens.length "+tokens.length);
            String net = tokens[0] + "." + tokens[1] + "." + tokens[2] + ".";
            // System.out.println("OSRoutingForwarder net "+net);
            while ((interf == null) && ((line = is.readLine()) != null)) {
                if (line.contains(net)) {
                    // System.out.println("OSRoutingForwarder previousLine "+previousLine);
                    interf = previousLine.split(":")[0];
                }
                if (line.toLowerCase().startsWith("ethernet adapter")) {
                    previousLine = line.substring("ethernet adapter".length() + 1);
                } else if (line.toLowerCase().startsWith("scheda ethernet")) {
                    previousLine = line.substring("scheda ethernet".length() + 1);
                }
            }
        } else if (RampEntryPoint.os.startsWith("linux")) {
            Process p = null;
            p = Runtime.getRuntime().exec("ip addr show");
            BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String previousLine = null;
            String previousPreviousLine = null;
            String line;

            String toIp = ip.replaceAll("/", "");
            // System.out.println("OSRoutingForwarder toIp "+toIp);
            String[] tokens = toIp.split("[.]");
            // System.out.println("OSRoutingForwarder tokens.length "+tokens.length);
            String net = tokens[0] + "." + tokens[1] + "." + tokens[2] + ".";
            // System.out.println("OSRoutingForwarder net "+net);
            while ((interf == null) && ((line = is.readLine()) != null)) {
                if (line.contains(net)) {
                    // System.out.println("OSRoutingForwarder previousPreviousLine "+previousPreviousLine);
                    interf = previousPreviousLine.split(": ")[1];
                    // System.out.println("OSRoutingForwarder interf "+interf);
                }
                previousPreviousLine = previousLine;
                previousLine = line;
            }
        } else {
            throw new Exception("Unsupported Operating System: " + System.getProperty("os.name"));
        }

        return interf;
    }

    /**
     * TODO FIXME
     * This component needs to be replaced since its performance are really poor
     * in case of multiple commands.
     *
     * @param command terminal command
     * @return standard output
     * @throws Exception in case of wrong superUser password
     */
    private static String sudoCommand(String command) throws Exception {
        String res = "";

        String[] commandArray = {"sh", "-c", "sudo -S " + command + " 2>&1"};
        /*
         * System.out.print("sudoCommand.commandArray: "); for(String s : commandArray){ System.out.print(s+" "); } System.out.println();/*
         */

        Process pRoot = Runtime.getRuntime().exec(commandArray);

        InputStream is = pRoot.getInputStream();

        int attempts = 10;
        while (attempts > 0 && is.available() == 0) {
            Thread.sleep(50);
            attempts--;
        }
        String line = "";
        while (is.available() > 0) {
            line += (char) is.read();
        }
        res += line;
        // System.out.println("sudoCommand line1: "+line);

        if (line.contains("password for") || line.contains("password di")) {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(pRoot.getOutputStream()));
            String pass = getSuperuserPassword();
            if (pass == null) {
                throw new Exception("Need a password");
            }
            bw.write(pass);
            bw.newLine();
            bw.flush();

            attempts = 10;
            while (attempts >= 0 && is.available() <= 1) {
                Thread.sleep(200);
                attempts--;
            }
            // System.out.println("sudoCommand is.available(): "+is.available());
            line = "";
            while (is.available() > 0) {
                line += (char) is.read();
            }

            /*
             * TODO Consider to do res = line instead in order to avoid to get the "[sudo] password for" string
             */
            //res += line;
            res = line;
            // System.out.println("sudoCommand line: "+line);

            if (line.contains("is not in the sudoers file")) {
                throw new Exception("The user is not in the sudoers file");
            } else if (line.contains("Sorry, try again")) {
                throw new Exception("Wrong password");
            }
        }

        String[] commandK = {"sh", "-c", "sudo -S -k 2>&1"};
        /*
         * System.out.print("sudoCommand.commandK: "); for(String s : commandK){ System.out.print(s+" "); } System.out.println();/*
         */

        // Process pK =
        Runtime.getRuntime().exec(commandK);
        /*
         * BufferedReader brK = new BufferedReader(new InputStreamReader(pK.getInputStream())); while( (line=brK.readLine()) != null ){ System.out.println("sudoCommand line k: "+line); }/*
         */

        // System.out.println("end sudoCommand: "+command);

        return res;
    }

    private static String getSuperuserPassword() {
        // TODO Check this
//        if (System.currentTimeMillis() - lastSuperuserPasswordRefresh > TIMEOUT_SU_PASSWORD) {
//            // discard previous password
//            superuserPassword = null;
//        }
        if (superuserPassword == null) {
            // get superuser password
            javax.swing.JPasswordField passwordField = new javax.swing.JPasswordField();
            Object[] message = {"root password?", passwordField};
            int res = javax.swing.JOptionPane.showConfirmDialog(null, message, "Granting root privileges", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE);
            if (res == javax.swing.JOptionPane.OK_OPTION) {
                superuserPassword = new String(passwordField.getPassword());
            }
            // TODO Add check in case of wrong password.
            /*
             * else{ throw new Exception("Need a password"); }
             */
            lastSuperuserPasswordRefresh = System.currentTimeMillis();
        }
        return superuserPassword;
    }
}
