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

/** The optional bounding-box families: BBColoc, BB-CPC and BBVolColoc. */
public class BoundingBoxOverlapTest {

    private static OverlapParameters allFamilies(ImagePlus a, ImagePlus b) {
        return OverlapParameters.builder(a, b)
                .bidirectional(true)
                .boundingBoxThresholdsPercent(Arrays.asList(20.0, 20.0))
                .includeBoundingBoxOverlap(true)
                .includeBoundingBoxCpc(true)
                .includeBoundingBoxVolumeFill(true)
                .build();
    }

    @Test
    public void parallelRowsExactlyMatchSerialRowsAndOrder() {
        ImagePlus source = Fixtures.plane("source", 1, 1, 0, 2, 2, 0, 3, 3, 4, 4);
        ImagePlus target = Fixtures.plane("target", 5, 5, 0, 6, 0, 0, 7, 7, 8, 0);
        OverlapParameters parameters = allFamilies(source, target);

        String previous = System.getProperty("volcoloc.parallelism");
        try {
            System.setProperty("volcoloc.parallelism", "1");
            List<OverlapResult.BoundingBoxDirectionResult> serial =
                    DirectionalPairRunner.run(parameters).getBoundingBoxDirectionResults();
            System.setProperty("volcoloc.parallelism", "4");
            List<OverlapResult.BoundingBoxDirectionResult> parallel =
                    DirectionalPairRunner.run(parameters).getBoundingBoxDirectionResults();
            assertEquals(serial.size(), parallel.size());
            for (int direction = 0; direction < serial.size(); direction++) {
                List<OverlapResult.BoundingBoxObjectResult> expected =
                        serial.get(direction).getObjects();
                List<OverlapResult.BoundingBoxObjectResult> actual =
                        parallel.get(direction).getObjects();
                assertEquals(expected.size(), actual.size());
                for (int row = 0; row < expected.size(); row++) {
                    assertSameRow(expected.get(row), actual.get(row));
                }
            }
        } finally {
            restore("volcoloc.parallelism", previous);
        }
    }

    @Test
    public void boundingBoxesSpanTheZAxis() {
        // A's single object spans all three slices, so its box is 1x1x3;
        // B's box is 1x1x1 at z=1, fully inside it.
        ImagePlus a = Fixtures.stack("A", 1, 1, new int[][]{{1}, {0}, {1}});
        ImagePlus b = Fixtures.stack("B", 1, 1, new int[][]{{0}, {5}, {0}});

        OverlapResult result = DirectionalPairRunner.run(allFamilies(a, b));
        OverlapResult.BoundingBoxObjectResult row =
                result.getBoundingBoxDirectionResults().get(0).getObjects().get(0);

        assertEquals(3L, row.getBoxVolume());
        assertEquals(100.0 / 3.0, row.getBoundingBoxOverlapPercent(), 0.0);
        assertEquals(100.0 / 3.0, row.getBoundingBoxVolumeBestPercent(), 0.0);
        assertEquals(5, row.getBoundingBoxVolumePartnerLabel());

        // The approximation is not the measure: A's voxels never touch B's.
        assertEquals(0.0, result.getDirectionResults().get(0)
                .getObjects().get(0).getOverlapPercent(), 0.0);
    }

    @Test
    public void familiesAreIndependentlySelectable() {
        ImagePlus a = Fixtures.plane("A", 1, 1, 0, 2, 2);
        ImagePlus b = Fixtures.plane("B", 5, 0, 0, 6, 6);

        OverlapResult.BoundingBoxDirectionResult onlyOverlap =
                DirectionalPairRunner.run(OverlapParameters.builder(a, b)
                        .includeBoundingBoxOverlap(true)
                        .build()).getBoundingBoxDirectionResults().get(0);
        assertTrue(onlyOverlap.isIncludeBoundingBoxOverlap());
        assertEquals(false, onlyOverlap.isIncludeBoundingBoxCpc());
        assertEquals(false, onlyOverlap.isIncludeBoundingBoxVolumeFill());
        // Unselected families report NaN rather than a misleading zero.
        assertTrue(Double.isNaN(onlyOverlap.getObjects().get(0)
                .getBoundingBoxVolumeBestPercent()));
    }

