/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package sc.fiji.volcoloc.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable analysis output: per-object rows, partner-detail rows, summaries
 * and multi-target patterns.
 *
 * <p><strong>No ImageJ tables.</strong> Table construction belongs to the
 * consuming plugin, so a consumer with a different table layout — 3D Objects
 * Counter+ appending overlap columns to its own per-object table — can use this
 * model without inheriting Volumetric Colocalization's presentation.
 *
 * <p>Every object row carries the continuous {@code overlapPercent} alongside
 * the {@code colocalized} flag. The flag is a reporting convention at the
 * configured threshold; the percentage is the measurement.
 */
public final class OverlapResult {

    private final OverlapParameters parameters;
    private final List<String> channelNames;
    private final List<DirectionResult> directions;
    private final List<MultiChannelResult> multiChannelResults;
    private final List<BoundingBoxDirectionResult> boundingBoxDirections;

    OverlapResult(OverlapParameters parameters,
                  List<String> channelNames,
                  List<DirectionResult> directions,
                  List<MultiChannelResult> multiChannelResults,
                  List<BoundingBoxDirectionResult> boundingBoxDirections) {
        this.parameters = parameters;
        this.channelNames = immutableCopy(channelNames);
        this.directions = immutableCopy(directions);
        this.multiChannelResults = immutableCopy(multiChannelResults);
        this.boundingBoxDirections = immutableCopy(boundingBoxDirections);
    }

    public OverlapParameters getParameters() {
        return parameters;
    }

    /**
     * Normalised channel names — blanks replaced with image titles, duplicates
     * made unique. These are the names that appear in multi-target patterns.
     */
    public List<String> getChannelNames() {
        return channelNames;
    }

    public List<DirectionResult> getDirectionResults() {
        return directions;
    }

    public List<MultiChannelResult> getMultiChannelResults() {
        return multiChannelResults;
    }

    public List<BoundingBoxDirectionResult> getBoundingBoxDirectionResults() {
        return boundingBoxDirections;
    }

    public static final class DirectionResult {
        private final int sourceIndex;
        private final int targetIndex;
        private final String sourceChannel;
        private final String targetChannel;
        private final double thresholdPercent;
        private final String volumeUnit;
        private final List<ObjectResult> objects;
        private final List<PartnerDetail> partnerDetails;
        private final Summary summary;

        DirectionResult(int sourceIndex, int targetIndex,
                        String sourceChannel, String targetChannel,
                        double thresholdPercent,
                        String volumeUnit,
                        List<ObjectResult> objects,
                        List<PartnerDetail> partnerDetails,
                        Summary summary) {
            this.sourceIndex = sourceIndex;
            this.targetIndex = targetIndex;
            this.sourceChannel = sourceChannel;
            this.targetChannel = targetChannel;
            this.thresholdPercent = thresholdPercent;
            this.volumeUnit = volumeUnit;
            this.objects = immutableCopy(objects);
            this.partnerDetails = immutableCopy(partnerDetails);
            this.summary = summary;
        }

        public int getSourceIndex() {
            return sourceIndex;
        }

        public int getTargetIndex() {
            return targetIndex;
        }

        public String getSourceChannel() {
            return sourceChannel;
        }

        public String getTargetChannel() {
            return targetChannel;
        }

        public double getThresholdPercent() {
            return thresholdPercent;
        }

        /**
         * Calibrated volume unit of the source channel, such as
         * {@code µm^3} or {@code pixel^3}.
         */
        public String getVolumeUnit() {
            return volumeUnit;
        }

        public List<ObjectResult> getObjects() {
            return objects;
        }

        public List<PartnerDetail> getPartnerDetails() {
            return partnerDetails;
        }

        public Summary getSummary() {
            return summary;
        }
    }

    public static final class ObjectResult {
        private final int sourceLabel;
        private final int sourceVoxels;
        private final double sourceVolume;
        private final String volumeUnit;
        private final int overlapVoxels;
        private final double overlapPercent;
        private final int bestPartnerLabel;
        private final int bestPartnerOverlapVoxels;
        private final int partnerCount;
        private final boolean colocalized;

