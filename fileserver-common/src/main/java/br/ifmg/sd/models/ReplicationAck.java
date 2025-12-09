package br.ifmg.sd.models;

import java.io.Serializable;

public class ReplicationAck implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String operationId;
    private final String senderId;
    private final boolean success;
    private final String errorMessage;

    public ReplicationAck(
        String operationId,
        String senderId,
        boolean success,
        String errorMessage
    ) {
        this.operationId = operationId;
        this.senderId = senderId;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return (
            "ReplicationAck{" +
            "operationId='" +
            operationId +
            '\'' +
            ", senderId='" +
            senderId +
            '\'' +
            ", success=" +
            success +
            ", errorMessage='" +
            errorMessage +
            '\'' +
            '}'
        );
    }
}
