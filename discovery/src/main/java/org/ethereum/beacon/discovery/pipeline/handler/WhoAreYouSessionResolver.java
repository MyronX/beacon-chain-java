package org.ethereum.beacon.discovery.pipeline.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.NodeSession;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.EnvelopeHandler;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.HandlerUtil;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;

import java.util.Optional;

/**
 * Resolves session using `authTagRepo` for `WHOAREYOU` packets which should be placed in {@link
 * Field#PACKET_WHOAREYOU}
 */
public class WhoAreYouSessionResolver implements EnvelopeHandler {
  private static final Logger logger = LogManager.getLogger(WhoAreYouSessionResolver.class);
  private final AuthTagRepository authTagRepo;

  public WhoAreYouSessionResolver(AuthTagRepository authTagRepo) {
    this.authTagRepo = authTagRepo;
  }

  @Override
  public void handle(Envelope envelope) {
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouSessionResolver, checking requirements satisfaction",
                envelope.getId()));
    if (!HandlerUtil.requireField(Field.PACKET_WHOAREYOU, envelope)) {
      return;
    }
    logger.trace(
        () ->
            String.format(
                "Envelope %s in WhoAreYouSessionResolver, requirements are satisfied!",
                envelope.getId()));

    WhoAreYouPacket whoAreYouPacket = (WhoAreYouPacket) envelope.get(Field.PACKET_WHOAREYOU);
    Optional<NodeSession> nodeSessionOptional = authTagRepo.get(whoAreYouPacket.getAuthTag());
    if (nodeSessionOptional.isPresent()
        && (nodeSessionOptional
                .get()
                .getStatus()
                .equals(
                    NodeSession.SessionStatus.RANDOM_PACKET_SENT) // We've started handshake before
            || nodeSessionOptional
                .get()
                .getStatus()
                .equals(
                    NodeSession.SessionStatus
                        .AUTHENTICATED))) { // We had authenticated session but it's expired
      envelope.put(Field.SESSION, nodeSessionOptional.get());
      logger.trace(
          () ->
              String.format(
                  "Session resolved: %s in envelope #%s",
                  nodeSessionOptional.get(), envelope.getId()));
    } else {
      envelope.put(Field.BAD_PACKET, envelope.get(Field.PACKET_WHOAREYOU));
      envelope.remove(Field.PACKET_WHOAREYOU);
      envelope.put(Field.BAD_EXCEPTION, new RuntimeException("Not expected WHOAREYOU packet"));
    }
  }
}