        ObjectResult(int sourceLabel, int sourceVoxels,
                     double sourceVolume, String volumeUnit,
                     int overlapVoxels, double overlapPercent,
                     int bestPartnerLabel, int bestPartnerOverlapVoxels,
                     int partnerCount, boolean colocalized) {
            this.sourceLabel = sourceLabel;
            this.sourceVoxels = sourceVoxels;
            this.sourceVolume = sourceVolume;
            this.volumeUnit = volumeUnit;
            this.overlapVoxels = overlapVoxels;
            this.overlapPercent = overlapPercent;
            this.bestPartnerLabel = bestPartnerLabel;
            this.bestPartnerOverlapVoxels = bestPartnerOverlapVoxels;
            this.partnerCount = partnerCount;
            this.colocalized = colocalized;
        }

        public int getSourceLabel() {
            return sourceLabel;
        }

        public int getSourceVoxels() {
            return sourceVoxels;
        }

        public double getSourceVolume() {
            return sourceVolume;
        }

        public String getVolumeUnit() {
            return volumeUnit;
        }

        /** Occupied voxels: the source object's voxels lying inside any target object. */
        public int getOverlapVoxels() {
            return overlapVoxels;
        }

        /**
         * Occupied percentage of the <em>source</em> object. The denominator is
         * the source object's own volume, which is what makes the measure
         * directional: A-in-B and B-in-A do not share a denominator.
         */
        public double getOverlapPercent() {
            return overlapPercent;
        }

        public int getBestPartnerLabel() {
            return bestPartnerLabel;
        }

        public int getBestPartnerOverlapVoxels() {
            return bestPartnerOverlapVoxels;
        }

        public int getPartnerCount() {
            return partnerCount;
        }

        /** Whether {@link #getOverlapPercent()} reached the configured threshold. */
        public boolean isColocalized() {
            return colocalized;
        }
    }

    public static final class PartnerDetail {
        private final int sourceLabel;
        private final int partnerLabel;
        private final int overlapVoxels;
        private final double sourceOverlapPercent;
        private final double partnerOverlapPercent;

        PartnerDetail(int sourceLabel, int partnerLabel, int overlapVoxels,
                      double sourceOverlapPercent, double partnerOverlapPercent) {
            this.sourceLabel = sourceLabel;
            this.partnerLabel = partnerLabel;
            this.overlapVoxels = overlapVoxels;
            this.sourceOverlapPercent = sourceOverlapPercent;
            this.partnerOverlapPercent = partnerOverlapPercent;
        }

        public int getSourceLabel() {
            return sourceLabel;
        }

        public int getPartnerLabel() {
            return partnerLabel;
        }

        public int getOverlapVoxels() {
            return overlapVoxels;
        }

        public double getSourceOverlapPercent() {
            return sourceOverlapPercent;
        }

        public double getPartnerOverlapPercent() {
            return partnerOverlapPercent;
        }
    }

    public static final class Summary {
        private final int objectCount;
        private final double meanOverlapPercent;
        private final double medianOverlapPercent;
        private final int colocalizedCount;
        private final double colocalizedPercent;

        Summary(int objectCount, double meanOverlapPercent,
                double medianOverlapPercent, int colocalizedCount,
                double colocalizedPercent) {
            this.objectCount = objectCount;
            this.meanOverlapPercent = meanOverlapPercent;
            this.medianOverlapPercent = medianOverlapPercent;
            this.colocalizedCount = colocalizedCount;
            this.colocalizedPercent = colocalizedPercent;
        }

        public int getObjectCount() {
            return objectCount;
        }

        public double getMeanOverlapPercent() {
            return meanOverlapPercent;
        }

        public double getMedianOverlapPercent() {
            return medianOverlapPercent;
        }

        public int getColocalizedCount() {
            return colocalizedCount;
        }

        public double getColocalizedPercent() {
            return colocalizedPercent;
        }
    }

