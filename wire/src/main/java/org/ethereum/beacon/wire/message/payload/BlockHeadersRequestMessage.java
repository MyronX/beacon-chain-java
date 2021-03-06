package org.ethereum.beacon.wire.message.payload;

import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.wire.message.RequestMessagePayload;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

@SSZSerializable
public class BlockHeadersRequestMessage extends RequestMessagePayload {
  public static final int METHOD_ID = 0x0D;
  public static final Hash32 NULL_START_ROOT = Hash32.fromHexString("11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff");
  public static final SlotNumber NULL_START_SLOT = SlotNumber.castFrom(UInt64.MAX_VALUE);

  @SSZ private final Hash32 startRoot;
  @SSZ private final SlotNumber startSlot;
  @SSZ private final UInt64 maxHeaders;
  @SSZ private final UInt64 skipSlots;

  public BlockHeadersRequestMessage(Hash32 startRoot,
      SlotNumber startSlot, UInt64 maxHeaders, UInt64 skipSlots) {
    this.startRoot = startRoot;
    this.startSlot = startSlot;
    this.maxHeaders = maxHeaders;
    this.skipSlots = skipSlots;
  }

  @Override
  public int getMethodId() {
    return METHOD_ID;
  }

  public Hash32 getStartRoot() {
    return startRoot;
  }

  public SlotNumber getStartSlot() {
    return startSlot;
  }

  public UInt64 getMaxHeaders() {
    return maxHeaders;
  }

  public UInt64 getSkipSlots() {
    return skipSlots;
  }

  @Override
  public String toString() {
    return "BlockHeadersRequestMessage{" +
        (NULL_START_ROOT.equals(startRoot) ? "" : "startRoot=" + startRoot + ", ") +
        (NULL_START_SLOT.equals(startSlot) ? "" : "startSlot=" + startSlot + ", ") +
        "maxHeaders=" + maxHeaders +
        ", skipSlots=" + skipSlots +
        '}';
  }
}
