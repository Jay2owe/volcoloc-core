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
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Optional bounding-box families copied from FLASH geometry:
 * BBColoc, BB-CPC, and BBVolColoc.
 *
 * <p>These are fast approximations of the voxel-exact measure, computed from
 * per-label bounding boxes rather than from voxel membership. They are specific
 * to this engine's reporting and stay here rather than moving into the shared
 * chassis — see {@code DECISIONS.md}.
 *
 * <p>Worker count is bounded by the {@code volcoloc.parallelism} system
 * property; row order is always the serial order regardless of it.
 */
final class BoundingBoxOverlap {

    private final OverlapParameters parameters;
    private final List<ImagePlus> images;
    private final List<String> channelNames;
    private final List<Double> thresholds;
    private final List<List<Geometry>> geometries =
            new ArrayList<List<Geometry>>();

    BoundingBoxOverlap(OverlapParameters parameters,
                       List<ImagePlus> images,
                       List<String> channelNames,
                       List<Double> thresholds) {
        this.parameters = parameters;
        this.images = images;
        this.channelNames = channelNames;
        this.thresholds = thresholds;
    }

    List<OverlapResult.BoundingBoxDirectionResult> run() {
        for (ImagePlus image : images) geometries.add(extract(image));
        List<OverlapResult.BoundingBoxDirectionResult> results =
                new ArrayList<OverlapResult.BoundingBoxDirectionResult>();
        int workers = workerCount(largestGeometryCount());
        ExecutorService executor = workers > 1
                ? Executors.newFixedThreadPool(workers) : null;
        try {
            for (int source = 0; source < images.size(); source++) {
                for (int target = source + 1; target < images.size(); target++) {
                    results.add(buildDirection(source, target, executor));
                    if (parameters.isBidirectional()) {
                        results.add(buildDirection(target, source, executor));
                    }
                }
            }
        } finally {
            stop(executor);
        }
        return results;
    }

    private OverlapResult.BoundingBoxDirectionResult buildDirection(
            int sourceIndex, int targetIndex, ExecutorService executor) {
        List<Geometry> sources = geometries.get(sourceIndex);
        final List<Geometry> partners = geometries.get(targetIndex);
        List<OverlapResult.BoundingBoxObjectResult> rows =
                new ArrayList<OverlapResult.BoundingBoxObjectResult>(sources.size());
        final double threshold = thresholds.get(sourceIndex).doubleValue();
        final int target = targetIndex;
        boolean safeParallelRead = !parameters.isIncludeBoundingBoxVolumeFill()
                || !images.get(targetIndex).getStack().isVirtual();
        if (executor == null || sources.size() < 2 || !safeParallelRead) {
            for (Geometry source : sources) {
                rows.add(buildObjectRow(
                        source, partners, target, threshold));
            }
        } else {
            List<Future<OverlapResult.BoundingBoxObjectResult>> futures =
                    new ArrayList<Future<OverlapResult.BoundingBoxObjectResult>>(sources.size());
            for (final Geometry source : sources) {
                futures.add(executor.submit(
                        new Callable<OverlapResult.BoundingBoxObjectResult>() {
                            @Override
                            public OverlapResult.BoundingBoxObjectResult call() {
                                return buildObjectRow(
                                        source, partners, target, threshold);
                            }
                        }));
            }
            collectOrdered(futures, rows);
        }
        return new OverlapResult.BoundingBoxDirectionResult(
                sourceIndex,
                targetIndex,
                channelNames.get(sourceIndex),
                channelNames.get(targetIndex),
                threshold,
                parameters.isIncludeBoundingBoxOverlap(),
                parameters.isIncludeBoundingBoxCpc(),
                parameters.isIncludeBoundingBoxVolumeFill(),
                rows);
    }

