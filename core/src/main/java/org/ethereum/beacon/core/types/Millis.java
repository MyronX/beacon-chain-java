package org.ethereum.beacon.core.types;

import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Time in milliseconds.
 *
 * @see Time
 */
@SSZSerializable(serializeAs = UInt64.class)
public class Millis extends UInt64 implements SafeComparable<Time> {

  public static final Millis ZERO = of(0);

  public Millis(UInt64 uint) {
    super(uint);
  }

  public static Millis of(long millis) {
    return new Millis(UInt64.valueOf(millis));
  }

  public static Millis castFrom(UInt64 time) {
    return new Millis(time);
  }

  public Millis plus(Millis addend) {
    return new Millis(super.plus(addend));
  }

  public Millis minus(Millis subtrahend) {
    return new Millis(super.minus(subtrahend));
  }

  @Override
  public Millis times(UInt64 unsignedMultiplier) {
    return new Millis(super.times(unsignedMultiplier));
  }

  public Millis times(int times) {
    return new Millis(super.times(times));
  }

  @Override
  public Millis dividedBy(UInt64 divisor) {
    return new Millis(super.dividedBy(divisor));
  }

  @Override
  public Millis dividedBy(long divisor) {
    return new Millis(super.dividedBy(divisor));
  }

  public Time getSeconds() {
    return Time.of(getValue() / 1000);
  }
}
