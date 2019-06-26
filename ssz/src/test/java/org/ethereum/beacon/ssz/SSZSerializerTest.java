package org.ethereum.beacon.ssz;

import static org.junit.Assert.assertEquals;

import com.google.common.base.Objects;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.ethereum.beacon.crypto.Hashes;
import org.ethereum.beacon.ssz.access.basic.UIntCodec;
import org.ethereum.beacon.ssz.access.container.SSZAnnotationSchemeBuilder;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.creator.CompositeObjCreator;
import org.ethereum.beacon.ssz.creator.ConstructorObjCreator;
import org.ethereum.beacon.ssz.fixtures.AttestationRecord;
import org.ethereum.beacon.ssz.fixtures.Bitfield;
import org.ethereum.beacon.ssz.fixtures.Sign;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.collections.ReadUnion.Null;
import tech.pegasys.artemis.util.collections.UnionImpl;
import tech.pegasys.artemis.util.collections.WriteUnion;
import tech.pegasys.artemis.util.uint.UInt64;

/** Tests of {@link SSZSerializer} */
public class SSZSerializerTest {
  private static byte[] DEFAULT_HASH =
      Hashes.sha256(BytesValue.fromHexString("aa")).getArrayUnsafe();
  private static Sign.Signature DEFAULT_SIG = new Sign.Signature();

  static {
    SecureRandom random = new SecureRandom();
    byte[] r = new byte[20];
    random.nextBytes(r);
    DEFAULT_SIG.r = new BigInteger(1, r);
    byte[] s = new byte[20];
    random.nextBytes(s);
    DEFAULT_SIG.s = new BigInteger(1, s);
  }

  private SSZSerializer sszSerializer;

  @Before
  public void setup() {
    sszSerializer = new SSZBuilder().withExplicitAnnotations(false).buildSerializer();
  }

  @Test
  public void bitfieldTest() {
    Bitfield expected = new Bitfield(BytesValue.fromHexString("abcd").getArrayUnsafe());

    byte[] encoded = sszSerializer.encode(expected);
    Bitfield constructed = (Bitfield) sszSerializer.decode(encoded, Bitfield.class);

    assertEquals(expected, constructed);
  }

  @Test
  public void SignatureTest() {
    Sign.Signature signature = new Sign.Signature();
    signature.r = new BigInteger("23452342342342342342342315643768758756967967");
    signature.s = new BigInteger("8713785871");

    byte[] encoded = sszSerializer.encode(signature);
    Sign.Signature constructed =
        sszSerializer.decode(encoded, Sign.Signature.class);

    assertEquals(signature, constructed);
  }

  @Test
  public void simpleTest() {
    List<byte[]> obliqueParentHashes = new ArrayList<>();
    obliqueParentHashes.add(new byte[] {0x12, 0x34, 0x56});
    obliqueParentHashes.add(new byte[] {0x55, 0x66, 0x77});
    AttestationRecord expected =
        new AttestationRecord(
            123,
            obliqueParentHashes,
            DEFAULT_HASH,
            new Bitfield(BytesValue.fromHexString("abcdef45").getArrayUnsafe()),
            DEFAULT_HASH,
            12412L,
            12400L,
            DEFAULT_SIG);

    byte[] encoded = sszSerializer.encode(expected);
    AttestationRecord constructed =
        sszSerializer.decode(encoded, AttestationRecord.class);

    assertEquals(expected, constructed);
  }

  @Test
  public void explicitAnnotationsAndLoggerTest() {
    SSZBuilder builder = new SSZBuilder(); //
    builder
        .withSSZSchemeBuilder(new SSZAnnotationSchemeBuilder().withLogger(Logger.getLogger("test")))
//        .withSSZCodecResolver(new SSZCodecRoulette())
        .withObjectCreator(new CompositeObjCreator(new ConstructorObjCreator()));
    builder.addDefaultBasicCodecs();
    SSZSerializer serializer = builder.buildSerializer();

    AttestationRecord expected =
        new AttestationRecord(
            123,
            Collections.emptyList(),
            DEFAULT_HASH,
            new Bitfield(BytesValue.fromHexString("abcdef45").getArrayUnsafe()),
            DEFAULT_HASH,
            12412L,
            12400L,
            DEFAULT_SIG);

    byte[] encoded = serializer.encode(expected);
    AttestationRecord constructed =
        serializer.decode(encoded, AttestationRecord.class);

    Assert.assertNotEquals(expected, constructed);

    assertEquals(expected.getShardId(), constructed.getShardId());
    assertEquals(expected.getObliqueParentHashes(), constructed.getObliqueParentHashes());
    Assert.assertArrayEquals(expected.getShardBlockHash(), constructed.getShardBlockHash());
    Assert.assertNull(constructed.getAggregateSig());
  }

