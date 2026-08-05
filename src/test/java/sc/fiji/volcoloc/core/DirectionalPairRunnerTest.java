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
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pair and direction bookkeeping, and the rejection contract.
 *
 * <p>The rejection messages are the plugin's contract with the user. They are
 * asserted verbatim here so a reword shows up as a failing test rather than as
 * a silent change to what a user reads.
 */
public class DirectionalPairRunnerTest {

    private static String messageFrom(OverlapParameters parameters) {
        try {
            DirectionalPairRunner.run(parameters);
            fail("expected a rejection");
            return null;
        } catch (IllegalArgumentException expected) {
            return expected.getMessage();
        }
    }

    @Test
    public void everyPairIsMeasuredInBothDirectionsByDefault() {
        OverlapResult result = DirectionalPairRunner.run(OverlapParameters.builder(
                Arrays.asList(Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7),
                        Fixtures.plane("C", 8, 8)))
                .build());
        // 3 pairs x 2 directions.
        assertEquals(6, result.getDirectionResults().size());
        assertEquals(0, result.getDirectionResults().get(0).getSourceIndex());
        assertEquals(1, result.getDirectionResults().get(0).getTargetIndex());
        assertEquals(1, result.getDirectionResults().get(1).getSourceIndex());
        assertEquals(0, result.getDirectionResults().get(1).getTargetIndex());
    }

    @Test
    public void oneDirectionalRunEmitsOnePairPerCombination() {
        OverlapResult result = DirectionalPairRunner.run(OverlapParameters.builder(
                Arrays.asList(Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7),
                        Fixtures.plane("C", 8, 8)))
                .bidirectional(false)
                .build());
        assertEquals(3, result.getDirectionResults().size());
    }

    @Test
    public void eachOrderedDirectionIsMeasuredAtMostOnce() {
        // The pairwise output and the multi-target pass both ask for the same
        // directions. Three channels have six ordered pairs; measuring any of
        // them twice would be wasted work on a real stack.
        DirectionalPairRunner runner = DirectionalPairRunner.validated(
                OverlapParameters.builder(Arrays.asList(
                        Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7),
                        Fixtures.plane("C", 8, 8)))
                        .build());
        runner.analyse();
        assertEquals(6, runner.getDirectionBuilds());
    }

    @Test
    public void oneDirectionalPairsStillMeasureEachDirectionOnce() {
        DirectionalPairRunner runner = DirectionalPairRunner.validated(
                OverlapParameters.builder(Arrays.asList(
                        Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7),
                        Fixtures.plane("C", 8, 8)))
                        .bidirectional(false)
                        .build());
        runner.analyse();
        // Three forward pairs plus the three reverses the multi pass needs.
        assertEquals(6, runner.getDirectionBuilds());
    }

    @Test
    public void blankNamesFallBackToImageTitles() {
        OverlapResult result = DirectionalPairRunner.run(OverlapParameters.builder(
                Fixtures.plane("Nuclei", 1, 1), Fixtures.plane("Puncta", 7, 7))
                .channelNames(Arrays.asList("", "  "))
                .build());
        assertEquals(Arrays.asList("Nuclei", "Puncta"), result.getChannelNames());
    }

    @Test
    public void untitledImagesFallBackToLetters() {
        OverlapResult result = DirectionalPairRunner.run(OverlapParameters.builder(
                Fixtures.plane("", 1, 1), Fixtures.plane("", 7, 7)).build());
        assertEquals(Arrays.asList("Channel A", "Channel B"), result.getChannelNames());
    }

    @Test
    public void tooFewImagesIsRejected() {
        assertEquals("Volumetric Colocalization requires at least 2 label images.",
                messageFrom(OverlapParameters.builder(
                        Collections.singletonList(Fixtures.plane("A", 1))).build()));
    }

    @Test
    public void tooManyImagesIsRejected() {
        List<ImagePlus> six = Arrays.asList(
                Fixtures.plane("A", 1), Fixtures.plane("B", 1), Fixtures.plane("C", 1),
                Fixtures.plane("D", 1), Fixtures.plane("E", 1), Fixtures.plane("F", 1));
        assertEquals("Volumetric Colocalization supports at most 5 label images.",
                messageFrom(OverlapParameters.builder(six).build()));
    }

    @Test
    public void rgbIsRejectedBecauseItsPixelsArePackedColours() {
        ImagePlus rgb = new ImagePlus("RGB", new ColorProcessor(2, 1));
        assertEquals("Label image 2 is an RGB image, whose pixel values are "
                        + "packed colours rather than object labels. Convert the "
                        + "segmentation to 8-, 16-, or 32-bit first.",
                messageFrom(OverlapParameters.builder(
                        Fixtures.plane("A", 1, 1), rgb).build()));
    }

    @Test
    public void hyperstackIsRejectedRatherThanMismeasured() {
        // Two channels would otherwise be walked as two extra Z layers, and
        // every object's volume would double.
        ImageStack stack = new ImageStack(2, 1);
        stack.addSlice(new ShortProcessor(2, 1));
        stack.addSlice(new ShortProcessor(2, 1));
        ImagePlus hyper = new ImagePlus("Hyper", stack);
        hyper.setDimensions(2, 1, 1);

        String message = messageFrom(OverlapParameters.builder(
                hyper, Fixtures.plane("B", 1, 1)).build());
        assertTrue(message, message.startsWith(
                "Label image 1 is a hyperstack (2 channel(s), 1 slice(s), 1 frame(s)). "
                        + "Volumetric Colocalization measures one volume at a time."));
    }

    @Test
    public void theSameImageInTwoSlotsIsRejected() {
        ImagePlus shared = Fixtures.plane("A", 1, 1);
        assertEquals("Each input slot must use a different ImagePlus.",
                messageFrom(OverlapParameters.builder(shared, shared).build()));
    }

    @Test
    public void mismatchedDimensionsAreRejected() {
        assertEquals("All label images must have identical width, height, "
                        + "channel, slice, and frame dimensions.",
                messageFrom(OverlapParameters.builder(
                        Fixtures.plane("A", 1, 1), Fixtures.plane("B", 1, 1, 1)).build()));
    }

    @Test
    public void thresholdOutOfRangeIsRejectedNamingWhichOne() {
        assertEquals("Threshold 2 must be between 0 and 100 percent.",
                messageFrom(OverlapParameters.builder(
                        Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7))
                        .thresholdsPercent(Arrays.asList(10.0, 140.0))
                        .build()));
    }

    @Test
    public void thresholdListOfTheWrongLengthIsRejected() {
        assertEquals("Threshold list must be empty or match the number of label images.",
                messageFrom(OverlapParameters.builder(
                        Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7))
                        .thresholdsPercent(Collections.singletonList(10.0))
                        .build()));
    }

    @Test
    public void boundingBoxThresholdIsNamedSeparatelyWhenRejected() {
        assertEquals("Bounding-box threshold 1 must be between 0 and 100 percent.",
                messageFrom(OverlapParameters.builder(
                        Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7))
                        .boundingBoxThresholdsPercent(Arrays.asList(-1.0, 10.0))
                        .build()));
    }

    @Test
    public void minimumDetailOverlapOutOfRangeIsRejected() {
        assertEquals("Minimum partner-detail overlap must be between 0 and 100 percent.",
                messageFrom(OverlapParameters.builder(
                        Fixtures.plane("A", 1, 1), Fixtures.plane("B", 7, 7))
                        .minimumDetailOverlapPercent(101.0)
                        .build()));
    }

    @Test
    public void nullParametersAreRejected() {
        assertEquals("Volumetric Colocalization parameters must not be null.",
                messageFrom(null));
    }

    @Test
    public void perSourceThresholdsApplyToTheirOwnDirection() {
        // A's object is 4 voxels overlapped once (25%); B's single voxel sits
        // entirely inside A (100%). One threshold of 40 therefore fails the
        // A direction and passes the B direction — the denominators differ.
        OverlapResult result = DirectionalPairRunner.run(OverlapParameters.builder(
                Fixtures.plane("A", 1, 1, 1, 1), Fixtures.plane("B", 5, 0, 0, 0))
                .thresholdsPercent(Arrays.asList(40.0, 40.0))
                .build());
        assertEquals(25.0, result.getDirectionResults().get(0)
                .getObjects().get(0).getOverlapPercent(), 0.0);
        assertEquals(100.0, result.getDirectionResults().get(1)
                .getObjects().get(0).getOverlapPercent(), 0.0);
        assertEquals(false, result.getDirectionResults().get(0)
                .getObjects().get(0).isColocalized());
        assertEquals(true, result.getDirectionResults().get(1)
                .getObjects().get(0).isColocalized());
    }
}
