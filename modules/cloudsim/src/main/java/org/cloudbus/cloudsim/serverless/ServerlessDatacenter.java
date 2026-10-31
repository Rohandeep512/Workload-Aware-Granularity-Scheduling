package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.container.core.*;
import org.cloudbus.cloudsim.container.resourceAllocators.ContainerVmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import java.text.DecimalFormat;
import java.util.*;

public class ServerlessDatacenter extends PowerContainerDatacenterCM {
    private static final DecimalFormat df = new DecimalFormat("0.00");
    private final Map<Integer, ServerlessRequest> tasksWaitingToReschedule;

    private static List<ServerlessInvoker> vmIdleList = new ArrayList<>();
    private boolean reschedule = false;

    private static Map<Integer, Double> runTimeVm = new HashMap<Integer, Double>();

    private static Map<Integer, ArrayList<Integer>> onlineBinOfVms = new HashMap<Integer, ArrayList<Integer>>();
    private static ArrayList<Integer> binNos = new ArrayList<>();
    private boolean monitoring = false;
    private List<Container> containersToDestroy = new ArrayList<>();
    protected boolean autoScalingInitialized = false;

    private RequestLoadBalancer requestLoadBalancer;
    private FunctionAutoScaler autoScaler;

    private FunctionScheduler fnsched;

    public ServerlessDatacenter(String name, ContainerDatacenterCharacteristics characteristics, ContainerVmAllocationPolicy vmAllocationPolicy, FunctionScheduler containerAllocationPolicy, List<Storage> storageList, double schedulingInterval, String experimentName, String logAddress, double vmStartupDelay, double containerStartupDelay, boolean monitor) throws Exception {
        super(name, characteristics, vmAllocationPolicy, containerAllocationPolicy, storageList, schedulingInterval, experimentName, logAddress, vmStartupDelay, containerStartupDelay);
        tasksWaitingToReschedule = new HashMap<Integer, ServerlessRequest>();
        setMonitoring(monitor);
        autoScaler = new FunctionAutoScaler(this);
    }

    public List<Container> getContainersToDestroy() {
        return containersToDestroy;
    }

    public RequestLoadBalancer getRequestLoadBalancer() {
        return requestLoadBalancer;
    }

    public Map<Integer,Double> getRunTimeVm(){
        return runTimeVm;
    }

    public boolean getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(boolean monitor) {
        this.monitoring = monitor;
    }

