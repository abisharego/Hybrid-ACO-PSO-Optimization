package helpers;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Calculates the fitness of a given solution based on the complex
 * multi-objective function (Power + Load Imbalance + Penalty).
 */
public class FitnessCalculator {

    private final double weightPower;
    private final double weightLoad;

    // Resource safety thresholds (e.g., 100% = 1.0)
    private static final double CPU_THRESHOLD = 0.95;
    static {

    }
    private static final double RAM_THRESHOLD = 0.90;

    public FitnessCalculator(ConfigReader config) {
        this.weightPower = config.getDouble("fitness.weight.power");
        this.weightLoad = config.getDouble("fitness.weight.load");
    }

    /**
     * Calculates the fitness for a given solution (VM-to-Host mapping).
     * Lower fitness is better.
     */
    public double calculateFitness(Solution solution) {
        Map<Host, Set<Vm>> hostVmMap = new HashMap<>();
        Set<Host> activeHosts = new HashSet<>();

        // 1. Check for constraints (Penalty P)
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

        if (activeHostCount == 0) return 0; // No VMs, perfect fitness

        double[] hostCpuLoads = new double[activeHostCount];
        int hostIndex = 0;

        for (Host host : activeHosts) {
            double hostRamUsed = 0;
            double hostCpuMipsUsed = 0;

            for (Vm vm : hostVmMap.get(host)) {
                hostRamUsed += vm.getRam().getCapacity();
                hostCpuMipsUsed += vm.getMips();
            }

            // --- PENALTY CHECK ---
            if (hostRamUsed > host.getRam().getCapacity() * RAM_THRESHOLD ||
                    hostCpuMipsUsed > host.getTotalMipsCapacity() * CPU_THRESHOLD) {
                // Infeasible solution! Return max value.
                return Double.MAX_VALUE;
            }

            // 2. Calculate Power (F1)
            // CloudSim power model needs utilization as a fraction (0.0 to 1.0)
            double hostCpuUtilization = hostCpuMipsUsed / host.getTotalMipsCapacity();
            totalPower += host.getPowerModel().getPower(hostCpuUtilization);

            // 3. Collect data for Load Imbalance (F2)
            hostCpuLoads[hostIndex++] = hostCpuUtilization;
            totalCpuMipsUsed += hostCpuMipsUsed;
            totalHostCpuMips += host.getTotalMipsCapacity();
        }

        // Calculate F2 (Load Imbalance)
        double avgLoad = totalCpuMipsUsed / totalHostCpuMips;
        double sumOfSquaredDiffs = 0;
        for (double load : hostCpuLoads) {
            sumOfSquaredDiffs += Math.pow(load - avgLoad, 2);
        }
        double loadImbalance = Math.sqrt(sumOfSquaredDiffs / activeHostCount);

        // 4. Return the final weighted, multi-objective fitness score
        // NOTE: For a production system, power and loadImbalance should be normalized
        // to a 0-1 scale before applying weights. For this comparison,
        // the raw weighted sum is sufficient to show the trade-off.
        return (weightPower * totalPower) + (weightLoad * loadImbalance);
    }
}