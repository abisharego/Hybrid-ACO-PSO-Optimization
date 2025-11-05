package scheduling;

import helpers.AdvancedFitnessCalculator;
import helpers.ConfigReader;
import helpers.Solution;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * This class is now a PURE REFINEMENT ENGINE.
 * It does NOT implement SchedulerInterface.
 * It takes the results from other schedulers and refines them.
 */
public class Hybrid_Scheduler {

    private final ConfigReader config;
    private final AdvancedFitnessCalculator fitnessCalc;
    private final int populationSize;
    private final int finalPsoIterations;

    public Hybrid_Scheduler(ConfigReader config, AdvancedFitnessCalculator fitnessCalc) {
        this.config = config;
        this.fitnessCalc = fitnessCalc;
        this.populationSize = config.getInt("pso.population");
        // Get the "refinement" iterations
        this.finalPsoIterations = config.getInt("hybrid.scout.pso.iterations"); // We re-use this config
    }

    /**
     * This new 'solve' method takes the scout solutions as input.
     */
    public Solution solve(List<Vm> vmList, List<Host> hostList,
                          Solution acoScoutSolution, Solution psoScoutSolution) {

        // --- 1. FUSION PHASE ---
        System.out.println("  Hybrid: Fusing scout results...");
        List<Solution> eliteSwarm = new ArrayList<>(populationSize);

        // Add the best solutions from the scout phase
        if (acoScoutSolution.getFitness() != Double.MAX_VALUE) {
            eliteSwarm.add(acoScoutSolution.clone());
        }
        if (psoScoutSolution.getFitness() != Double.MAX_VALUE) {
            eliteSwarm.add(psoScoutSolution.clone());
        }

        if (eliteSwarm.isEmpty()) {
            System.out.println("  Hybrid: Neither scout found a feasible solution. Hybrid fails.");
            return psoScoutSolution; // Return one of the 'Infinity' solutions
        }

        // Find the "best of the best" to fill the swarm
        Solution bestOfScouts = (acoScoutSolution.getFitness() < psoScoutSolution.getFitness())
                ? acoScoutSolution : psoScoutSolution;

        while (eliteSwarm.size() < populationSize) {
            eliteSwarm.add(bestOfScouts.clone());
        }

        // --- 2. REFINEMENT PHASE ---
        System.out.println("  Hybrid: Running final refinement PSO stage for " + finalPsoIterations + " iterations...");
        PSO_Scheduler finalPso = new PSO_Scheduler(config, fitnessCalc, finalPsoIterations);
        return finalPso.solveWithInitialSwarm(eliteSwarm, vmList, hostList);
    }
}