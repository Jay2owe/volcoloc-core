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
import ij.measure.Calibration;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The measure itself: occupancy, denominators, partners and the threshold flag. */
public class VolumeOverlapTest {

    @Test
    public void aInBAndBInAUseTheirOwnDenominators() {
        // 1x1x4 column. A occupies z=0..2, B occupies z=2..3, so exactly one
        // of A's three voxels is overlapped, and one of B's two.
        ImagePlus a = Fixtures.stack("A", 1, 1, new int[][]{{1}, {1}, {1}, {0}});
        ImagePlus b = Fixtures.stack("B", 1, 1, new int[][]{{0}, {0}, {7}, {7}});

        OverlapResult result = DirectionalPairRunner.run(
                OverlapParameters.builder(a, b).build());

        OverlapResult.ObjectResult aInB =
                result.getDirectionResults().get(0).getObjects().get(0);
        assertEquals(3, aInB.getSourceVoxels());
        assertEquals(1, aInB.getOverlapVoxels());
        assertEquals(100.0 / 3.0, aInB.getOverlapPercent(), 0.0);
        assertEquals(7, aInB.getBestPartnerLabel());

        OverlapResult.ObjectResult bInA =
                result.getDirectionResults().get(1).getObjects().get(0);
        assertEquals(2, bInA.getSourceVoxels());
        assertEquals(1, bInA.getOverlapVoxels());
        assertEquals(50.0, bInA.getOverlapPercent(), 0.0);

        // The two directions share a numerator and nothing else. Neither is
        // derivable from the other, which is why both are reported.
        assertEquals(aInB.getOverlapVoxels(), bInA.getOverlapVoxels());
        assertFalse(aInB.getOverlapPercent() == bInA.getOverlapPercent());
    }

    @Test
    public void continuousPercentageSurvivesEveryThreshold() {
        // The 30% default is a reporting convention. Whatever the threshold,
        // the measured percentage must be the same number.
        ImagePlus a = Fixtures.plane("A", 1, 1, 1, 1);
        ImagePlus b = Fixtures.plane("B", 5, 0, 0, 0);

        double[] sweep = {0.0, 25.0, 30.0, 100.0};
        boolean[] expectedFlag = {true, true, false, false};
        for (int i = 0; i < sweep.length; i++) {
            OverlapResult.ObjectResult row = DirectionalPairRunner.run(
                    OverlapParameters.builder(a, b)
                            .thresholdPercent(sweep[i])
                            .build())
                    .getDirectionResults().get(0).getObjects().get(0);
            assertEquals("threshold " + sweep[i], 25.0, row.getOverlapPercent(), 0.0);
            assertEquals("threshold " + sweep[i], expectedFlag[i], row.isColocalized());
        }
    }

    @Test
    public void thresholdIsInclusiveAtItsOwnValue() {
        ImagePlus a = Fixtures.plane("A", 1, 1, 1, 1);
        ImagePlus b = Fixtures.plane("B", 5, 0, 0, 0);
        assertTrue(DirectionalPairRunner.run(
                OverlapParameters.builder(a, b).thresholdPercent(25.0).build())
                .getDirectionResults().get(0).getObjects().get(0).isColocalized());
    }

    @Test
    public void everyPartnerIsCountedNotJustTheStrongest() {
        // One source object of 300 voxels, each voxel overlapped by a
        // different partner: 300 partners, 100% occupied, none dominant.
        int count = 300;
        int[] sourceRow = new int[count];
        int[] partnerRow = new int[count];
        for (int i = 0; i < count; i++) {
            sourceRow[i] = 1;
            partnerRow[i] = i + 1;
        }
        OverlapResult.ObjectResult row = DirectionalPairRunner.run(
                OverlapParameters.builder(
                        Fixtures.plane("A", sourceRow), Fixtures.plane("B", partnerRow))
                        .build())
                .getDirectionResults().get(0).getObjects().get(0);

        assertEquals(count, row.getPartnerCount());
        assertEquals(count, row.getOverlapVoxels());
        assertEquals(100.0, row.getOverlapPercent(), 0.0);
        assertEquals(1, row.getBestPartnerOverlapVoxels());
        // Tied partners resolve to the lowest label, deterministically.
        assertEquals(1, row.getBestPartnerLabel());
    }

    @Test
    public void strongestPartnerWinsOnVoxelsNotOnLabelOrder() {
        // Partner 9 contributes two voxels, partner 2 only one.
        OverlapResult.ObjectResult row = DirectionalPairRunner.run(
                OverlapParameters.builder(
                        Fixtures.plane("A", 1, 1, 1), Fixtures.plane("B", 2, 9, 9))
                        .build())
                .getDirectionResults().get(0).getObjects().get(0);
        assertEquals(9, row.getBestPartnerLabel());
        assertEquals(2, row.getBestPartnerOverlapVoxels());
        assertEquals(2, row.getPartnerCount());
    }

    @Test
    public void calibrationScalesVolumeAndNamesTheUnit() {
        ImagePlus a = Fixtures.stack("A", 1, 1, new int[][]{{1}, {1}, {1}, {0}});
        ImagePlus b = Fixtures.stack("B", 1, 1, new int[][]{{0}, {0}, {7}, {7}});
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 4.0;
        calibration.setUnit("um");
        a.setCalibration(calibration);

        OverlapResult.DirectionResult aToB = DirectionalPairRunner.run(
                OverlapParameters.builder(a, b).build()).getDirectionResults().get(0);

        assertEquals(3.0, aToB.getObjects().get(0).getSourceVolume(), 0.0);
        assertEquals("\u00b5m^3", aToB.getVolumeUnit());
    }

