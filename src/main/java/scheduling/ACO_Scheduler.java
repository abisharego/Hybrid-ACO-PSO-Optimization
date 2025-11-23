package scheduling;

import helpers.AdvancedFitnessCalculator; // <-- 1. IMPORT the new calculator
import helpers.ConfigReader;
import helpers.SimulationFactory;
import helpers.Solution;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

/**
 * Implements the Ant Colony Optimization (ACO) algorithm for VM placement.
 */
public class ACO_Scheduler implements SchedulerInterface {

    private final int numAnts;
    private int maxIterations;
    private final double alpha; // Pheromone influence
    private final double beta;  // Heuristic influence
    private final double rho;   // Evaporation rate
    private final double q;     // Pheromone deposit factor
    private final int hybridInitGenerations;

    private final AdvancedFitnessCalculator fitnessCalc;
    private double[][] pheromoneTrails;
    private List<Vm> vmList;
    private List<Host> hostList;
    private Solution globalBestSolution;

    public ACO_Scheduler(ConfigReader config, AdvancedFitnessCalculator fitnessCalc) {
        this.numAnts = config.getInt("aco.ants");
        this.maxIterations = config.getInt("aco.iterations");
        this.alpha = config.getDouble("aco.alpha");
        this.beta = config.getDouble("aco.beta");
        this.rho = config.getDouble("aco.rho");
        this.q = config.getDouble("aco.q");
        this.hybridInitGenerations = config.getInt("hybrid.aco.initGenerations");
        this.fitnessCalc = fitnessCalc;
        this.globalBestSolution = new Solution();
    }

    public ACO_Scheduler(ConfigReader config, AdvancedFitnessCalculator fitnessCalc, int overrideIterations) {
        this(config, fitnessCalc); // Calls the main constructor
        this.maxIterations = overrideIterations; // But then overrides the iteration count
    }

    @Override
    public Solution solve(List<Vm> vmList, List<Host> hostList) {
        initialize(vmList, hostList);
        runACO(this.maxIterations);
        return globalBestSolution;
    }

    /**
     * A special method for the Hybrid scheduler.
     * Runs a few iterations to "warm up" the trails, then returns
     * a full population of ant-generated solutions.
     */

    private void runACO(int iterations) {
        for (int iter = 0; iter < iterations; iter++) {
            List<Solution> antSolutions = new ArrayList<>(numAnts);
            for (int i = 0; i < numAnts; i++) {
                Solution antSolution = buildSolutionForAnt();
                antSolution.setFitness(fitnessCalc.calculateFitness(antSolution, vmList));
                antSolutions.add(antSolution);

                if (iter == 0 && i == 0) {
                    globalBestSolution = antSolution.clone();
                }
            }

            // Update global best
            for (Solution solution : antSolutions) {
                if (solution.getFitness() < globalBestSolution.getFitness()) {
                    globalBestSolution = solution.clone();
                }
            }
            updatePheromones(antSolutions);
        }
    }

