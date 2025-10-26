package helpers;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single complete solution (a VM-to-Host mapping)
 * and its calculated fitness.
 */
public class Solution implements Cloneable {
    private Map<Vm, Host> mapping;
    private double fitness;
    private long duration; // <-- Added

    public Solution() {
        this.mapping = new HashMap<>();
        this.fitness = Double.MAX_VALUE;
        this.duration = 0;
    }

    public void setMapping(Map<Vm, Host> mapping) {
        this.mapping = mapping;
    }

    public Map<Vm, Host> getMapping() {
        return mapping;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    public double getFitness() {
        return fitness;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    @Override
    public Solution clone() {
        try {
            Solution newSolution = (Solution) super.clone();
            newSolution.mapping = new HashMap<>(this.mapping);
            newSolution.fitness = this.fitness;
            newSolution.duration = this.duration; // <-- Copy duration too
            return newSolution;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
