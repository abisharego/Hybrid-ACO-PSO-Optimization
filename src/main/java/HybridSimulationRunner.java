import helpers.ConfigReader;
import helpers.FitnessCalculator;
import helpers.SimulationFactory;
import helpers.Solution;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import scheduling.ACO_Scheduler;
import scheduling.Hybrid_Scheduler;
import scheduling.PSO_Scheduler;
import scheduling.SchedulerInterface;

import java.util.List;

/**
 * Main class to run the comparison between ACO, PSO, and Hybrid ACO-PSO.
 */
public class HybridSimulationRunner {

    private final ConfigReader config;
    private final List<Vm> vmList;
    private final List<Host> hostList;
    private final FitnessCalculator fitnessCalc;

    public HybridSimulationRunner() {
        // 1. Load Configuration
        this.config = new ConfigReader("config.properties");

        // 2. Create simulation entities
        // We create them ONCE to ensure all algorithms use the exact same problem set
        System.out.println("Creating simulation environment...");
        this.hostList = SimulationFactory.createHostList(config.getInt("simulation.hosts"));
        this.vmList = SimulationFactory.createVmList(config.getInt("simulation.vms"));
        System.out.printf("Created %d Hosts and %d VMs.%n%n", hostList.size(), vmList.size());

        // 3. Create the fitness calculator
        this.fitnessCalc = new FitnessCalculator(config);
    }

    public void runAllSimulations() {
        System.out.println("--- Starting Algorithm Comparison ---");

        // Create the schedulers
        ACO_Scheduler aco = new ACO_Scheduler(config, fitnessCalc);
        PSO_Scheduler pso = new PSO_Scheduler(config, fitnessCalc);
        Hybrid_Scheduler hybrid = new Hybrid_Scheduler(config, fitnessCalc);

        // Run simulations
        Solution acoSolution = runAlgorithm("Standalone ACO", aco);
        Solution psoSolution = runAlgorithm("Standalone PSO", pso);
        Solution hybridSolution = runAlgorithm("Hybrid ACO-PSO", hybrid);

        // Print final report
        System.out.println("\n--- 🏁 Final Comparison Report ---");
        System.out.println("----------------------------------------------------------");
        System.out.printf("| %-20s | %-15s | %-15s |%n", "Algorithm", "Best Fitness", "Time (ms)");
        System.out.println("----------------------------------------------------------");

        printReportLine("Standalone ACO", acoSolution);
        printReportLine("Standalone PSO", psoSolution);
        printReportLine("Hybrid ACO-PSO", hybridSolution);

        System.out.println("----------------------------------------------------------");
        System.out.println("*Lower fitness is better. Fitness of 'Infinity' means no feasible solution was found.");
    }

    private Solution runAlgorithm(String name, SchedulerInterface scheduler) {
        System.out.printf("Running %s...%n", name);
        long startTime = System.currentTimeMillis();

        // Run the algorithm
        Solution bestSolution = scheduler.solve(vmList, hostList);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        bestSolution.setFitness(fitnessCalc.calculateFitness(bestSolution)); // Recalculate just to be 100% sure
        bestSolution.getMapping().put(null, null); // Placeholder for any algorithm-specific data
        bestSolution.setDuration(duration); // Store duration for the report

        System.out.printf("Finished %s in %d ms. Best Fitness: %.4f%n",
                name, duration, bestSolution.getFitness());

        return bestSolution;
    }

    private void printReportLine(String name, Solution solution) {
        long duration = solution.getDuration();
        System.out.printf("| %-20s | %-15.4f | %-15d |%n",
                name,
                solution.getFitness(),
                duration);
    }


    public static void main(String[] args) {
        try {
            HybridSimulationRunner runner = new HybridSimulationRunner();
            runner.runAllSimulations();
        } catch (Exception e) {
            System.err.println("An error occurred during the simulation.");
            e.printStackTrace();
        }
    }
}