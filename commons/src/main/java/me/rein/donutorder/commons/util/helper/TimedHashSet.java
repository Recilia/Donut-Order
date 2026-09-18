package me.rein.donutorder.commons.util.helper;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TimedHashSet<E> implements Set<E>, AutoCloseable {

    private final long expiry;
    private final Set<ElementTimed> delegate = ConcurrentHashMap.newKeySet();
    private final Timer timer;

    public TimedHashSet() {
        this(5000L);
    }

    public TimedHashSet(
            long expiry
    ) {
        this.expiry = expiry;

        this.timer = new Timer(true); // daemon thread so we don't prevent jvm shutdown
        timer.schedule(new ElemTimer(), 0L, 500L);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Deprecated(forRemoval = true)
    @Override
    public boolean contains(
            Object o
    ) {
        return delegate.contains(o);
    }

    // this is made so that when we keep getting an element
    // it means it's "hot", so then we don't just remove it after 5 seconds
    // we keep updating until it's not "hot"
    @SuppressWarnings("unchecked")
    public boolean containsAndRefresh(
            Object o
    ) {
        boolean contains = delegate.contains(o);

        if (contains) {
            delegate.remove(o);
            delegate.add(new ElementTimed((E) o));
        }

        return contains;
    }

    @Override
    public @NotNull Iterator<E> iterator() {
        Iterator<ElementTimed> iterator = delegate.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public E next() {
                return iterator.next().getElement();
            }
        };
    }

    @Override
    public @NotNull Object[] toArray() {
        Object[] array = new Object[size()];

        int cnt = 0;
        for (ElementTimed element : delegate)
            array[cnt++] = element.getElement();

        return array;
    }

    @Override
    public @NotNull <T> T[] toArray(
            @NotNull T[] a
    ) {
        T[] array = prepareArray(a);

        int cnt = 0;
        for (ElementTimed element : delegate)
            array[cnt++] = (T) element.getElement();

        return array;
    }

    // java.util.HashMap
    @SuppressWarnings("unchecked")
    final <T> T[] prepareArray(
            T[] a
    ) {
        int size = size();
        if (a.length < size)
            return (T[]) java.lang.reflect.Array
                    .newInstance(a.getClass().getComponentType(), size);

        if (a.length > size)
            a[size] = null;

        return a;
    }

    @Override
    public boolean add(
            E e
    ) {
        return delegate.add(new ElementTimed(e));
    }

    @Override
    public boolean remove(
            Object o
    ) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(
            @NotNull Collection<?> c
    ) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(
            @NotNull Collection<? extends E> c
    ) {
        boolean modified = false;

        for (E e : c)
            if (add(e))
                modified = true;

        return modified;
    }

    @Override
    public boolean retainAll(
            @NotNull Collection<?> c
    ) {
        return delegate.retainAll(c);
    }

    @Override
    public boolean removeAll(
            @NotNull Collection<?> c
    ) {
        return delegate.removeAll(c);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public void close() {
        timer.cancel();
    }

    // even though it's deprecated
    // it's called upon the garbage collection
    // in case we forget to use try-with-resources or just all close
    @Override
    protected void finalize() throws Throwable {
        timer.cancel();
        super.finalize();
    }

    private class ElemTimer extends TimerTask {

        @Override
        public void run() {
            delegate.removeIf(elementTimed ->
                            System.currentTimeMillis() - elementTimed.getTimestamp() > expiry);
        }
    }

    @Getter
    private class ElementTimed {

        private final E element;
        private final long timestamp;

        public ElementTimed(
                E element
        ) {
            this.element = element;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(element);
        }

        @Override
        public boolean equals(
                Object obj
        ) {
            if (obj instanceof TimedHashSet<?>.ElementTimed elementTimed)
                return Objects.equals(element, elementTimed.element);

            return Objects.equals(element, obj);
        }
    }
}