    public static final class BoundingBoxDirectionResult {
        private final int sourceIndex;
        private final int targetIndex;
        private final String sourceChannel;
        private final String targetChannel;
        private final double thresholdPercent;
        private final boolean includeBoundingBoxOverlap;
        private final boolean includeBoundingBoxCpc;
        private final boolean includeBoundingBoxVolumeFill;
        private final List<BoundingBoxObjectResult> objects;

        BoundingBoxDirectionResult(
                int sourceIndex, int targetIndex,
                String sourceChannel, String targetChannel,
                double thresholdPercent,
                boolean includeBoundingBoxOverlap,
                boolean includeBoundingBoxCpc,
                boolean includeBoundingBoxVolumeFill,
                List<BoundingBoxObjectResult> objects) {
            this.sourceIndex = sourceIndex;
            this.targetIndex = targetIndex;
            this.sourceChannel = sourceChannel;
            this.targetChannel = targetChannel;
            this.thresholdPercent = thresholdPercent;
            this.includeBoundingBoxOverlap = includeBoundingBoxOverlap;
            this.includeBoundingBoxCpc = includeBoundingBoxCpc;
            this.includeBoundingBoxVolumeFill = includeBoundingBoxVolumeFill;
            this.objects = immutableCopy(objects);
        }

        public int getSourceIndex() {
            return sourceIndex;
        }

        public int getTargetIndex() {
            return targetIndex;
        }

        public String getSourceChannel() {
            return sourceChannel;
        }

        public String getTargetChannel() {
            return targetChannel;
        }

        public double getThresholdPercent() {
            return thresholdPercent;
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

        public List<BoundingBoxObjectResult> getObjects() {
            return objects;
        }
    }

    public static final class BoundingBoxObjectResult {
        private final int sourceLabel;
        private final long boxVolume;
        private final double boundingBoxOverlapPercent;
        private final int boundingBoxOverlapPartnerLabel;
        private final boolean boundingBoxOverlapColocalized;
        private final boolean boundingBoxCpcColocalized;
        private final int boundingBoxCpcPartnerLabel;
        private final int boundingBoxCpcContainsCount;
        private final double boundingBoxVolumeBestPercent;
        private final double boundingBoxVolumeTotalPercent;
        private final int boundingBoxVolumePartnerLabel;
        private final boolean boundingBoxVolumeColocalized;

        BoundingBoxObjectResult(
                int sourceLabel,
                long boxVolume,
                double boundingBoxOverlapPercent,
                int boundingBoxOverlapPartnerLabel,
                boolean boundingBoxOverlapColocalized,
                boolean boundingBoxCpcColocalized,
                int boundingBoxCpcPartnerLabel,
                int boundingBoxCpcContainsCount,
                double boundingBoxVolumeBestPercent,
                double boundingBoxVolumeTotalPercent,
                int boundingBoxVolumePartnerLabel,
                boolean boundingBoxVolumeColocalized) {
            this.sourceLabel = sourceLabel;
            this.boxVolume = boxVolume;
            this.boundingBoxOverlapPercent = boundingBoxOverlapPercent;
            this.boundingBoxOverlapPartnerLabel =
                    boundingBoxOverlapPartnerLabel;
            this.boundingBoxOverlapColocalized =
                    boundingBoxOverlapColocalized;
            this.boundingBoxCpcColocalized = boundingBoxCpcColocalized;
            this.boundingBoxCpcPartnerLabel = boundingBoxCpcPartnerLabel;
            this.boundingBoxCpcContainsCount = boundingBoxCpcContainsCount;
            this.boundingBoxVolumeBestPercent =
                    boundingBoxVolumeBestPercent;
            this.boundingBoxVolumeTotalPercent =
                    boundingBoxVolumeTotalPercent;
            this.boundingBoxVolumePartnerLabel =
                    boundingBoxVolumePartnerLabel;
            this.boundingBoxVolumeColocalized =
                    boundingBoxVolumeColocalized;
        }

        public int getSourceLabel() {
            return sourceLabel;
        }

        public long getBoxVolume() {
            return boxVolume;
        }

        public double getBoundingBoxOverlapPercent() {
            return boundingBoxOverlapPercent;
        }

        public int getBoundingBoxOverlapPartnerLabel() {
            return boundingBoxOverlapPartnerLabel;
        }

