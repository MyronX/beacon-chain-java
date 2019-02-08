package org.ethereum.beacon.core.spec;

import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.ShardNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Misc beacon chain constants.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#misc">Misc</a>
 *     in the spec.
 */
public interface MiscParameters {

  ShardNumber SHARD_COUNT = ShardNumber.of(1 << 10); // 1024 shards
  ValidatorIndex TARGET_COMMITTEE_SIZE = ValidatorIndex.of(1 << 7); // 128 validators
  Gwei EJECTION_BALANCE = Gwei.ofEthers(1 << 4); // 16 ETH
  UInt64 MAX_BALANCE_CHURN_QUOTIENT = UInt64.valueOf(1 << 5); // 32
  ShardNumber BEACON_CHAIN_SHARD_NUMBER = ShardNumber.of(UInt64.MAX_VALUE); // (1 << 64) - 1
  UInt64 MAX_INDICES_PER_SLASHABLE_VOTE = UInt64.valueOf(1 << 12);
  UInt64 MAX_WITHDRAWALS_PER_EPOCH = UInt64.valueOf(1 << 2); // 4

  /* Values defined in the spec. */

  default ShardNumber getShardCount() {
    return SHARD_COUNT;
  }

  default ValidatorIndex getTargetCommitteeSize() {
    return TARGET_COMMITTEE_SIZE;
  }

  default Gwei getEjectionBalance() {
    return EJECTION_BALANCE;
  }

  default UInt64 getMaxBalanceChurnQuotient() {
    return MAX_BALANCE_CHURN_QUOTIENT;
  }

  default ShardNumber getBeaconChainShardNumber() {
    return BEACON_CHAIN_SHARD_NUMBER;
  }

  default UInt64 getMaxIndicesPerSlashableVote() {
    return MAX_INDICES_PER_SLASHABLE_VOTE;
  }

  default UInt64 getMaxWithdrawalsPerEpoch() {
    return MAX_WITHDRAWALS_PER_EPOCH;
  }

}
