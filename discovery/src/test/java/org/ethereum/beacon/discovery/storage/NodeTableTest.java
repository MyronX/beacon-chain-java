package org.ethereum.beacon.discovery.storage;

import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.discovery.NodeRecordInfo;
import org.ethereum.beacon.discovery.NodeStatus;
import org.ethereum.beacon.discovery.enr.EnrScheme;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import org.ethereum.beacon.discovery.enr.NodeRecordFactory;
import org.javatuples.Pair;
import org.junit.Test;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes4;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.ethereum.beacon.discovery.storage.NodeTableStorage.DEFAULT_SERIALIZER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NodeTableTest {
  private static final NodeRecordFactory NODE_RECORD_FACTORY = NodeRecordFactory.DEFAULT;

  private Supplier<NodeRecord> homeNodeSupplier =
      () -> {
        try {
          return NODE_RECORD_FACTORY.createFromValues(
              EnrScheme.V4,
              UInt64.valueOf(1),
              Bytes96.EMPTY,
              new ArrayList<Pair<String, Object>>() {
                {
                  add(
                      Pair.with(
                          NodeRecord.FIELD_IP_V4,
                          Bytes4.wrap(InetAddress.getByName("127.0.0.1").getAddress())));
                  add(Pair.with(NodeRecord.FIELD_UDP_V4, 30303));
                  add(
                      Pair.with(
                          NodeRecord.FIELD_PKEY_SECP256K1,
                          BytesValue.fromHexString(
                              "0bfb48004b1698f05872cf18b1f278998ad8f7d4c135aa41f83744e7b850ab6b98")));
                }
              });
        } catch (UnknownHostException e) {
          throw new RuntimeException(e);
        }
      };

  @Test
  public void testCreate() throws Exception {
    final String localhostEnr =
        "-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8";
    NodeRecord nodeRecord = NODE_RECORD_FACTORY.fromBase64(localhostEnr);
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage =
        nodeTableStorageFactory.createTable(
            database,
            DEFAULT_SERIALIZER,
            homeNodeSupplier,
            () -> {
              List<NodeRecord> nodes = new ArrayList<>();
              nodes.add(nodeRecord);
              return nodes;
            });
    Optional<NodeRecordInfo> extendedEnr = nodeTableStorage.get().getNode(nodeRecord.getNodeId());
    assertTrue(extendedEnr.isPresent());
    NodeRecordInfo nodeRecord2 = extendedEnr.get();
    assertEquals(
        nodeRecord.get(NodeRecord.FIELD_PKEY_SECP256K1),
        nodeRecord2.getNode().get(NodeRecord.FIELD_PKEY_SECP256K1));
    assertEquals(
        nodeTableStorage.get().getHomeNode().getNodeId(), homeNodeSupplier.get().getNodeId());
  }

  @Test
  public void testFind() throws Exception {
    final String localhostEnr =
        "-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8";
    NodeRecord localHostNode = NODE_RECORD_FACTORY.fromBase64(localhostEnr);
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    Database database = Database.inMemoryDB();
    NodeTableStorage nodeTableStorage =
        nodeTableStorageFactory.createTable(
            database,
            DEFAULT_SERIALIZER,
            homeNodeSupplier,
            () -> {
              List<NodeRecord> nodes = new ArrayList<>();
              nodes.add(localHostNode);
              return nodes;
            });

    // node is adjusted to be close to localhostEnr
    NodeRecord closestNode =
        NODE_RECORD_FACTORY.createFromValues(
            EnrScheme.V4,
            UInt64.valueOf(1),
            Bytes96.EMPTY,
            new ArrayList<Pair<String, Object>>() {
              {
                add(
                    Pair.with(
                        NodeRecord.FIELD_IP_V4,
                        Bytes4.wrap(InetAddress.getByName("127.0.0.2").getAddress())));
                add(Pair.with(NodeRecord.FIELD_UDP_V4, 30303));
                add(
                    Pair.with(
                        NodeRecord.FIELD_PKEY_SECP256K1,
                        BytesValue.fromHexString(
                            "aafb48004b1698f05872cf18b1f278998ad8f7d4c135aa41f83744e7b850ab6b98")));
              }
            });
    nodeTableStorage.get().save(new NodeRecordInfo(closestNode, -1L, NodeStatus.ACTIVE, 0));
    assertEquals(
        nodeTableStorage
            .get()
            .getNode(closestNode.getNodeId())
            .get()
            .getNode()
            .get(NodeRecord.FIELD_PKEY_SECP256K1),
        closestNode.get(NodeRecord.FIELD_PKEY_SECP256K1));
    NodeRecord farNode =
        NODE_RECORD_FACTORY.createFromValues(
            EnrScheme.V4,
            UInt64.valueOf(1),
            Bytes96.EMPTY,
            new ArrayList<Pair<String, Object>>() {
              {
                add(
                    Pair.with(
                        NodeRecord.FIELD_IP_V4,
                        Bytes4.wrap(InetAddress.getByName("127.0.0.3").getAddress())));
                add(Pair.with(NodeRecord.FIELD_UDP_V4, 30303));
                add(
                    Pair.with(
                        NodeRecord.FIELD_PKEY_SECP256K1,
                        BytesValue.fromHexString(
                            "bafb48004b1698f05872cf18b1f278998ad8f7d4c135aa41f83744e7b850ab6b98")));
              }
            });
    nodeTableStorage.get().save(new NodeRecordInfo(farNode, -1L, NodeStatus.ACTIVE, 0));
    List<NodeRecordInfo> closestNodes =
        nodeTableStorage.get().findClosestNodes(closestNode.getNodeId(), 252);
    assertEquals(2, closestNodes.size());
    Set<BytesValue> publicKeys = new HashSet<>();
    closestNodes.forEach(
        n -> publicKeys.add((BytesValue) n.getNode().get(NodeRecord.FIELD_PKEY_SECP256K1)));
    assertTrue(publicKeys.contains(localHostNode.get(NodeRecord.FIELD_PKEY_SECP256K1)));
    assertTrue(publicKeys.contains(closestNode.get(NodeRecord.FIELD_PKEY_SECP256K1)));
    List<NodeRecordInfo> farNodes = nodeTableStorage.get().findClosestNodes(farNode.getNodeId(), 1);
    assertEquals(1, farNodes.size());
    assertEquals(
        farNodes.get(0).getNode().get(NodeRecord.FIELD_PKEY_SECP256K1),
        farNode.get(NodeRecord.FIELD_PKEY_SECP256K1));
  }

  /**
   * Verifies that calculated index number is in range of [0, {@link
   * NodeTableImpl#NUMBER_OF_INDEXES})
   */
  @Test
  public void testIndexCalculation() {
    Bytes32 nodeId0 =
        Bytes32.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");
    Bytes32 nodeId1a =
        Bytes32.fromHexString("0000000000000000000000000000000000000000000000000000000000000001");
    Bytes32 nodeId1b =
        Bytes32.fromHexString("1000000000000000000000000000000000000000000000000000000000000000");
    Bytes32 nodeId1s =
        Bytes32.fromHexString("1111111111111111111111111111111111111111111111111111111111111111");
    Bytes32 nodeId9s =
        Bytes32.fromHexString("9999999999999999999999999999999999999999999999999999999999999999");
    Bytes32 nodeIdfs =
        Bytes32.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    assertEquals(0, NodeTableImpl.getNodeIndex(nodeId0));
    assertEquals(0, NodeTableImpl.getNodeIndex(nodeId1a));
    assertEquals(16, NodeTableImpl.getNodeIndex(nodeId1b));
    assertEquals(17, NodeTableImpl.getNodeIndex(nodeId1s));
    assertEquals(153, NodeTableImpl.getNodeIndex(nodeId9s));
    assertEquals(255, NodeTableImpl.getNodeIndex(nodeIdfs));
  }
}
