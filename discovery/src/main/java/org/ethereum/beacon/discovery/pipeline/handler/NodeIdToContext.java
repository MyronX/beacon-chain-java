package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.NodeContext;
import org.ethereum.beacon.discovery.NodeRecordInfo;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.network.NetworkParcelV5;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.util.ExpirationScheduler;
import org.javatuples.Pair;
import tech.pegasys.artemis.util.bytes.Bytes32;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Performs {@link Field#NEED_CONTEXT} request. Looks up for Node context based on NodeId, which
 * should be in request field and stores it in {@link Field#CONTEXT} field.
 */
public class NodeIdToContext implements EnvelopeHandler {
  private static final int CLEANUP_DELAY_SECONDS = 180;
  private static final Logger logger = LogManager.getLogger(NodeIdToContext.class);
  private final NodeRecord homeNodeRecord;
  private final NodeBucketStorage nodeBucketStorage;
  private final AuthTagRepository authTagRepo;
  private final Map<Bytes32, NodeContext> recentContexts =
      new ConcurrentHashMap<>(); // nodeId -> context
  private final NodeTable nodeTable;
  private final Pipeline outgoingPipeline;
  private ExpirationScheduler<Bytes32> contextExpirationScheduler =
      new ExpirationScheduler<>(CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);

  public NodeIdToContext(
      NodeRecord homeNodeRecord,
      NodeBucketStorage nodeBucketStorage,
      AuthTagRepository authTagRepo,
      NodeTable nodeTable,
      Pipeline outgoingPipeline) {
    this.homeNodeRecord = homeNodeRecord;
    this.nodeBucketStorage = nodeBucketStorage;
    this.authTagRepo = authTagRepo;
    this.nodeTable = nodeTable;
    this.outgoingPipeline = outgoingPipeline;
  }

  @Override
  public void handle(Envelope envelope) {
    if (!envelope.contains(Field.NEED_CONTEXT)) {
      return;
    }
    Pair<Bytes32, Runnable> contextRequest =
        (Pair<Bytes32, Runnable>) envelope.get(Field.NEED_CONTEXT);
    envelope.remove(Field.NEED_CONTEXT);
    Optional<NodeContext> nodeContextOptional = getContext(contextRequest.getValue0());
    if (nodeContextOptional.isPresent()) {
      envelope.put(Field.CONTEXT, nodeContextOptional.get());
      logger.trace(
          () ->
              String.format(
                  "Context resolved: %s in envelope #%s",
                  nodeContextOptional.get(), envelope.getId()));
    } else {
      contextRequest.getValue1().run();
    }
  }

  private Optional<NodeContext> getContext(Bytes32 nodeId) {
    NodeContext context = recentContexts.get(nodeId);
    if (context == null) {
      Optional<NodeRecordInfo> nodeOptional = nodeTable.getNode(nodeId);
      if (!nodeOptional.isPresent()) {
        logger.trace(
            () -> String.format("Couldn't find node record for nodeId %s, ignoring", nodeId));
        return Optional.empty();
      }
      NodeRecord nodeRecord = nodeOptional.get().getNode();
      SecureRandom random = new SecureRandom();
      context =
          new NodeContext(
              nodeRecord,
              homeNodeRecord,
              nodeTable,
              nodeBucketStorage,
              authTagRepo,
              packet -> outgoingPipeline.push(new NetworkParcelV5(packet, nodeRecord)),
              random);
      recentContexts.put(nodeId, context);
    }

    final NodeContext contextBackup = context;
    contextExpirationScheduler.put(
        context.getNodeRecord().getNodeId(),
        () -> {
          recentContexts.remove(contextBackup.getNodeRecord().getNodeId());
          contextBackup.cleanup();
        });
    return Optional.of(context);
  }
}