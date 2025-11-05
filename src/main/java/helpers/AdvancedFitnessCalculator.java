package helpers;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

/**
 * Calculates fitness based on 4 objectives:
 * 1. Power Consumption (F_power)
 * 2. Load Imbalance (F_load)
 * 3. Total Network Traffic (F_network)
 * 4. Max Link/Host Utilization (F_link)
 */
public class AdvancedFitnessCalculator {

    // Weights for the 4 objectives
    private final double w_power;
    private final double w_load;
    private final double w_network;
    private final double w_link;

    // These will store the raw (unweighted) components of the last calculation
    private double lastPower = 0;
    private double lastLoad = 0;
    private double lastNetwork = 0;
    private double lastLink = 0;

    // Safety thresholds
    private static final double CPU_THRESHOLD = 1.0;
    private static final double RAM_THRESHOLD = 1.0;

    public AdvancedFitnessCalculator(ConfigReader config) {
        this.w_power = config.getDouble("fitness.weight.power");
        this.w_load = config.getDouble("fitness.weight.load");
        this.w_network = config.getDouble("fitness.weight.network");
        this.w_link = config.getDouble("fitness.weight.link");
    }

    public double calculateFitness(Solution solution, List<Vm> vmList) {
        Map<Host, Set<Vm>> hostVmMap = new HashMap<>();
        Set<Host> activeHosts = new HashSet<>();

        // --- 1. CONSTRAINT & RESOURCE CHECK (F_power, F_load) ---

        // Map VMs to Hosts and check resource constraints
        for (Map.Entry<Vm, Host> entry : solution.getMapping().entrySet()) {
            Vm vm = entry.getKey();
            Host host = entry.getValue();
            activeHosts.add(host);

            hostVmMap.putIfAbsent(host, new HashSet<>());
            hostVmMap.get(host).add(vm);
        }

        double totalPower = 0;
        double totalCpuMipsUsed = 0;
        double totalHostCpuMips = 0;
        int activeHostCount = activeHosts.size();

        if (activeHostCount == 0 && !vmList.isEmpty()) {
            // This is an uninitialized or empty solution, but not a valid "0" score
            // It will be penalized by the network traffic part if VMs have traffic
        }
        if (activeHostCount == 0 && vmList.isEmpty()){
            return 0;
        }

        double[] hostCpuLoads = new double[activeHostCount];
        int hostIndex = 0;

        // This map will store the total network I/O for each host
        Map<Host, Double> hostNetworkIo = new HashMap<>();

        for (Host host : activeHosts) {
            double hostRamUsed = 0;
            double hostCpuMipsUsed = 0;
            hostNetworkIo.put(host, 0.0); // Initialize network I/O

            if(hostVmMap.get(host) == null) continue; // Skip if host is active but has no VMs mapped (??)

            for (Vm vm : hostVmMap.get(host)) {
                hostRamUsed += vm.getRam().getCapacity();
                hostCpuMipsUsed += vm.getMips();
            }

            // --- PENALTY CHECK ---
            if (hostRamUsed > host.getRam().getCapacity() * RAM_THRESHOLD ||
                    hostCpuMipsUsed > host.getTotalMipsCapacity() * CPU_THRESHOLD) {
                return Double.MAX_VALUE; // Infeasible solution
            }

            // --- F_power ---
            double hostCpuUtilization = hostCpuMipsUsed / host.getTotalMipsCapacity();
            totalPower += host.getPowerModel().getPower(hostCpuUtilization);

            // --- F_load (Data collection) ---
            hostCpuLoads[hostIndex++] = hostCpuUtilization;
            totalCpuMipsUsed += hostCpuMipsUsed;
            totalHostCpuMips += host.getTotalMipsCapacity();
        }

        // --- F_load (Calculation) ---
        double loadImbalance = 0;
        if (activeHostCount > 0) {
            double avgLoad = totalCpuMipsUsed / (totalHostCpuMips + 1e-6); // Avoid div by zero
            double sumOfSquaredDiffs = 0;
            for (double load : hostCpuLoads) {
                sumOfSquaredDiffs += Math.pow(load - avgLoad, 2);
            }
            loadImbalance = Math.sqrt(sumOfSquaredDiffs / activeHostCount);
        }

        // --- 2. NETWORK CALCULATION (F_network, F_link) ---
        double totalNetworkTraffic = 0;

        for (int i = 0; i < vmList.size(); i++) {
            for (int j = i + 1; j < vmList.size(); j++) {
                Vm vm_i = vmList.get(i);
                Vm vm_j = vmList.get(j);

                int traffic = SimulationFactory.vmTrafficMatrix[(int) vm_i.getId()][(int) vm_j.getId()];
                if (traffic == 0) continue;

                Host host_i = solution.getMapping().get(vm_i);
                Host host_j = solution.getMapping().get(vm_j);

                if (host_i == null || host_j == null) {
                    // This happens with an empty solution. Penalize it heavily.
                    totalNetworkTraffic += (traffic * 2); // 2 hops is default penalty
                    continue;
                }

                int hops = SimulationFactory.hostHopMatrix[(int) host_i.getId()][(int) host_j.getId()];

                // --- F_network ---
                totalNetworkTraffic += (traffic * hops);

                // --- F_link (Data collection) ---
                if (hops > 0) {
                    // This traffic goes over the network
                    hostNetworkIo.put(host_i, hostNetworkIo.getOrDefault(host_i, 0.0) + traffic);
                    hostNetworkIo.put(host_j, hostNetworkIo.getOrDefault(host_j, 0.0) + traffic);
                }
            }
        }

        // --- F_link (Calculation) ---
        double maxHostNetworkLoad = 0;
        for (Double load : hostNetworkIo.values()) {
            maxHostNetworkLoad = Math.max(maxHostNetworkLoad, load);
        }

        // Using the static HOST_BW_CAPACITY from SimulationFactory
        double maxLinkUtilization = maxHostNetworkLoad / (SimulationFactory.HOST_BW_CAPACITY + 1e-6);

        if (maxLinkUtilization > 1.0) {
            return Double.MAX_VALUE; // Infeasible (congested) solution
        }

        this.lastPower = totalPower;
        this.lastLoad = loadImbalance;
        this.lastNetwork = totalNetworkTraffic;
        this.lastLink = maxLinkUtilization;

        // --- 3. FINAL WEIGHTED SUM ---
        double finalFitness = (w_power * totalPower) +
                (w_load * loadImbalance) +
                (w_network * totalNetworkTraffic) +
                (w_link * maxLinkUtilization);

        return finalFitness;
    }
    public double getLastPower() {
        return lastPower;
    }

    public double getLastLoad() {
        return lastLoad;
    }

    public double getLastNetwork() {
        return lastNetwork;
    }

    public double getLastLink() {
        return lastLink;
    }
}