package org.ethereum.beacon.chain;

import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.core.BeaconBlock;

public class BeaconTuple {

  private final BeaconBlock block;
  private final BeaconStateEx state;

  BeaconTuple(BeaconBlock block, BeaconStateEx state) {
    this.block = block;
    this.state = state;
  }

  public static BeaconTuple of(BeaconBlock block, BeaconStateEx state) {
    return new BeaconTuple(block, state);
  }

  public BeaconBlock getBlock() {
    return block;
  }

  public BeaconStateEx getState() {
    return state;
  }
}
