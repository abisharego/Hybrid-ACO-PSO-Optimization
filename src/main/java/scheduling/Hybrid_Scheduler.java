package scheduling;

import helpers.ConfigReader;
import helpers.FitnessCalculator;
import helpers.Solution;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;

/**
 * Implements the sequential Hybrid ACO-PSO algorithm.
 * 1. Uses ACO to generate a smart initial population.
 * 2. Uses PSO to refine that population to find the global best.
 */
public class Hybrid_Scheduler implements SchedulerInterface {

    private final ACO_Scheduler aco;
    private final PSO_Scheduler pso;
    private final int populationSize;

    public Hybrid_Scheduler(ConfigReader config, FitnessCalculator fitnessCalc) {
        // Create internal instances of ACO and PSO
        this.aco = new ACO_Scheduler(config, fitnessCalc);
        this.pso = new PSO_Scheduler(config, fitnessCalc);
        this.populationSize = config.getInt("pso.population");
    }

    @Override
    public Solution solve(List<Vm> vmList, List<Host> hostList) {
        // Stage 1: Use ACO to generate an intelligent initial population
        // This swarm is "warm" - it's already composed of good, feasible solutions.
        List<Solution> initialSwarm = aco.getInitialPopulation(populationSize, vmList, hostList);

        // Stage 2: Feed this smart swarm to PSO for refinement.
        // PSO will now work on *refining* good solutions, not *finding* feasible ones.
        return pso.solveWithInitialSwarm(initialSwarm, vmList, hostList);
    }
}