    private OverlapResult.BoundingBoxObjectResult buildObjectRow(
            Geometry source, List<Geometry> partners,
            int targetIndex, double threshold) {
            double boxOverlapPercent = Double.NaN;
            int boxOverlapPartner = 0;
            boolean boxOverlapPositive = false;
            if (parameters.isIncludeBoundingBoxOverlap()) {
                long bestIntersection = 0L;
                for (Geometry partner : partners) {
                    long intersection = source.intersectionVolume(partner);
                    if (intersection > bestIntersection) {
                        bestIntersection = intersection;
                        boxOverlapPartner = partner.label;
                    }
                }
                boxOverlapPercent = percent(
                        bestIntersection, source.boxVolume());
                boxOverlapPositive = boxOverlapPercent >= threshold;
            }

            boolean boxCpcPositive = false;
            int boxCpcPartner = 0;
            int boxCpcContains = 0;
            if (parameters.isIncludeBoundingBoxCpc()) {
                long roundedX = Math.round(source.cx);
                long roundedY = Math.round(source.cy);
                long roundedZ = Math.round(source.cz);
                for (Geometry partner : partners) {
                    if (partner.contains(roundedX, roundedY, roundedZ)) {
                        boxCpcPositive = true;
                        boxCpcPartner = partner.label;
                        break;
                    }
                }
                for (Geometry partner : partners) {
                    if (source.contains(
                            Math.round(partner.cx),
                            Math.round(partner.cy),
                            Math.round(partner.cz))) {
                        boxCpcContains++;
                    }
                }
            }

            double boxVolumeBestPercent = Double.NaN;
            double boxVolumeTotalPercent = Double.NaN;
            int boxVolumePartner = 0;
            boolean boxVolumePositive = false;
            if (parameters.isIncludeBoundingBoxVolumeFill()) {
                Fill fill = fill(source, images.get(targetIndex));
                boxVolumeBestPercent = percent(
                        fill.bestVoxels, source.boxVolume());
                boxVolumeTotalPercent = percent(
                        fill.totalVoxels, source.boxVolume());
                boxVolumePartner = fill.bestPartnerLabel;
                boxVolumePositive = boxVolumeBestPercent >= threshold;
            }

            return new OverlapResult.BoundingBoxObjectResult(
                    source.label,
                    source.boxVolume(),
                    boxOverlapPercent,
                    boxOverlapPartner,
                    boxOverlapPositive,
                    boxCpcPositive,
                    boxCpcPartner,
                    boxCpcContains,
                    boxVolumeBestPercent,
                    boxVolumeTotalPercent,
                    boxVolumePartner,
                    boxVolumePositive);
    }

    private int largestGeometryCount() {
        int largest = 0;
        for (List<Geometry> channel : geometries) {
            if (channel.size() > largest) largest = channel.size();
        }
        return largest;
    }

    private static int workerCount(int tasks) {
        if (tasks < 2) return 1;
        int configured = Integer.getInteger("volcoloc.parallelism", 0).intValue();
        int available = Runtime.getRuntime().availableProcessors();
        int desired = configured > 0 ? configured : Math.min(available, 8);
        return Math.max(1, Math.min(tasks, desired));
    }

    private static <T> void collectOrdered(
            List<Future<T>> futures, List<T> output) {
        try {
            for (Future<T> future : futures) output.add(future.get());
        } catch (InterruptedException interrupted) {
            cancel(futures);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bounding-box analysis was interrupted.", interrupted);
        } catch (ExecutionException failed) {
            cancel(futures);
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Bounding-box analysis failed.", cause);
        }
    }

