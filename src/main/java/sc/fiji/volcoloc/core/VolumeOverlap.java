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
import ij.measure.Calibration;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The measure: what fraction of a source object's voxels lie inside objects in
 * a target channel.
 *
 * <p>One pass over the voxels of every channel builds two tables — per-label
 * voxel counts, and per-ordered-pair overlap counts. Every pair count survives;
 * no best-partner collapse occurs in the hot path, so a single scan serves both
 * directions of every pair and the multi-target pass.
 *
 * <p><strong>Directional.</strong> {@link #measure} always divides by the
 * <em>source</em> object's own volume. A-in-B and B-in-A therefore use
 * different denominators and are different numbers; neither is derivable from
 * the other.
 */
public final class VolumeOverlap {

    private final List<ImagePlus> images;
    private final PrimitiveMaps.IntIntMap[] sizes;
    private final PrimitiveMaps.LongIntMap[][] overlaps;

    private VolumeOverlap(List<ImagePlus> images,
                          PrimitiveMaps.IntIntMap[] sizes,
                          PrimitiveMaps.LongIntMap[][] overlaps) {
        this.images = images;
        this.sizes = sizes;
        this.overlaps = overlaps;
    }

    /**
     * Scans every channel once.
     *
     * @throws IllegalArgumentException if any voxel holds a value that is not a
     *         non-negative integer label
     */
    public static VolumeOverlap scan(List<ImagePlus> images) {
        int channelCount = images.size();
        PrimitiveMaps.IntIntMap[] sizes = new PrimitiveMaps.IntIntMap[channelCount];
        PrimitiveMaps.LongIntMap[][] overlaps =
                new PrimitiveMaps.LongIntMap[channelCount][channelCount];
        for (int i = 0; i < channelCount; i++) {
            sizes[i] = new PrimitiveMaps.IntIntMap();
            for (int j = i + 1; j < channelCount; j++) {
                overlaps[i][j] = new PrimitiveMaps.LongIntMap();
            }
        }

        ImageStack[] stacks = new ImageStack[channelCount];
        for (int i = 0; i < channelCount; i++) stacks[i] = images.get(i).getStack();
        int[] labels = new int[channelCount];
        int slices = stacks[0].getSize();
        for (int slice = 1; slice <= slices; slice++) {
            ImageProcessor[] processors = new ImageProcessor[channelCount];
            for (int c = 0; c < channelCount; c++) {
                processors[c] = stacks[c].getProcessor(slice);
            }
            int pixels = processors[0].getPixelCount();
            for (int pixel = 0; pixel < pixels; pixel++) {
                for (int c = 0; c < channelCount; c++) {
                    int label = readLabel(processors[c], pixel, c, slice);
                    labels[c] = label;
                    if (label > 0) sizes[c].increment(label);
                }
                for (int a = 0; a < channelCount; a++) {
                    if (labels[a] <= 0) continue;
                    for (int b = a + 1; b < channelCount; b++) {
                        if (labels[b] > 0) {
                            overlaps[a][b].increment(pack(labels[a], labels[b]));
                        }
                    }
                }
            }
        }
        return new VolumeOverlap(images, sizes, overlaps);
    }

    private static int readLabel(ImageProcessor processor, int pixel, int channel, int slice) {
        double value = processor.getf(pixel);
        if (!Double.isFinite(value) || value < 0.0
                || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            throw new IllegalArgumentException("Image " + (channel + 1)
                    + " contains an invalid label value (" + value + ") on slice "
                    + slice + ". Labels must be non-negative integers.");
        }
        return (int) value;
    }

    /** Ascending label numbers present in a channel. */
    public int[] sortedLabels(int channel) {
        return sizes[channel].sortedKeys();
    }

    /** Voxel count of one label in one channel; zero if the label is absent. */
    public int voxelCount(int channel, int label) {
        return sizes[channel].get(label);
    }

    /**
     * Measures every object of {@code source} against the objects of
     * {@code target}, dividing by each source object's own volume.
     */
    Measurement measure(final int source, final int target,
                        double threshold,
                        boolean includePartnerDetails,
                        double minimumDetailOverlapPercent) {
        final boolean naturalOrder = source < target;
        PrimitiveMaps.LongIntMap pairMap = naturalOrder
                ? overlaps[source][target]
                : overlaps[target][source];
        final Map<Integer, List<RawPartner>> bySource =
                new TreeMap<Integer, List<RawPartner>>();

        pairMap.forEach(new PrimitiveMaps.LongIntConsumer() {
            @Override
            public void accept(long key, int overlapVoxels) {
                int first = first(key);
                int second = second(key);
                int sourceLabel = naturalOrder ? first : second;
                int targetLabel = naturalOrder ? second : first;
                List<RawPartner> partners = bySource.get(Integer.valueOf(sourceLabel));
                if (partners == null) {
                    partners = new ArrayList<RawPartner>();
                    bySource.put(Integer.valueOf(sourceLabel), partners);
                }
                partners.add(new RawPartner(targetLabel, overlapVoxels));
            }
        });

        List<OverlapResult.ObjectResult> objectRows =
                new ArrayList<OverlapResult.ObjectResult>();
        List<OverlapResult.PartnerDetail> detailRows =
                new ArrayList<OverlapResult.PartnerDetail>();
        int[] sourceLabels = sizes[source].sortedKeys();
        Calibration calibration = images.get(source).getCalibration();
        double voxelVolume = voxelVolume(calibration);
        String volumeUnit = volumeUnit(calibration);
        int colocalized = 0;
        double[] percentages = new double[sourceLabels.length];

        for (int rowIndex = 0; rowIndex < sourceLabels.length; rowIndex++) {
            int sourceLabel = sourceLabels[rowIndex];
            int sourceVoxels = sizes[source].get(sourceLabel);
            List<RawPartner> partners = bySource.get(Integer.valueOf(sourceLabel));
            int totalOverlap = 0;
            int bestLabel = 0;
            int bestOverlap = 0;
            int partnerCount = partners == null ? 0 : partners.size();
            if (partners != null) {
                Collections.sort(partners, new Comparator<RawPartner>() {
                    @Override
                    public int compare(RawPartner left, RawPartner right) {
                        return Integer.compare(left.label, right.label);
                    }
                });
                for (RawPartner partner : partners) {
                    totalOverlap += partner.overlap;
                    if (partner.overlap > bestOverlap
                            || (partner.overlap == bestOverlap && partner.label < bestLabel)) {
                        bestOverlap = partner.overlap;
                        bestLabel = partner.label;
                    }
                    double sourcePartnerPercent =
                            percent(partner.overlap, sourceVoxels);
                    if (includePartnerDetails
                            && sourcePartnerPercent >= minimumDetailOverlapPercent) {
                        int targetVoxels = sizes[target].get(partner.label);
                        detailRows.add(new OverlapResult.PartnerDetail(
                                sourceLabel,
                                partner.label,
                                partner.overlap,
                                sourcePartnerPercent,
                                percent(partner.overlap, targetVoxels)));
                    }
                }
            }
            double overlapPercent = percent(totalOverlap, sourceVoxels);
            percentages[rowIndex] = overlapPercent;
            boolean passes = overlapPercent >= threshold;
            if (passes) colocalized++;
            objectRows.add(new OverlapResult.ObjectResult(
                    sourceLabel,
                    sourceVoxels,
                    sourceVoxels * voxelVolume,
                    volumeUnit,
                    totalOverlap,
                    overlapPercent,
                    bestLabel,
                    bestOverlap,
                    partnerCount,
                    passes));
        }

        OverlapResult.Summary summary = new OverlapResult.Summary(
                sourceLabels.length,
                mean(percentages),
                median(percentages),
                colocalized,
                percent(colocalized, sourceLabels.length));
        return new Measurement(volumeUnit, objectRows, detailRows, summary);
    }

    /** One direction's measured rows, before channel names are attached. */
    static final class Measurement {
        final String volumeUnit;
        final List<OverlapResult.ObjectResult> objects;
        final List<OverlapResult.PartnerDetail> partnerDetails;
        final OverlapResult.Summary summary;

        Measurement(String volumeUnit,
                    List<OverlapResult.ObjectResult> objects,
                    List<OverlapResult.PartnerDetail> partnerDetails,
                    OverlapResult.Summary summary) {
            this.volumeUnit = volumeUnit;
            this.objects = objects;
            this.partnerDetails = partnerDetails;
            this.summary = summary;
        }
    }

    private static double mean(double[] values) {
        if (values.length == 0) return 0.0;
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double median(double[] values) {
        if (values.length == 0) return 0.0;
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        if ((sorted.length & 1) == 1) return sorted[middle];
        return (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    static double percent(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : numerator * 100.0 / denominator;
    }

    private static double voxelVolume(Calibration calibration) {
        if (calibration == null) return 1.0;
        double width = positiveOrOne(calibration.pixelWidth);
        double height = positiveOrOne(calibration.pixelHeight);
        double depth = positiveOrOne(calibration.pixelDepth);
        return width * height * depth;
    }

    private static double positiveOrOne(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    private static String volumeUnit(Calibration calibration) {
        if (calibration == null) return "pixel^3";
        String unit = calibration.getUnit();
        if (unit == null || unit.trim().length() == 0 || "pixel".equalsIgnoreCase(unit)) {
            return "pixel^3";
        }
        String normalized = unit.trim();
        if ("micron".equalsIgnoreCase(normalized)
                || "microns".equalsIgnoreCase(normalized)
                || "um".equalsIgnoreCase(normalized)
                || "\u00b5m".equalsIgnoreCase(normalized)) {
            return "\u00b5m^3";
        }
        return normalized + "^3";
    }

    private static long pack(int first, int second) {
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    private static int first(long key) {
        return (int) (key >>> 32);
    }

    private static int second(long key) {
        return (int) key;
    }

    private static final class RawPartner {
        final int label;
        final int overlap;

        RawPartner(int label, int overlap) {
            this.label = label;
            this.overlap = overlap;
        }
    }
}
