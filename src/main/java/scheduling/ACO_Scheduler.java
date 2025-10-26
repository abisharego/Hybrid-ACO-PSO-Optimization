package scheduling;

import helpers.ConfigReader;
import helpers.FitnessCalculator;
import helpers.Solution;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

/**
 * Implements the Ant Colony Optimization (ACO) algorithm for VM placement.
 */
public class ACO_Scheduler implements SchedulerInterface {

    private final int numAnts;
    private final int maxIterations;
    private final double alpha; // Pheromone influence
    private final double beta;  // Heuristic influence
    private final double rho;   // Evaporation rate
    private final double q;     // Pheromone deposit factor
    private final int hybridInitGenerations;

    private final FitnessCalculator fitnessCalc;
    private double[][] pheromoneTrails;
    private List<Vm> vmList;
    private List<Host> hostList;
    private Solution globalBestSolution;

    public ACO_Scheduler(ConfigReader config, FitnessCalculator fitnessCalc) {
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
    public List<Solution> getInitialPopulation(int populationSize, List<Vm> vmList, List<Host> hostList) {
        initialize(vmList, hostList);
        runACO(this.hybridInitGenerations); // Warm up the trails

        // Now generate the population
        List<Solution> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            Solution antSolution = buildSolutionForAnt();
            antSolution.setFitness(fitnessCalc.calculateFitness(antSolution));
            population.add(antSolution);
        }
        return population;
    }


    private void initialize(List<Vm> vmList, List<Host> hostList) {
        this.vmList = vmList;
        this.hostList = hostList;
        this.globalBestSolution = new Solution();

        // Initialize pheromone trails
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

    private void runACO(int iterations) {
        for (int iter = 0; iter < iterations; iter++) {
            List<Solution> antSolutions = new ArrayList<>(numAnts);
            for (int i = 0; i < numAnts; i++) {
                Solution antSolution = buildSolutionForAnt();
                antSolution.setFitness(fitnessCalc.calculateFitness(antSolution));
                antSolutions.add(antSolution);
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
        Map<Host, Double> hostLoad = new HashMap<>(); // Track load for heuristic

        for (Vm vm : vmList) {
            Host selectedHost = selectHostForVm(vm, hostLoad);
            mapping.put(vm, selectedHost);

            // Update temp load for heuristic calculation
            double currentLoad = hostLoad.getOrDefault(selectedHost, 0.0);
            hostLoad.put(selectedHost, currentLoad + vm.getMips());
        }
        solution.setMapping(mapping);
        return solution;
    }

    private Host selectHostForVm(Vm vm, Map<Host, Double> currentHostLoad) {
        int vmIndex = vmList.indexOf(vm);
        double[] probabilities = new double[hostList.size()];
        double totalProbability = 0;

        for (int j = 0; j < hostList.size(); j++) {
            Host host = hostList.get(j);
            double pheromone = Math.pow(pheromoneTrails[vmIndex][j], alpha);

            // Heuristic (eta): 1.0 / (current_load + vm_load)
            // This favors hosts that will be *less* loaded *after* placing the VM.
            double currentLoad = currentHostLoad.getOrDefault(host, 0.0);
            double newLoad = (currentLoad + vm.getMips()) / host.getTotalMipsCapacity();
            double heuristic = 1.0 / (newLoad + 1e-6); // 1e-6 to avoid div by zero

            // Check for feasibility (simple check)
            double newRam = host.getRam().getAllocatedResource() + vm.getRam().getCapacity();
            if (newLoad > 1.0 || newRam > host.getRam().getCapacity()) {
                probabilities[j] = 0; // Infeasible, 0 probability
            } else {
                probabilities[j] = pheromone * Math.pow(heuristic, beta);
            }
            totalProbability += probabilities[j];
        }

        // Roulette Wheel Selection
        double rand = Math.random() * totalProbability;
        double cumulativeProbability = 0;
        for (int j = 0; j < hostList.size(); j++) {
            cumulativeProbability += probabilities[j];
            if (rand <= cumulativeProbability) {
                return hostList.get(j);
            }
        }

        // Fallback: If all probabilities were 0 (e.g., no host can fit it)
        // just return a random host (will be penalized by fitness function)
        return hostList.get(new Random().nextInt(hostList.size()));
    }

    private void updatePheromones(List<Solution> antSolutions) {
        // 1. Evaporation
        for (int i = 0; i < vmList.size(); i++) {
            for (int j = 0; j < hostList.size(); j++) {
                pheromoneTrails[i][j] *= (1.0 - rho);
            }
        }

        // 2. Deposition (using the global best solution)
        if (globalBestSolution.getFitness() == Double.MAX_VALUE) {
            return; // No feasible solution found yet
        }

        double deposit = q / globalBestSolution.getFitness();
        for (Map.Entry<Vm, Host> entry : globalBestSolution.getMapping().entrySet()) {
            int vmIndex = vmList.indexOf(entry.getKey());
            int hostIndex = hostList.indexOf(entry.getValue());
            if (vmIndex != -1 && hostIndex != -1) {
                pheromoneTrails[vmIndex][hostIndex] += deposit;
            }
        }
    }
}