  @Test(expected = NullPointerException.class)
  public void nullFixedSizeFieldTest() {
    AttestationRecord expected3 =
        new AttestationRecord(
            123,
            Collections.emptyList(),
            null,
            new Bitfield(BytesValue.fromHexString("abcdef45").getArrayUnsafe()),
            null,
            12412L,
            12400L,
            DEFAULT_SIG);
    sszSerializer.encode(expected3);
  }

  @Test(expected = NullPointerException.class)
  public void nullListTest() {
    AttestationRecord expected4 =
        new AttestationRecord(
            123,
            null,
            DEFAULT_HASH,
            new Bitfield(BytesValue.fromHexString("abcdef45").getArrayUnsafe()),
            DEFAULT_HASH,
            12412L,
            12400L,
            DEFAULT_SIG);
    sszSerializer.encode(expected4);
  }

  @SSZSerializable
  public static class ListsObject {
    private final List<Integer> list1;
    private final List<String> list2;

    public ListsObject(List<Integer> list1, List<String> list2) {
      this.list1 = list1;
      this.list2 = list2;
    }

    public List<Integer> getList1() {
      return list1;
    }

    public List<String> getList2() {
      return list2;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ListsObject that = (ListsObject) o;
      return Objects.equal(list1, that.list1) &&
          Objects.equal(list2, that.list2);
    }
  }

  @Test
  public void shouldHandleLists() {
    List<Integer> list1 = new ArrayList<>();
    list1.add(1);
    list1.add(2);
    List<String> list2 = new ArrayList<>();
    list2.add("aa");
    ListsObject expected = new ListsObject(list1, list2);
    byte[] encoded = sszSerializer.encode(expected);
    ListsObject actual = sszSerializer.decode(encoded, ListsObject.class);

    assertEquals(expected, actual);
  }

  /** Checks that we could handle list placed inside another list */
  @Test
  public void shouldHandleListList() {
    List<String> list1 = new ArrayList<>();
    list1.add("aa");
    list1.add("bb");
    List<String> list2 = new ArrayList<>();
    list2.add("cc");
    list2.add("dd");
    List<List<String>> listOfLists = new ArrayList<>();
    listOfLists.add(list1);
    listOfLists.add(list2);
    listOfLists.add(new ArrayList<>());
    ListListObject expected = new ListListObject(listOfLists);
    byte[] encoded = sszSerializer.encode(expected);
    ListListObject actual = sszSerializer.decode(encoded, ListListObject.class);

    assertEquals(expected, actual);
  }

  @SSZSerializable
  public static class SomeObject {
    private final String name;

    @SSZ(type = "uint8")
    private final int number;

    @SSZ(type = "uint256")
    private final BigInteger longNumber;

    public SomeObject(String name, int number, BigInteger longNumber) {
      this.name = name;
      this.number = number;
      this.longNumber = longNumber;
    }

    public String getName() {
      return name;
    }

    public int getNumber() {
      return number;
    }

    public BigInteger getLongNumber() {
      return longNumber;
    }
  }

  @SSZSerializable
  public static class ListListObject {
    private final List<List<String>> names;

    public ListListObject(List<List<String>> names) {
      this.names = names;
    }

    public List<List<String>> getNames() {
      return names;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ListListObject that = (ListListObject) o;
      return names.equals(that.names);
    }
  }

  @SSZSerializable(serializeAs = UInt64.class)
  public static class Child extends UInt64 {
    public Child(UInt64 b) {
      super(b);
    }
  }

  @SSZSerializable
  public static class Wrapper {
    @SSZ
    public Child c1;

    @SSZ
    public List<Child> c2;

    @SSZ
    public Child[] c3;

    public Wrapper(Child c1, List<Child> c2, Child[] c3) {
      this.c1 = c1;
      this.c2 = c2;
      this.c3 = c3;
    }