    private static void cancel(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) future.cancel(true);
    }

    private static void stop(ExecutorService executor) {
        if (executor == null) return;
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<Geometry> extract(ImagePlus image) {
        Map<Integer, Geometry> byLabel = new TreeMap<Integer, Geometry>();
        ImageStack stack = image.getStack();
        for (int z = 0; z < stack.getSize(); z++) {
            ImageProcessor processor = stack.getProcessor(z + 1);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int label = Math.round(processor.getf(x, y));
                    if (label <= 0) continue;
                    Geometry geometry = byLabel.get(Integer.valueOf(label));
                    if (geometry == null) {
                        geometry = new Geometry(label);
                        byLabel.put(Integer.valueOf(label), geometry);
                    }
                    geometry.add(x, y, z);
                }
            }
        }
        List<Geometry> result =
                new ArrayList<Geometry>(byLabel.values());
        for (Geometry geometry : result) geometry.finish();
        return result;
    }

    private static Fill fill(Geometry source, ImagePlus target) {
        PrimitiveMaps.IntIntMap counts = new PrimitiveMaps.IntIntMap();
        long total = 0L;
        ImageStack stack = target.getStack();
        for (int z = source.zmin; z <= source.zmax; z++) {
            ImageProcessor processor = stack.getProcessor(z + 1);
            for (int y = source.ymin; y <= source.ymax; y++) {
                for (int x = source.xmin; x <= source.xmax; x++) {
                    int label = Math.round(processor.getf(x, y));
                    if (label <= 0) continue;
                    counts.increment(label);
                    total++;
                }
            }
        }
        int bestLabel = 0;
        int best = 0;
        for (int label : counts.sortedKeys()) {
            int count = counts.get(label);
            if (count > best) {
                best = count;
                bestLabel = label;
            }
        }
        return new Fill(bestLabel, best, total);
    }

    private static double percent(long numerator, long denominator) {
        return denominator == 0L ? 0.0 : numerator * 100.0 / denominator;
    }

    private static final class Fill {
        final int bestPartnerLabel;
        final long bestVoxels;
        final long totalVoxels;

        Fill(int bestPartnerLabel, long bestVoxels, long totalVoxels) {
            this.bestPartnerLabel = bestPartnerLabel;
            this.bestVoxels = bestVoxels;
            this.totalVoxels = totalVoxels;
        }
    }

    private static final class Geometry {
        final int label;
        long sumX;
        long sumY;
        long sumZ;
        long voxels;
        int xmin = Integer.MAX_VALUE;
        int xmax = Integer.MIN_VALUE;
        int ymin = Integer.MAX_VALUE;
        int ymax = Integer.MIN_VALUE;
        int zmin = Integer.MAX_VALUE;
        int zmax = Integer.MIN_VALUE;
        double cx;
        double cy;
        double cz;

        Geometry(int label) {
            this.label = label;
        }

        void add(int x, int y, int z) {
            sumX += x;
            sumY += y;
            sumZ += z;
            voxels++;
            if (x < xmin) xmin = x;
            if (x > xmax) xmax = x;
            if (y < ymin) ymin = y;
            if (y > ymax) ymax = y;
            if (z < zmin) zmin = z;
            if (z > zmax) zmax = z;
        }

        void finish() {
            if (voxels == 0L) return;
            cx = (double) sumX / voxels;
            cy = (double) sumY / voxels;
            cz = (double) sumZ / voxels;
        }

        long boxVolume() {
            if (voxels == 0L) return 0L;
            return (long) (xmax - xmin + 1)
                    * (ymax - ymin + 1)
                    * (zmax - zmin + 1);
        }

        boolean contains(long x, long y, long z) {
            return voxels > 0L
                    && x >= xmin && x <= xmax
                    && y >= ymin && y <= ymax
                    && z >= zmin && z <= zmax;
        }

        long intersectionVolume(Geometry other) {
            long x = Math.max(
                    0, Math.min(xmax, other.xmax)
                            - Math.max(xmin, other.xmin) + 1);
            long y = Math.max(
                    0, Math.min(ymax, other.ymax)
                            - Math.max(ymin, other.ymin) + 1);
            long z = Math.max(
                    0, Math.min(zmax, other.zmax)
                            - Math.max(zmin, other.zmin) + 1);
            return x * y * z;
        }
    }
}