    private Solution buildSolutionForAnt() {
        Solution solution = new Solution();
        Map<Vm, Host> mapping = new HashMap<>();

        // Maps to track the load *during* the build process
        Map<Host, Double> hostCpuMipsLoad = new HashMap<>();
        Map<Host, Double> hostRamLoad = new HashMap<>();
        Map<Host, Double> hostNetworkIoLoad = new HashMap<>(); // <-- NEW

        for (Vm vm : vmList) {
            // Pass all three load maps to the ant's "brain"
            Host selectedHost = selectHostForVm(vm, solution, hostCpuMipsLoad, hostRamLoad, hostNetworkIoLoad); // <-- MODIFIED

            mapping.put(vm, selectedHost);
            solution.setMapping(mapping); // Update solution *inside* loop

            // 1. Update CPU Load
            double currentMips = hostCpuMipsLoad.getOrDefault(selectedHost, 0.0);
            hostCpuMipsLoad.put(selectedHost, currentMips + vm.getMips());

            // 2. Update RAM Load
            double currentRam = hostRamLoad.getOrDefault(selectedHost, 0.0);
            hostRamLoad.put(selectedHost, currentRam + vm.getRam().getCapacity());

            // 3. Update Network I/O Load (for this host AND its neighbors)
            double newIoForThisHost = 0.0;
            for (Map.Entry<Vm, Host> entry : solution.getMapping().entrySet()) {
                Vm placedVm = entry.getKey();
                if (placedVm.getId() == vm.getId()) continue; // Don't compare to self

                Host placedHost = entry.getValue();

                // Get traffic between the new VM and the already-placed VM
                int traffic = SimulationFactory.vmTrafficMatrix[(int)vm.getId()][(int)placedVm.getId()];

                if (traffic > 0 && selectedHost.getId() != placedHost.getId()) {
                    // This traffic goes over the network

                    // Add I/O load to the new VM's host
                    newIoForThisHost += traffic;

                    // Add I/O load to the *other* host
                    double otherHostLoad = hostNetworkIoLoad.getOrDefault(placedHost, 0.0);
                    hostNetworkIoLoad.put(placedHost, otherHostLoad + traffic);
                }
            }
            // Add the new I/O to this host's total
            double thisHostLoad = hostNetworkIoLoad.getOrDefault(selectedHost, 0.0);
            hostNetworkIoLoad.put(selectedHost, thisHostLoad + newIoForThisHost);
        }
        return solution;
    }

    private static final double CPU_THRESHOLD = 1.0;
    private static final double RAM_THRESHOLD = 1.0;