    @Override
    public boolean equals(Object o) {
      Wrapper wrapper = (Wrapper) o;
      if (c1 != null ? !c1.equals(wrapper.c1) : wrapper.c1 != null) {return false;}
      if (c2 != null ? !c2.equals(wrapper.c2) : wrapper.c2 != null) {return false;}
      return Arrays.equals(c3, wrapper.c3);
    }
  }

  @Test
  public void serializeAsTest1() {
    Wrapper w = new Wrapper(
        new Child(UInt64.valueOf(1)),
        Arrays.asList(
            new Child(UInt64.valueOf(2)),
            new Child(UInt64.valueOf(3))
        ),
        new Child[] {
            new Child(UInt64.valueOf(4)),
            new Child(UInt64.valueOf(5))
        });

    SSZSerializer ssz = new SSZBuilder()
        .addBasicCodecs(new UIntCodec())
        .buildSerializer();

    byte[] bytes = ssz.encode(w);

    Wrapper w1 = ssz.decode(bytes, Wrapper.class);

    Assert.assertEquals(w, w1);
  }

  @SSZSerializable
  public static class Base1 {
    @SSZ
    public int a;
    @SSZ
    public String b;

    public Base1(int a, String b) {
      this.a = a;
      this.b = b;
    }

    @Override
    public boolean equals(Object o) {
      Base1 base = (Base1) o;
      if (a != base.a) {return false;}
      return b != null ? b.equals(base.b) : base.b == null;
    }
  }

  @SSZSerializable(serializeAs = UInt64.class)
  public static class Child1 extends Base1 {
    public Child1(Base1 b) {
      super(b.a, b.b);
    }
  }

  @SSZSerializable
  public static class Wrapper1 {
    @SSZ
    public Child1 c1;

    @SSZ
    public List<Child1> c2;

    @SSZ
    public Child1[] c3;

    @Override
    public boolean equals(Object o) {
      Wrapper1 wrapper = (Wrapper1) o;
      if (c1 != null ? !c1.equals(wrapper.c1) : wrapper.c1 != null) {return false;}
      if (c2 != null ? !c2.equals(wrapper.c2) : wrapper.c2 != null) {return false;}
      return Arrays.equals(c3, wrapper.c3);
    }
  }


  @Ignore
  @Test
  public void serializeAsTest2() {
    Wrapper1 w = new Wrapper1();
    w.c1 = new Child1(new Base1(1, "a"));
    w.c2 = Arrays.asList(
            new Child1(new Base1(2, "b")),
            new Child1(new Base1(3, "c"))
          );
    w.c3 = new Child1[] {
            new Child1(new Base1(4, "d")),
            new Child1(new Base1(5, "e"))
          };

    SSZSerializer ssz = sszSerializer;

    byte[] bytes = ssz.encode(w);

    Wrapper1 w1 = ssz.decode(bytes, Wrapper1.class);

    Assert.assertEquals(w, w1);
  }

  @SSZSerializable
  public static class Union1 extends UnionImpl {

    public Union1() {
    }

    public Union1(UInt64 intMember) {
      setValue(1, intMember);
    }

    @SSZ
    private Null ignore;

    @SSZ
    private UInt64 intMember;
  }

  @Test
  public void testUnion1() {
    Union1 union1 = new Union1();
    union1.setValue(0, null);

    byte[] bytes1 = sszSerializer.encode(union1);
    Assert.assertArrayEquals(new byte[4], bytes1);
    Union1 decode1 = sszSerializer.decode(bytes1, Union1.class);
    Assert.assertEquals(union1, decode1);

    union1.setValue(1, UInt64.ZERO);
    BytesValue bytes2 = sszSerializer.encode2(union1);
    Assert.assertEquals(BytesValue.fromHexString("010000000000000000000000"), bytes2);
    Union1 decode2 = sszSerializer.decode(bytes2, Union1.class);
    Assert.assertEquals(union1, decode2);
  }

  @SSZSerializable
  public static class SafeUnion extends UnionImpl {

    public SafeUnion() {
      setValue(0, null);
    }

    public SafeUnion(UInt64 intMember) {
      setValue(1, intMember);
    }

