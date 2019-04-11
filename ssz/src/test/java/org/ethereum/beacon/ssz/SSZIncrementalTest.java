package org.ethereum.beacon.ssz;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.ethereum.beacon.crypto.Hashes;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.access.list.ReadListAccessor;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.incremental.ObservableComposite;
import org.ethereum.beacon.ssz.incremental.ObservableCompositeHelper;
import org.ethereum.beacon.ssz.incremental.ObservableCompositeHelper.ObsValue;
import org.ethereum.beacon.ssz.incremental.ObservableListImpl;
import org.ethereum.beacon.ssz.incremental.UpdateListener;
import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.TypeResolver;
import org.ethereum.beacon.ssz.visitor.SSZIncrementalHasher;
import org.ethereum.beacon.ssz.visitor.SSZSimpleHasher;
import org.ethereum.beacon.ssz.visitor.SSZSimpleHasher.MerkleTrie;
import org.ethereum.beacon.ssz.visitor.SSZVisitorHost;
import org.junit.Assert;
import org.junit.Test;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.collections.WriteList;

public class SSZIncrementalTest {

  @SSZSerializable
  public static class I1 implements ObservableComposite {
    UpdateListener updateListener;

    @SSZ
    private int a1;
    @SSZ private long a2;
    @SSZ private int a3;

    public I1(int a1, long a2, int a3) {
      this.a1 = a1;
      this.a2 = a2;
      this.a3 = a3;
    }

    @Override
    public UpdateListener getUpdateListener(
        String observerId, Supplier<UpdateListener> listenerFactory) {

      return updateListener != null ? updateListener : (updateListener = listenerFactory.get());
    }

    @Override
    public Map<String, UpdateListener> getAllUpdateListeners() {
      return null;
    }

    public int getA1() {
      return a1;
    }

    public long getA2() {
      return a2;
    }

    public int getA3() {
      return a3;
    }

    public void setA1(int a1) {
      this.a1 = a1;
      updateListener.childUpdated(0);
    }

    public void setA2(long a2) {
      this.a2 = a2;
      updateListener.childUpdated(1);
    }

    public void setA3(int a3) {
      this.a3 = a3;
      updateListener.childUpdated(2);
    }
  }