    @Test
    public void noBoundingBoxFamilyMeansNoBoundingBoxOutput() {
        assertTrue(DirectionalPairRunner.run(OverlapParameters.builder(
                Fixtures.plane("A", 1, 1), Fixtures.plane("B", 5, 5)).build())
                .getBoundingBoxDirectionResults().isEmpty());
    }

    @Test
    public void cpcSeparatesBeingContainedFromContainingOthers() {
        // A spans x=0..3; B has objects at x=0 and x=3 only. Both B centroids
        // fall inside A's box, but A's own centroid (x=1.5, rounded to 2) lands
        // between the two B boxes. The contains-count and the coincidence flag
        // answer different questions and must not be conflated.
        ImagePlus a = Fixtures.plane("A", 1, 1, 1, 1);
        ImagePlus b = Fixtures.plane("B", 5, 0, 0, 6);

        OverlapResult.BoundingBoxObjectResult row =
                DirectionalPairRunner.run(OverlapParameters.builder(a, b)
                        .includeBoundingBoxCpc(true).build())
                        .getBoundingBoxDirectionResults().get(0).getObjects().get(0);

        assertEquals(2, row.getBoundingBoxCpcContainsCount());
        assertEquals(false, row.isBoundingBoxCpcColocalized());
        assertEquals(0, row.getBoundingBoxCpcPartnerLabel());
    }

    @Test
    public void cpcFlagsASourceWhoseCentroidLandsInAPartnerBox() {
        // A's centroid now falls inside B's box, so the flag is set and names
        // the partner it landed in.
        ImagePlus a = Fixtures.plane("A", 1, 1, 1, 1);
        ImagePlus b = Fixtures.plane("B", 0, 0, 5, 0);

        OverlapResult.BoundingBoxObjectResult row =
                DirectionalPairRunner.run(OverlapParameters.builder(a, b)
                        .includeBoundingBoxCpc(true).build())
                        .getBoundingBoxDirectionResults().get(0).getObjects().get(0);

        assertTrue(row.isBoundingBoxCpcColocalized());
        assertEquals(5, row.getBoundingBoxCpcPartnerLabel());
        assertEquals(1, row.getBoundingBoxCpcContainsCount());
    }

    private static void assertSameRow(
            OverlapResult.BoundingBoxObjectResult expected,
            OverlapResult.BoundingBoxObjectResult actual) {
        assertEquals(expected.getSourceLabel(), actual.getSourceLabel());
        assertEquals(expected.getBoxVolume(), actual.getBoxVolume());
        assertEquals(expected.getBoundingBoxOverlapPercent(),
                actual.getBoundingBoxOverlapPercent(), 0.0);
        assertEquals(expected.getBoundingBoxOverlapPartnerLabel(),
                actual.getBoundingBoxOverlapPartnerLabel());
        assertEquals(expected.getBoundingBoxCpcPartnerLabel(),
                actual.getBoundingBoxCpcPartnerLabel());
        assertEquals(expected.getBoundingBoxCpcContainsCount(),
                actual.getBoundingBoxCpcContainsCount());
        assertEquals(expected.getBoundingBoxVolumeBestPercent(),
                actual.getBoundingBoxVolumeBestPercent(), 0.0);
        assertEquals(expected.getBoundingBoxVolumeTotalPercent(),
                actual.getBoundingBoxVolumeTotalPercent(), 0.0);
        assertEquals(expected.getBoundingBoxVolumePartnerLabel(),
                actual.getBoundingBoxVolumePartnerLabel());
    }

    private static void restore(String key, String previous) {
        if (previous == null) System.clearProperty(key);
        else System.setProperty(key, previous);
    }
}
