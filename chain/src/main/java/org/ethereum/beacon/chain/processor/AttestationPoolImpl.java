package org.ethereum.beacon.chain.processor;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.ethereum.beacon.chain.eventbus.EventBus;
import org.ethereum.beacon.chain.eventbus.events.ProposerStateYielded;
import org.ethereum.beacon.chain.observer.NaivePendingOperations;
import org.ethereum.beacon.chain.observer.ObservableBeaconState;
import org.ethereum.beacon.chain.processor.attestation.AttestationHelper;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.SlotNumber;

public class AttestationPoolImpl implements AttestationPool {

  private static final EpochNumber HISTORIC_EPOCHS = EpochNumber.of(1);

  private final EventBus eventBus;
  private final BeaconChainSpec spec;
  private final TreeMap<EpochNumber, Set<Attestation>> epochs = new TreeMap<>();

  private EpochNumber currentEpoch;

  public AttestationPoolImpl(EventBus eventBus, BeaconChainSpec spec) {
    this.eventBus = eventBus;
    this.spec = spec;
  }

  @Override
  public void onTick(SlotNumber slot) {
    if (currentEpoch != null) {
      EpochNumber newEpoch = spec.compute_epoch_at_slot(slot);
      if (newEpoch.greater(currentEpoch)) {
        currentEpoch = newEpoch;
        EpochNumber threshold = historyThreshold(currentEpoch);
        epochs.keySet().removeAll(epochs.headMap(threshold).keySet());
      }
    } else {
      currentEpoch = spec.compute_epoch_at_slot(slot);
    }
  }

  @Override
  public void onAttestation(Attestation attestation) {
    if (currentEpoch == null) {
      return;
    }

    EpochNumber threshold = historyThreshold(currentEpoch);
    EpochNumber targetEpoch = attestation.getData().getTarget().getEpoch();
    if (targetEpoch.greaterEqual(threshold)) {
      Set<Attestation> attestations = epochs.computeIfAbsent(targetEpoch, epoch -> new HashSet<>());
      attestations.add(attestation);
    }
  }

  @Override
  public void onStateAtTheBeginningOfSlot(ObservableBeaconState stateAtTheTip) {
    BeaconState state = stateAtTheTip.getLatestSlotState();
    List<Attestation> offChainAttestations =
        getChurn().stream()
            // sort out on chain attestations
            .filter(
                attestation -> {
                  BitSet onChainBits =
                      AttestationHelper.getOnChainBits(state, attestation.getData());
                  BitSet complement = BitSet.valueOf(onChainBits.toLongArray());
                  complement.or(attestation.getAggregationBits().toBitSet());
                  return !onChainBits.equals(complement);
                })
            // sort out attestations not applicable to provided state
            .filter(attestation -> spec.verify_attestation(state, attestation))
            .collect(Collectors.toList());

    ObservableBeaconState withAttestations =
        new ObservableBeaconState(
            stateAtTheTip.getHead(),
            stateAtTheTip.getLatestSlotState(),
            new NaivePendingOperations(offChainAttestations));

    eventBus.publish(ProposerStateYielded.wrap(withAttestations));
  }

  Set<Attestation> getChurn() {
    return epochs.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
  }

  private EpochNumber historyThreshold(EpochNumber currentEpoch) {
    if (currentEpoch.less(HISTORIC_EPOCHS)) {
      return EpochNumber.ZERO;
    }
    return currentEpoch.minus(HISTORIC_EPOCHS);
  }
}