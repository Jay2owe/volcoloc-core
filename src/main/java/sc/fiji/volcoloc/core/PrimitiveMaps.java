/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package sc.fiji.volcoloc.core;

import java.util.Arrays;

/**
 * Small primitive maps used in the voxel hot path. Label zero is background,
 * so zero can safely remain the empty-slot sentinel.
 *
 * <p>Kept here rather than promoted into {@code oc3d-core}: this is a hot-path
 * implementation detail of the pairwise voxel scan, not an abstraction the
 * plugin family shares. See {@code DECISIONS.md}.
 */
final class PrimitiveMaps {

    private PrimitiveMaps() {
    }

    interface LongIntConsumer {
        void accept(long key, int value);
    }

    static final class IntIntMap {
        private int[] keys = new int[16];
        private int[] values = new int[16];
        private int size;
        private int resizeAt = 10;

        void increment(int key) {
            if (key <= 0) return;
            if (size >= resizeAt) resize();
            int slot = slot(key, keys.length);
            while (keys[slot] != 0 && keys[slot] != key) {
                slot = (slot + 1) & (keys.length - 1);
            }
            if (keys[slot] == 0) {
                keys[slot] = key;
                size++;
            }
            values[slot]++;
        }

        int get(int key) {
            if (key <= 0) return 0;
            int slot = slot(key, keys.length);
            while (keys[slot] != 0) {
                if (keys[slot] == key) return values[slot];
                slot = (slot + 1) & (keys.length - 1);
            }
            return 0;
        }

        int[] sortedKeys() {
            int[] result = new int[size];
            int cursor = 0;
            for (int key : keys) {
                if (key != 0) result[cursor++] = key;
            }
            Arrays.sort(result);
            return result;
        }

        private void resize() {
            int[] oldKeys = keys;
            int[] oldValues = values;
            keys = new int[oldKeys.length << 1];
            values = new int[keys.length];
            resizeAt = (int) (keys.length * 0.65);
            for (int i = 0; i < oldKeys.length; i++) {
                int key = oldKeys[i];
                if (key == 0) continue;
                int slot = slot(key, keys.length);
                while (keys[slot] != 0) slot = (slot + 1) & (keys.length - 1);
                keys[slot] = key;
                values[slot] = oldValues[i];
            }
        }
    }

    static final class LongIntMap {
        private long[] keys = new long[16];
        private int[] values = new int[16];
        private int size;
        private int resizeAt = 10;

        void increment(long key) {
            if (key == 0L) return;
            if (size >= resizeAt) resize();
            int slot = slot(key, keys.length);
            while (keys[slot] != 0L && keys[slot] != key) {
                slot = (slot + 1) & (keys.length - 1);
            }
            if (keys[slot] == 0L) {
                keys[slot] = key;
                size++;
            }
            values[slot]++;
        }

        void forEach(LongIntConsumer consumer) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] != 0L) consumer.accept(keys[i], values[i]);
            }
        }

        private void resize() {
            long[] oldKeys = keys;
            int[] oldValues = values;
            keys = new long[oldKeys.length << 1];
            values = new int[keys.length];
            resizeAt = (int) (keys.length * 0.65);
            for (int i = 0; i < oldKeys.length; i++) {
                long key = oldKeys[i];
                if (key == 0L) continue;
                int slot = slot(key, keys.length);
                while (keys[slot] != 0L) slot = (slot + 1) & (keys.length - 1);
                keys[slot] = key;
                values[slot] = oldValues[i];
            }
        }
    }

    private static int slot(int key, int length) {
        int h = key;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        return h & (length - 1);
    }

    private static int slot(long key, int length) {
        long h = key;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return ((int) h) & (length - 1);
    }
}
