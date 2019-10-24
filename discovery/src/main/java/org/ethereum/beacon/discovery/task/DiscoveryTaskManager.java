package org.ethereum.beacon.discovery.task;

import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.NodeRecordInfo;
import org.ethereum.beacon.discovery.NodeStatus;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.schedulers.Scheduler;
import tech.pegasys.artemis.util.bytes.Bytes32;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.ethereum.beacon.discovery.NodeStatus.DEAD;
import static org.ethereum.beacon.discovery.task.TaskMessageFactory.DEFAULT_DISTANCE;

/** Manages recurrent node check task(s) */
public class DiscoveryTaskManager {
  private static final int LIVE_CHECK_DISTANCE = DEFAULT_DISTANCE;
  private static final int RECURSIVE_LOOKUP_DISTANCE = DEFAULT_DISTANCE;
  private static final int MS_IN_SECOND = 1000;
  private static final int STATUS_EXPIRATION_SECONDS = 600;
  private static final int LIVE_CHECK_INTERVAL_SECONDS = 1;
  private static final int RECURSIVE_LOOKUP_INTERVAL_SECONDS = 10;
  private static final int RETRY_TIMEOUT_SECONDS = 60;
  private static final int MAX_RETRIES = 10;
  private final Scheduler scheduler;
  private final Bytes32 homeNodeId;
  private final LiveCheckTasks liveCheckTasks;
  private final RecursiveLookupTasks recursiveLookupTasks;
  private final NodeTable nodeTable;
  private final NodeBucketStorage nodeBucketStorage;
  /**
   * Checks whether {@link org.ethereum.beacon.discovery.enr.NodeRecord} is ready for alive status
   * check. Plus, marks records as DEAD if there were a lot of unsuccessful retries to get reply
   * from node.
   *
   * <p>We don't need to recheck the node if
   *
   * <ul>
   *   <li>Node is ACTIVE and last connection retry was not too much time ago
   *   <li>Node is marked as {@link NodeStatus#DEAD}
   *   <li>Node is not ACTIVE but last connection retry was "seconds ago"
   * </ul>
   *
   * <p>In all other cases method returns true, meaning node is ready for ping check
   */
  private final Predicate<NodeRecordInfo> LIVE_CHECK_NODE_RULE =
      nodeRecord -> {
        long currentTime = System.currentTimeMillis() / MS_IN_SECOND;
        if (nodeRecord.getStatus() == NodeStatus.ACTIVE
            && nodeRecord.getLastRetry() > currentTime - STATUS_EXPIRATION_SECONDS) {
          return false; // no need to rediscover
        }
        if (DEAD.equals(nodeRecord.getStatus())) {
          return false; // node looks dead but we are keeping its records for some reason
        }
        if ((currentTime - nodeRecord.getLastRetry())
            < (nodeRecord.getRetry() * nodeRecord.getRetry())) {
          return false; // too early for retry
        }

        return true;
      };

  /**
   * Checks whether {@link org.ethereum.beacon.discovery.enr.NodeRecord} is ready for FINDNODE query
   * which expands the list of all known nodes.
   *
   * <p>Node is eligible if
   *
   * <ul>
   *   <li>Node is ACTIVE and last connection retry was not too much time ago
   * </ul>
   */
  private final Predicate<NodeRecordInfo> RECURSIVE_LOOKUP_NODE_RULE =
      nodeRecord -> {
        long currentTime = System.currentTimeMillis() / MS_IN_SECOND;
        if (nodeRecord.getStatus() == NodeStatus.ACTIVE
            && nodeRecord.getLastRetry() > currentTime - STATUS_EXPIRATION_SECONDS) {
          return true;
        }

        return false;
      };

  /** Checks whether node is eligible to be considered as dead */
  private final Predicate<NodeRecordInfo> DEAD_RULE =
      nodeRecord -> nodeRecord.getRetry() >= MAX_RETRIES;

  private boolean resetDead;
  private boolean removeDead;

