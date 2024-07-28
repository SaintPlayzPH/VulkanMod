package net.vulkanmod.render.chunk.util;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Consumer;

public class StaticQueue<T> implements Iterable<T> {
    final T[] queue;
    final int capacity;
    int position = 0;
    int limit = 0;

    public StaticQueue() {
        this(1024);
    }

    @SuppressWarnings("unchecked")
    public StaticQueue(int initialCapacity) {
        this.capacity = initialCapacity;

        this.queue = (T[]) (new Object[capacity]);
    }

    public boolean hasNext() {
        return this.position < this.limit;
    }

    public T poll() {
        T t = this.queue[position];
        this.position++;

        return t;
    }

    public void add(T t) {
        this.queue[this.limit++] = t;
    }

    public int size() {
        return this.limit;
    }

    public void clear() {
        this.position = 0;
        this.limit = 0;
    }

    public Iterator<T> iterator(boolean reverseOrder) {
        return reverseOrder ? new Iterator<>() {
            final int limit = -1;
            int pos = StaticQueue.this.limit - 1;

            @Override
            public boolean hasNext() {
                return pos > limit;
            }

            @Override
            public T next() {
                return queue[pos--];
            }
        }
                : new Iterator<>() {
            final int limit = StaticQueue.this.limit;
            int pos = 0;

            @Override
            public boolean hasNext() {
                return pos < limit;
            }

            @Override
            public T next() {
                return queue[pos++];
            }
        };
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return iterator(false);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for (int i = 0; i < this.limit; ++i) {
            action.accept(this.queue[i]);
        }

    }
}
