package tech.pegasys.artemis.util.bytes;

import static com.google.common.base.Preconditions.checkArgument;

import java.security.MessageDigest;

public interface MutableBytes96 extends MutableBytesValue, Bytes96 {

  /**
   * Wraps a 48 bytes array as a mutable 48 bytes value.
   *
   * <p>This method behave exactly as {@link Bytes96#wrap(byte[])} except that the result is a
   * mutable.
   *
   * @param value The value to wrap.
   * @return A {@link MutableBytes96} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.length != 48}.
   */
  static MutableBytes96 wrap(byte[] value) {
    return new MutableArrayWrappingBytes96(value);
  }

  /**
   * Creates a new mutable 48 bytes value.
   *
   * @return A newly allocated {@link MutableBytesValue}.
   */
  static MutableBytes96 create() {
    return new MutableArrayWrappingBytes96(new byte[SIZE]);
  }

  /**
   * Wraps an existing {@link MutableBytesValue} of size 48 as a mutable 48 bytes value.
   *
   * <p>This method does no copy the provided bytes and so any mutation on {@code value} will also
   * be reflected in the value returned by this method. If a copy is desirable, this can be simply
   * achieved with calling {@link BytesValue#copyTo(MutableBytesValue)} with a newly created {@link
   * MutableBytes96} as destination to the copy.
   *
   * @param value The value to wrap.
   * @return A {@link MutableBytes96} wrapping {@code value}.
   * @throws IllegalArgumentException if {@code value.size() != 48}.
   */
  static MutableBytes96 wrap(MutableBytesValue value) {
    checkArgument(value.size() == SIZE, "Expected %s bytes but got %s", SIZE, value.size());
    return new MutableBytes96() {
      @Override
      public void set(int i, byte b) {
        value.set(i, b);
      }

      @Override
      public MutableBytesValue mutableSlice(int i, int length) {
        return value.mutableSlice(i, length);
      }

      @Override
      public byte get(int i) {
        return value.get(i);
      }

      @Override
      public BytesValue slice(int index) {
        return value.slice(index);
      }

      @Override
      public BytesValue slice(int index, int length) {
        return value.slice(index, length);
      }

      @Override
      public Bytes96 copy() {
        return Bytes96.wrap(value.extractArray());
      }

      @Override
      public MutableBytes96 mutableCopy() {
        return MutableBytes96.wrap(value.extractArray());
      }

      @Override
      public void copyTo(MutableBytesValue destination) {
        value.copyTo(destination);
      }

      @Override
      public void copyTo(MutableBytesValue destination, int destinationOffset) {
        value.copyTo(destination, destinationOffset);
      }

      @Override
      public int commonPrefixLength(BytesValue other) {
        return value.commonPrefixLength(other);
      }

      @Override
      public BytesValue commonPrefix(BytesValue other) {
        return value.commonPrefix(other);
      }

      @Override
      public void update(MessageDigest digest) {
        value.update(digest);
      }

      @Override
      public boolean isZero() {
        return value.isZero();
      }

      @Override
      public boolean equals(Object other) {
        return value.equals(other);
      }

      @Override
      public int hashCode() {
        return value.hashCode();
      }

      @Override
      public String toString() {
        return value.toString();
      }
    };
  }
}