  /**
   * @param discoveryManager Discovery manager
   * @param nodeTable Ethereum node records storage, stores all found nodes
   * @param nodeBucketStorage Node bucket storage. stores only closest nodes in ready-to-answer
   *     format
   * @param homeNode Home node
   * @param scheduler scheduler to run recurrent tasks on
   * @param resetDead Whether to reset dead status of the nodes on start. If set to true, resets its
   *     status at startup and sets number of used retries to 0
   * @param removeDead Whether to remove nodes that are found dead after several retries
   */
  public DiscoveryTaskManager(
      DiscoveryManager discoveryManager,
      NodeTable nodeTable,
      NodeBucketStorage nodeBucketStorage,
      NodeRecord homeNode,
      Scheduler scheduler,
      boolean resetDead,
      boolean removeDead) {
    this.scheduler = scheduler;
    this.nodeTable = nodeTable;
    this.nodeBucketStorage = nodeBucketStorage;
    this.homeNodeId = homeNode.getNodeId();
    this.liveCheckTasks =
        new LiveCheckTasks(discoveryManager, scheduler, Duration.ofSeconds(RETRY_TIMEOUT_SECONDS));
    this.recursiveLookupTasks =
        new RecursiveLookupTasks(
            discoveryManager, scheduler, Duration.ofSeconds(RETRY_TIMEOUT_SECONDS));
    this.resetDead = resetDead;
    this.removeDead = removeDead;
  }

  public void start() {
    scheduler.executeAtFixedRate(
        Duration.ZERO, Duration.ofSeconds(LIVE_CHECK_INTERVAL_SECONDS), this::liveCheckTask);
    scheduler.executeAtFixedRate(
        Duration.ZERO,
        Duration.ofSeconds(RECURSIVE_LOOKUP_INTERVAL_SECONDS),
        this::recursiveLookupTask);
  }

  private void liveCheckTask() {
    List<NodeRecordInfo> nodes = nodeTable.findClosestNodes(homeNodeId, LIVE_CHECK_DISTANCE);
    Stream<NodeRecordInfo> closestNodes = nodes.stream();
    closestNodes
        .filter(DEAD_RULE)
        .forEach(
            deadMarkedNode -> {
              if (removeDead) {
                nodeTable.remove(deadMarkedNode);
              } else {
                nodeTable.save(
                    new NodeRecordInfo(
                        deadMarkedNode.getNode(),
                        deadMarkedNode.getLastRetry(),
                        DEAD,
                        deadMarkedNode.getRetry()));
              }
            });
    if (resetDead) {
      closestNodes =
          closestNodes.map(
              nodeRecordInfo -> {
                if (DEAD.equals(nodeRecordInfo.getStatus())) {
                  return new NodeRecordInfo(
                      nodeRecordInfo.getNode(), nodeRecordInfo.getLastRetry(), NodeStatus.SLEEP, 0);
                } else {
                  return nodeRecordInfo;
                }
              });
      resetDead = false;
    }
    closestNodes
        .filter(LIVE_CHECK_NODE_RULE)
        .forEach(
            nodeRecord ->
                liveCheckTasks.add(
                    nodeRecord,
                    () ->
                        updateNode(
                            new NodeRecordInfo(
                                nodeRecord.getNode(),
                                System.currentTimeMillis() / MS_IN_SECOND,
                                NodeStatus.ACTIVE,
                                0)),
                    () ->
                        updateNode(
                            new NodeRecordInfo(
                                nodeRecord.getNode(),
                                System.currentTimeMillis() / MS_IN_SECOND,
                                NodeStatus.SLEEP,
                                (nodeRecord.getRetry() + 1)))));
  }

  private void recursiveLookupTask() {
    List<NodeRecordInfo> nodes = nodeTable.findClosestNodes(homeNodeId, RECURSIVE_LOOKUP_DISTANCE);
    nodes.stream()
        .filter(RECURSIVE_LOOKUP_NODE_RULE)
        .forEach(
            nodeRecord ->
                recursiveLookupTasks.add(
                    nodeRecord,
                    () -> {},
                    () ->
                        updateNode(
                            new NodeRecordInfo(
                                nodeRecord.getNode(),
                                System.currentTimeMillis() / MS_IN_SECOND,
                                NodeStatus.SLEEP,
                                (nodeRecord.getRetry() + 1)))));
  }

  private void updateNode(NodeRecordInfo nodeRecordInfo) {
    nodeTable.save(nodeRecordInfo);
    nodeBucketStorage.put(nodeRecordInfo);
  }
}