        public boolean isBoundingBoxOverlapColocalized() {
            return boundingBoxOverlapColocalized;
        }

        public boolean isBoundingBoxCpcColocalized() {
            return boundingBoxCpcColocalized;
        }

        public int getBoundingBoxCpcPartnerLabel() {
            return boundingBoxCpcPartnerLabel;
        }

        public int getBoundingBoxCpcContainsCount() {
            return boundingBoxCpcContainsCount;
        }

        public double getBoundingBoxVolumeBestPercent() {
            return boundingBoxVolumeBestPercent;
        }

        public double getBoundingBoxVolumeTotalPercent() {
            return boundingBoxVolumeTotalPercent;
        }

        public int getBoundingBoxVolumePartnerLabel() {
            return boundingBoxVolumePartnerLabel;
        }

        public boolean isBoundingBoxVolumeColocalized() {
            return boundingBoxVolumeColocalized;
        }
    }

    public static final class MultiChannelResult {
        private final int sourceIndex;
        private final String sourceChannel;
        private final List<MultiObjectResult> objects;
        private final List<PatternSummary> patterns;

        MultiChannelResult(int sourceIndex, String sourceChannel,
                           List<MultiObjectResult> objects,
                           List<PatternSummary> patterns) {
            this.sourceIndex = sourceIndex;
            this.sourceChannel = sourceChannel;
            this.objects = immutableCopy(objects);
            this.patterns = immutableCopy(patterns);
        }

        public int getSourceIndex() {
            return sourceIndex;
        }

        public String getSourceChannel() {
            return sourceChannel;
        }

        public List<MultiObjectResult> getObjects() {
            return objects;
        }

        /**
         * Combination-pattern counts. The {@code None} and {@code — Any —} rows
         * are always present, in that order, as the final two entries — a
         * script reading the non-colocalized count must not silently get
         * nothing back for a perfectly colocalized image.
         */
        public List<PatternSummary> getPatterns() {
            return patterns;
        }
    }

    public static final class MultiObjectResult {
        private final int sourceLabel;
        private final String pattern;
        private final List<TargetStatus> targets;

        MultiObjectResult(int sourceLabel, String pattern, List<TargetStatus> targets) {
            this.sourceLabel = sourceLabel;
            this.pattern = pattern;
            this.targets = immutableCopy(targets);
        }

        public int getSourceLabel() {
            return sourceLabel;
        }

        public String getPattern() {
            return pattern;
        }

        public List<TargetStatus> getTargets() {
            return targets;
        }

        /** Number of targets this object colocalized with — the Targets Hit column. */
        public int getTargetsHit() {
            int hit = 0;
            for (TargetStatus status : targets) {
                if (status.isColocalized()) hit++;
            }
            return hit;
        }
    }

    public static final class TargetStatus {
        private final int targetIndex;
        private final String targetChannel;
        private final int partnerLabel;
        private final double overlapPercent;
        private final boolean colocalized;

        TargetStatus(int targetIndex, String targetChannel, int partnerLabel,
                     double overlapPercent, boolean colocalized) {
            this.targetIndex = targetIndex;
            this.targetChannel = targetChannel;
            this.partnerLabel = partnerLabel;
            this.overlapPercent = overlapPercent;
            this.colocalized = colocalized;
        }

        public int getTargetIndex() {
            return targetIndex;
        }

        public String getTargetChannel() {
            return targetChannel;
        }

        public int getPartnerLabel() {
            return partnerLabel;
        }

        public double getOverlapPercent() {
            return overlapPercent;
        }

        public boolean isColocalized() {
            return colocalized;
        }
    }

    public static final class PatternSummary {
        private final String pattern;
        private final int objectCount;
        private final double objectPercent;

        PatternSummary(String pattern, int objectCount, double objectPercent) {
            this.pattern = pattern;
            this.objectCount = objectCount;
            this.objectPercent = objectPercent;
        }

        public String getPattern() {
            return pattern;
        }

        public int getObjectCount() {
            return objectCount;
        }

        public double getObjectPercent() {
            return objectPercent;
        }
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        if (source == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }
}
