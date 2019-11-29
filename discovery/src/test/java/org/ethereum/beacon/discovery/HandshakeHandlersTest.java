package org.ethereum.beacon.discovery;

import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.packet.MessagePacket;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.ethereum.beacon.discovery.pipeline.Field;
import org.ethereum.beacon.discovery.pipeline.Pipeline;
import org.ethereum.beacon.discovery.pipeline.PipelineImpl;
import org.ethereum.beacon.discovery.pipeline.handler.AuthHeaderMessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.MessageHandler;
import org.ethereum.beacon.discovery.pipeline.handler.MessagePacketHandler;
import org.ethereum.beacon.discovery.pipeline.handler.WhoAreYouPacketHandler;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.task.TaskMessageFactory;
import org.ethereum.beacon.discovery.task.TaskOptions;
import org.ethereum.beacon.discovery.task.TaskType;
import org.ethereum.beacon.schedulers.Scheduler;
import org.ethereum.beacon.schedulers.Schedulers;
import org.javatuples.Pair;
import org.junit.Test;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.ethereum.beacon.discovery.TestUtil.NODE_RECORD_FACTORY_NO_VERIFICATION;
import static org.ethereum.beacon.discovery.TestUtil.TEST_SERIALIZER;
import static org.ethereum.beacon.discovery.pipeline.Field.BAD_PACKET;
import static org.ethereum.beacon.discovery.pipeline.Field.MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.PACKET_AUTH_HEADER_MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.PACKET_MESSAGE;
import static org.ethereum.beacon.discovery.pipeline.Field.SESSION;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HandshakeHandlersTest {

  @Test
  public void authHandlerWithMessageRoundTripTest() throws Exception {
    // Node1
    Pair<BytesValue, NodeRecord> nodePair1 = TestUtil.generateUnverifiedNode(30303);
    NodeRecord nodeRecord1 = nodePair1.getValue1();
    // Node2
    Pair<BytesValue, NodeRecord> nodePair2 = TestUtil.generateUnverifiedNode(30304);
    NodeRecord nodeRecord2 = nodePair2.getValue1();
    Random rnd = new Random();
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database1 = Database.inMemoryDB();
    Database database2 = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage1 =
        nodeTableStorageFactory.createTable(
            database1,
            TEST_SERIALIZER,
            (oldSeq) -> nodeRecord1,
            () ->
                new ArrayList<NodeRecord>() {
                  {
                    add(nodeRecord2);
                  }
                });
    NodeBucketStorage nodeBucketStorage1 =
        nodeTableStorageFactory.createBucketStorage(database1, TEST_SERIALIZER, nodeRecord1);
    NodeTableStorage nodeTableStorage2 =
        nodeTableStorageFactory.createTable(
            database2,
            TEST_SERIALIZER,
            (oldSeq) -> nodeRecord2,
            () ->
                new ArrayList<NodeRecord>() {
                  {
                    add(nodeRecord1);
                  }
                });
    NodeBucketStorage nodeBucketStorage2 =
        nodeTableStorageFactory.createBucketStorage(database2, TEST_SERIALIZER, nodeRecord2);

    // Node1 create AuthHeaderPacket
    final Packet[] outgoing1Packets = new Packet[2];
    final Semaphore outgoing1PacketsSemaphore = new Semaphore(2);
    outgoing1PacketsSemaphore.acquire(2);
    final Consumer<Packet> outgoingMessages1to2 =
        packet -> {
          System.out.println("Outgoing packet from 1 to 2: " + packet);
          outgoing1Packets[outgoing1PacketsSemaphore.availablePermits()] = packet;
          outgoing1PacketsSemaphore.release(1);
        };
    AuthTagRepository authTagRepository1 = new AuthTagRepository();
    NodeSession nodeSessionAt1For2 =
        new NodeSession(
            nodeRecord2,
            nodeRecord1,
            nodePair1.getValue0(),
            nodeTableStorage1.get(),
            nodeBucketStorage1,
            authTagRepository1,
            outgoingMessages1to2,
            rnd);
    final Consumer<Packet> outgoingMessages2to1 =
        packet -> {
          // do nothing, we don't need to test it here
        };
    NodeSession nodeSessionAt2For1 =
        new NodeSession(
            nodeRecord1,
            nodeRecord2,
            nodePair2.getValue0(),
            nodeTableStorage2.get(),
            nodeBucketStorage2,
            new AuthTagRepository(),
            outgoingMessages2to1,
            rnd);

    Scheduler taskScheduler = Schedulers.createDefault().events();
    Pipeline outgoingPipeline = new PipelineImpl().build();
    WhoAreYouPacketHandler whoAreYouPacketHandlerNode1 =
        new WhoAreYouPacketHandler(outgoingPipeline, taskScheduler);
    Envelope envelopeAt1From2 = new Envelope();
    byte[] idNonceBytes = new byte[32];
    Functions.getRandom().nextBytes(idNonceBytes);
    Bytes32 idNonce = Bytes32.wrap(idNonceBytes);
    nodeSessionAt2For1.setIdNonce(idNonce);
    BytesValue authTag = nodeSessionAt2For1.generateNonce();
    authTagRepository1.put(authTag, nodeSessionAt1For2);
    envelopeAt1From2.put(
        Field.PACKET_WHOAREYOU,
        WhoAreYouPacket.createFromNodeId(nodePair1.getValue1().getNodeId(), authTag, idNonce, UInt64.ZERO));
    envelopeAt1From2.put(Field.SESSION, nodeSessionAt1For2);
    CompletableFuture<Void> future = new CompletableFuture<>();
    nodeSessionAt1For2.createNextRequest(TaskType.FINDNODE, new TaskOptions(true), future);
    whoAreYouPacketHandlerNode1.handle(envelopeAt1From2);
    assert outgoing1PacketsSemaphore.tryAcquire(1, 1, TimeUnit.SECONDS);
    outgoing1PacketsSemaphore.release();

    // Node2 handle AuthHeaderPacket and finish handshake
    AuthHeaderMessagePacketHandler authHeaderMessagePacketHandlerNode2 =
        new AuthHeaderMessagePacketHandler(
            outgoingPipeline, taskScheduler, NODE_RECORD_FACTORY_NO_VERIFICATION);
    Envelope envelopeAt2From1 = new Envelope();
    envelopeAt2From1.put(PACKET_AUTH_HEADER_MESSAGE, outgoing1Packets[0]);
    envelopeAt2From1.put(SESSION, nodeSessionAt2For1);
    assertFalse(nodeSessionAt2For1.isAuthenticated());
    authHeaderMessagePacketHandlerNode2.handle(envelopeAt2From1);
    assertTrue(nodeSessionAt2For1.isAuthenticated());

    // Node 1 handles message from Node 2
    MessagePacketHandler messagePacketHandler1 = new MessagePacketHandler();
    Envelope envelopeAt1From2WithMessage = new Envelope();
    BytesValue pingAuthTag = nodeSessionAt1For2.generateNonce();
    MessagePacket pingPacketFrom2To1 =
        TaskMessageFactory.createPingPacket(
            pingAuthTag,
            nodeSessionAt2For1,
            nodeSessionAt2For1
                .createNextRequest(TaskType.PING, new TaskOptions(true), new CompletableFuture<>())
                .getRequestId());
    envelopeAt1From2WithMessage.put(PACKET_MESSAGE, pingPacketFrom2To1);
    envelopeAt1From2WithMessage.put(SESSION, nodeSessionAt1For2);
    messagePacketHandler1.handle(envelopeAt1From2WithMessage);
    assertNull(envelopeAt1From2WithMessage.get(BAD_PACKET));
    assertNotNull(envelopeAt1From2WithMessage.get(MESSAGE));

    MessageHandler messageHandler = new MessageHandler(NODE_RECORD_FACTORY_NO_VERIFICATION);
    messageHandler.handle(envelopeAt1From2WithMessage);
    assert outgoing1PacketsSemaphore.tryAcquire(2, 1, TimeUnit.SECONDS);

    // Node 2 handles message from Node 1
    MessagePacketHandler messagePacketHandler2 = new MessagePacketHandler();
    Envelope envelopeAt2From1WithMessage = new Envelope();
    Packet pongPacketFrom1To2 = outgoing1Packets[1];
    MessagePacket pongMessagePacketFrom1To2 = (MessagePacket) pongPacketFrom1To2;
    envelopeAt2From1WithMessage.put(PACKET_MESSAGE, pongMessagePacketFrom1To2);
    envelopeAt2From1WithMessage.put(SESSION, nodeSessionAt2For1);
    messagePacketHandler2.handle(envelopeAt2From1WithMessage);
    assertNull(envelopeAt2From1WithMessage.get(BAD_PACKET));
    assertNotNull(envelopeAt2From1WithMessage.get(MESSAGE));
  }
}
