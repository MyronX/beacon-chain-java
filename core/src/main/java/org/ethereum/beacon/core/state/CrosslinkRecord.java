package org.ethereum.beacon.core.state;

import com.google.common.base.Objects;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Crosslink to a shard block.
 *
 * @see BeaconState
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#crosslinkrecord">CrosslinkRecord
 *     in the spec</a>
 */
@SSZSerializable
public class CrosslinkRecord {

  public static final CrosslinkRecord EMPTY = new CrosslinkRecord(EpochNumber.of(0), Hash32.ZERO);

  /** Slot number. */
  @SSZ private final EpochNumber epoch;
  /** Shard block hash. */
  @SSZ private final Hash32 shardBlockRoot;

  public CrosslinkRecord(EpochNumber epoch, Hash32 shardBlockRoot) {
    this.epoch = epoch;
    this.shardBlockRoot = shardBlockRoot;
  }

  public EpochNumber getEpoch() {
    return epoch;
  }

  public Hash32 getShardBlockRoot() {
    return shardBlockRoot;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CrosslinkRecord that = (CrosslinkRecord) o;
    return Objects.equal(epoch, that.epoch) && Objects.equal(shardBlockRoot, that.shardBlockRoot);
  }
}
