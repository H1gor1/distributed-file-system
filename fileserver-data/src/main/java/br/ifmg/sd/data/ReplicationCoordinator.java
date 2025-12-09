package br.ifmg.sd.data;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ReplicationCoordinator {

    private final Map<String, ReplicationState> pendingOperations = new ConcurrentHashMap<>();

    public ReplicationState startOperation(String operationId, int expectedAcks) {
        ReplicationState state = new ReplicationState(expectedAcks);
        pendingOperations.put(operationId, state);
        return state;
    }

    public void registerAck(String operationId, String senderId, boolean success) {
        ReplicationState state = pendingOperations.get(operationId);
        if (state != null) {
            state.registerAck(senderId, success);
        }
    }

    public boolean waitForCompletion(String operationId, long timeout, TimeUnit unit) 
            throws InterruptedException {
        ReplicationState state = pendingOperations.get(operationId);
        if (state == null) {
            return false;
        }

        try {
            boolean completed = state.await(timeout, unit);
            return completed && state.isAllSuccess();
        } finally {
            pendingOperations.remove(operationId);
        }
    }

    public static class ReplicationState {
        private final CountDownLatch latch;
        private final Set<String> successfulAcks = ConcurrentHashMap.newKeySet();
        private final Set<String> failedAcks = ConcurrentHashMap.newKeySet();

        public ReplicationState(int expectedAcks) {
            this.latch = new CountDownLatch(expectedAcks);
        }

        public void registerAck(String senderId, boolean success) {
            if (success) {
                successfulAcks.add(senderId);
            } else {
                failedAcks.add(senderId);
            }
            latch.countDown();
        }

        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        public boolean isAllSuccess() {
            return failedAcks.isEmpty() && latch.getCount() == 0;
        }

        public int getSuccessCount() {
            return successfulAcks.size();
        }

        public int getFailureCount() {
            return failedAcks.size();
        }
    }
}