    @SSZ(order = 1)
    public Null getNull() {
      throw new RuntimeException("Shouldn't be called");
    }

    @SSZ(order = 2)
    public UInt64 getIntMember() {
      return getValueSafe(1);
    }
  }

  @Test
  public void testUnionSafe() {
    SSZSerializer sszSerializer = new SSZBuilder().withExplicitAnnotations(true).buildSerializer();
    SafeUnion union1 = new SafeUnion();

    byte[] bytes1 = sszSerializer.encode(union1);
    Assert.assertArrayEquals(new byte[4], bytes1);
    SafeUnion decode1 = sszSerializer.decode(bytes1, SafeUnion.class);
    Assert.assertEquals(union1, decode1);

    union1 = new SafeUnion(UInt64.ZERO);
    BytesValue bytes2 = sszSerializer.encode2(union1);
    Assert.assertEquals(BytesValue.fromHexString("010000000000000000000000"), bytes2);
    SafeUnion decode2 = sszSerializer.decode(bytes2, SafeUnion.class);
    Assert.assertEquals(union1, decode2);
  }

  @SSZSerializable
  public static class Union2 extends UnionImpl {

    @SSZ
    private Null ignore;

    @SSZ
    private Union1 union1;

    @SSZ
    private List<Union1> unionList;
  }

  @Test
  public void testUnion2() {
    Union2 union1 = new Union2();

    {
      union1.setValue(0, null);
      byte[] bytes1 = sszSerializer.encode(union1);
      Assert.assertArrayEquals(new byte[4], bytes1);
      Union2 decode1 = sszSerializer.decode(bytes1, Union2.class);
      Assert.assertEquals(union1, decode1);
    }
    {
      union1.setValue(1, new Union1());
      byte[] bytes1 = sszSerializer.encode(union1);
      Union2 decode1 = sszSerializer.decode(bytes1, Union2.class);
      Assert.assertEquals(union1, decode1);
    }
    {
      union1.setValue(2, Collections.emptyList());
      byte[] bytes1 = sszSerializer.encode(union1);
      Union2 decode1 = sszSerializer.decode(bytes1, Union2.class);
      Assert.assertEquals(union1, decode1);
    }
    {
      union1.setValue(2, Arrays
          .asList(
              new Union1(), new Union1(UInt64.valueOf(0x1122334455667788L)), new Union1()));
      byte[] bytes1 = sszSerializer.encode(union1);
      Union2 decode1 = sszSerializer.decode(bytes1, Union2.class);
      Assert.assertEquals(union1, decode1);
    }
  }

  @SSZSerializable
  public static class AnonymousUnionContainer {

    @SSZ
    public List<Integer> l1;

    @SSZ
    public WriteUnion.U3<Null, UInt64, List<Integer>> union = WriteUnion.U3.create();

    @SSZ
    public int i1;

    @Override
    public boolean equals(Object o) {
      AnonymousUnionContainer that = (AnonymousUnionContainer) o;
      if (i1 != that.i1) {
        return false;
      }
      if (l1 != null ? !l1.equals(that.l1) : that.l1 != null) {
        return false;
      }
      return union != null ? union.equals(that.union) : that.union == null;
    }
  }

  @Test
  public void testAnonymousUnion1() {
    AnonymousUnionContainer c = new AnonymousUnionContainer();
    c.union.setMember2(UInt64.valueOf(0x1234));
    c.i1 = 0x8989898;
    c.l1 = Arrays.asList(11, 22, 33);
    {
      byte[] bytes1 = sszSerializer.encode(c);
      AnonymousUnionContainer decode1 = sszSerializer.decode(bytes1, AnonymousUnionContainer.class);
      Assert.assertEquals(c, decode1);
    }
    {
      c.union.setMember3(Arrays.asList(11, 22, 33));
      byte[] bytes1 = sszSerializer.encode(c);
      AnonymousUnionContainer decode1 = sszSerializer.decode(bytes1, AnonymousUnionContainer.class);
      Assert.assertEquals(c, decode1);
    }
    {
      c.union.setMember1(null);
      byte[] bytes1 = sszSerializer.encode(c);
      AnonymousUnionContainer decode1 = sszSerializer.decode(bytes1, AnonymousUnionContainer.class);
      Assert.assertEquals(c, decode1);
    }
  }
}