    // This is the 4-objective heuristic
    private Host selectHostForVm(Vm vm, Solution currentAntSolution,
                                 Map<Host, Double> currentHostCpuMipsLoad,
                                 Map<Host, Double> currentHostRamLoad,
                                 Map<Host, Double> currentHostNetworkIoLoad) {

        int vmIndex = vmList.indexOf(vm);
        double[] probabilities = new double[hostList.size()];
        double totalProbability = 0;

        double w_h_load = 0.5;
        double w_h_network = 0.5;

        for (int j = 0; j < hostList.size(); j++) {
            Host host = hostList.get(j);
            double pheromone = Math.pow(pheromoneTrails[vmIndex][j], alpha);

            // --- 1. Heuristic for Load (h_load) ---
            double currentMips = currentHostCpuMipsLoad.getOrDefault(host, 0.0);
            double newLoadRatio = (currentMips + vm.getMips()) / host.getTotalMipsCapacity();
            double h_load = 1.0 / (newLoadRatio + 1e-6);

            // --- 2. Heuristic for Network (h_network) ---
            double cost_network = 0.0; // This is the *new* traffic this VM will add
            int vmi_id = (int) vm.getId();
            int hostj_id = (int) host.getId();

            for (Map.Entry<Vm, Host> entry : currentAntSolution.getMapping().entrySet()) {
                Vm placedVm = entry.getKey();
                Host placedHost = entry.getValue();
                int vmk_id = (int) placedVm.getId();
                int hostm_id = (int) placedHost.getId();

                int traffic = SimulationFactory.vmTrafficMatrix[vmi_id][vmk_id];
                if (traffic > 0 && hostj_id != hostm_id) { // Only count if on different hosts
                    cost_network += traffic;
                }
            }
            double h_network = 1.0 / (cost_network + 1e-6);

            // --- 3. Final Heuristic & Probability ---
            double heuristic = Math.pow(h_load, w_h_load) * Math.pow(h_network, w_h_network);

            // --- 4. Feasibility Check ---

            // Check CPU
            if (newLoadRatio > CPU_THRESHOLD) {
                probabilities[j] = 0; // Infeasible
                continue; // Go to next host
            }

            // Check RAM
            double currentRam = currentHostRamLoad.getOrDefault(host, 0.0);
            double newRamRatio = (currentRam + vm.getRam().getCapacity()) / host.getRam().getCapacity();
            if (newRamRatio > RAM_THRESHOLD) {
                probabilities[j] = 0; // Infeasible
                continue; // Go to next host
            }

            // Check Network I/O
            double currentNetworkIo = currentHostNetworkIoLoad.getOrDefault(host, 0.0);
            double newNetworkIo = currentNetworkIo + cost_network;
            double newNetworkUtilization = newNetworkIo / SimulationFactory.HOST_BW_CAPACITY;

            if (newNetworkUtilization > 1.0) { // Check against 100% capacity
                probabilities[j] = 0; // Infeasible
                continue; // Go to next host
            }

            // If we are here, the host is feasible
            probabilities[j] = pheromone * Math.pow(heuristic, beta);
            totalProbability += probabilities[j];
        }

        // --- 5. Roulette Wheel Selection (Updated Fallback) ---
        if (totalProbability == 0) {
            // All hosts are infeasible or have 0 probability. Try a robust fallback.
            for (int j = 0; j < hostList.size(); j++) {
                Host host = hostList.get(j);
                double currentMips = currentHostCpuMipsLoad.getOrDefault(host, 0.0);
                double newLoadRatio = (currentMips + vm.getMips()) / host.getTotalMipsCapacity();
                double currentRam = currentHostRamLoad.getOrDefault(host, 0.0);
                double newRamRatio = (currentRam + vm.getRam().getCapacity()) / host.getRam().getCapacity();

                // Calculate network cost for this host
                double fallback_cost_network = 0.0;
                for (Map.Entry<Vm, Host> entry : currentAntSolution.getMapping().entrySet()) {
                    int traffic = SimulationFactory.vmTrafficMatrix[(int)vm.getId()][(int)entry.getKey().getId()];
                    if (traffic > 0 && host.getId() != entry.getValue().getId()) {
                        fallback_cost_network += traffic;
                    }
                }
                double currentNetworkIo = currentHostNetworkIoLoad.getOrDefault(host, 0.0);
                double newNetworkUtilization = (currentNetworkIo + fallback_cost_network) / SimulationFactory.HOST_BW_CAPACITY;

                // Check all 3 constraints
                if (newLoadRatio <= CPU_THRESHOLD && newRamRatio <= RAM_THRESHOLD && newNetworkUtilization <= 1.0) {
                    return host; // Return first feasible host
                }
            }
            // Super-Fallback: If no host can fit it, return first host (will be penalized)
            return hostList.get(0);
        }

        // Standard roulette wheel
        double rand = Math.random() * totalProbability;
        double cumulativeProbability = 0;
        for (int j = 0; j < hostList.size(); j++) {
            if (probabilities[j] == 0) continue;
            cumulativeProbability += probabilities[j];
            if (rand <= cumulativeProbability) {
                return hostList.get(j);
            }
        }

        // Should not be reached, but as a final safety net
        return hostList.get(0);
    }

    private void updatePheromones(List<Solution> antSolutions) {
        // 1. Evaporation
        for (int i = 0; i < vmList.size(); i++) {
            for (int j = 0; j < hostList.size(); j++) {
                pheromoneTrails[i][j] *= (1.0 - rho);
            }
        }

        // 2. Deposition
        if (globalBestSolution.getFitness() == Double.MAX_VALUE) {
            return;
        }

        double deposit = q / (globalBestSolution.getFitness() + 1.0);

        for (Map.Entry<Vm, Host> entry : globalBestSolution.getMapping().entrySet()) {
            int vmIndex = vmList.indexOf(entry.getKey());
            int hostIndex = hostList.indexOf(entry.getValue());
            if (vmIndex != -1 && hostIndex != -1) {
                pheromoneTrails[vmIndex][hostIndex] += deposit;
            }
        }
    }

    private void initialize(List<Vm> vmList, List<Host> hostList) {
        this.vmList = vmList;
        this.hostList = hostList;
        this.globalBestSolution = new Solution();

        int numVMs = vmList.size();
        int numHosts = hostList.size();
        this.pheromoneTrails = new double[numVMs][numHosts];
        double initialPheromone = 1.0;
        for (int i = 0; i < numVMs; i++) {
            for (int j = 0; j < numHosts; j++) {
                pheromoneTrails[i][j] = initialPheromone;
            }
        }
    }
}