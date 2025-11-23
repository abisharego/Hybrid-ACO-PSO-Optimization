package scheduling;

import helpers.AdvancedFitnessCalculator;
import helpers.ConfigReader;
import helpers.Solution;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Hybrid_Scheduler {

    private final ConfigReader config;
    private final AdvancedFitnessCalculator fitnessCalc;
    private final int populationSize;
    private final int finalPsoIterations;
    private final Random random;

    public Hybrid_Scheduler(ConfigReader config, AdvancedFitnessCalculator fitnessCalc) {
        this.config = config;
        this.fitnessCalc = fitnessCalc;
        this.populationSize = config.getInt("pso.population");
        this.finalPsoIterations = config.getInt("hybrid.scout.pso.iterations");
        this.random = new Random();
    }

    public Solution solve(List<Vm> vmList, List<Host> hostList,
                          Solution acoScoutSolution, Solution psoScoutSolution) {

        System.out.println("  Hybrid: Fusing scout results...");
        List<Solution> eliteSwarm = new ArrayList<>(populationSize);

        Solution bestOfScouts = null;
        boolean acoValid = acoScoutSolution.getFitness() != Double.MAX_VALUE;
        boolean psoValid = psoScoutSolution.getFitness() != Double.MAX_VALUE;

        if (acoValid && psoValid) {
            bestOfScouts = (acoScoutSolution.getFitness() < psoScoutSolution.getFitness())
                    ? acoScoutSolution : psoScoutSolution;
        } else if (acoValid) {
            bestOfScouts = acoScoutSolution;
        } else if (psoValid) {
            bestOfScouts = psoScoutSolution;
        }

        if (bestOfScouts == null) {
            System.out.println("  Hybrid: Neither scout found a feasible solution. Hybrid fails.");
            return psoScoutSolution;
        }

        // 1. Add the Anchor
        eliteSwarm.add(bestOfScouts.clone());

        // 2. Generate "Smart" Mutations (Now with Power Optimization!)
        while (eliteSwarm.size() < populationSize) {
            Solution mutatedSolution = bestOfScouts.clone();

            int strategy = random.nextInt(3); // 0, 1, or 2

            if (strategy == 0) {
                // Strategy 1: Power Optimizer (Consolidation)
                smartPowerPerturbation(mutatedSolution, vmList, hostList);
            } else if (strategy == 1) {
                // Strategy 2: Network Optimizer (Traffic Reduction)
                smartNetworkPerturbation(mutatedSolution, vmList);
            } else {
                // Strategy 3: Load Balancer (Prevent Hotspots)
                smartBalancePerturbation(mutatedSolution, vmList, hostList);
            }

            double newFit = fitnessCalc.calculateFitness(mutatedSolution, vmList);
            mutatedSolution.setFitness(newFit);
            eliteSwarm.add(mutatedSolution);
        }

        System.out.println("  Hybrid: Running final refinement PSO stage for " + finalPsoIterations + " iterations...");
        PSO_Scheduler finalPso = new PSO_Scheduler(config, fitnessCalc, finalPsoIterations);
        return finalPso.solveWithInitialSwarm(eliteSwarm, vmList, hostList);
    }

    /**
     * Strategy 1: Power Optimizer (Consolidation)
     * Moves a VM from a random host to a *different* random host.
     * This encourages packing by chance, which the fitness function will reward.
     */
    private void smartPowerPerturbation(Solution solution, List<Vm> vmList, List<Host> hostList) {
        Map<Vm, Host> mapping = solution.getMapping();
        int numberOfMoves = Math.max(1, (int)(vmList.size() * 0.05));

        for (int i = 0; i < numberOfMoves; i++) {
            // Pick a random VM
            Vm vm = vmList.get(random.nextInt(vmList.size()));
            Host currentHost = mapping.get(vm);

            // Try to move it to a random host (hopefully filling it up)
            Host newHost = hostList.get(random.nextInt(hostList.size()));

            if (newHost.getId() != currentHost.getId()) {
                mapping.put(vm, newHost);
            }
        }
        solution.setMapping(mapping);
    }

    /**
     * Strategy 2: Load Balancer
     * Moves a VM to a random host to fix imbalances.
     */
    private void smartBalancePerturbation(Solution solution, List<Vm> vmList, List<Host> hostList) {
        // Similar logic, but the Fitness Function will select the ones that balance load
        smartPowerPerturbation(solution, vmList, hostList);
    }

    /**
     * Strategy 3: Network Optimizer
     * Swap two VMs. Changes network paths without changing load/power much.
     */
    private void smartNetworkPerturbation(Solution solution, List<Vm> vmList) {
        Map<Vm, Host> mapping = solution.getMapping();
        int numberOfSwaps = Math.max(1, (int)(vmList.size() * 0.10));

        for (int k = 0; k < numberOfSwaps; k++) {
            Vm vm1 = vmList.get(random.nextInt(vmList.size()));
            Vm vm2 = vmList.get(random.nextInt(vmList.size()));

            Host host1 = mapping.get(vm1);
            Host host2 = mapping.get(vm2);

            if (host1.getId() != host2.getId()) {
                mapping.put(vm1, host2);
                mapping.put(vm2, host1);
            }
        }
        solution.setMapping(mapping);
    }
}