    @Test
    public void uncalibratedVolumeIsCountedInPixels() {
        OverlapResult.DirectionResult aToB = DirectionalPairRunner.run(
                OverlapParameters.builder(
                        Fixtures.plane("A", 1, 1, 1), Fixtures.plane("B", 5, 0, 0))
                        .build()).getDirectionResults().get(0);
        assertEquals("pixel^3", aToB.getVolumeUnit());
        assertEquals(3.0, aToB.getObjects().get(0).getSourceVolume(), 0.0);
    }

    @Test
    public void bitDepthDoesNotChangeTheNumbers() {
        int[] source = {1, 1, 1, 1};
        int[] partner = {5, 5, 0, 0};
        double reference = -1.0;
        for (int depth : new int[]{8, 16, 32}) {
            OverlapResult.ObjectResult row = DirectionalPairRunner.run(
                    OverlapParameters.builder(
                            Fixtures.planeOfDepth("A", depth, source),
                            Fixtures.planeOfDepth("B", depth, partner))
                            .build())
                    .getDirectionResults().get(0).getObjects().get(0);
            if (reference < 0.0) reference = row.getOverlapPercent();
            assertEquals("bit depth " + depth, reference, row.getOverlapPercent(), 0.0);
            assertEquals("bit depth " + depth, 50.0, row.getOverlapPercent(), 0.0);
        }
    }

    @Test
    public void partnerDetailRowsRespectTheMinimumContribution() {
        // Partner 5 contributes 3 of A's 4 voxels (75%), partner 6 just one (25%).
        ImagePlus a = Fixtures.plane("A", 1, 1, 1, 1);
        ImagePlus b = Fixtures.plane("B", 5, 5, 5, 6);

        List<OverlapResult.PartnerDetail> all = DirectionalPairRunner.run(
                OverlapParameters.builder(a, b)
                        .minimumDetailOverlapPercent(0.0).build())
                .getDirectionResults().get(0).getPartnerDetails();
        assertEquals(2, all.size());

        List<OverlapResult.PartnerDetail> strong = DirectionalPairRunner.run(
                OverlapParameters.builder(a, b)
                        .minimumDetailOverlapPercent(50.0).build())
                .getDirectionResults().get(0).getPartnerDetails();
        assertEquals(1, strong.size());
        assertEquals(5, strong.get(0).getPartnerLabel());
        assertEquals(75.0, strong.get(0).getSourceOverlapPercent(), 0.0);
        // The partner-side percentage uses the partner's own volume.
        assertEquals(100.0, strong.get(0).getPartnerOverlapPercent(), 0.0);

        assertTrue(DirectionalPairRunner.run(
                OverlapParameters.builder(a, b)
                        .includePartnerDetails(false).build())
                .getDirectionResults().get(0).getPartnerDetails().isEmpty());
    }

    @Test
    public void summaryReportsMeanMedianAndColocalizedShare() {
        // Three A objects at 100%, 50% and 0% occupancy.
        ImagePlus a = Fixtures.plane("A", 1, 1, 2, 2, 3, 3);
        ImagePlus b = Fixtures.plane("B", 7, 7, 8, 0, 0, 0);

        OverlapResult.Summary summary = DirectionalPairRunner.run(
                OverlapParameters.builder(a, b).thresholdPercent(30.0).build())
                .getDirectionResults().get(0).getSummary();

        assertEquals(3, summary.getObjectCount());
        assertEquals(50.0, summary.getMeanOverlapPercent(), 0.0);
        assertEquals(50.0, summary.getMedianOverlapPercent(), 0.0);
        assertEquals(2, summary.getColocalizedCount());
        assertEquals(200.0 / 3.0, summary.getColocalizedPercent(), 0.0);
    }

    @Test
    public void emptyChannelProducesNoRowsRatherThanDividingByZero() {
        OverlapResult.DirectionResult aToB = DirectionalPairRunner.run(
                OverlapParameters.builder(
                        Fixtures.plane("A", 0, 0, 0), Fixtures.plane("B", 5, 5, 5))
                        .build()).getDirectionResults().get(0);
        assertTrue(aToB.getObjects().isEmpty());
        assertEquals(0, aToB.getSummary().getObjectCount());
        assertEquals(0.0, aToB.getSummary().getMeanOverlapPercent(), 0.0);
        assertEquals(0.0, aToB.getSummary().getColocalizedPercent(), 0.0);
    }

    @Test
    public void nonIntegerLabelValueIsRejectedWithItsSliceAndImageNumber() {
        ImagePlus a = Fixtures.planeOfDepth("A", 32, 1, 1);
        a.getProcessor().setf(1, 0, 1.5f);
        try {
            DirectionalPairRunner.run(OverlapParameters.builder(
                    a, Fixtures.planeOfDepth("B", 32, 5, 5)).build());
            fail("expected a rejection");
        } catch (IllegalArgumentException expected) {
            assertEquals("Image 1 contains an invalid label value (1.5) on "
                    + "slice 1. Labels must be non-negative integers.",
                    expected.getMessage());
        }
    }

    @Test
    public void scanExposesLabelsAndVoxelCounts() {
        VolumeOverlap overlap = VolumeOverlap.scan(Arrays.asList(
                Fixtures.plane("A", 3, 3, 1, 0), Fixtures.plane("B", 5, 0, 0, 0)));
        assertArrayEqualsInt(new int[]{1, 3}, overlap.sortedLabels(0));
        assertEquals(2, overlap.voxelCount(0, 3));
        assertEquals(1, overlap.voxelCount(0, 1));
        assertEquals(0, overlap.voxelCount(0, 99));
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    }
}
