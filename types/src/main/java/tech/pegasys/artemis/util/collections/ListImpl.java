package tech.pegasys.artemis.util.collections;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

class ListImpl<IndexType extends Number, ValueType> implements WriteList<IndexType, ValueType> {

  private final List<ValueType> backedList;
  private final Function<Integer, IndexType> indexConverter;
  private final boolean vector;
  private final long maxSize;

  private ListImpl(
      Collection<ValueType> source, Function<Integer, IndexType> indexConverter, boolean vector, long maxSize) {
    this.backedList = new ArrayList<>(source);
    this.indexConverter = indexConverter;
    this.vector = vector;
    this.maxSize = maxSize;
  }

  ListImpl(Function<Integer, IndexType> indexConverter, boolean vector, long maxSize) {
    this(new ArrayList<>(), indexConverter, vector, maxSize);
  }

  static <IndexType extends Number, ValueType> WriteList<IndexType, ValueType> wrap(
      List<ValueType> backedList, Function<Integer, IndexType> indexConverter, boolean vector) {
    return new ListImpl<>(backedList, indexConverter, vector, -1);
  }

  static <IndexType extends Number, ValueType> WriteList<IndexType, ValueType> wrap(
      List<ValueType> backedList, Function<Integer, IndexType> indexConverter, long maxSize) {
    return new ListImpl<>(backedList, indexConverter, false, maxSize);
  }

  @Override
  public IndexType size() {
    return indexConverter.apply(backedList.size());
  }

  @Override
  public ValueType get(IndexType index) {
    return backedList.get(index.intValue());
  }

  @Override
  public ListImpl<IndexType, ValueType> subList(IndexType fromIndex, IndexType toIndex) {
    return new ListImpl<>(
        backedList.subList(fromIndex.intValue(), toIndex.intValue()), indexConverter, vector, maxSize);
  }

  @Override
  public WriteList<IndexType, ValueType> createMutableCopy() {
    return new ListImpl<>(backedList, indexConverter, vector, maxSize);
  }

  @Override
  public ReadList<IndexType, ValueType> cappedCopy(long maxSize) {
    assert !isVector();
    return new ListImpl<>(backedList, indexConverter, vector, maxSize);
  }

  @NotNull
  @Override
  public Iterator<ValueType> iterator() {
    return backedList.iterator();
  }

  @Override
  public Stream<ValueType> stream() {
    return backedList.stream();
  }

  @Override
  public boolean add(ValueType valueType) {
    ensureCouldAddOne(valueType);
    return backedList.add(valueType);
  }

  private void ensureCouldAddOne(ValueType value) {
    if (maxSize() != VARIABLE_SIZE && backedList.size() == maxSize()) {
      throw new MaxSizeOverflowException(
          String.format(
              "Cannot add element %s, maxSize of %s is filled with elements", value, maxSize()));
    }
  }

  @Override
  public boolean remove(ValueType o) {
    return backedList.remove(o);
  }

  @Override
  public boolean addAll(@NotNull Iterable<? extends ValueType> c) {
    if (c instanceof Collection) {
      ensureCouldAddMany((Collection) c);
      return backedList.addAll((Collection<? extends ValueType>) c);
    } else if (c instanceof ListImpl) {
      ensureCouldAddMany(((ListImpl<?, ? extends ValueType>) c).backedList);
      return backedList.addAll(((ListImpl<?, ? extends ValueType>) c).backedList);
    } else {
      boolean hasAny = false;
      for (ValueType val : c) {
        ensureCouldAddOne(val);
        backedList.add(val);
        hasAny = true;
      }
      return hasAny;
    }
  }

  private void ensureCouldAddMany(Collection c) {
    if (maxSize() != VARIABLE_SIZE && (backedList.size() + c.size()) > maxSize()) {
      throw new MaxSizeOverflowException(
          String.format(
              "Cannot add collection, maxSize of %s will be overflown with elements (Currently: %s)", maxSize(), backedList.size()));
    }
  }

  @Override
  public boolean addAll(IndexType index, @NotNull Iterable<? extends ValueType> c) {
    if (c instanceof Collection) {
      ensureCouldAddMany((Collection) c);
      return backedList.addAll(index.intValue(), (Collection<? extends ValueType>) c);
    } else if (c instanceof ListImpl) {
      ensureCouldAddMany(((ListImpl<?, ? extends ValueType>) c).backedList);
      return backedList.addAll(index.intValue(), ((ListImpl<?, ? extends ValueType>) c).backedList);
    } else {
      int idx = index.intValue();
      for (ValueType val : c) {
        ensureCouldAddOne(val);
        backedList.add(idx++, val);
      }
      return idx > index.intValue();
    }
  }

  @Override
  public void sort(Comparator<? super ValueType> c) {
    backedList.sort(c);
  }

  @Override
  public void clear() {
    backedList.clear();
  }

  @Override
  public List<ValueType> listCopy() {
    return new ArrayList<>(backedList);
  }

  @Override
  public ValueType set(IndexType index, ValueType element) {
    return backedList.set(index.intValue(), element);
  }

  @Override
  public void setAll(ValueType singleValue) {
    for (int i = 0; i < backedList.size(); i++) {
      backedList.set(i, singleValue);
    }
  }

  @Override
  public void setAll(Iterable<ValueType> singleValue) {
    Iterator<ValueType> it = singleValue.iterator();
    int idx = 0;
    while (it.hasNext() && idx < backedList.size()) {
      backedList.set(idx, it.next());
      idx++;
    }
    if (it.hasNext() || idx < backedList.size()) {
      throw new IllegalArgumentException("The sizes of this vector and supplied collection differ");
    }
  }

  @Override
  public void add(IndexType index, ValueType element) {
    ensureCouldAddOne(element);
    backedList.add(index.intValue(), element);
  }

  @Override
  public ValueType remove(IndexType index) {
    return backedList.remove(index.intValue());
  }

  @Override
  public void retainAll(ReadList<IndexType, ValueType> other) {
    backedList.retainAll(other.listCopy());
  }

  @Override
  public ReadList<IndexType, ValueType> createImmutableCopy() {
    return new ListImpl<>(backedList, indexConverter, vector, maxSize);
  }

  @Override
  public boolean isVector() {
    return vector;
  }

  @Override
  public long maxSize() {
    return maxSize;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ListImpl<?, ?> list = (ListImpl<?, ?>) o;
    return vector == list.vector &&
        maxSize == list.maxSize &&
        Objects.equal(backedList, list.backedList);
  }

  @Override
  public int hashCode() {
    return backedList.hashCode();
  }

  public static class MaxSizeOverflowException extends RuntimeException {
    public MaxSizeOverflowException() {
      super();
    }

    public MaxSizeOverflowException(String message) {
      super(message);
    }
  }
}
