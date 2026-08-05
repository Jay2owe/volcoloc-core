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
import java.util.List;

/**
 * Immutable input bundle for volumetric-overlap analysis.
 *
 * <p>Engine inputs only. Which tables a consumer chooses to display is a
 * presentation decision and is not represented here.
 *
 * <p>The default threshold of 30% is a <strong>reporting convention, not a
 * truth claim</strong>. The continuous overlap percentage is always retained on
 * every object row regardless of the threshold, so a consumer can re-threshold
 * or ignore the flag entirely.
 */
public final class OverlapParameters {

    public static final int MIN_IMAGES = 2;
    public static final int MAX_IMAGES = 5;
    public static final double DEFAULT_THRESHOLD_PERCENT = 30.0;
    public static final double DEFAULT_BOUNDING_BOX_THRESHOLD_PERCENT = 30.0;

    private final List<ImagePlus> images;
    private final List<String> channelNames;
    private final List<Double> thresholdsPercent;
    private final List<Double> boundingBoxThresholdsPercent;
    private final boolean bidirectional;
    private final boolean includePartnerDetails;
    private final boolean includeMultiColocalization;
    private final double minimumDetailOverlapPercent;
    private final boolean includeBoundingBoxOverlap;
    private final boolean includeBoundingBoxCpc;
    private final boolean includeBoundingBoxVolumeFill;

    private OverlapParameters(Builder builder) {
        images = immutableCopy(builder.images);
        channelNames = immutableCopy(builder.channelNames);
        thresholdsPercent = immutableCopy(builder.thresholdsPercent);
        boundingBoxThresholdsPercent =
                immutableCopy(builder.boundingBoxThresholdsPercent);
        bidirectional = builder.bidirectional;
        includePartnerDetails = builder.includePartnerDetails;
        includeMultiColocalization = builder.includeMultiColocalization;
        minimumDetailOverlapPercent = builder.minimumDetailOverlapPercent;
        includeBoundingBoxOverlap = builder.includeBoundingBoxOverlap;
        includeBoundingBoxCpc = builder.includeBoundingBoxCpc;
        includeBoundingBoxVolumeFill = builder.includeBoundingBoxVolumeFill;
    }

    public static Builder builder(List<ImagePlus> images) {
        return new Builder(images);
    }

    public static Builder builder(ImagePlus imageA, ImagePlus imageB) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        images.add(imageA);
        images.add(imageB);
        return builder(images);
    }

    public List<ImagePlus> getImages() {
        return images;
    }

    /**
     * User-facing names parallel to {@link #getImages()}. Empty entries are
     * replaced with image titles during normalisation.
     */
    public List<String> getChannelNames() {
        return channelNames;
    }

    /**
     * One outgoing-overlap threshold per source channel.
     */
    public List<Double> getThresholdsPercent() {
        return thresholdsPercent;
    }

    /**
     * One outgoing bounding-box threshold per source channel, used by
     * BBColoc and BBVolColoc.
     */
    public List<Double> getBoundingBoxThresholdsPercent() {
        return boundingBoxThresholdsPercent;
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public boolean isIncludePartnerDetails() {
        return includePartnerDetails;
    }

    public boolean isIncludeMultiColocalization() {
        return includeMultiColocalization;
    }

    /**
     * Minimum percentage of the source object's volume contributed by one
     * partner before that pair receives a detail row.
     */
    public double getMinimumDetailOverlapPercent() {
        return minimumDetailOverlapPercent;
    }

    public boolean isIncludeBoundingBoxOverlap() {
        return includeBoundingBoxOverlap;
    }

    public boolean isIncludeBoundingBoxCpc() {
        return includeBoundingBoxCpc;
    }

    public boolean isIncludeBoundingBoxVolumeFill() {
        return includeBoundingBoxVolumeFill;
    }

    public boolean hasBoundingBoxAnalysis() {
        return includeBoundingBoxOverlap
                || includeBoundingBoxCpc
                || includeBoundingBoxVolumeFill;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        if (source == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    public static final class Builder {
        private List<ImagePlus> images;
        private List<String> channelNames = new ArrayList<String>();
        private List<Double> thresholdsPercent = new ArrayList<Double>();
        private List<Double> boundingBoxThresholdsPercent =
                new ArrayList<Double>();
        private boolean bidirectional = true;
        private boolean includePartnerDetails = true;
        private boolean includeMultiColocalization = true;
        private double minimumDetailOverlapPercent = 50.0;
        private boolean includeBoundingBoxOverlap;
        private boolean includeBoundingBoxCpc;
        private boolean includeBoundingBoxVolumeFill;

        private Builder(List<ImagePlus> images) {
            this.images = images == null
                    ? new ArrayList<ImagePlus>()
                    : new ArrayList<ImagePlus>(images);
        }

        public Builder images(List<ImagePlus> images) {
            this.images = images == null
                    ? new ArrayList<ImagePlus>()
                    : new ArrayList<ImagePlus>(images);
            return this;
        }

        public Builder channelNames(List<String> channelNames) {
            this.channelNames = channelNames == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(channelNames);
            return this;
        }

        public Builder thresholdsPercent(List<Double> thresholdsPercent) {
            this.thresholdsPercent = thresholdsPercent == null
                    ? new ArrayList<Double>()
                    : new ArrayList<Double>(thresholdsPercent);
            return this;
        }

        public Builder boundingBoxThresholdsPercent(
                List<Double> boundingBoxThresholdsPercent) {
            this.boundingBoxThresholdsPercent =
                    boundingBoxThresholdsPercent == null
                            ? new ArrayList<Double>()
                            : new ArrayList<Double>(
                                    boundingBoxThresholdsPercent);
            return this;
        }

        public Builder thresholdPercent(double thresholdPercent) {
            this.thresholdsPercent = new ArrayList<Double>();
            int count = Math.max(images.size(), MIN_IMAGES);
            for (int i = 0; i < count; i++) {
                this.thresholdsPercent.add(Double.valueOf(thresholdPercent));
            }
            return this;
        }

        public Builder bidirectional(boolean bidirectional) {
            this.bidirectional = bidirectional;
            return this;
        }

        public Builder includePartnerDetails(boolean includePartnerDetails) {
            this.includePartnerDetails = includePartnerDetails;
            return this;
        }

        public Builder includeMultiColocalization(boolean includeMultiColocalization) {
            this.includeMultiColocalization = includeMultiColocalization;
            return this;
        }

        public Builder minimumDetailOverlapPercent(double minimumDetailOverlapPercent) {
            this.minimumDetailOverlapPercent = minimumDetailOverlapPercent;
            return this;
        }

        public Builder includeBoundingBoxOverlap(boolean value) {
            includeBoundingBoxOverlap = value;
            return this;
        }

        public Builder includeBoundingBoxCpc(boolean value) {
            includeBoundingBoxCpc = value;
            return this;
        }

        public Builder includeBoundingBoxVolumeFill(boolean value) {
            includeBoundingBoxVolumeFill = value;
            return this;
        }

        public OverlapParameters build() {
            return new OverlapParameters(this);
        }
    }
}
