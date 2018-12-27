package tech.pegasys.artemis.util.bytes;

import static com.google.common.base.Preconditions.checkArgument;

public interface Bytes96 extends BytesValue {
  int SIZE = 96;

  Bytes96 ZERO = wrap(new byte[96]);

  /**
   * Wraps the provided byte array, which must be of length 48, as a {@link Bytes96}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @return A {@link Bytes96} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.length != 48}.
   */
  static Bytes96 wrap(byte[] bytes) {
    checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
    return wrap(bytes, 0);
  }

  /**
   * Wraps a slice/sub-part of the provided array as a {@link Bytes96}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * within the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *     value. In other words, you will have {@code wrap(value, i).get(0) == value[i]}.
   * @return A {@link Bytes96} that exposes the bytes of {@code value} from {@code offset}
   *     (inclusive) to {@code offset + 48} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.length &gt; 0 && offset >=
   *     value.length)}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 48 &gt; value.length}.
   */
  static Bytes96 wrap(byte[] bytes, int offset) {
    return new ArrayWrappingBytes96(bytes, offset);
  }

  /**
   * Wraps a slice/sub-part of the provided value as a {@link Bytes96}.
   *
   * <p>Note that value is not copied, only wrapped, and thus any future update to {@code value}
   * within the wrapped parts will be reflected in the returned value.
   *
   * @param bytes The bytes to wrap.
   * @param offset The index (inclusive) in {@code value} of the first byte exposed by the returned
   *     value. In other words, you will have {@code wrap(value, i).get(0) == value.get(i)}.
   * @return A {@link Bytes96} that exposes the bytes of {@code value} from {@code offset}
   *     (inclusive) to {@code offset + 48} (exclusive).
   * @throws IndexOutOfBoundsException if {@code offset &lt; 0 || (value.size() &gt; 0 && offset >=
   *     value.size())}.
   * @throws IllegalArgumentException if {@code length &lt; 0 || offset + 48 &gt; value.size()}.
   */
  static Bytes96 wrap(BytesValue bytes, int offset) {
    BytesValue slice = bytes.slice(offset, Bytes96.SIZE);
    return slice instanceof Bytes96 ? (Bytes96) slice : new WrappingBytes96(slice);
  }

  /**
   * Left pad a {@link BytesValue} with zero bytes to create a {@link Bytes96}
   *
   * @param value The bytes value pad.
   * @return A {@link Bytes96} that exposes the left-padded bytes of {@code value}.
   * @throws IllegalArgumentException if {@code value.size() &gt; 48}.
   */
  static Bytes96 leftPad(BytesValue value) {
    checkArgument(
        value.size() <= SIZE, "Expected at most %s bytes but got only %s", SIZE, value.size());

    MutableBytes96 bytes = MutableBytes96.create();
    value.copyTo(bytes, SIZE - value.size());
    return bytes;
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes96}.
   *
   * <p>This method is lenient in that {@code str} may of an odd length, in which case it will
   * behave exactly as if it had an additional 0 in front.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *     representation may contain less than 48 bytes, in which case the result is left padded with
   *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation or contains more than 48 bytes.
   */
  static Bytes96 fromHexStringLenient(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, true));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes96}.
   *
   * <p>This method is strict in that {@code str} must of an even length.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x". That
   *     representation may contain less than 48 bytes, in which case the result is left padded with
   *     zeros (see {@link #fromHexStringStrict} if this is not what you want).
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, is of an odd length, or contains more than 48 bytes.
   */
  static Bytes96 fromHexString(String str) {
    return wrap(BytesValues.fromRawHexString(str, SIZE, false));
  }

  /**
   * Parse an hexadecimal string into a {@link Bytes96}.
   *
   * <p>This method is extra strict in that {@code str} must of an even length and the provided
   * representation must have exactly 48 bytes.
   *
   * @param str The hexadecimal string to parse, which may or may not start with "0x".
   * @return The value corresponding to {@code str}.
   * @throws IllegalArgumentException if {@code str} does not correspond to valid hexadecimal
   *     representation, is of an odd length or does not contain exactly 48 bytes.
   */
  static Bytes96 fromHexStringStrict(String str) {
    return wrap(BytesValues.fromRawHexString(str, -1, false));
  }

  @Override
  default int size() {
    return SIZE;
  }

  @Override
  Bytes96 copy();

  @Override
  MutableBytes96 mutableCopy();
}
