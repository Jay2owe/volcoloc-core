/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package sc.fiji.volcoloc.core;

import ij.ImagePlus;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Combination patterns. The {@code None} and {@code — Any —} rows are
 * script-readable output, so their presence and position are contract.
 */
public class MultiTargetSummaryTest {

    private static OverlapResult runThreeChannel(List<String> names,
                                                 int[] a, int[] b, int[] c) {
        return DirectionalPairRunner.run(OverlapParameters.builder(Arrays.asList(
                Fixtures.plane("A", a), Fixtures.plane("B", b), Fixtures.plane("C", c)))
                .channelNames(names)
                .thresholdPercent(30.0)
                .build());
    }

    @Test
    public void noneAndAnyAreTheLastTwoRowsForEverySource() {
        OverlapResult result = runThreeChannel(
                Arrays.asList("Red", "Green", "Blue"),
                new int[]{1, 1, 2, 2},
                new int[]{7, 7, 0, 0},
                new int[]{8, 8, 0, 0});

        assertEquals(3, result.getMultiChannelResults().size());
        for (OverlapResult.MultiChannelResult multi : result.getMultiChannelResults()) {
            List<OverlapResult.PatternSummary> patterns = multi.getPatterns();
            assertEquals(MultiTargetSummary.NO_HITS_PATTERN,
                    patterns.get(patterns.size() - 2).getPattern());
            assertEquals(MultiTargetSummary.ANY_PATTERN,
                    patterns.get(patterns.size() - 1).getPattern());
        }
    }

    @Test
    public void noneIsPresentAtZeroForAPerfectlyColocalizedImage() {
        // Every A object hits both targets, so None is genuinely zero — and
        // must still be emitted, or a script reads nothing back.
        OverlapResult result = runThreeChannel(
                Arrays.asList("Red", "Green", "Blue"),
                new int[]{1, 1},
                new int[]{7, 7},
                new int[]{8, 8});

        List<OverlapResult.PatternSummary> patterns =
                result.getMultiChannelResults().get(0).getPatterns();
        OverlapResult.PatternSummary none = patterns.get(patterns.size() - 2);
        assertEquals(MultiTargetSummary.NO_HITS_PATTERN, none.getPattern());
        assertEquals(0, none.getObjectCount());
        assertEquals(0.0, none.getObjectPercent(), 0.0);

        OverlapResult.PatternSummary any = patterns.get(patterns.size() - 1);
        assertEquals(1, any.getObjectCount());
        assertEquals(100.0, any.getObjectPercent(), 0.0);
    }

    @Test
    public void anyCountsObjectsHittingAtLeastOneTargetNotHitsThemselves() {
        OverlapResult result = runThreeChannel(
                Arrays.asList("Red", "Green", "Blue"),
                new int[]{1, 1, 2, 2},
                new int[]{7, 7, 0, 0},
                new int[]{8, 8, 0, 0});

        List<OverlapResult.PatternSummary> patterns =
                result.getMultiChannelResults().get(0).getPatterns();
        OverlapResult.PatternSummary any = patterns.get(patterns.size() - 1);
        // Object 1 hits both targets, object 2 hits neither. Any is 1, not 2.
        assertEquals(1, any.getObjectCount());
        assertEquals(50.0, any.getObjectPercent(), 0.0);

        OverlapResult.PatternSummary none = patterns.get(patterns.size() - 2);
        assertEquals(1, none.getObjectCount());
    }

    @Test
    public void targetsHitCountsColocalizedTargetsPerObject() {
        OverlapResult result = runThreeChannel(
                Arrays.asList("Red", "Green", "Blue"),
                new int[]{1, 1, 2, 2},
                new int[]{7, 7, 0, 0},
                new int[]{8, 8, 0, 0});

        List<OverlapResult.MultiObjectResult> objects =
                result.getMultiChannelResults().get(0).getObjects();
        assertEquals(2, objects.get(0).getTargetsHit());
        assertEquals("Green + Blue", objects.get(0).getPattern());
        assertEquals(0, objects.get(1).getTargetsHit());
        assertEquals(MultiTargetSummary.NO_HITS_PATTERN, objects.get(1).getPattern());
    }

    @Test
    public void aChannelNamedLikeAReservedRowIsQuoted() {
        // A user naming a channel "None" must not silently merge with the
        // reserved row.
        OverlapResult result = runThreeChannel(
                Arrays.asList("Red", MultiTargetSummary.NO_HITS_PATTERN, "Blue"),
                new int[]{1, 1},
                new int[]{7, 7},
                new int[]{0, 0});

        assertEquals("\"" + MultiTargetSummary.NO_HITS_PATTERN + "\"",
                result.getMultiChannelResults().get(0).getObjects().get(0).getPattern());
    }

    @Test
    public void aChannelNamedLikeACombinationIsQuoted() {
        OverlapResult result = runThreeChannel(
                Arrays.asList("Red", "Green + Blue", "Blue"),
                new int[]{1, 1},
                new int[]{7, 7},
                new int[]{0, 0});
        assertEquals("\"Green + Blue\"",
                result.getMultiChannelResults().get(0).getObjects().get(0).getPattern());
    }

    @Test
    public void duplicateChannelNamesAreMadeUniqueBeforePatternsAreBuilt() {
        OverlapResult result = runThreeChannel(
                Arrays.asList("Same", "Same", "Same"),
                new int[]{1, 1},
                new int[]{7, 7},
                new int[]{8, 8});
        assertEquals(Arrays.asList("Same", "Same 2", "Same 3"), result.getChannelNames());
        assertEquals("Same 2 + Same 3",
                result.getMultiChannelResults().get(0).getObjects().get(0).getPattern());
    }

    @Test
    public void twoChannelsProduceNoMultiTargetOutput() {
        OverlapResult result = DirectionalPairRunner.run(OverlapParameters.builder(
                Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7)).build());
        assertTrue(result.getMultiChannelResults().isEmpty());
    }

    @Test
    public void multiTargetCanBeTurnedOff() {
        OverlapResult result = DirectionalPairRunner.run(OverlapParameters.builder(
                Arrays.asList(Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7),
                        Fixtures.plane("C", 8, 8)))
                .includeMultiColocalization(false)
                .build());
        assertTrue(result.getMultiChannelResults().isEmpty());
    }

    @Test
    public void everyChannelIsASourceEvenWhenPairsAreOneDirectional() {
        // Multi-target anchors on every image regardless of the bidirectional
        // pairwise setting.
        OverlapResult result = DirectionalPairRunner.run(OverlapParameters.builder(
                Arrays.asList(Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7),
                        Fixtures.plane("C", 8, 8)))
                .bidirectional(false)
                .build());
        assertEquals(3, result.getMultiChannelResults().size());
        assertEquals(3, result.getDirectionResults().size());
    }
}
