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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Source-anchored combination patterns across three or more channels: which
 * targets each source object colocalized with, and how many objects share each
 * combination.
 *
 * <p>The {@code None} and {@code — Any —} rows are <strong>always emitted</strong>,
 * even at zero, as the last two rows for every source channel. Volumetric
 * Colocalization documents them as script-readable: a script deriving the
 * non-colocalized count from the {@code None} row must not silently get nothing
 * back for a perfectly colocalized image.
 */
public final class MultiTargetSummary {

    /** Pattern label for an object that colocalized with no target. */
    public static final String NO_HITS_PATTERN = "None";
    /** Pattern label for the roll-up of every object hitting at least one target. */
    public static final String ANY_PATTERN = "\u2014 Any \u2014";
    private static final String PATTERN_SEPARATOR = " + ";

    private MultiTargetSummary() {
    }

    /** Resolves one ordered direction, so a cached direction is reused. */
    interface DirectionLookup {
        OverlapResult.DirectionResult get(int source, int target);
    }

    static List<OverlapResult.MultiChannelResult> build(
            int channelCount,
            List<String> channelNames,
            VolumeOverlap overlap,
            DirectionLookup lookup) {
        List<OverlapResult.MultiChannelResult> result =
                new ArrayList<OverlapResult.MultiChannelResult>();
        // Match CPC: multi-target analysis treats every input image as a
        // source, independently of the pairwise bidirectional setting.
        for (int source = 0; source < channelCount; source++) {
            Map<Integer, List<OverlapResult.TargetStatus>> statuses =
                    new TreeMap<Integer, List<OverlapResult.TargetStatus>>();
            int[] sourceLabels = overlap.sortedLabels(source);
            for (int sourceLabel : sourceLabels) {
                statuses.put(Integer.valueOf(sourceLabel),
                        new ArrayList<OverlapResult.TargetStatus>());
            }

            for (int target = 0; target < channelCount; target++) {
                if (target == source) continue;
                OverlapResult.DirectionResult direction = lookup.get(source, target);
                for (OverlapResult.ObjectResult object : direction.getObjects()) {
                    statuses.get(Integer.valueOf(object.getSourceLabel())).add(
                            new OverlapResult.TargetStatus(
                                    target,
                                    channelNames.get(target),
                                    object.getBestPartnerLabel(),
                                    object.getOverlapPercent(),
                                    object.isColocalized()));
                }
            }

            List<OverlapResult.MultiObjectResult> objects =
                    new ArrayList<OverlapResult.MultiObjectResult>();
            Map<String, Integer> patternCounts = new LinkedHashMap<String, Integer>();
            int anyCount = 0;
            for (int sourceLabel : sourceLabels) {
                List<OverlapResult.TargetStatus> targetStatuses =
                        statuses.get(Integer.valueOf(sourceLabel));
                Collections.sort(targetStatuses, new Comparator<OverlapResult.TargetStatus>() {
                    @Override
                    public int compare(OverlapResult.TargetStatus left,
                                       OverlapResult.TargetStatus right) {
                        return Integer.compare(left.getTargetIndex(), right.getTargetIndex());
                    }
                });
                String pattern = pattern(targetStatuses);
                if (!NO_HITS_PATTERN.equals(pattern)) anyCount++;
                objects.add(new OverlapResult.MultiObjectResult(
                        sourceLabel, pattern, targetStatuses));
                Integer previous = patternCounts.get(pattern);
                patternCounts.put(pattern, Integer.valueOf(previous == null ? 1 : previous + 1));
            }

            // Always report None, even at zero. A script deriving the
            // non-colocalized count from this row must not silently get
            // nothing back for a perfectly colocalized image. Pull it out of
            // the observed order and place it immediately before the total, so
            // the last two rows are the same for every image.
            Integer noHits = patternCounts.remove(NO_HITS_PATTERN);
            List<OverlapResult.PatternSummary> patterns =
                    new ArrayList<OverlapResult.PatternSummary>();
            for (Map.Entry<String, Integer> entry : patternCounts.entrySet()) {
                patterns.add(new OverlapResult.PatternSummary(
                        entry.getKey(),
                        entry.getValue().intValue(),
                        roundedPercent(entry.getValue().intValue(), sourceLabels.length)));
            }
            int noHitsCount = noHits == null ? 0 : noHits.intValue();
            patterns.add(new OverlapResult.PatternSummary(
                    NO_HITS_PATTERN,
                    noHitsCount,
                    roundedPercent(noHitsCount, sourceLabels.length)));
            patterns.add(new OverlapResult.PatternSummary(
                    ANY_PATTERN,
                    anyCount,
                    roundedPercent(anyCount, sourceLabels.length)));
            result.add(new OverlapResult.MultiChannelResult(
                    source, channelNames.get(source), objects, patterns));
        }
        return result;
    }

    private static String pattern(List<OverlapResult.TargetStatus> statuses) {
        StringBuilder builder = new StringBuilder();
        for (OverlapResult.TargetStatus status : statuses) {
            if (!status.isColocalized()) continue;
            if (builder.length() > 0) builder.append(PATTERN_SEPARATOR);
            builder.append(patternComponent(status.getTargetChannel()));
        }
        return builder.length() == 0 ? NO_HITS_PATTERN : builder.toString();
    }

    /**
     * Quotes a channel name that would otherwise be indistinguishable from a
     * reserved row or from a two-channel combination.
     */
    private static String patternComponent(String channel) {
        if (!NO_HITS_PATTERN.equals(channel)
                && !ANY_PATTERN.equals(channel)
                && channel.indexOf(PATTERN_SEPARATOR) < 0
                && channel.indexOf('"') < 0
                && channel.indexOf('\\') < 0) {
            return channel;
        }
        return "\"" + channel.replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    private static double roundedPercent(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }
}
