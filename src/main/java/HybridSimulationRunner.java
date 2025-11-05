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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Main class to run the comparison across DIFFERENT HOST COUNTS
 * and APPEND results to CSV for multiple runs per host count.
 */
public class HybridSimulationRunner {

    private final ConfigReader config;
    private final FitnessCalculator fitnessCalc;
    private final int runNumber;
    private final int hostCount; // Added host count for this scenario
    private List<ResultData> currentRunResults;

    // Simple class to hold result data
    private static class ResultData {
        int run;
        int hosts; // Added host count
        String algorithmName;
        double fitness;
        long duration;

        ResultData(int runNo, int hostCnt, String name, double fit, long dur) {
            this.run = runNo;
            this.hosts = hostCnt;
            this.algorithmName = name;
            this.fitness = fit;
            this.duration = dur;
        }
    }

    // Constructor now takes runNumber and hostCount
    public HybridSimulationRunner(int runNumber, int hostCount, ConfigReader config) {
        this.runNumber = runNumber;
        this.hostCount = hostCount; // Store the host count for this run
        this.config = config; // Use shared config
        this.fitnessCalc = new FitnessCalculator(config); // Fitness calc uses config weights
        this.currentRunResults = new ArrayList<>();
    }

    public void runSingleSimulationScenario() {
        System.out.printf("--- Starting Run #%d with %d Hosts ---%n", runNumber, hostCount);

        // Create simulation entities FOR THIS SCENARIO using the specified hostCount
        List<Host> hostList = SimulationFactory.createHostList(hostCount); // Use the passed hostCount
        List<Vm> vmList = SimulationFactory.createVmList(config.getInt("simulation.vms")); // VMs stay constant
        System.out.printf("Created %d Hosts and %d VMs.%n", hostList.size(), vmList.size());


        ACO_Scheduler aco = new ACO_Scheduler(config, fitnessCalc);
        PSO_Scheduler pso = new PSO_Scheduler(config, fitnessCalc);
        Hybrid_Scheduler hybrid = new Hybrid_Scheduler(config, fitnessCalc);

        runAlgorithm("ACO", aco, vmList, hostList);
        runAlgorithm("PSO", pso, vmList, hostList);
        runAlgorithm("Hybrid ACO-PSO", hybrid, vmList, hostList);

        printCurrentRunReport();
        // Append results, header is handled inside saveResultsToCsv
        saveResultsToCsv("results.csv", runNumber == 1 && hostCount == getConfiguredHostCounts().get(0));

        System.out.printf("--- Finished Run #%d with %d Hosts ---%n%n", runNumber, hostCount);
    }

    private void runAlgorithm(String name, SchedulerInterface scheduler, List<Vm> vmList, List<Host> hostList) {
        System.out.printf("Running %s (%d Hosts, Run #%d)...%n", name, hostCount, runNumber);
        long startTime = System.currentTimeMillis();
        Solution bestSolution = scheduler.solve(vmList, hostList);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        double finalFitness = fitnessCalc.calculateFitness(bestSolution);

        System.out.printf("Finished %s in %d ms. Best Fitness: %s%n",
                name, duration, (finalFitness == Double.MAX_VALUE ? "Infinity (Failed)" : String.format("%.4f", finalFitness)));

        // Store result with run number AND host count
        currentRunResults.add(new ResultData(runNumber, hostCount, name, finalFitness, duration));
    }

    private void printCurrentRunReport() {
        System.out.printf("%n--- Comparison Report (%d Hosts, Run #%d) ---%n", hostCount, runNumber);
        System.out.println("--------------------------------------------------------------");
        System.out.printf("| %-20s | %-17s | %-15s |%n", "Algorithm", "Best Fitness", "Time (ms)");
        System.out.println("--------------------------------------------------------------");

        for (ResultData result : currentRunResults) {
            String fitnessStr = (result.fitness == Double.MAX_VALUE) ? "Infinity (Failed)" : String.format("%.4f", result.fitness);
            System.out.printf("| %-20s | %-17s | %-15d |%n",
                    result.algorithmName,
                    fitnessStr,
                    result.duration);
        }
        System.out.println("--------------------------------------------------------------");
        System.out.println("*Lower fitness is better.");
    }

    // Modified to include hostCount and handle header correctly
    private void saveResultsToCsv(String filename, boolean isFirstRunOverall) {
        File file = new File(filename);
        boolean writeHeader = isFirstRunOverall || !file.exists() || file.length() == 0;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, !isFirstRunOverall))) { // Append if not first run

            if (writeHeader) {
                // Write header only once at the very beginning
                writer.println("HostCount,Run,Algorithm,BestFitness,TimeMs");
            }

            // Write data for the current run
            for (ResultData result : currentRunResults) {
                String fitnessCsv = (result.fitness == Double.MAX_VALUE) ? "FAILED" : String.format("%.4f", result.fitness);
                writer.printf("%d,%d,%s,%s,%d%n",
                        result.hosts, // Add host count column
                        result.run,
                        result.algorithmName,
                        fitnessCsv,
                        result.duration);
            }
            // Print message only once per conceptual start
            if (isFirstRunOverall) {
                System.out.printf("%nResults will be saved/appended to %s%n", filename);
            }

        } catch (IOException e) {
            System.err.println("Error writing results to CSV file: " + e.getMessage());
        }
    }

    // Helper method to get the list of host counts (can be configured elsewhere too)
    private static List<Integer> getConfiguredHostCounts() {
        // Define the different scenarios you want to test
        return List.of(15, 16, 18, 20, 25, 30, 35); // Example list
    }

    public static void main(String[] args) {
        int numberOfRunsPerScenario = 5; // <<--- SET RUNS PER HOST COUNT
        String resultsFilename = "results.csv";
        ConfigReader config = new ConfigReader("config.properties"); // Load config once
        List<Integer> hostCounts = getConfiguredHostCounts(); // Get the list of scenarios

        // --- Delete old results file before starting the batch ---
        File oldResults = new File(resultsFilename);
        if (oldResults.exists()) {
            System.out.println("Deleting old results file: " + resultsFilename);
            oldResults.delete();
        }
        // --------------------------------------------------------

        try {
            // Outer loop: Iterate through different host counts (scenarios)
            for (int h = 0; h < hostCounts.size(); h++) {
                int currentHostCount = hostCounts.get(h);
                System.out.printf("%n=== Starting Scenario: %d Hosts ===%n", currentHostCount);

                // Inner loop: Run simulation multiple times for the current host count
                for (int i = 1; i <= numberOfRunsPerScenario; i++) {
                    // Pass run number, host count, and the shared config
                    HybridSimulationRunner runner = new HybridSimulationRunner(i, currentHostCount, config);
                    runner.runSingleSimulationScenario();
                }
            }
            System.out.println("\n === All scenarios complete. Final results are in " + resultsFilename + " ===");

        } catch (Exception e) {
            System.err.println("An error occurred during the simulation runs.");
            e.printStackTrace();
        }
    }
}