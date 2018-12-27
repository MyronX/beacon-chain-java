package tech.pegasys.artemis.util.bytes;

import static com.google.common.base.Preconditions.checkArgument;

public class ArrayWrappingBytes96 extends ArrayWrappingBytesValue implements Bytes96 {

  ArrayWrappingBytes96(byte[] bytes) {
    this(checkLength(bytes), 0);
  }

  ArrayWrappingBytes96(byte[] bytes, int offset) {
    super(checkLength(bytes, offset), offset, SIZE);
  }

  // Ensures a proper error message.
  private static byte[] checkLength(byte[] bytes) {
    checkArgument(bytes.length == SIZE, "Expected %s bytes but got %s", SIZE, bytes.length);
    return bytes;
  }

  // Ensures a proper error message.
  private static byte[] checkLength(byte[] bytes, int offset) {
    checkArgument(
        bytes.length - offset >= SIZE,
        "Expected at least %s bytes from offset %s but got only %s",
        SIZE,
        offset,
        bytes.length - offset);
    return bytes;
  }

  @Override
  public Bytes96 copy() {
    // Because MutableArrayWrappingBytesValue overrides this, we know we are immutable. We may
    // retain more than necessary however.
    if (offset == 0 && length == bytes.length) return this;

    return new ArrayWrappingBytes96(arrayCopy());
  }

  @Override
  public MutableBytes96 mutableCopy() {
    return new MutableArrayWrappingBytes96(arrayCopy());
  }
}
