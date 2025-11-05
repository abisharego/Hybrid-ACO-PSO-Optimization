package scheduling;

import helpers.ConfigReader;
import helpers.FitnessCalculator;
import helpers.Solution;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.ArrayList;
import java.util.List;

public class Hybrid_Scheduler implements SchedulerInterface {

    private final ConfigReader config;
    private final FitnessCalculator fitnessCalc;
    private final int populationSize;
    private final int scoutAcoIterations;
    private final int scoutPsoIterations;
    private final int finalPsoIterations;

    public Hybrid_Scheduler(ConfigReader config, FitnessCalculator fitnessCalc) {
        this.config = config;
        this.fitnessCalc = fitnessCalc;
        this.populationSize = config.getInt("pso.population");
        this.scoutAcoIterations = config.getInt("hybrid.scout.aco.iterations");
        this.scoutPsoIterations = config.getInt("hybrid.scout.pso.iterations");
        this.finalPsoIterations = config.getInt("pso.iterations"); // Main PSO iterations for refinement
    }

    @Override
    public Solution solve(List<Vm> vmList, List<Host> hostList) {
        // --- 1. SCOUT PHASE ---
        // Create scout schedulers with their specific iteration counts
        ACO_Scheduler acoScout = new ACO_Scheduler(config, fitnessCalc, scoutAcoIterations);
        PSO_Scheduler psoScout = new PSO_Scheduler(config, fitnessCalc, scoutPsoIterations);

        System.out.println("  Hybrid Step 1: Running ACO Scout for " + scoutAcoIterations + " iterations...");
        Solution acoBestSolution = acoScout.solve(vmList, hostList);

        System.out.println("  Hybrid Step 2: Running PSO Scout for " + scoutPsoIterations + " iterations...");
        Solution psoBestSolution = psoScout.solve(vmList, hostList);

        // --- 2. FUSION PHASE ---
        System.out.println("  Hybrid Step 3: Fusing results and creating elite swarm...");
        List<Solution> eliteSwarm = new ArrayList<>(populationSize);

        if (acoBestSolution.getFitness() != Double.MAX_VALUE) {
            eliteSwarm.add(acoBestSolution.clone());
        }
        if (psoBestSolution.getFitness() != Double.MAX_VALUE) {
            eliteSwarm.add(psoBestSolution.clone());
        }

        if (eliteSwarm.isEmpty()) {
            System.out.println("  Warning: Neither scout found a feasible solution. The hybrid will also fail.");
            // Return one of the failed solutions to report Infinity correctly
            return psoBestSolution;
        }

        Solution bestOfScouts = (acoBestSolution.getFitness() < psoBestSolution.getFitness())
                ? acoBestSolution : psoBestSolution;

        while (eliteSwarm.size() < populationSize) {
            eliteSwarm.add(bestOfScouts.clone());
        }

        // --- 3. REFINEMENT PHASE ---
        System.out.println("  Hybrid Step 4: Running final refinement PSO stage for " + finalPsoIterations + " iterations...");
        PSO_Scheduler finalPso = new PSO_Scheduler(config, fitnessCalc, finalPsoIterations);
        return finalPso.solveWithInitialSwarm(eliteSwarm, vmList, hostList);
    }
}