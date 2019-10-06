package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.Functions;
import org.ethereum.beacon.discovery.NodeContext;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.task.TaskMessageFactory;
import org.ethereum.beacon.discovery.task.TaskType;
import org.javatuples.Triplet;
import org.web3j.crypto.ECKeyPair;
import tech.pegasys.artemis.util.bytes.BytesValue;

import java.util.concurrent.CompletableFuture;

/** Handles {@link WhoAreYouPacket} in {@link Field#PACKET_WHOAREYOU} field */
public class WhoAreYouPacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(WhoAreYouPacketHandler.class);

  @Override
  public void handle(Envelope envelope) {
    if (!envelope.contains(Field.PACKET_WHOAREYOU)) {
      return;
    }
    if (!envelope.contains(Field.CONTEXT)) {
      return;
    }
    WhoAreYouPacket packet = (WhoAreYouPacket) envelope.get(Field.PACKET_WHOAREYOU);
    NodeContext context = (NodeContext) envelope.get(Field.CONTEXT);
    try {
      BytesValue authTag = context.getAuthTag().get();
      packet.verify(context.getHomeNodeId(), authTag);
      packet.getEnrSeq(); // FIXME: Their side enr seq. Do we need it?
      byte[] ephemeralKeyBytes = new byte[32];
      Functions.getRandom().nextBytes(ephemeralKeyBytes);
      ECKeyPair ephemeralKey = ECKeyPair.create(ephemeralKeyBytes); // TODO: generate
      Triplet<BytesValue, BytesValue, BytesValue> hkdf =
          Functions.hkdf_expand(
              context.getHomeNodeId(),
              context.getNodeRecord().getNodeId(),
              BytesValue.wrap(ephemeralKey.getPrivateKey().toByteArray()),
              packet.getIdNonce(),
              (BytesValue) context.getNodeRecord().get(NodeRecord.FIELD_PKEY_SECP256K1));
      BytesValue initiatorKey = hkdf.getValue0();
      BytesValue staticNodeKey = hkdf.getValue1();
      BytesValue authResponseKey = hkdf.getValue2();
      V5Message taskMessage = null;
      if (context.loadTask() == TaskType.PING) {
        taskMessage = TaskMessageFactory.createPing(context);
      } else if (context.loadTask() == TaskType.FINDNODE) {
        taskMessage = TaskMessageFactory.createFindNode(context);
      } else {
        throw new RuntimeException(
            String.format(
                "Type %s in envelope #%s is not known", context.loadTask(), envelope.getId()));
      }

      AuthHeaderMessagePacket response =
          AuthHeaderMessagePacket.create(
              context.getHomeNodeId(),
              context.getNodeRecord().getNodeId(),
              authResponseKey,
              packet.getIdNonce(),
              staticNodeKey,
              context.getHomeNodeRecord(),
              BytesValue.wrap(ephemeralKey.getPublicKey().toByteArray()),
              authTag,
              initiatorKey,
              DiscoveryV5Message.from(taskMessage));
      context.sendOutgoing(response);
    } catch (AssertionError ex) {
      logger.info(
          String.format(
              "Verification not passed for message [%s] from node %s in status %s",
              packet, context.getNodeRecord(), context.getStatus()));
    } catch (Exception ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, context.getNodeRecord(), context.getStatus());
      logger.error(error, ex);
      envelope.remove(Field.PACKET_WHOAREYOU);
      if (envelope.contains(Field.FUTURE)) {
        CompletableFuture<Void> future = (CompletableFuture<Void>) envelope.get(Field.FUTURE);
        future.completeExceptionally(ex);
      }
      return;
    }
    context.setStatus(NodeContext.SessionStatus.AUTHENTICATED);
    envelope.remove(Field.PACKET_WHOAREYOU);
  }
}