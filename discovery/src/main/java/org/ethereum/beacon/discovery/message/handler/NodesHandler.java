package org.ethereum.beacon.discovery.message.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.NodeRecordInfo;
import org.ethereum.beacon.discovery.NodeSession;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.task.TaskType;
import tech.pegasys.artemis.util.bytes.BytesValue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class NodesHandler implements MessageHandler<NodesMessage> {
  private static final Logger logger = LogManager.getLogger(FindNodeHandler.class);

  @Override
  public void handle(NodesMessage message, NodeSession session) {
    // NODES total count handling
    Optional<NodeSession.RequestInfo> requestInfoOpt = session.getRequestId(message.getRequestId());
    if (!requestInfoOpt.isPresent()) {
      throw new RuntimeException(
          String.format(
              "Request #%s not found in session %s when handling message %s",
              message.getRequestId(), session, message));
    }
    NodeSession.RequestInfo requestInfo = requestInfoOpt.get();
    if (requestInfo instanceof FindNodeRequestInfo) {
      int newNodesCount = ((FindNodeRequestInfo) requestInfo).getRemainingNodes() - 1;
      if (newNodesCount == 0) {
        session.clearRequestId(message.getRequestId(), TaskType.FINDNODE);
      } else {
        session.updateRequestInfo(
            message.getRequestId(),
            new FindNodeRequestInfo(
                TaskStatus.IN_PROCESS,
                message.getRequestId(),
                requestInfo.getFuture(),
                newNodesCount));
      }
    } else {
      if (message.getTotal() > 1) {
        session.updateRequestInfo(
            message.getRequestId(),
            new FindNodeRequestInfo(
                TaskStatus.IN_PROCESS,
                message.getRequestId(),
                requestInfo.getFuture(),
                message.getTotal() - 1));
      } else {
        session.clearRequestId(message.getRequestId(), TaskType.FINDNODE);
      }
    }

    // Parse node records
    logger.trace(
        () ->
            String.format(
                "Received %s node records in session %s. Total buckets expected: %s",
                message.getNodeRecordsSize(), session, message.getTotal()));
    message
        .getNodeRecords()
        .forEach(
            nodeRecordV5 -> {
              NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(nodeRecordV5);
              if (!session.getNodeTable().getNode(nodeRecordV5.getNodeId()).isPresent()) {
                session.getNodeTable().save(nodeRecordInfo);
              }
              session.putRecordInBucket(nodeRecordInfo);
            });
  }

  public static class FindNodeRequestInfo extends NodeSession.GeneralRequestInfo {
    private final int remainingNodes;

    public FindNodeRequestInfo(
        TaskStatus taskStatus,
        BytesValue requestId,
        CompletableFuture<Void> future,
        int remainingNodes) {
      super(TaskType.FINDNODE, taskStatus, requestId, future);
      this.remainingNodes = remainingNodes;
    }

    public int getRemainingNodes() {
      return remainingNodes;
    }

    @Override
    public String toString() {
      return "FindNodeRequestInfo{" + "remainingNodes=" + remainingNodes + '}';
    }
  }
}
