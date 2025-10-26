package helpers;

import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.power.models.PowerModelHost;
import org.cloudbus.cloudsim.power.models.PowerModelHostSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory class to create Hosts and VMs for CloudSim Plus 7.3.0 simulations.
 */
public class SimulationFactory {

    // ---------------- HOST CONSTANTS ----------------
    private static final int HOST_PES = 8;              // Number of processing elements
    private static final long HOST_MIPS = 10000;        // 10,000 MIPS per PE
    private static final long HOST_RAM = 32768;         // 32 GB
    private static final long HOST_BW = 100000;         // 100 Gbit/s
    private static final long HOST_STORAGE = 1_000_000; // 1 TB (in MB)

    // Power Model (Watts)
    private static final double HOST_MAX_POWER = 250;   // 250 W at 100% utilization
    private static final double HOST_IDLE_POWER = 100;  // 100 W at 0% utilization

    // ---------------- VM CONSTANTS ----------------
    private static final long VM_MIPS = 1000;
    private static final int VM_PES = 2;
    private static final long VM_RAM = 4096;            // 4 GB
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;          // 10 GB

    /**
     * Creates and returns a list of Hosts.
     *
     * @param numHosts number of hosts to create
     * @return list of Host objects
     */
    public static List<Host> createHostList(int numHosts) {
        List<Host> hostList = new ArrayList<>(numHosts);

        for (int i = 0; i < numHosts; i++) {
            // ✅ Use List<Pe>, not List<PeSimple>
            List<Pe> peList = new ArrayList<>(HOST_PES);
            for (int j = 0; j < HOST_PES; j++) {
                peList.add(new PeSimple(HOST_MIPS));
            }

            // ✅ Correct HostSimple constructor (all long parameters)
            HostSimple host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);

            host.setRamProvisioner(new ResourceProvisionerSimple());
            host.setBwProvisioner(new ResourceProvisionerSimple());
            host.setVmScheduler(new VmSchedulerTimeShared());

            // ✅ Power model for CloudSim 7.3.0
            PowerModelHost powerModel = new PowerModelHostSimple(HOST_MAX_POWER, HOST_IDLE_POWER);
            host.setPowerModel(powerModel);

            host.setId(i);
            hostList.add(host);
        }
        return hostList;
    }

    /**
     * Creates and returns a list of VMs.
     *
     * @param numVMs number of VMs to create
     * @return list of Vm objects
     */
    public static List<Vm> createVmList(int numVMs) {
        List<Vm> vmList = new ArrayList<>(numVMs);

        for (int i = 0; i < numVMs; i++) {
            long mips = (long) (VM_MIPS + (Math.random() * 500 - 250)); // small variation
            long ram = (long) (VM_RAM + (Math.random() * 1024 - 512));
            int pes = (Math.random() > 0.5) ? VM_PES : VM_PES - 1;

            VmSimple vm = new VmSimple(mips, pes);
            vm.setRam(ram);
            vm.setBw(VM_BW);
            vm.setSize(VM_SIZE);
            vm.setId(i);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());

            vmList.add(vm);
        }

        return vmList;
    }
}
