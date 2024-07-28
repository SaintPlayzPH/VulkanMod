package net.vulkanmod.render.chunk.util;

import org.apache.commons.lang3.Validate;

import java.util.Iterator;

public class CircularIntList {
    private final int startIndex;
    private int[] list;
    private int[] previous;
    private int[] next;

    private OwnIterator iterator;

    public CircularIntList(int size, int startIndex) {
        this.startIndex = startIndex;

        this.generateList(size);
    }

    private void generateList(int size) {
        int[] list = new int[size];

        this.previous = new int[size];
        this.next = new int[size];

        int k = 0;
        for (int i = startIndex; i < size; ++i) {
            list[k] = i;

            ++k;
        }
        for (int i = 0; i < startIndex; ++i) {
            list[k] = i;
            ++k;
        }

        this.previous[0] = -1;
        System.arraycopy(list, 0, this.previous, 1, size - 1);

        this.next[size - 1] = -1;
        System.arraycopy(list, 1, this.next, 0, size - 1);

        this.list = list;
    }

    public int getNext(int i) {
        return this.next[i];
    }

    public int getPrevious(int i) {
        return this.previous[i];
    }

    public OwnIterator iterator() {
        return new OwnIterator();
    }

    public RangeIterator rangeIterator(int startIndex, int endIndex) {
        return new RangeIterator(startIndex, endIndex);
    }

    public void restartIterator() {
        this.iterator.restart();
    }

    public class OwnIterator implements Iterator<Integer> {
        private final int maxIndex = list.length - 1;
        private int currentIndex = -1;

        @Override
        public boolean hasNext() {
            return currentIndex < maxIndex;
        }

        @Override
        public Integer next() {
            currentIndex++;
            return list[currentIndex];
        }

        public int getCurrentIndex() {
            return currentIndex;
        }

        public void restart() {
            this.currentIndex = -1;
        }
    }

    public class RangeIterator implements Iterator<Integer> {
        private final int startIndex;
        private final int maxIndex;
        private int currentIndex;

        public RangeIterator(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.maxIndex = endIndex;
            Validate.isTrue(this.maxIndex < list.length, "Beyond max size");

            this.restart();
        }

        @Override
        public boolean hasNext() {
            return currentIndex < maxIndex;
        }

        @Override
        public Integer next() {
            currentIndex++;
            try {
                return list[currentIndex];
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException();
            }

        }

        public int getCurrentIndex() {
            return currentIndex;
        }

        public void restart() {
            this.currentIndex = this.startIndex - 1;
        }
    }
}
