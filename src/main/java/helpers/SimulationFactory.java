package helpers;

import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.power.models.PowerModelHost;
import org.cloudbus.cloudsim.power.models.PowerModelHostSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe; // Use the Pe interface
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SimulationFactory {

    // --- Host constants ---
    private static final int HOST_PES = 8;
    private static final long HOST_MIPS = 10000;
    private static final long HOST_RAM = 32768; // 32 GB
    private static final long HOST_STORAGE = 1000000; // 1 TB
    public static final long HOST_BW_CAPACITY = 5000; // 5000 MB/s

    private static final double HOST_MAX_POWER = 250;
    private static final double HOST_IDLE_POWER = 100;

    private static final long VM_MIPS = 1000;
    private static final int VM_PES = 2;
    private static final long VM_RAM = 4096;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;

    // --- Static Network Matrices ---
    public static int[][] vmTrafficMatrix;
    public static int[][] hostHopMatrix;


    public static List<Host> createHostList(int numHosts) {
        List<Host> hostList = new ArrayList<>(numHosts);
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>(HOST_PES); // Use Pe interface
            for (int j = 0; j < HOST_PES; j++) {
                peList.add(new PeSimple(HOST_MIPS));
            }

            // 1. The 4-argument constructor
            HostSimple host = new HostSimple(
                    HOST_RAM,
                    HOST_BW_CAPACITY,
                    HOST_STORAGE,
                    peList
            );

            // 2. Use the setter methods that match your API
            host.setRamProvisioner(new ResourceProvisionerSimple());
            host.setBwProvisioner(new ResourceProvisionerSimple());
            host.setVmScheduler(new VmSchedulerTimeShared());

            // 3. Use the PowerModelHostSimple
            PowerModelHost powerModel = new PowerModelHostSimple(HOST_MAX_POWER, HOST_IDLE_POWER);
            host.setPowerModel(powerModel);

            host.setId(i);
            hostList.add(host);
        }
        return hostList;
    }

    public static List<Vm> createVmList(int numVMs) {
        List<Vm> vmList = new ArrayList<>(numVMs);
        for (int i = 0; i < numVMs; i++) {
            long mips = (long) (VM_MIPS + (Math.random() * 500 - 250));
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

    public static void createHostHopMatrix(List<Host> hostList) {
        int numHosts = hostList.size();
        hostHopMatrix = new int[numHosts][numHosts];
        for (int i = 0; i < numHosts; i++) {
            for (int j = 0; j < numHosts; j++) {
                if (i == j) {
                    hostHopMatrix[i][j] = 0;
                } else {
                    hostHopMatrix[i][j] = 2;
                }
            }
        }
    }

    public static void createVmTrafficMatrix(List<Vm> vmList) {
        int numVMs = vmList.size();
        vmTrafficMatrix = new int[numVMs][numVMs]; // Fix: use numVMs for both dimensions
        Random rand = new Random();

        for (int i = 0; i < numVMs; i++) {
            for (int j = i + 1; j < numVMs; j++) {
                if (rand.nextDouble() < 0.1) {
                    int traffic = rand.nextInt(41) + 10;
                    vmTrafficMatrix[i][j] = traffic;
                    vmTrafficMatrix[j][i] = traffic;
                } else {
                    vmTrafficMatrix[i][j] = 0;
                    vmTrafficMatrix[j][i] = 0;
                }
            }
        }
    }
}