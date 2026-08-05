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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Entry point: validates the inputs, scans once, and measures every pair in
 * every requested direction.
 *
 * <p>A-in-B and B-in-A are separate rows with different denominators, and both
 * are reported when {@link OverlapParameters#isBidirectional()} is set. Each
 * ordered direction is built at most once: the pairwise output and the
 * multi-target pass both ask for directions, and the cache stops the second
 * request rebuilding the first's work.
 *
 * <p><strong>Headless.</strong> No dialog, no Swing, no {@code IJ.error}. Bad
 * input throws {@link IllegalArgumentException}; the consuming plugin decides
 * how to present it.
 */
public final class DirectionalPairRunner {

    private final OverlapParameters parameters;
    private final List<ImagePlus> images;
    private final List<String> channelNames;
    private final List<Double> thresholds;
    private final List<Double> boundingBoxThresholds;
    private final VolumeOverlap overlap;
    /**
     * Directions are requested twice — once for the pairwise output and again
     * per ordered pair by the multi-colocalization pass — so each is built at
     * most once and reused.
     */
    private final Map<Long, OverlapResult.DirectionResult> directionCache =
            new HashMap<Long, OverlapResult.DirectionResult>();

    private int directionBuilds;

    private DirectionalPairRunner(OverlapParameters parameters,
                                  Validated validated) {
        this.parameters = parameters;
        this.images = parameters.getImages();
        this.channelNames = validated.channelNames;
        this.thresholds = validated.thresholds;
        this.boundingBoxThresholds = validated.boundingBoxThresholds;
        this.overlap = VolumeOverlap.scan(images);
    }

    /**
     * Validates, scans and measures.
     *
     * @throws IllegalArgumentException if the inputs are not a usable set of
     *         label images, or a threshold is out of range
     */
    public static OverlapResult run(OverlapParameters parameters) {
        return validated(parameters).analyse();
    }

    /**
     * Validated, scanned, but not yet measured — the seam the cache-reuse test
     * needs so it can read {@link #getDirectionBuilds()} afterwards.
     */
    static DirectionalPairRunner validated(OverlapParameters parameters) {
        return new DirectionalPairRunner(parameters, validate(parameters));
    }

    /** Number of distinct ordered directions actually measured. */
    int getDirectionBuilds() {
        return directionBuilds;
    }

    OverlapResult analyse() {
        List<OverlapResult.DirectionResult> directions =
                new ArrayList<OverlapResult.DirectionResult>();
        int count = images.size();
        for (int a = 0; a < count; a++) {
            for (int b = a + 1; b < count; b++) {
                directions.add(direction(a, b));
                if (parameters.isBidirectional()) {
                    directions.add(direction(b, a));
                }
            }
        }

        List<OverlapResult.MultiChannelResult> multi =
                parameters.isIncludeMultiColocalization() && images.size() >= 3
                        ? MultiTargetSummary.build(
                                images.size(),
                                channelNames,
                                overlap,
                                new MultiTargetSummary.DirectionLookup() {
                                    @Override
                                    public OverlapResult.DirectionResult get(int source, int target) {
                                        return direction(source, target);
                                    }
                                })
                        : Collections.<OverlapResult.MultiChannelResult>emptyList();
        List<OverlapResult.BoundingBoxDirectionResult> boundingBoxes =
                parameters.hasBoundingBoxAnalysis()
                        ? new BoundingBoxOverlap(
                                parameters,
                                images,
                                channelNames,
                                boundingBoxThresholds).run()
                        : Collections.<OverlapResult.BoundingBoxDirectionResult>emptyList();
        // Reverse directions built only for the multi pass are not part of the
        // output; drop the cache so they become collectible.
        directionCache.clear();
        return new OverlapResult(
                parameters, channelNames, directions, multi, boundingBoxes);
    }

    private OverlapResult.DirectionResult direction(int source, int target) {
        Long key = Long.valueOf(pack(source, target));
        OverlapResult.DirectionResult cached = directionCache.get(key);
        if (cached == null) {
            directionBuilds++;
            cached = buildDirection(source, target);
            directionCache.put(key, cached);
        }
        return cached;
    }

    private OverlapResult.DirectionResult buildDirection(int source, int target) {
        double threshold = thresholds.get(source).doubleValue();
        VolumeOverlap.Measurement measurement = overlap.measure(
                source,
                target,
                threshold,
                parameters.isIncludePartnerDetails(),
                parameters.getMinimumDetailOverlapPercent());
        return new OverlapResult.DirectionResult(
                source,
                target,
                channelNames.get(source),
                channelNames.get(target),
                threshold,
                measurement.volumeUnit,
                measurement.objects,
                measurement.partnerDetails,
                measurement.summary);
    }

    private static long pack(int first, int second) {
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    // ------------------------------------------------------------------
    // Validation and normalisation.
    //
    // Every rejection below is part of the plugin's contract with the user
    // and the message text is asserted by the equivalence harness. Do not
    // reword one without treating it as a user-visible change.
    // ------------------------------------------------------------------

    static Validated validate(OverlapParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException(
                    "Volumetric Colocalization parameters must not be null.");
        }
        List<ImagePlus> images = parameters.getImages();
        if (images.size() < OverlapParameters.MIN_IMAGES) {
            throw new IllegalArgumentException(
                    "Volumetric Colocalization requires at least 2 label images.");
        }
        if (images.size() > OverlapParameters.MAX_IMAGES) {
            throw new IllegalArgumentException(
                    "Volumetric Colocalization supports at most 5 label images.");
        }
        Set<ImagePlus> unique = new HashSet<ImagePlus>();
        ImagePlus first = images.get(0);
        for (int i = 0; i < images.size(); i++) {
            ImagePlus image = images.get(i);
            if (image == null || image.getStack() == null) {
                throw new IllegalArgumentException(
                        "Label image " + (i + 1) + " is null or has no stack.");
            }
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getStackSize() <= 0) {
                throw new IllegalArgumentException(
                        "Label image " + (i + 1) + " is empty.");
            }
            // Only RGB is rejected. An indexed-colour image still stores the
            // palette index as its pixel value, which is the label, so images
            // carrying a glasbey-style LUT stay usable.
            if (image.getType() == ImagePlus.COLOR_RGB) {
                throw new IllegalArgumentException(
                        "Label image " + (i + 1) + " is an RGB image, whose "
                                + "pixel values are packed colours rather than "
                                + "object labels. Convert the segmentation to "
                                + "8-, 16-, or 32-bit first.");
            }
            // The engine walks the stack as one Z series. A hyperstack's extra
            // channels or frames would be counted as further Z layers, so an
            // object's volume would be multiplied by the channel and frame
            // count instead of measured. Refuse rather than mismeasure.
            if (image.getNChannels() > 1 || image.getNFrames() > 1) {
                throw new IllegalArgumentException(
                        "Label image " + (i + 1) + " is a hyperstack ("
                                + image.getNChannels() + " channel(s), "
                                + image.getNSlices() + " slice(s), "
                                + image.getNFrames() + " frame(s)). "
                                + "Volumetric Colocalization measures one "
                                + "volume at a time. Split it with Image > "
                                + "Stacks > Tools > Make Substack, or if this "
                                + "is really a z-stack, correct the dimensions "
                                + "in Image > Properties.");
            }
            if (!unique.add(image)) {
                throw new IllegalArgumentException(
                        "Each input slot must use a different ImagePlus.");
            }
            if (image.getWidth() != first.getWidth()
                    || image.getHeight() != first.getHeight()
                    || image.getStackSize() != first.getStackSize()
                    || image.getNChannels() != first.getNChannels()
                    || image.getNSlices() != first.getNSlices()
                    || image.getNFrames() != first.getNFrames()) {
                throw new IllegalArgumentException(
                        "All label images must have identical width, height, "
                                + "channel, slice, and frame dimensions.");
            }
        }
        if (!Double.isFinite(parameters.getMinimumDetailOverlapPercent())
                || parameters.getMinimumDetailOverlapPercent() < 0.0
                || parameters.getMinimumDetailOverlapPercent() > 100.0) {
            throw new IllegalArgumentException(
                    "Minimum partner-detail overlap must be between 0 and 100 percent.");
        }

        List<String> names = normalizeNames(images, parameters.getChannelNames());
        List<Double> thresholds = normalizeThresholds(
                images.size(), parameters.getThresholdsPercent());
        List<Double> boundingBoxThresholds = normalizeThresholds(
                images.size(),
                parameters.getBoundingBoxThresholdsPercent(),
                "Bounding-box threshold",
                OverlapParameters.DEFAULT_BOUNDING_BOX_THRESHOLD_PERCENT);
        return new Validated(names, thresholds, boundingBoxThresholds);
    }

    /**
     * Blank names fall back to the image title, then to a letter. Duplicates
     * are made unique, because channel names are what multi-target patterns are
     * built from and two identical names would produce an unreadable pattern.
     */
    private static List<String> normalizeNames(List<ImagePlus> images,
                                               List<String> requested) {
        List<String> names = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < images.size(); i++) {
            String name = i < requested.size() ? requested.get(i) : null;
            if (name == null || name.trim().length() == 0) {
                name = images.get(i).getTitle();
            }
            if (name == null || name.trim().length() == 0) {
                name = "Channel " + (char) ('A' + i);
            }
            name = name.trim();
            String unique = name;
            int suffix = 2;
            while (!seen.add(unique.toLowerCase(Locale.ROOT))) {
                unique = name + " " + suffix++;
            }
            names.add(unique);
        }
        return names;
    }

    private static List<Double> normalizeThresholds(int count, List<Double> requested) {
        return normalizeThresholds(
                count,
                requested,
                "Threshold",
                OverlapParameters.DEFAULT_THRESHOLD_PERCENT);
    }

    private static List<Double> normalizeThresholds(
            int count, List<Double> requested, String label, double defaultValue) {
        if (!requested.isEmpty() && requested.size() != count) {
            throw new IllegalArgumentException(
                    label + " list must be empty or match the number of label images.");
        }
        List<Double> thresholds = new ArrayList<Double>();
        for (int i = 0; i < count; i++) {
            if (!requested.isEmpty() && requested.get(i) == null) {
                throw new IllegalArgumentException(
                        label + " " + (i + 1) + " must not be null.");
            }
            double value = requested.isEmpty()
                    ? defaultValue
                    : requested.get(i).doubleValue();
            if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
                throw new IllegalArgumentException(
                        label + " " + (i + 1)
                                + " must be between 0 and 100 percent.");
            }
            thresholds.add(Double.valueOf(value));
        }
        return thresholds;
    }

    static final class Validated {
        final List<String> channelNames;
        final List<Double> thresholds;
        final List<Double> boundingBoxThresholds;

        Validated(List<String> channelNames, List<Double> thresholds,
                  List<Double> boundingBoxThresholds) {
            this.channelNames = channelNames;
            this.thresholds = thresholds;
            this.boundingBoxThresholds = boundingBoxThresholds;
        }
    }
}
