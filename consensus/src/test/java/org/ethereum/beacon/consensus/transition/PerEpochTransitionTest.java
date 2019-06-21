package org.ethereum.beacon.consensus.transition;

import java.util.List;
import java.util.Random;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.ChainStart;
import org.ethereum.beacon.consensus.TestUtils;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.junit.Assert;
import org.junit.Test;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

public class PerEpochTransitionTest {

  @Test
  public void test1() {
    Random rnd = new Random();
    Time genesisTime = Time.castFrom(UInt64.random(rnd));
    SpecConstants specConstants =
        new SpecConstants() {
          @Override
          public SlotNumber.EpochLength getSlotsPerEpoch() {
            return new SlotNumber.EpochLength(UInt64.valueOf(8));
          }
        };

    BeaconChainSpec spec = BeaconChainSpec.createWithDefaultHasher(specConstants);

    List<Deposit> deposits = TestUtils.getAnyDeposits(rnd, spec, 8).getValue0();
    Eth1Data eth1Data = new Eth1Data(Hash32.random(rnd), UInt64.valueOf(deposits.size()), Hash32.random(rnd));
    InitialStateTransition initialStateTransition =
        new InitialStateTransition(new ChainStart(genesisTime, eth1Data, deposits), spec);

    BeaconStateEx initialState = initialStateTransition.apply(spec.get_empty_block());
    BeaconStateEx currentState = initialState;
    ExtendedSlotTransition extendedSlotTransition = ExtendedSlotTransition.create(spec);
    for (int i = 0; i < 2 * spec.getConstants().getSlotsPerEpoch().getIntValue(); i++) {
      currentState = extendedSlotTransition.apply(currentState);
    }

    // check validators penalized for inactivity
    for (int i = 0; i < deposits.size(); i++) {
      Gwei balanceBefore =
          initialState.getBalances().get(ValidatorIndex.of(i));
      Gwei balanceAfter =
          currentState.getBalances().get(ValidatorIndex.of(i));
      Assert.assertTrue(balanceAfter.less(balanceBefore));
    }
  }
}
