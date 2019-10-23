package org.ethereum.beacon.discovery.task;

import org.ethereum.beacon.discovery.NodeSession;
import org.ethereum.beacon.discovery.message.DiscoveryV5Message;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class TaskMessageFactory {
  public static final int DEFAULT_DISTANCE = 10;

  public static MessagePacket createPacketFromRequest(
      NodeSession.RequestInfo requestInfo, BytesValue authTag, NodeSession session) {
    switch (requestInfo.getTaskType()) {
      case PING:
        {
          return createPingPacket(authTag, session, requestInfo.getRequestId());
        }
      case FINDNODE:
        {
          return createFindNodePacket(authTag, session, requestInfo.getRequestId());
        }
      default:
        {
          throw new RuntimeException(
              String.format("Type %s is not supported!", requestInfo.getTaskType()));
        }
    }
  }

  public static V5Message createMessageFromRequest(
      NodeSession.RequestInfo requestInfo, NodeSession session) {
    switch (requestInfo.getTaskType()) {
      case PING:
        {
          return createPing(session, requestInfo.getRequestId());
        }
      case FINDNODE:
        {
          return createFindNode(requestInfo.getRequestId());
        }
      default:
        {
          throw new RuntimeException(
              String.format("Type %s is not supported!", requestInfo.getTaskType()));
        }
    }
  }

  public static MessagePacket createPingPacket(
      BytesValue authTag, NodeSession session, BytesValue requestId) {

    return MessagePacket.create(
        session.getHomeNodeId(),
        session.getNodeRecord().getNodeId(),
        authTag,
        session.getInitiatorKey(),
        DiscoveryV5Message.from(createPing(session, requestId)));
  }

  public static PingMessage createPing(NodeSession session, BytesValue requestId) {
    return new PingMessage(requestId, session.getNodeRecord().getSeq());
  }

  public static MessagePacket createFindNodePacket(
      BytesValue authTag, NodeSession session, BytesValue requestId) {
    FindNodeMessage findNodeMessage = createFindNode(requestId);
    return MessagePacket.create(
        session.getHomeNodeId(),
        session.getNodeRecord().getNodeId(),
        authTag,
        session.getInitiatorKey(),
        DiscoveryV5Message.from(findNodeMessage));
  }

  public static FindNodeMessage createFindNode(BytesValue requestId) {
    return new FindNodeMessage(requestId, DEFAULT_DISTANCE);
  }
}
