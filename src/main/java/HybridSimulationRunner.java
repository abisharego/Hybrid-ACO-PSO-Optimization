import helpers.AdvancedFitnessCalculator;
import helpers.ConfigReader;
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

public class HybridSimulationRunner {

    private final ConfigReader config;
    private final AdvancedFitnessCalculator fitnessCalc; // <-- 2. CHANGE this field type
    private final int runNumber;
    private final int hostCount;
    private List<ResultData> currentRunResults;

    // --- Store vmList and hostList as fields ---
    private final List<Vm> vmList;
    private final List<Host> hostList;

    private static class ResultData {
        int run;
        int hosts;
        String algorithmName;
        double fitness;
        long duration;
        double power;
        double load;
        double network;
        double link;
        ResultData(int runNo, int hostCnt, String name, long dur,
                   double fit, double p, double l, double n, double k) {
            this.run = runNo;
            this.hosts = hostCnt;
            this.algorithmName = name;
            this.duration = dur;
            this.fitness = fit;
            this.power = p;
            this.load = l;
            this.network = n;
            this.link = k;
        }
    }

    public HybridSimulationRunner(int runNumber, int hostCount, ConfigReader config) {
        this.runNumber = runNumber;
        this.hostCount = hostCount;
        this.config = config;
        this.fitnessCalc = new AdvancedFitnessCalculator(config); // <-- 3. CHANGE this instantiation
        this.currentRunResults = new ArrayList<>();

        // --- CREATE ENTITIES AND NETWORK DATA ---
        System.out.printf("--- Creating Environment for Run #%d (%d Hosts) ---%n", runNumber, hostCount);
        this.hostList = SimulationFactory.createHostList(hostCount);
        this.vmList = SimulationFactory.createVmList(config.getInt("simulation.vms"));

        // NEW: Create the static network matrices
        SimulationFactory.createHostHopMatrix(this.hostList);
        SimulationFactory.createVmTrafficMatrix(this.vmList);

        System.out.printf("Created %d Hosts, %d VMs, and Network Matrices.%n", hostList.size(), vmList.size());
    }

    // In HybridSimulationRunner.java

    public void runSingleSimulationScenario() {
        System.out.printf("--- Starting Run #%d with %d Hosts ---%n", runNumber, hostCount);

        // --- 1. CREATE ALGORITHMS ---
        // Create the two "standalone" competitors using their full iterations
        ACO_Scheduler aco = new ACO_Scheduler(config, fitnessCalc, config.getInt("aco.iterations"));
        PSO_Scheduler pso = new PSO_Scheduler(config, fitnessCalc, config.getInt("pso.iterations"));
        // Create the hybrid "refiner"
        Hybrid_Scheduler hybrid = new Hybrid_Scheduler(config, fitnessCalc);

        // --- 2. RUN STANDALONE ALGORITHMS (THE "SCOUTS") ---
        // These results will be used for BOTH the report AND as input for the hybrid

        System.out.println("Running ACO (Standalone)...");
        long acoStartTime = System.currentTimeMillis();
        Solution acoSolution = aco.solve(vmList, hostList); // Run ACO
        long acoEndTime = System.currentTimeMillis();
        long acoDuration = acoEndTime - acoStartTime;
        double acoFitness = fitnessCalc.calculateFitness(acoSolution, vmList); // Get its final score
        System.out.printf("Finished ACO in %d ms. Best Fitness: %s%n",
                acoDuration, (acoFitness == Double.MAX_VALUE ? "Infinity (Failed)" : String.format("%.4f", acoFitness)));

        System.out.println("Running PSO (Standalone)...");
        long psoStartTime = System.currentTimeMillis();
        Solution psoSolution = pso.solve(vmList, hostList); // Run PSO
        long psoEndTime = System.currentTimeMillis();
        long psoDuration = psoEndTime - psoStartTime;
        double psoFitness = fitnessCalc.calculateFitness(psoSolution, vmList); // Get its final score
        System.out.printf("Finished PSO in %d ms. Best Fitness: %s%n",
                psoDuration, (psoFitness == Double.MAX_VALUE ? "Infinity (Failed)" : String.format("%.4f", psoFitness)));

        // --- 3. RUN HYBRID ALGORITHM (THE "REFINEMENT") ---
        // This is a 100% fair comparison.

        System.out.println("Running Hybrid ACO-PSO (Refinement Stage)...");
        long hybridStartTime = System.currentTimeMillis();
        // Pass the *results* from the standalone runs directly to the hybrid
        Solution hybridSolution = hybrid.solve(vmList, hostList, acoSolution, psoSolution);
        long hybridEndTime = System.currentTimeMillis();

        // The hybrid's "refinement" time
        long hybridRefinementDuration = hybridEndTime - hybridStartTime;
        // The "Total" hybrid time is the time of its scouts + its refinement
        // We assume the scouts run in parallel, so we take the *slowest* scout
        long totalHybridTime = Math.max(acoDuration, psoDuration) + hybridRefinementDuration;

        double hybridFitness = fitnessCalc.calculateFitness(hybridSolution, vmList); // Get final hybrid score
        System.out.printf("Finished Hybrid in %d ms (Refinement: %d ms). Best Fitness: %s%n",
                totalHybridTime, hybridRefinementDuration, (hybridFitness == Double.MAX_VALUE ? "Infinity (Failed)" : String.format("%.4f", hybridFitness)));

        // --- 4. STORE ALL RESULTS ---
        // We re-calculate the components for each solution to be safe

        // ACO
        fitnessCalc.calculateFitness(acoSolution, vmList);
        currentRunResults.add(new ResultData(runNumber, hostCount, "ACO", acoDuration,
                acoFitness, fitnessCalc.getLastPower(), fitnessCalc.getLastLoad(),
                fitnessCalc.getLastNetwork(), fitnessCalc.getLastLink()));

        // PSO
        fitnessCalc.calculateFitness(psoSolution, vmList);
        currentRunResults.add(new ResultData(runNumber, hostCount, "PSO", psoDuration,
                psoFitness, fitnessCalc.getLastPower(), fitnessCalc.getLastLoad(),
                fitnessCalc.getLastNetwork(), fitnessCalc.getLastLink()));

        // Hybrid
        fitnessCalc.calculateFitness(hybridSolution, vmList);
        currentRunResults.add(new ResultData(runNumber, hostCount, "Hybrid ACO-PSO", totalHybridTime,
                hybridFitness, fitnessCalc.getLastPower(), fitnessCalc.getLastLoad(),
                fitnessCalc.getLastNetwork(), fitnessCalc.getLastLink()));

        // --- 5. PRINT & SAVE ---
        printCurrentRunReport();
        saveResultsToCsv("results.csv", runNumber == 1 && hostCount == getConfiguredHostCounts().get(0));
        System.out.printf("--- Finished Run #%d with %d Hosts ---%n%n", runNumber, hostCount);
    }

