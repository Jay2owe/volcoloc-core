/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package sc.fiji.volcoloc.core;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

/** Fuzz and boundary coverage for the hand-rolled open-addressing maps. */
public class PrimitiveMapsTest {

    @Test
    public void intIntMapMatchesHashMapOverManyIncrements() {
        Random random = new Random(12345L);
        PrimitiveMaps.IntIntMap subject = new PrimitiveMaps.IntIntMap();
        Map<Integer, Integer> reference = new TreeMap<Integer, Integer>();
        for (int i = 0; i < 300000; i++) {
            int key = 1 + random.nextInt(20000);
            subject.increment(key);
            Integer previous = reference.get(Integer.valueOf(key));
            reference.put(Integer.valueOf(key),
                    Integer.valueOf(previous == null ? 1 : previous.intValue() + 1));
        }
        int[] keys = subject.sortedKeys();
        assertEquals(reference.size(), keys.length);
        int index = 0;
        for (Map.Entry<Integer, Integer> entry : reference.entrySet()) {
            assertEquals(entry.getKey().intValue(), keys[index++]);
            assertEquals(entry.getValue().intValue(),
                    subject.get(entry.getKey().intValue()));
        }
    }

    @Test
    public void intIntMapHandlesLargeAndBoundaryKeys() {
        PrimitiveMaps.IntIntMap subject = new PrimitiveMaps.IntIntMap();
        int[] keys = {1, 2, 16, 17, 65535, 65536, 16777216,
                Integer.MAX_VALUE, Integer.MAX_VALUE - 1};
        for (int key : keys) {
            subject.increment(key);
            subject.increment(key);
        }
        assertEquals(keys.length, subject.sortedKeys().length);
        for (int key : keys) assertEquals(2, subject.get(key));
        assertEquals(0, subject.get(3));
        assertEquals(0, subject.get(0));
        assertEquals(0, subject.get(-5));
    }

    @Test
    public void longIntMapForEachReturnsEveryPairExactlyOnce() {
        Random random = new Random(6789L);
        PrimitiveMaps.LongIntMap subject = new PrimitiveMaps.LongIntMap();
        final Map<Long, Integer> reference = new HashMap<Long, Integer>();
        for (int i = 0; i < 200000; i++) {
            int a = 1 + random.nextInt(700);
            int b = 1 + random.nextInt(700);
            long key = ((long) a << 32) | (b & 0xffffffffL);
            subject.increment(key);
            Integer previous = reference.get(Long.valueOf(key));
            reference.put(Long.valueOf(key),
                    Integer.valueOf(previous == null ? 1 : previous.intValue() + 1));
        }
        final Map<Long, Integer> seen = new HashMap<Long, Integer>();
        subject.forEach(new PrimitiveMaps.LongIntConsumer() {
            @Override
            public void accept(long key, int value) {
                Integer duplicate = seen.put(Long.valueOf(key), Integer.valueOf(value));
                if (duplicate != null) {
                    throw new AssertionError("duplicate key emitted: " + key);
                }
            }
        });
        assertEquals(reference, seen);
    }

    @Test
    public void longIntMapHandlesHighBitLabels() {
        PrimitiveMaps.LongIntMap subject = new PrimitiveMaps.LongIntMap();
        long[] keys = {
                ((long) 1 << 32) | 1L,
                ((long) 1 << 32) | 0xffffffffL,
                ((long) Integer.MAX_VALUE << 32) | 1L,
                ((long) 16777216 << 32) | 16777216L,
        };
        for (long key : keys) subject.increment(key);
        final int[] count = {0};
        subject.forEach(new PrimitiveMaps.LongIntConsumer() {
            @Override
            public void accept(long key, int value) {
                count[0]++;
                assertEquals(1, value);
            }
        });
        assertEquals(keys.length, count[0]);
    }
}
