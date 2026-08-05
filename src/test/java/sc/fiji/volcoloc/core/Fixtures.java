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
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/** Deterministic label images, built in code so no binaries are committed. */
final class Fixtures {

    private Fixtures() {
    }

    /** One row of labels, as a 16-bit single-slice image. */
    static ImagePlus plane(String title, int... labels) {
        ShortProcessor processor = new ShortProcessor(labels.length, 1);
        for (int x = 0; x < labels.length; x++) processor.set(x, 0, labels[x]);
        return new ImagePlus(title, processor);
    }

    /** A stack of {@code width x height} slices, one int[] per slice. */
    static ImagePlus stack(String title, int width, int height, int[][] slices) {
        ImageStack imageStack = new ImageStack(width, height);
        for (int[] slice : slices) {
            ShortProcessor processor = new ShortProcessor(width, height);
            for (int i = 0; i < slice.length; i++) {
                processor.set(i % width, i / width, slice[i]);
            }
            imageStack.addSlice(processor);
        }
        return new ImagePlus(title, imageStack);
    }

    /** Same row of labels at a chosen bit depth, to prove depth does not matter. */
    static ImagePlus planeOfDepth(String title, int bitDepth, int... labels) {
        ImageProcessor processor;
        if (bitDepth == 8) {
            processor = new ByteProcessor(labels.length, 1);
        } else if (bitDepth == 16) {
            processor = new ShortProcessor(labels.length, 1);
        } else {
            processor = new FloatProcessor(labels.length, 1);
        }
        for (int x = 0; x < labels.length; x++) processor.setf(x, 0, labels[x]);
        return new ImagePlus(title, processor);
    }
}