    public void setRequestLoadBalancerR(RequestLoadBalancer lb) {
        this.requestLoadBalancer = lb;
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case CloudSimTags.CONTAINER_DESTROY:
                processContainerDestroy(ev, false);
                break;

            case CloudSimTags.CONTAINER_DESTROY_ACK:
                processContainerDestroy(ev, true);
                break;

            case containerCloudSimTags.CONTAINER_SUBMIT_FOR_RESCHEDULE:
                ((ServerlessContainer) ev.getData()).setReschedule(true);
                processContainerSubmit(ev, true);
                break;

            case CloudSimSCTags.AUTO_SCALE:
                processAutoScaling(ev);
                break;

            default:
                super.processOtherEvent(ev);
                break;
        }
    }

    public void containerVerticalScale(Container container, ServerlessInvoker vm, int cpuChange, int memChange){
        boolean result = ((FunctionScheduler) getContainerAllocationPolicy()).reallocateVmResourcesForContainer(container, vm, cpuChange, memChange);
    }

    public void processAutoScaling(SimEvent ev){
        Log.printLine(String.format("%s: Autoscaling", CloudSim.clock()));
        autoScaler.scaleFunctions();
        destroyIdleContainers();
        send(this.getId(), Constants.AUTO_SCALING_INTERVAL, CloudSimSCTags.AUTO_SCALE);
    }

    public void processContainerDestroy(SimEvent ev, boolean ack){
        Container container = (Container) ev.getData();
        if (Constants.CONTAINER_IDLING_ENABLED){
            ServerlessContainer sc = (ServerlessContainer) container;
            if (sc.getIdleStartTime() > 0 && CloudSim.clock() - sc.getIdleStartTime() >= sc.keepAlive - 0.001) {
                ServerlessInvoker vm = (ServerlessInvoker)container.getVm();
                if(vm!=null) {
                    getContainerAllocationPolicy().deallocateVmForContainer(container);

                    if ((vm.getContainerList()).size() == 0) {
                        vmIdleList.add((ServerlessInvoker) container.getVm());
                        if (vm.getStatus().equals("ON")) {
                            vm.setStatus("OFF");
                            vm.onTime += (CloudSim.clock() - vm.getRecordTime());
                        } else if (vm.getStatus().equals("OFF")) {
                            vm.offTime += (CloudSim.clock() - vm.getRecordTime());
                        }
                        vm.setRecordTime(CloudSim.clock());
                    }
                    if (ack) {
                        int[] data = new int[4];
                        data[0] = getId();
                        data[1] = container.getId();
                        data[2] = CloudSimTags.TRUE;
                        data[3] = vm.getId();

                        sendNow(container.getUserId(), CloudSimTags.CONTAINER_DESTROY_ACK, data);
                    }

                    getContainerList().remove(container);
                }
            }
        }
        else {
            ServerlessInvoker vm = (ServerlessInvoker) container.getVm();
            if (vm != null) {
                getContainerAllocationPolicy().deallocateVmForContainer(container);

                if ((vm.getContainerList()).size() == 0) {
                    vmIdleList.add((ServerlessInvoker) container.getVm());
                    if (vm.getStatus().equals("ON")) {
                        vm.setStatus("OFF");
                        vm.onTime += (CloudSim.clock() - vm.getRecordTime());
                    } else if (vm.getStatus().equals("OFF")) {
                        vm.offTime += (CloudSim.clock() - vm.getRecordTime());
                    }
                    vm.setRecordTime(CloudSim.clock());
                }
                if (ack) {
                    int[] data = new int[4];
                    data[0] = getId();
                    data[1] = container.getId();
                    data[2] = CloudSimTags.TRUE;
                    data[3] = vm.getId();

                    sendNow(container.getUserId(), CloudSimTags.CONTAINER_DESTROY_ACK, data);
                }

                getContainerList().remove(container);
            }
        }
    }

    @Override
    protected void processVmCreate(SimEvent ev, boolean ack) {

        ContainerVm containerVm = (ContainerVm) ev.getData();
        Log.printLine(String.format("In processVmcreate in serverlessDC to create vm #%s", containerVm.getId()));

        boolean result = getVmAllocationPolicy().allocateHostForVm(containerVm);

        if (ack) {
            int[] data = new int[3];
            data[0] = getId();
            data[1] = containerVm.getId();

            if (result) {
                data[2] = CloudSimTags.TRUE;
            } else {
                data[2] = CloudSimTags.FALSE;
            }
            send(containerVm.getUserId(), CloudSim.getMinTimeBetweenEvents(), CloudSimTags.VM_CREATE_ACK, data);

            vmIdleList.add((ServerlessInvoker)containerVm);
            ((ServerlessInvoker)containerVm).setStatus("OFF");
            ((ServerlessInvoker)containerVm).setRecordTime(CloudSim.clock());
        }

        runTimeVm.put(containerVm.getId(), (double) 0);

        if (result) {
            getContainerVmList().add((PowerContainerVm) containerVm);

            if (containerVm.isBeingInstantiated()) {
                containerVm.setBeingInstantiated(false);
            }

            containerVm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(containerVm).getContainerVmScheduler()
                    .getAllocatedMipsForContainerVm(containerVm));

            if(Constants.MONITORING) {
                send(containerVm.getUserId(), Constants.CPU_USAGE_MONITORING_INTERVAL, CloudSimSCTags.RECORD_CPU_USAGE, containerVm);
            }
        }
    }

    @Override
    public void processContainerSubmit(SimEvent ev, boolean ack) {
        Container container = (Container) ev.getData();
        boolean result = false;

        if (container.getVm() != null) {
            result = ((FunctionScheduler)getContainerAllocationPolicy()).allocateVmForContainer(container, container.getVm(), getContainerVmList());
        } else {
            result = ((FunctionScheduler)getContainerAllocationPolicy()).allocateVmForContainer(container, getContainerVmList());
        }

        if (ack) {
            int[] data = new int[4];
            data[1] = container.getId();
            if (result) {
                data[2] = CloudSimTags.TRUE;
            } else {
                data[2] = CloudSimTags.FALSE;
            }
            data[3] = 0;
            if (result) {
                ServerlessInvoker vm = ((ServerlessInvoker) container.getVm());
                if (getMonitoring()) {
                    if (vm.getContainerList().size() == 1) {
                        vmIdleList.remove(vm);
                        vm.setStatus("ON");
                        vm.offTime += (CloudSim.clock() - vm.getRecordTime());
                        vm.setRecordTime(CloudSim.clock());
                    }
                }
                data[0] = vm.getId();
                if (vm.getId() == -1) {
                    Log.printConcatLine("The ContainerVM ID is not known (-1) !");
                }
                getContainerList().add(container);
                if (container.isBeingInstantiated()) {
                    container.setBeingInstantiated(false);
                }

                ((ServerlessContainer) container).updateContainerProcessing(CloudSim.clock(), getContainerAllocationPolicy().getContainerVm(container).getContainerScheduler().getAllocatedMipsForContainer(container), vm);
                vm.setFunctionContainerMapPending(container, ((ServerlessContainer) container).getType());
                send(ev.getSource(), Constants.CONTAINER_STARTTUP_DELAY, containerCloudSimTags.CONTAINER_CREATE_ACK, data);

            }
            else {
                data[0] = -1;
                Log.printLine(String.format("Couldn't find a vm to host the container #%s", container.getUid()));
            }
        }
    }

    @Override
    public void updateCloudletProcessing(){

        if (CloudSim.clock() < 0.111 || CloudSim.clock() > getLastProcessTime()) {
            List<? extends ContainerHost> list = getVmAllocationPolicy().getContainerHostList();
            double smallerTime = Double.MAX_VALUE;
            for (ContainerHost host : list) {
                double time = host.updateContainerVmsProcessing(CloudSim.clock());
                if (time < smallerTime) {
                    smallerTime = time;
                }
            }

            if (smallerTime != Double.MAX_VALUE) {
                schedule(getId(), (smallerTime - CloudSim.clock()), CloudSimTags.VM_DATACENTER_EVENT);
            }
            setLastProcessTime(CloudSim.clock());

            if(!Constants.FUNCTION_AUTOSCALING){
                destroyIdleContainers();
            }
        }
    }

    @Override
    protected void processCloudletSubmit(SimEvent ev, boolean ack) {
        updateCloudletProcessing();

        try {
            ServerlessRequest cl = (ServerlessRequest) ev.getData();

            if (cl.isFinished()) {
                String name = CloudSim.getEntityName(cl.getUserId());
                Log.printConcatLine(getName(), ": Warning - request #", cl.getCloudletId(), " owned by ", name,
                        " is already completed/finished.");
                Log.printLine("Therefore, it is not being executed again");
                Log.printLine();

                int[] data = new int[3];
                data[0] = getId();
                data[1] = cl.getCloudletId();

                int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
                sendNow(cl.getUserId(), tag, data);

                sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);

                return;
            }

            cl.setResourceParameter(getId(), getCharacteristics().getCostPerSecond(), getCharacteristics()
                    .getCostPerBw(), cl.getVmId());

            int userId = cl.getUserId();
            int vmId = cl.getVmId();
            int containerId = cl.getContainerId();

            double fileTransferTime = predictFileTransferTime(cl.getRequiredFiles());
            ContainerHost host=null;

            host = getVmAllocationPolicy().getHost(vmId, userId);
            ServerlessInvoker vm = null;
            Container container = null;
            double estimatedFinishTime = 0;

            vm = (ServerlessInvoker)host.getContainerVm(vmId, userId);
            container = vm.getContainer(containerId, userId);

            estimatedFinishTime =((ServerlessRequestScheduler) container.getContainerCloudletScheduler()).requestSubmit(cl, vm, (ServerlessContainer)(container));
            System.out.println("Est finish time of function# "+ cl.getCloudletId()+" at the beginning is "+ estimatedFinishTime);

            getContainersToDestroy().remove(container);
            if (estimatedFinishTime > 0.0 && !Double.isInfinite(estimatedFinishTime)) {
                estimatedFinishTime += fileTransferTime;

                send(getId(), (estimatedFinishTime-CloudSim.clock()), CloudSimTags.VM_DATACENTER_EVENT);
            }

            int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
            sendNow(cl.getUserId(), tag, cl);

        } catch (ClassCastException c) {
            Log.printLine(String.format("%s.processCloudletSubmit(): ClassCastException error.", getName()));
            c.printStackTrace();
        } catch (Exception e) {
            Log.printLine(String.format("%s.processCloudletSubmit(): Exception error.", getName()));
            e.printStackTrace();
        }

        checkCloudletCompletion();
        if (Constants.FUNCTION_AUTOSCALING && !autoScalingInitialized){
            autoScalingInitialized = true;
            autoScaler.scaleFunctions();
            destroyIdleContainers();
            send(this.getId(), Constants.AUTO_SCALING_INTERVAL, CloudSimSCTags.AUTO_SCALE);
        }
    }

    protected void sendScaledContainerCreationRequest(String[] data){
        sendNow(Integer.parseInt(data[0]), CloudSimSCTags.SCALED_CONTAINER, data);
    }

    @Override
    protected void checkCloudletCompletion() {
        List<? extends ContainerHost> list = getVmAllocationPolicy().getContainerHostList();
        for (int i = 0; i < list.size(); i++) {
            ContainerHost host = list.get(i);
            for (ContainerVm vm : host.getVmList()) {
                for (Container container : vm.getContainerList()) {
                    while (container.getContainerCloudletScheduler().isFinishedCloudlets()) {
                        Cloudlet cl = container.getContainerCloudletScheduler().getNextFinishedCloudlet();
                        if (cl != null) {
                            Map.Entry<Cloudlet, ContainerVm> data =  new AbstractMap.SimpleEntry<>(cl, vm);
                            for(int x=0; x<((ServerlessInvoker)vm).getRunningRequestList().size();x++){
                                if(((ServerlessInvoker)vm).getRunningRequestList().get(x)==(ServerlessRequest)cl){
                                    ((ServerlessInvoker)vm).getRunningRequestList().remove(x);
                                }
                            }

                            sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, data);
                        }
                    }
                }
            }
        }
    }

    protected void destroyIdleContainers() {
        while (!getContainersToDestroy().isEmpty()) {
            for (int x = 0; x < getContainersToDestroy().size(); x++) {
                if (((ServerlessContainer) getContainersToDestroy().get(x)).newContainer) {
                    ((ServerlessContainer)getContainersToDestroy().get(x)).setIdleStartTime(0);
                    continue;
                }
                if(!Constants.CONTAINER_IDLING_ENABLED){
                    sendNow(getId(), CloudSimTags.CONTAINER_DESTROY_ACK, getContainersToDestroy().get(x));
                }
                else {
                    ServerlessContainer sc = (ServerlessContainer) getContainersToDestroy().get(x);
                    sc.keepAlive = KeepAlive.idleTime(sc.getType());
                    send(getId(), sc.keepAlive, CloudSimTags.CONTAINER_DESTROY_ACK, sc);
                }
            }
            getContainersToDestroy().clear();
        }
    }

    public void updateRunTimeVm(ServerlessInvoker vm, double time){
        if(runTimeVm.get(vm.getId())<time){
            runTimeVm.put(vm.getId(),time);
        }
    }
}