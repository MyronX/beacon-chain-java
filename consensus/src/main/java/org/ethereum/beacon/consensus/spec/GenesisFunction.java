package org.ethereum.beacon.consensus.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.state.Fork;
import org.ethereum.beacon.core.state.ValidatorRecord;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.core.types.ValidatorIndex;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes4;
import tech.pegasys.artemis.util.uint.UInt64;
import tech.pegasys.artemis.util.uint.UInt64s;

/**
 * On genesis part.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#genesis">Genesis</a>
 *     in the spec.
 */
public interface GenesisFunction extends BlockProcessing {

  /*
   Before the Ethereum 2.0 genesis has been triggered, and for every Ethereum 1.0 block,
   let candidate_state = initialize_beacon_state_from_eth1(eth1_block_hash, eth1_timestamp, deposits) where:

    - eth1_block_hash is the hash of the Ethereum 1.0 block
    - eth1_timestamp is the Unix timestamp corresponding to eth1_block_hash
    - deposits is the sequence of all deposits, ordered chronologically, up to the block with hash eth1_block_hash
  */
  default BeaconState initialize_beacon_state_from_eth1(
      Hash32 eth1_block_hash, Time eth1_timestamp, List<Deposit> deposits) {
    MutableBeaconState state = BeaconState.getEmpty(getConstants()).createMutableCopy();

    state.setPreviousJustifiedCheckpoint(
        new Checkpoint(getConstants().getGenesisEpoch(), Hash32.ZERO));
    state.setCurrentJustifiedCheckpoint(
        new Checkpoint(getConstants().getGenesisEpoch(), Hash32.ZERO));
    state.setFinalizedCheckpoint(
        new Checkpoint(getConstants().getGenesisEpoch(), Hash32.ZERO));

    state.setSlot(getConstants().getGenesisSlot());
    state.setGenesisTime(eth1_timestamp);
    state.setFork(new Fork(Bytes4.ZERO, Bytes4.ZERO, getConstants().getGenesisEpoch()));
    state.setLatestBlockHeader(get_block_header(get_empty_block()));

    // Process deposits
    // Spec maintains initial deposit proofs and roots in incremental fashion,
    // i.e. new root is computed for each new update of deposit list.
    // Our impl works in more efficient way: one root to proof them all
    List<DepositData> deposit_data_list =
        deposits.stream().map(Deposit::getData).collect(Collectors.toList());
    state.setEth1Data(new Eth1Data(
        hash_tree_root(deposit_data_list), UInt64.valueOf(deposits.size()), eth1_block_hash));
    for (Deposit deposit : deposits) {
      verify_deposit(state, deposit);
      process_deposit(state, deposit);
    }

    // Process activations
    for (ValidatorIndex index : state.getValidators().size().iterateFromZero()) {
      Gwei balance = state.getBalances().get(index);
      Gwei effective_balance = Gwei.castFrom(
          UInt64s.min(
              balance.minus(balance.modulo(getConstants().getEffectiveBalanceIncrement())),
              getConstants().getMaxEffectiveBalance()
          ));
      state.getValidators().update(index,
          v -> ValidatorRecord.Builder.fromRecord(v)
              .withEffectiveBalance(effective_balance).build());

      ValidatorRecord validator = state.getValidators().get(index);
      if (validator.getEffectiveBalance().equals(getConstants().getMaxEffectiveBalance())) {
        state.getValidators().update(index,
            v -> ValidatorRecord.Builder.fromRecord(v)
                .withActivationEpoch(getConstants().getGenesisEpoch())
                .withActivationEligibilityEpoch(getConstants().getGenesisEpoch()).build());
      }
    }

    // Populate active_index_roots and compact_committees_roots
    List<ValidatorIndex> indices_list =
        new ArrayList<>(get_active_validator_indices(state, getConstants().getGenesisEpoch()));
    Hash32 active_index_root = hash_tree_root(indices_list);
    Hash32 committee_root = get_compact_committees_root(state, getConstants().getGenesisEpoch());

    for (EpochNumber index : getConstants().getEpochsPerHistoricalVector().iterateFrom(EpochNumber.ZERO)) {
      state.getActiveIndexRoots().set(index, active_index_root);
      state.getCompactCommitteesRoots().set(index, committee_root);
    }

    return state.createImmutable();
  }

  /*
    def is_valid_genesis_state(state: BeaconState) -> bool:
      if state.genesis_time < MIN_GENESIS_TIME:
          return False
      if len(get_active_validator_indices(state, GENESIS_EPOCH)) < MIN_GENESIS_ACTIVE_VALIDATOR_COUNT:
          return False
      return True
   */
  default boolean is_genesis_trigger(BeaconState state) {
    if (state.getGenesisTime().less(getConstants().getMinGenesisTime())) {
      return false;
    }
    if (get_active_validator_indices(state, getConstants().getGenesisEpoch()).size()
          < getConstants().getMinGenesisActiveValidatorCount().getIntValue()) {
      return false;
    }
    return true;
  }
}
