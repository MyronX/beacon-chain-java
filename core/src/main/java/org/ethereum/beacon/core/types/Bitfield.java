package org.ethereum.beacon.core.types;

import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.uint.UInt64;

@SSZSerializable(serializeAs = UInt64.class)
public class Bitfield extends UInt64 {

  public static final Bitfield ZERO = new Bitfield(UInt64.ZERO);

  public Bitfield(UInt64 uint) {
    super(uint);
  }

  @Override
  public Bitfield shl(int number) {
    return new Bitfield(super.shl(number));
  }

  public Bitfield or(long uint) {
    return new Bitfield(super.or(UInt64.valueOf(uint)));
  }
}
