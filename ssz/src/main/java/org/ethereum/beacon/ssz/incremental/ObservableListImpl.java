package org.ethereum.beacon.ssz.incremental;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.collections.WriteList;

/**
 * {@link WriteList} wrapper supporting {@link ObservableComposite} functionality
 */
public class ObservableListImpl<IndexType extends Number, ValueType>
    implements WriteList<IndexType, ValueType>, ObservableComposite {

  private final WriteList<IndexType, ValueType> delegate;
  private final ObservableCompositeHelper observableHelper;

  public ObservableListImpl(
      WriteList<IndexType, ValueType> delegate) {
    this(delegate,  new ObservableCompositeHelper());
  }

  public ObservableListImpl(
      WriteList<IndexType, ValueType> delegate,
      ObservableCompositeHelper observableHelper) {
    this.delegate = delegate;
    this.observableHelper = observableHelper;
  }

  public static <IndexType1 extends Number, ValueType1> WriteList<IndexType1, ValueType1> wrap(
      List<ValueType1> srcList,
      Function<Integer, IndexType1> indexConverter) {
    return new ObservableListImpl<>(WriteList.wrap(srcList, indexConverter));
  }

  public static <IndexType1 extends Number, ValueType1> WriteList<IndexType1, ValueType1> wrapIfNeeded(
      WriteList<IndexType1, ValueType1> writeList) {
    if (!(writeList instanceof ObservableListImpl)) {
      return new ObservableListImpl<>(writeList);
    }

    return writeList;
  }

  public static <IndexType1 extends Number, ValueType1> WriteList<IndexType1, ValueType1> create(
      Function<Integer, IndexType1> indexConverter) {
    return new ObservableListImpl<>(WriteList.create(indexConverter));
  }

  @Override
  public WriteList<IndexType, ValueType> createMutableCopy() {
    return new ObservableListImpl<>(delegate.createMutableCopy(), observableHelper.fork());
  }

  @Override
  public ReadList<IndexType, ValueType> createImmutableCopy() {
    // TODO dirty hack with cast
    return new ObservableListImpl<>(
        (WriteList<IndexType, ValueType>) delegate.createImmutableCopy(), observableHelper.fork());
  }


  @Override
  public UpdateListener getUpdateListener(String observerId,
      Supplier<UpdateListener> listenerFactory) {
    return observableHelper.getUpdateListener(observerId, listenerFactory);
  }

  @Override
  public Map<String, UpdateListener> getAllUpdateListeners() {
    return observableHelper.getAllUpdateListeners();
  }

  /*******  update methods  ******/

  @Override
  public boolean add(ValueType valueType) {
    int size = size().intValue();
    boolean ret = delegate.add(valueType);
    observableHelper.childUpdated(size);
    return ret;
  }

  @Override
  public boolean remove(ValueType o) {
    boolean ret = delegate.remove(o);
    observableHelper.childrenUpdated(0, size().intValue() + 1);
    return ret;
  }

  @Override
  public boolean addAll(Collection<? extends ValueType> c) {
    int size = size().intValue();
    boolean ret = delegate.addAll(c);
    observableHelper.childrenUpdated(size, c.size());
    return ret;
  }

  @Override
  public boolean addAll(IndexType index, Collection<? extends ValueType> c) {
    boolean ret = delegate.addAll(index, c);
    observableHelper.childrenUpdated(index.intValue(), size().intValue() - index.intValue());
    return ret;
  }

  @Override
  public void sort(Comparator<? super ValueType> c) {
    delegate.sort(c);
    observableHelper.childrenUpdated(0, size().intValue());
  }

  @Override
  public void clear() {
    int size = size().intValue();
    delegate.clear();
    observableHelper.childrenUpdated(0, size);
  }

  @Override
  public ValueType set(IndexType index, ValueType element) {
    ValueType ret = delegate.set(index, element);
    observableHelper.childUpdated(index.intValue());
    return ret;
  }

  @Override
  public void add(IndexType index, ValueType element) {
    delegate.add(index, element);
    observableHelper.childrenUpdated(index.intValue(), size().intValue() - index.intValue());
  }

  @Override
  public ValueType remove(IndexType index) {
    ValueType ret = delegate.remove(index);
    observableHelper.childrenUpdated(index.intValue(), size().intValue() - index.intValue() + 1);
    return ret;
  }

  @Override
  public void retainAll(ReadList<IndexType, ValueType> other) {
    int size = size().intValue();
    delegate.retainAll(other);
    observableHelper.childrenUpdated(0, size);
  }

  @Override
  public ValueType update(IndexType index, Function<ValueType, ValueType> updater) {
    ValueType ret = delegate.update(index, updater);
    observableHelper.childUpdated(index.intValue());
    return ret;
  }

  @Override
  public void remove(Predicate<ValueType> removeFilter) {
    int size = size().intValue();
    delegate.remove(removeFilter);
    observableHelper.childrenUpdated(0, size);
  }

  @Override
  public void setAll(ValueType singleValue) {
    delegate.setAll(singleValue);
    observableHelper.childrenUpdated(0, size().intValue());
  }

  @Override
  public void setAll(Iterable<ValueType> singleValue) {
    delegate.setAll(singleValue);
    observableHelper.childrenUpdated(0, size().intValue());
  }

  /*******  read methods  ******/

  @Override
  public IndexType size() {
    return delegate.size();
  }

  @Override
  public ValueType get(IndexType index) {
    return delegate.get(index);
  }

  @Override
  public ReadList<IndexType, ValueType> subList(IndexType fromIndex, IndexType toIndex) {
    return delegate.subList(fromIndex, toIndex);
  }

  @Override
  public Stream<ValueType> stream() {
    return delegate.stream();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public List<ValueType> listCopy() {
    return delegate.listCopy();
  }

  @Override
  public ReadList<IndexType, ValueType> intersection(
      ReadList<IndexType, ValueType> other) {
    return delegate.intersection(other);
  }

  @Override
  public Iterator<ValueType> iterator() {
    return delegate.iterator();
  }

  @Override
  public void forEach(Consumer<? super ValueType> action) {
    delegate.forEach(action);
  }

  @Override
  public Spliterator<ValueType> spliterator() {
    return delegate.spliterator();
  }

  @Override
  public boolean equals(Object obj) {
    if (getClass().equals(obj.getClass())) {
      return this.delegate.equals(((ObservableListImpl) obj).delegate);
    }

    return delegate.equals(obj);
  }
}
