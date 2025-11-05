package scheduling;

import helpers.AdvancedFitnessCalculator;
import helpers.ConfigReader;
import helpers.Solution;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

/**
 * Implements a Discrete Particle Swarm Optimization (DPSO) algorithm.
 * A particle's "position" is an array where index = VM_ID and value = Host_ID.
 */
public class PSO_Scheduler implements SchedulerInterface {

    private int maxIterations;
    private final int populationSize;
    private final double w;  // Inertia
    private final double c1; // Cognitive
    private final double c2; // Social

    private final AdvancedFitnessCalculator fitnessCalc;
    private List<Vm> vmList;
    private List<Host> hostList;
    private Solution globalBestSolution;

    // PSO specific data structures
    private Solution[] particles;
    private Solution[] pBestSolutions;
    private double[][] velocities;

    public PSO_Scheduler(ConfigReader config, AdvancedFitnessCalculator fitnessCalc) {
        this.maxIterations = config.getInt("pso.iterations");
        this.populationSize = config.getInt("pso.population");
        this.w = config.getDouble("pso.w");
        this.c1 = config.getDouble("pso.c1");
        this.c2 = config.getDouble("pso.c2");
        this.fitnessCalc = fitnessCalc;
    }

    public PSO_Scheduler(ConfigReader config, AdvancedFitnessCalculator fitnessCalc, int overrideIterations) {
        this(config, fitnessCalc); // Calls the main constructor
        this.maxIterations = overrideIterations; // But then overrides the iteration count
    }

    @Override
    public Solution solve(List<Vm> vmList, List<Host> hostList) {
        // This is the "standalone" PSO. It initializes its swarm RANDOMLY.
        List<Solution> randomSwarm = createRandomSwarm(vmList, hostList);
        return solveWithInitialSwarm(randomSwarm, vmList, hostList);
    }

    /**
     * The main PSO solver, which takes a pre-initialized swarm.
     * The Hybrid scheduler will call this with an ACO-generated swarm.
     */
    public Solution solveWithInitialSwarm(List<Solution> initialSwarm, List<Vm> vmList, List<Host> hostList) {
        initialize(initialSwarm, vmList, hostList);

        for (int iter = 0; iter < maxIterations; iter++) {
            for (int i = 0; i < populationSize; i++) {
                // 1. Update particle's velocity
                updateVelocity(i);

                // 2. Update particle's position
                updatePosition(i);

                // 3. Calculate fitness of new position
                double fitness = fitnessCalc.calculateFitness(particles[i], vmList);
                particles[i].setFitness(fitness);

                // 4. Update pBest
                if (fitness < pBestSolutions[i].getFitness()) {
                    pBestSolutions[i] = particles[i].clone();

                    // 5. Update gBest
                    if (fitness < globalBestSolution.getFitness()) {
                        globalBestSolution = particles[i].clone();
                    }
                }
            }
        }
        return globalBestSolution;
    }

    private void initialize(List<Solution> initialSwarm, List<Vm> vmList, List<Host> hostList) {
        this.vmList = vmList;
        this.hostList = hostList;

        this.particles = new Solution[populationSize];
        this.pBestSolutions = new Solution[populationSize];
        this.velocities = new double[populationSize][vmList.size()];
        Random rand = new Random();

        if (initialSwarm.isEmpty()) {
            throw new RuntimeException("Initial swarm cannot be empty!");
        }
        // Initialize gBest to the first particle.
        this.globalBestSolution = initialSwarm.get(0).clone();
        // --- END OF CORRECTION ---

        for (int i = 0; i < populationSize; i++) {
            particles[i] = initialSwarm.get(i);
            pBestSolutions[i] = particles[i].clone();

            // Initialize velocities randomly
            for (int j = 0; j < vmList.size(); j++) {
                velocities[i][j] = rand.nextDouble() * 2 - 1; // Random value between -1 and 1
            }

            // Check if this particle is better than the (now non-empty) gBest
            if (particles[i].getFitness() < globalBestSolution.getFitness()) {
                globalBestSolution = particles[i].clone();
            }
        }
    }

    private void updateVelocity(int particleIndex) {
        int[] currentPos = solutionToPositionArray(particles[particleIndex]);
        int[] pBestPos = solutionToPositionArray(pBestSolutions[particleIndex]);
        int[] gBestPos = solutionToPositionArray(globalBestSolution);

        double r1 = Math.random();
        double r2 = Math.random();

        for (int i = 0; i < vmList.size(); i++) {
            double cognitive = c1 * r1 * (pBestPos[i] - currentPos[i]);
            double social = c2 * r2 * (gBestPos[i] - currentPos[i]);
            velocities[particleIndex][i] = (w * velocities[particleIndex][i]) + cognitive + social;
        }
    }

    private void updatePosition(int particleIndex) {
        int[] currentPos = solutionToPositionArray(particles[particleIndex]);
        int[] newPos = new int[vmList.size()];
        Map<Vm, Host> newMapping = new HashMap<>();

        for (int i = 0; i < vmList.size(); i++) {
            // This is the core of Discrete PSO
            double newPositionValue = currentPos[i] + velocities[particleIndex][i];

            // Round to nearest integer (Host ID)
            int newHostId = (int) Math.round(newPositionValue);

            // Clamp the value to be a valid Host ID
            newHostId = Math.max(0, Math.min(newHostId, hostList.size() - 1));

            newPos[i] = newHostId;
            newMapping.put(vmList.get(i), hostList.get(newHostId));
        }
        particles[particleIndex].setMapping(newMapping);
    }

    private int[] solutionToPositionArray(Solution s) {
        int[] pos = new int[vmList.size()];
        for (int i = 0; i < vmList.size(); i++) {
            Vm vm = vmList.get(i);
            Host host = s.getMapping().get(vm);
            if (host == null) {
                // Handle case where a VM might not be mapped in an empty solution
                pos[i] = -1; // Or some other invalid index
            } else {
                pos[i] = hostList.indexOf(host);
            }
        }
        return pos;
    }

    private List<Solution> createRandomSwarm(List<Vm> vmList, List<Host> hostList) {
        List<Solution> swarm = new ArrayList<>(populationSize);
        Random rand = new Random();
        for (int i = 0; i < populationSize; i++) {
            Solution s = new Solution();
            Map<Vm, Host> mapping = new HashMap<>();
            for (Vm vm : vmList) {
                // Assign to a completely random host
                Host host = hostList.get(rand.nextInt(hostList.size()));
                mapping.put(vm, host);
            }
            s.setMapping(mapping);
            s.setFitness(fitnessCalc.calculateFitness(s, vmList));
            swarm.add(s);
        }
        return swarm;
    }
}