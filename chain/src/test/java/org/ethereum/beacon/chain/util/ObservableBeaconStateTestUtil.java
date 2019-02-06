package org.ethereum.beacon.chain.util;

import java.util.Collections;
import java.util.Random;
import org.ethereum.beacon.chain.observer.ObservableBeaconState;
import org.ethereum.beacon.chain.observer.PendingOperations;
import org.ethereum.beacon.consensus.SpecHelpers;
import org.ethereum.beacon.consensus.transition.InitialStateTransition;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconBlocks;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.pow.DepositContract.ChainStart;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

public class ObservableBeaconStateTestUtil {

  public static ObservableBeaconState createInitialState(Random random, SpecHelpers specHelpers) {
    return createInitialState(
        random, specHelpers, PendingOperationsTestUtil.createEmptyPendingOperations());
  }

  public static ObservableBeaconState createInitialState(
      Random random, SpecHelpers specHelpers, PendingOperations operations) {
    BeaconBlock genesis = BeaconBlocks.createGenesis(specHelpers.getChainSpec());
    ChainStart chainStart =
        new ChainStart(
            UInt64.ZERO,
            new Eth1Data(Hash32.random(random), Hash32.random(random)),
            Collections.emptyList());
    InitialStateTransition stateTransition = new InitialStateTransition(chainStart, specHelpers);

    BeaconState state = stateTransition.apply(genesis).getCanonicalState();
    return new ObservableBeaconState(genesis, state, operations);
  }
}