  @Test
  public void testHashIncremental1() throws Exception {
    class CountingHash implements Function<BytesValue, Hash32> {
      int counter = 0;

      @Override
      public Hash32 apply(BytesValue bytesValue) {
        counter++;
        return Hashes.keccak256(bytesValue);
      }
    }
    SSZBuilder sszBuilder = new SSZBuilder();
    TypeResolver typeResolver = sszBuilder.getTypeResolver();

    SSZVisitorHost visitorHost = new SSZVisitorHost();
    SSZSerializer serializer = new SSZSerializer(visitorHost, typeResolver);
    CountingHash countingHashSimp = new CountingHash();
    CountingHash countingHashInc = new CountingHash();
    SSZIncrementalHasher incrementalHasher = new SSZIncrementalHasher(serializer, countingHashInc, 32);
    SSZSimpleHasher simpleHasher = new SSZSimpleHasher(serializer, countingHashSimp, 32);

    I1 i1 = new I1(0x1111, 0x2222, 0x3333);

    {
      MerkleTrie mt0 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, simpleHasher);
      MerkleTrie mt1 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, incrementalHasher);
      Assert.assertEquals(mt0.getFinalRoot(), mt1.getFinalRoot());
    }

    i1.setA1(0x4444);

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, simpleHasher);
      MerkleTrie mt3 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);

      countingHashInc.counter = 0;
      MerkleTrie mt4 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt4.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter  == 0);
    }

    i1.setA2(0x5555);

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, simpleHasher);
      MerkleTrie mt3 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }

    i1.setA3(0x5555);

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, simpleHasher);
      MerkleTrie mt3 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }

    i1.setA1(0x6666);
    i1.setA2(0x7777);

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, simpleHasher);
      MerkleTrie mt3 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }

    i1.setA1(0xaaaa);
    i1.setA2(0xbbbb);
    i1.setA3(0xcccc);

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, simpleHasher);
      MerkleTrie mt3 = visitorHost
          .handleAny(typeResolver.resolveSSZType(I1.class), i1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter == countingHashSimp.counter);
    }
  }


  @SSZSerializable
  public static class A1 {
    @SSZ public int a1;

    public A1(int a1) {
      this.a1 = a1;
    }
  }

  @Test
  public void testReadList() {
    class CountingHash implements Function<BytesValue, Hash32> {
      int counter = 0;

      @Override
      public Hash32 apply(BytesValue bytesValue) {
        counter++;
        return Hashes.keccak256(bytesValue);
      }
    }
    SSZBuilder sszBuilder = new SSZBuilder()
        .addDefaultListAccessors()
        .addListAccessors(new ReadListAccessor());
    TypeResolver typeResolver = sszBuilder.getTypeResolver();

    SSZVisitorHost visitorHost = new SSZVisitorHost();
    SSZSerializer serializer = new SSZSerializer(visitorHost, typeResolver);
    CountingHash countingHashSimp = new CountingHash();
    CountingHash countingHashInc = new CountingHash();
    SSZIncrementalHasher incrementalHasher = new SSZIncrementalHasher(serializer, countingHashInc, 32);
    SSZSimpleHasher simpleHasher = new SSZSimpleHasher(serializer, countingHashSimp, 32);

    WriteList<Integer, A1> list1 = new ObservableListImpl<>(WriteList.create(Integer::valueOf));
    list1.add(new A1(0x1111));
    list1.add(new A1(0x2222));
    list1.add(new A1(0x3333));

    ReadList<Integer, A1> list2 = list1.createImmutableCopy();

    SSZType sszListType = typeResolver.resolveSSZType(SSZField.resolveFromValue(list2));

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost.handleAny(sszListType, list2, simpleHasher);
      MerkleTrie mt3 = visitorHost.handleAny(sszListType, list2, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter == countingHashSimp.counter);
    }

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost.handleAny(sszListType, list2, simpleHasher);
      MerkleTrie mt3 = visitorHost.handleAny(sszListType, list2, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter == 0);
    }

    WriteList<Integer, A1> list3 = list2.createMutableCopy();
    list3.add(new A1(0x4444));
    ReadList<Integer, A1> list4 = list3.createImmutableCopy();

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost.handleAny(sszListType, list4, simpleHasher);
      MerkleTrie mt3 = visitorHost.handleAny(sszListType, list4, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }
  }

  @Test
  public void testReadListBranching() {
    class CountingHash implements Function<BytesValue, Hash32> {
      int counter = 0;

      @Override
      public Hash32 apply(BytesValue bytesValue) {
        counter++;
        return Hashes.keccak256(bytesValue);
      }
    }
    CountingHash countingHashSimp = new CountingHash();
    CountingHash countingHashInc = new CountingHash();

    SSZHasher sszHasherSimple = new SSZBuilder()
        .addDefaultListAccessors()
        .addListAccessors(new ReadListAccessor())
        .withIncrementalHasher(false)
        .buildHasher(countingHashSimp);
    SSZHasher sszHasherIncremental = new SSZBuilder()
        .addDefaultListAccessors()
        .addListAccessors(new ReadListAccessor())
        .withIncrementalHasher(true)
        .buildHasher(countingHashInc);


    WriteList<Integer, A1> list1 = new ObservableListImpl<>(WriteList.create(Integer::valueOf));
    list1.add(new A1(0x1111));
    list1.add(new A1(0x2222));
    list1.add(new A1(0x3333));

    ReadList<Integer, A1> list2 = list1.createImmutableCopy();

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      byte[] hashSimple = sszHasherSimple.hash(list2);
      byte[] hashIncremental = sszHasherIncremental.hash(list2);
      Assert.assertArrayEquals(hashSimple, hashIncremental);
      Assert.assertTrue(countingHashInc.counter == countingHashSimp.counter);
    }


    WriteList<Integer, A1> list3_1 = list2.createMutableCopy();
    list3_1.add(new A1(0x4444));
    ReadList<Integer, A1> list4_1 = list3_1.createImmutableCopy();

    WriteList<Integer, A1> list3_2 = list2.createMutableCopy();
    list3_2.set(0, new A1(0x5555));
    ReadList<Integer, A1> list4_2 = list3_2.createImmutableCopy();

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      byte[] hashSimple = sszHasherSimple.hash(list4_1);
      byte[] hashIncremental = sszHasherIncremental.hash(list4_1);
      Assert.assertArrayEquals(hashSimple, hashIncremental);
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }
    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      byte[] hashSimple = sszHasherSimple.hash(list4_2);
      byte[] hashIncremental = sszHasherIncremental.hash(list4_2);
      Assert.assertArrayEquals(hashSimple, hashIncremental);
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }

    WriteList<Integer, A1> list5 = list2.createMutableCopy();
    list3_1.add(new A1(0x4444));

    WriteList<Integer, A1> list6_1 = list5.createMutableCopy();
    list6_1.add(new A1(0x5555));
    WriteList<Integer, A1> list6_2 = list5.createMutableCopy();
    list6_2.set(0, new A1(0x6666));

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      byte[] hashSimple = sszHasherSimple.hash(list6_1);
      byte[] hashIncremental = sszHasherIncremental.hash(list6_1);
      Assert.assertArrayEquals(hashSimple, hashIncremental);
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }
    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      byte[] hashSimple = sszHasherSimple.hash(list6_2);
      byte[] hashIncremental = sszHasherIncremental.hash(list6_2);
      Assert.assertArrayEquals(hashSimple, hashIncremental);
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }
  }

  @SSZSerializable
  public static class SimpleContainer1 {
    @SSZ public int a1;

    public SimpleContainer1(int a1) {
      this.a1 = a1;
    }
  }


  @SSZSerializable
  public interface Container1Ifc extends ObservableComposite {

    @SSZ(order = 0) int getA1();
    @SSZ(order = 1) WriteList<Integer, SimpleContainer1> getL1();
    @SSZ(order = 2) int getA2();

    void setA1(int a1);
    void setL1(WriteList<Integer, SimpleContainer1> l1);
    void setA2(int a2);
  }

  @SSZSerializable
  public static class Container1 implements Container1Ifc {
    ObservableCompositeHelper helper = new ObservableCompositeHelper();

    private ObsValue<Integer> a1 = helper.newValue(0);
    private ObsValue<WriteList<Integer, SimpleContainer1>> l1 =
        helper.newValue(ObservableListImpl.create(Integer::valueOf));
    private ObsValue<Integer> a2 = helper.newValue(0);

    public Container1() {
    }

    public Container1(Container1Ifc c) {
      setA1(c.getA1());
      setA2(c.getA2());
      setL1(c.getL1());
      helper.addAllListeners(c.getAllUpdateListeners());
    }

    @Override
    public int getA1() {
      return a1.get();
    }

    @Override
    public void setA1(int a1) {
      this.a1.set(a1);
    }

    @Override
    public WriteList<Integer, SimpleContainer1> getL1() {
      return l1.get();
    }

    @Override
    public void setL1(
        WriteList<Integer, SimpleContainer1> l1) {
      this.l1.set(l1);
    }

    @Override
    public int getA2() {
      return a2.get();
    }

    @Override
    public void setA2(int a2) {
      this.a2.set(a2);
    }

    @Override
    public UpdateListener getUpdateListener(String observerId,
        Supplier<UpdateListener> listenerFactory) {
      return helper.getUpdateListener(observerId, listenerFactory);
    }

    @Override
    public Map<String, UpdateListener> getAllUpdateListeners() {
      return helper.getAllUpdateListeners();
    }
  }

  @Test
  public void testComplexStruct() {
    class CountingHash implements Function<BytesValue, Hash32> {
      int counter = 0;

      @Override
      public Hash32 apply(BytesValue bytesValue) {
        counter++;
        return Hashes.keccak256(bytesValue);
      }
    }
    SSZBuilder sszBuilder = new SSZBuilder()
        .addDefaultListAccessors()
        .addListAccessors(new ReadListAccessor());
    TypeResolver typeResolver = sszBuilder.getTypeResolver();

    SSZVisitorHost visitorHost = new SSZVisitorHost();
    SSZSerializer serializer = new SSZSerializer(visitorHost, typeResolver);
    CountingHash countingHashSimp = new CountingHash();
    CountingHash countingHashInc = new CountingHash();
    SSZIncrementalHasher incrementalHasher = new SSZIncrementalHasher(serializer, countingHashInc, 32);
    SSZSimpleHasher simpleHasher = new SSZSimpleHasher(serializer, countingHashSimp, 32);

    Container1 c1 = new Container1();
    SSZType sszType = typeResolver.resolveSSZType(Container1.class);
    System.out.println(sszType.dumpHierarchy(""));

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost.handleAny(sszType, c1, simpleHasher);
      MerkleTrie mt3 = visitorHost.handleAny(sszType, c1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter == countingHashSimp.counter);
      countingHashInc.counter = 0;
      MerkleTrie mt4 = visitorHost.handleAny(sszType, c1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt4.getFinalRoot());
      Assert.assertEquals(0, countingHashInc.counter);
    }

    c1.setA1(0x1111);

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost.handleAny(sszType, c1, simpleHasher);
      MerkleTrie mt3 = visitorHost.handleAny(sszType, c1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }

    for(int i = 0; i < 200; i++) {
      c1.getL1().add(new SimpleContainer1(0x2200 + i));
    }

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost.handleAny(sszType, c1, simpleHasher);
      MerkleTrie mt3 = visitorHost.handleAny(sszType, c1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      Assert.assertTrue(countingHashInc.counter < countingHashSimp.counter);
    }

    c1.getL1().update(100, v -> new SimpleContainer1(v.a1 + 1));
    c1.setA2(0x7777);

    {
      countingHashInc.counter = 0;
      countingHashSimp.counter = 0;
      MerkleTrie mt2 = visitorHost.handleAny(sszType, c1, simpleHasher);
      MerkleTrie mt3 = visitorHost.handleAny(sszType, c1, incrementalHasher);
      Assert.assertEquals(mt2.getFinalRoot(), mt3.getFinalRoot());
      System.out.println("Incremental hashes: " + countingHashInc.counter + ", Simple hashes: " + countingHashSimp.counter);
      Assert.assertTrue(countingHashInc.counter * 10 < countingHashSimp.counter);
    }
  }
}
