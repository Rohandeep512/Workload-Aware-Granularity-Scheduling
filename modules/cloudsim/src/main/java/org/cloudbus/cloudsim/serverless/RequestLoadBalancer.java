package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.lists.ContainerVmList;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.serverless.custom.TreeInfo;

import java.util.List;

public class RequestLoadBalancer {

    private ServerlessController broker;
    private ServerlessDatacenter DC;

    public RequestLoadBalancer(ServerlessController controller, ServerlessDatacenter dc){
        setBroker(controller);
        setServerlessDatacenter(dc);
    }

    public void setBroker(ServerlessController br) {
        this.broker = br;
    }

    public ServerlessController getBroker() {
        return broker;
    }

    public void setServerlessDatacenter(ServerlessDatacenter dc) {
        this.DC = dc;
    }

    public ServerlessDatacenter getServerlessDatacenter() {
        return DC;
    }

    public void routeRequest(ServerlessRequest request){
        KeepAlive.record(request.getRequestFunctionId());
        TreeInfo.record(request.getRequestFunctionId());
        if (request.retry > Constants.MAX_RESCHEDULE_TRIES){
            broker.getCloudletList().remove(request);
            request.setSuccess(false);
            broker.getCloudletReceivedList().add(request);
        }
        else if (Constants.SCALE_PER_REQUEST){
            broker.toSubmitOnContainerCreation.add(request);
            broker.createContainer(request, request.getRequestFunctionId(), request.getUserId());
            broker.requestSubmitClock = CloudSim.clock();
        }
        else{
            boolean containerSelected = selectContainer(request);
            if (!containerSelected) {
                broker.getCloudletList().remove(request);
            }
        }
    }

    private void assign(ServerlessRequest task, ServerlessInvoker vm, ServerlessContainer cont,
                        ServerlessRequestScheduler clScheduler, double delay) {
        clScheduler.setTotalCurrentAllocatedRamForRequests(task);
        clScheduler.setTotalCurrentAllocatedMipsShareForRequests(task);
        Log.printLine(String.format("Using idling container: container #%s", cont.getId()));
        task.setContainerId(cont.getId());
        broker.addToVmTaskMap(task, vm);
        cont.setRunningTask(task);
        cont.setIdling(false);
        cont.setIdleStartTime(0);
        broker.setFunctionVmMap(vm, task.getRequestFunctionId());
        broker.requestSubmitClock = CloudSim.clock();
        broker.submitRequestToDC(task, vm.getId(), delay, cont.getId());
    }

    protected boolean selectContainer(ServerlessRequest task){
        boolean contTypeExists = false;
        String fn = task.getRequestFunctionId();
        int vmCount = broker.getVmsCreatedList().size();

        for (int x = 1; x <= vmCount; x++) {
            ServerlessInvoker vm = (ServerlessInvoker) (ContainerVmList.getById(broker.getVmsCreatedList(), x));
            assert vm != null;
            if (vm.getFunctionContainerMap().containsKey(fn)) {
                contTypeExists = true;
                List<Container> contList = vm.getFunctionContainerMap().get(fn);
                for (Container container : contList) {
                    ServerlessContainer cont = (ServerlessContainer) (container);
                    ServerlessRequestScheduler clScheduler = (ServerlessRequestScheduler) (container.getContainerCloudletScheduler());
                    if (clScheduler.isSuitableForRequest(task, cont)) {
                        TreeInfo.warm++;
                        assign(task, vm, cont, clScheduler, 0);
                        return true;
                    }
                }
            }
        }

        ServerlessInvoker bestVm = null;
        ServerlessContainer best = null;
        int bestMiss = 0;
        for (int x = 1; x <= vmCount; x++) {
            ServerlessInvoker vm = (ServerlessInvoker) (ContainerVmList.getById(broker.getVmsCreatedList(), x));
            for (List<Container> list : vm.getFunctionContainerMap().values()) {
                for (Container container : list) {
                    ServerlessContainer cont = (ServerlessContainer) container;
                    String have = cont.getType();
                    if (have == null || have.equals(fn) || !TreeInfo.compatible(fn, have)) continue;
                    if (!cont.getRunningTasks().isEmpty()) continue;
                    int miss = TreeInfo.missing(fn, have);
                    boolean better = best == null || miss < bestMiss
                            || (KeepAlive.aware && miss == bestMiss && TreeInfo.freq(have) < TreeInfo.freq(best.getType()));
                    if (!better) continue;
                    ServerlessRequestScheduler s = (ServerlessRequestScheduler) container.getContainerCloudletScheduler();
                    if (s.isSuitableForRequest(task, cont)) {
                        best = cont;
                        bestVm = vm;
                        bestMiss = miss;
                    }
                }
            }
        }

        if (best != null) {
            ServerlessRequestScheduler s = (ServerlessRequestScheduler) best.getContainerCloudletScheduler();
            String old = best.getType();
            best.setType(fn);
            bestVm.getFunctionContainerMap().get(old).remove(best);
            bestVm.setFunctionContainerMap(best, fn);
            TreeInfo.tepid++;
            assign(task, bestVm, best, s, TreeInfo.tepidDelay(bestMiss));
            return true;
        }

        if(Constants.CONTAINER_CONCURRENCY && Constants.FUNCTION_HORIZONTAL_AUTOSCALING){
            if (contTypeExists){
                broker.sendFunctionRetryRequest(task);
                Log.printLine(String.format("clock %s Container type exists so rescheduling", CloudSim.clock()));

                task.retry++;
                return false;
            }
            for (int x = 1; x <= vmCount; x++) {
                ServerlessInvoker vm = (ServerlessInvoker) (ContainerVmList.getById(broker.getVmsCreatedList(), x));
                assert vm != null;
                if (vm.getFunctionContainerMapPending().containsKey(fn)) {
                    Log.printLine(String.format("clock %s Pending Container of type exists so rescheduling", CloudSim.clock()));

                    broker.sendFunctionRetryRequest(task);
                    task.retry++;
                    return false;
                }
            }
            Log.printLine(String.format("clock %s Container type does not exist so creating new", CloudSim.clock()));

            broker.createContainer(task, fn, task.getUserId());
            broker.sendFunctionRetryRequest(task);
            task.retry++;

            return false;
        }
        else {
            TreeInfo.cold++;
            broker.toSubmitOnContainerCreation.add(task);
            broker.createContainer(task, fn, task.getUserId());
            broker.requestSubmitClock = CloudSim.clock();

            return true;
        }
    }

}