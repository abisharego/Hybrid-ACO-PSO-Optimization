import helpers.ConfigReader;
import helpers.AdvancedFitnessCalculator;
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
    private final AdvancedFitnessCalculator fitnessCalc;
    private final int runNumber;
    private final int hostCount;
    private List<ResultData> currentRunResults;
    private final List<Vm> vmList;
    private final List<Host> hostList;

    private static class ResultData {
        int run;
        int hosts;
        String algorithmName;
        long duration;
        double fitness;
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
        this.fitnessCalc = new AdvancedFitnessCalculator(config);
        this.currentRunResults = new ArrayList<>();

        System.out.printf("--- Creating Environment for Run #%d (%d Hosts) ---%n", runNumber, hostCount);
        this.hostList = SimulationFactory.createHostList(hostCount);
        this.vmList = SimulationFactory.createVmList(config.getInt("simulation.vms"));

        SimulationFactory.createHostHopMatrix(this.hostList);
        SimulationFactory.createVmTrafficMatrix(this.vmList);
    }

    public void runSingleSimulationScenario() {
        System.out.printf("--- Starting Run #%d with %d Hosts ---%n", runNumber, hostCount);

        ACO_Scheduler aco = new ACO_Scheduler(config, fitnessCalc, config.getInt("aco.iterations"));
        PSO_Scheduler pso = new PSO_Scheduler(config, fitnessCalc, config.getInt("pso.iterations"));
        Hybrid_Scheduler hybrid = new Hybrid_Scheduler(config, fitnessCalc);

        Solution acoSolution = runAlgorithm("ACO", aco, vmList, hostList);
        Solution psoSolution = runAlgorithm("PSO", pso, vmList, hostList);

        System.out.println("Running Hybrid ACO-PSO (Refinement Stage)...");
        long hybridStartTime = System.currentTimeMillis();
        Solution hybridSolution = hybrid.solve(vmList, hostList, acoSolution, psoSolution);
        long hybridEndTime = System.currentTimeMillis();

        long hybridRefinementDuration = hybridEndTime - hybridStartTime;
        long acoDuration = getDurationFromResult("ACO");
        long psoDuration = getDurationFromResult("PSO");
        long totalHybridTime = Math.max(acoDuration, psoDuration) + hybridRefinementDuration;

        double hybridFitness = fitnessCalc.calculateFitness(hybridSolution, vmList);

        System.out.printf("Finished Hybrid in %d ms. Best Fitness: %s%n",
                hybridRefinementDuration, (hybridFitness == Double.MAX_VALUE ? "Infinity (Failed)" : String.format("%.4f", hybridFitness)));

        fitnessCalc.calculateFitness(hybridSolution, vmList);
        currentRunResults.add(new ResultData(runNumber, hostCount, "Hybrid_ACO_PSO", totalHybridTime,
                hybridFitness, fitnessCalc.getLastPower(), fitnessCalc.getLastLoad(),
                fitnessCalc.getLastNetwork(), fitnessCalc.getLastLink()));

        printCurrentRunReport();

        // SAVE BOTH CSV AND PROMETHEUS METRICS
        boolean isFirst = runNumber == 1 && hostCount == getConfiguredHostCounts().get(0);
        saveResultsToCsv("results.csv", isFirst);
        saveResultsToPrometheus("simulation_metrics.prom"); // <--- NEW

        System.out.printf("--- Finished Run #%d with %d Hosts ---%n%n", runNumber, hostCount);
    }

    private Solution runAlgorithm(String name, SchedulerInterface scheduler, List<Vm> vmList, List<Host> hostList) {
        System.out.printf("Running %s (Standalone)...%n", name);
        long startTime = System.currentTimeMillis();
        Solution bestSolution = scheduler.solve(vmList, hostList);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        double finalFitness = fitnessCalc.calculateFitness(bestSolution, vmList);
        System.out.printf("Finished %s in %d ms. Best Fitness: %s%n",
                name, duration, (finalFitness == Double.MAX_VALUE ? "Infinity (Failed)" : String.format("%.4f", finalFitness)));

        currentRunResults.add(new ResultData(runNumber, hostCount, name, duration,
                finalFitness, fitnessCalc.getLastPower(), fitnessCalc.getLastLoad(),
                fitnessCalc.getLastNetwork(), fitnessCalc.getLastLink()));

        return bestSolution;
    }

    private long getDurationFromResult(String algorithmName) {
        for (ResultData result : currentRunResults) {
            if (result.algorithmName.equals(algorithmName)) {
                return result.duration;
            }
        }
        return 0;
    }

    private void printCurrentRunReport() {
        // ... (Same as before) ...
        System.out.println("--- Report Generated ---");
    }

    private void saveResultsToCsv(String filename, boolean isFirstRunOverall) {
        // ... (Same as before) ...
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, !isFirstRunOverall))) {
            if (isFirstRunOverall) writer.println("HostCount,Run,Algorithm,BestFitness,Power,Load,Network,Link");
            for (ResultData r : currentRunResults) {
                String fit = (r.fitness == Double.MAX_VALUE) ? "FAILED" : String.format("%.4f", r.fitness);
                writer.printf("%d,%d,%s,%s,%.2f,%.4f,%.0f,%.4f%n", r.hosts, r.run, r.algorithmName, fit, r.power, r.load, r.network, r.link);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // --- NEW METHOD: Export to Prometheus Format ---
    private void saveResultsToPrometheus(String filename) {
        // Note: Prometheus Textfile Collector overwrites the file every time.
        // We append new metrics to a list, but we write the whole file fresh for the current state.
        // For simplicity, we will just append the latest run's data.

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, true))) { // Append mode
            for (ResultData r : currentRunResults) {
                if (r.fitness == Double.MAX_VALUE) continue; // Skip failed runs

                // Create clean labels
                String labels = String.format("algorithm=\"%s\",hosts=\"%d\",run=\"%d\"", r.algorithmName, r.hosts, r.run);

                // Write metrics
                writer.printf("simulation_fitness{%s} %.4f%n", labels, r.fitness);
                writer.printf("simulation_power_watts{%s} %.2f%n", labels, r.power);
                writer.printf("simulation_load_variance{%s} %.6f%n", labels, r.load);
                writer.printf("simulation_network_traffic{%s} %.0f%n", labels, r.network);
                writer.printf("simulation_link_utilization{%s} %.6f%n", labels, r.link);
                writer.printf("simulation_duration_ms{%s} %d%n", labels, r.duration);
            }
        } catch (IOException e) {
            System.err.println("Error writing Prometheus metrics: " + e.getMessage());
        }
    }

    private static List<Integer> getConfiguredHostCounts() {
        return List.of(16, 18, 20, 30, 40);
    }

    public static void main(String[] args) {
        int numberOfRunsPerScenario = 5;
        String resultsFilename = "results.csv";
        // Clear old metrics
        new File("simulation_metrics.prom").delete();

        ConfigReader config = new ConfigReader("config.properties");
        List<Integer> hostCounts = getConfiguredHostCounts();

        File oldResults = new File(resultsFilename);
        if (oldResults.exists()) oldResults.delete();

        try {
            for (int h = 0; h < hostCounts.size(); h++) {
                int currentHostCount = hostCounts.get(h);
                System.out.printf("%n=== Starting Scenario: %d Hosts ===%n", currentHostCount);
                for (int i = 1; i <= numberOfRunsPerScenario; i++) {
                    HybridSimulationRunner runner = new HybridSimulationRunner(i, currentHostCount, config);
                    runner.runSingleSimulationScenario();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}