    private void runAlgorithm(String name, SchedulerInterface scheduler, List<Vm> vmList, List<Host> hostList) {
        System.out.printf("Running %s (%d Hosts, Run #%d)...%n", name, hostCount, runNumber);
        long startTime = System.currentTimeMillis();
        Solution bestSolution = scheduler.solve(vmList, hostList);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        double finalFitness = fitnessCalc.calculateFitness(bestSolution, vmList);

        // Now, get the component parts from the calculator
        double power = (finalFitness == Double.MAX_VALUE) ? 0 : fitnessCalc.getLastPower();
        double load = (finalFitness == Double.MAX_VALUE) ? 0 : fitnessCalc.getLastLoad();
        double network = (finalFitness == Double.MAX_VALUE) ? 0 : fitnessCalc.getLastNetwork();
        double link = (finalFitness == Double.MAX_VALUE) ? 0 : fitnessCalc.getLastLink();

        System.out.printf("Finished %s in %d ms. Best Fitness: %s%n",
                name, duration, (finalFitness == Double.MAX_VALUE ? "Infinity (Failed)" : String.format("%.4f", finalFitness)));

        // Store ALL 9 pieces of data
        currentRunResults.add(new ResultData(runNumber, hostCount, name, duration,
                finalFitness, power, load, network, link));
    }

    private void printCurrentRunReport() {
        System.out.printf("%n--- Comparison Report (%d Hosts, Run #%d) ---%n", hostCount, runNumber);
        System.out.println("--------------------------------------------------------------------------------------------------------------");
        System.out.printf("| %-16s | %-18s | %-10s | %-10s | %-10s | %-12s | %-10s |%n",
                "Algorithm", "Best Fitness", "Time (ms)", "Power", "Load", "Network (hops)", "Max Link %");
        System.out.println("--------------------------------------------------------------------------------------------------------------");

        for (ResultData result : currentRunResults) {
            String fitnessStr = (result.fitness == Double.MAX_VALUE) ? "Infinity (Failed)" : String.format("%.2f", result.fitness);

            // Print all the data in the table
            System.out.printf("| %-16s | %-18s | %-10d | %-10.2f | %-10.4f | %-14f | %-10.4f |%n",
                    result.algorithmName,
                    fitnessStr,
                    result.duration,
                    result.power,
                    result.load,
                    result.network,
                    result.link);
        }
        System.out.println("--------------------------------------------------------------------------------------------------------------");
    }

    private void saveResultsToCsv(String filename, boolean isFirstRunOverall) {
        File file = new File(filename);
        boolean writeHeader = isFirstRunOverall || !file.exists() || file.length() == 0;

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, !isFirstRunOverall))) {
            if (writeHeader) {
                // New CSV header with all columns
                writer.println("HostCount,Run,Algorithm,BestFitness,TimeMs,Power,Load,Network,Link");
            }

            for (ResultData result : currentRunResults) {
                String fitnessCsv = (result.fitness == Double.MAX_VALUE) ? "FAILED" : String.format("%.4f", result.fitness);
                // Save all 9 columns to the CSV
                writer.printf("%d,%d,%s,%s,%d,%.2f,%.4f,%.2f,%.4f%n",
                        result.hosts,
                        result.run,
                        result.algorithmName,
                        fitnessCsv,
                        result.duration,
                        result.power,
                        result.load,
                        result.network,
                        result.link);
            }
            if (isFirstRunOverall) {
                System.out.printf("%nResults will be saved/appended to %s%n", filename);
            }
        } catch (IOException e) {
            System.err.println("Error writing results to CSV file: " + e.getMessage());
        }
    }

    private static List<Integer> getConfiguredHostCounts() {
        // Defining the different scenarios
        return List.of(16, 18, 20, 30, 40);
    }

    public static void main(String[] args) {
        int numberOfRunsPerScenario = 3; // SET RUNS PER HOST COUNT
        String resultsFilename = "results.csv";
        ConfigReader config = new ConfigReader("config.properties"); // Load config once
        List<Integer> hostCounts = getConfiguredHostCounts(); // Get the list of scenarios

        File oldResults = new File(resultsFilename);
        if (oldResults.exists()) {
            System.out.println("Deleting old results file: " + resultsFilename);
            oldResults.delete();
        }

        try {
            for (int h = 0; h < hostCounts.size(); h++) {
                int currentHostCount = hostCounts.get(h);
                System.out.printf("%n=== Starting Scenario: %d Hosts ===%n", currentHostCount);
                for (int i = 1; i <= numberOfRunsPerScenario; i++) {
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