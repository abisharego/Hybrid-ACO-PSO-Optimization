package scheduling;

import helpers.Solution;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;

/**
 * A common interface for all scheduling algorithms.
 */
public interface SchedulerInterface {

    /**
     * Solves the VM placement problem.
     * @param vmList The list of VMs to be scheduled.
     * @param hostList The list of available hosts.
     * @return The best Solution (mapping) found.
     */
    Solution solve(List<Vm> vmList, List<Host> hostList);
}