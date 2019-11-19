package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.Functions;
import org.ethereum.beacon.discovery.NodeSession;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.task.TaskMessageFactory;
import org.ethereum.beacon.schedulers.Scheduler;
import org.ethereum.beacon.util.Utils;
import org.web3j.crypto.ECKeyPair;
import tech.pegasys.artemis.util.bytes.BytesValue;

import java.util.Optional;
import java.util.function.Supplier;

import static org.ethereum.beacon.discovery.Functions.PUBKEY_SIZE;
import static org.ethereum.beacon.discovery.enr.NodeRecord.FIELD_PKEY_SECP256K1;

/** Handles {@link WhoAreYouPacket} in {@link Field#PACKET_WHOAREYOU} field */
public class WhoAreYouPacketHandler implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(WhoAreYouPacketHandler.class);
  private final Pipeline outgoingPipeline;
  private final Scheduler scheduler;

  public WhoAreYouPacketHandler(Pipeline outgoingPipeline, Scheduler scheduler) {
    this.outgoingPipeline = outgoingPipeline;
    this.scheduler = scheduler;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouPacketHandler, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.SESSION, envelope)) {
      return;
    }
    if (!HandlerUtil.requireField(Field.PACKET_WHOAREYOU, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouPacketHandler, requirements are satisfied!",
                envelope.getId()));

    WhoAreYouPacket packet = (WhoAreYouPacket) envelope.get(Field.PACKET_WHOAREYOU);
    NodeSession session = (NodeSession) envelope.get(Field.SESSION);
    try {
      NodeRecord respRecord = null;
      if (packet.getEnrSeq().compareTo(session.getHomeNodeRecord().getSeq()) < 0) {
        respRecord = session.getHomeNodeRecord();
      }
      BytesValue remotePubKey = (BytesValue) session.getNodeRecord().getKey(FIELD_PKEY_SECP256K1);
      byte[] ephemeralKeyBytes = new byte[32];
      Functions.getRandom().nextBytes(ephemeralKeyBytes);
      ECKeyPair ephemeralKey = ECKeyPair.create(ephemeralKeyBytes);

      Functions.HKDFKeys hkdfKeys =
          Functions.hkdf_expand(
              session.getHomeNodeId(),
              session.getNodeRecord().getNodeId(),
              BytesValue.wrap(ephemeralKeyBytes),
              remotePubKey,
              packet.getIdNonce());
      session.setInitiatorKey(hkdfKeys.getInitiatorKey());
      session.setRecipientKey(hkdfKeys.getRecipientKey());
      BytesValue authResponseKey = hkdfKeys.getAuthResponseKey();
      Optional<NodeSession.RequestInfo> requestInfoOpt = session.getFirstAwaitRequestInfo();
      final V5Message message =
          requestInfoOpt
              .map(requestInfo -> TaskMessageFactory.createMessageFromRequest(requestInfo, session))
              .orElseThrow(
                  (Supplier<Throwable>)
                      () ->
                          new RuntimeException(
                              String.format(
                                  "Received WHOAREYOU in envelope #%s but no requests await in %s session",
                                  envelope.getId(), session)));

      BytesValue ephemeralPubKey =
          BytesValue.wrap(Utils.extractBytesFromUnsignedBigInt(ephemeralKey.getPublicKey(), PUBKEY_SIZE));
      AuthHeaderMessagePacket response =
          AuthHeaderMessagePacket.create(
              session.getHomeNodeId(),
              session.getNodeRecord().getNodeId(),
              authResponseKey,
              packet.getIdNonce(),
              session.getStaticNodeKey(),
              respRecord,
              ephemeralPubKey,
              session.generateNonce(),
              hkdfKeys.getInitiatorKey(),
              DiscoveryV5Message.from(message));
      session.sendOutgoing(response);
    } catch (AssertionError ex) {
      String error =
          String.format(
              "Verification not passed for message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus());
      logger.error(error, ex);
      envelope.remove(Field.PACKET_WHOAREYOU);
      session.cancelAllRequests("Bad WHOAREYOU received from node");
      return;
    } catch (Throwable ex) {
      String error =
          String.format(
              "Failed to read message [%s] from node %s in status %s",
              packet, session.getNodeRecord(), session.getStatus());
      logger.error(error, ex);
      envelope.remove(Field.PACKET_WHOAREYOU);
      session.cancelAllRequests("Bad WHOAREYOU received from node");
      return;
    }
    session.setStatus(NodeSession.SessionStatus.AUTHENTICATED);
    envelope.remove(Field.PACKET_WHOAREYOU);
    NextTaskHandler.tryToSendAwaitTaskIfAny(session, outgoingPipeline, scheduler);
  }
}
