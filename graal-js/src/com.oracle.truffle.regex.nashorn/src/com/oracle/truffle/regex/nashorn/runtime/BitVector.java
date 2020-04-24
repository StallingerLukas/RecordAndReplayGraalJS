/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.truffle.regex.nashorn.runtime;

// @formatter:off

import java.util.Arrays;

/**
 * Faster implementation of BitSet
 */
public final class BitVector implements Cloneable {
    /** Number of bits per slot. */
    private static final int BITSPERSLOT = 64;

    /** Growth quanta when resizing. */
    private static final int SLOTSQUANTA = 4;

    /** Shift for indexing. */
    private static final int BITSHIFT = 6;

    /** Mask for indexing. */
    private static final int BITMASK = BITSPERSLOT - 1;

    /** Bit area. */
    private long[] bits;

    /**
     * Constructor.
     */
    public BitVector() {
        this.bits = new long[SLOTSQUANTA];
    }

    /**
     * Constructor
     * @param length initial length in bits
     */
    public BitVector(final long length) {
        final int need = (int)growthNeeded(length);
        this.bits = new long[need];
    }

    /**
     * Copy constructor
     * @param bits a bits array from another bit vector
     */
    public BitVector(final long[] bits) {
        this.bits = bits.clone();
    }

    /**
     * Copy another BitVector into this one
     * @param other the source
     */
    public void copy(final BitVector other) {
        bits = other.bits.clone();
    }

    /**
     * Calculate the number of slots need for the specified length of bits.
     * @param length Number of bits required.
     * @return Number of slots needed.
     */
    private static long slotsNeeded(final long length) {
        return (length + BITMASK) >> BITSHIFT;
    }

    /**
     * Calculate the number of slots need for the specified length of bits
     * rounded to allocation quanta.
     * @param length Number of bits required.
     * @return Number of slots needed rounded to allocation quanta.
     */
    private static long growthNeeded(final long length) {
        return (slotsNeeded(length) + SLOTSQUANTA - 1) / SLOTSQUANTA * SLOTSQUANTA;
    }

    /**
     * Return a slot from bits, zero if slot is beyond length.
     * @param index Slot index.
     * @return Slot value.
     */
    private long slot(final int index) {
        return 0 <= index && index < bits.length ? bits[index] : 0L;
    }

    /**
     * Resize the bit vector to accommodate the new length.
     * @param length Number of bits required.
     */
    public void resize(final long length) {
        final int need = (int)growthNeeded(length);

        if (bits.length != need) {
            bits = Arrays.copyOf(bits, need);
        }

        final int shift = (int)(length & BITMASK);
        int slot = (int)(length >> BITSHIFT);

        if (shift != 0) {
            bits[slot] &= (1L << shift) - 1;
            slot++;
        }

        for ( ; slot < bits.length; slot++) {
            bits[slot] = 0;
        }
    }

    /**
     * Set a bit in the bit vector.
     * @param bit Bit number.
     */
    public void set(final long bit) {
        bits[(int)(bit >> BITSHIFT)] |= (1L << (int)(bit & BITMASK));
    }

    /**
     * Clear a bit in the bit vector.
     * @param bit Bit number.
     */
    public void clear(final long bit) {
        bits[(int)(bit >> BITSHIFT)] &= ~(1L << (int)(bit & BITMASK));
    }

    /**
     * Toggle a bit in the bit vector.
     * @param bit Bit number.
     */
    public void toggle(final long bit) {
        bits[(int)(bit >> BITSHIFT)] ^= (1L << (int)(bit & BITMASK));
    }

    /**
     * Sets all bits in the vector up to the length.
     *
     * @param length max bit where to stop setting bits
     */
    public void setTo(final long length) {
        if (0 < length) {
            final int lastWord = (int)(length >> BITSHIFT);
            final long lastBits = (1L << (int)(length & BITMASK)) - 1L;
            Arrays.fill(bits, 0, lastWord, ~0L);

            if (lastBits != 0L) {
                bits[lastWord] |= lastBits;
            }
        }
    }

    /**
     * Clears all bits in the vector.
     */
    public void clearAll() {
        Arrays.fill(bits, 0L);
    }

    /**
     * Test if bit is set in the bit vector.
     * @param bit Bit number.
     * @return true if bit in question is set
     */
    public boolean isSet(final long bit) {
        return (bits[(int)(bit >> BITSHIFT)] & (1L << (int)(bit & BITMASK))) != 0;
    }

    /**
     * Test if a bit is clear in the bit vector.
     * @param bit Bit number.
     * @return true if bit in question is clear
     */
    public boolean isClear(final long bit) {
        return (bits[(int)(bit >> BITSHIFT)] & (1L << (int)(bit & BITMASK))) == 0;
    }

    /**
     * Shift bits to the left by shift.
     * @param shift  Amount of shift.
     * @param length Length of vector after shift.
     */
    public void shiftLeft(final long shift, final long length) {
        if (shift != 0) {
            final int leftShift  = (int)(shift & BITMASK);
            final int rightShift = BITSPERSLOT - leftShift;
            final int slotShift  = (int)(shift >> BITSHIFT);
            final int slotCount  = bits.length - slotShift;
            int slot, from;

            if (leftShift == 0) {
                for (slot = 0, from = slotShift; slot < slotCount; slot++, from++) {
                    bits[slot] = slot(from);
                }
            } else {
                for (slot = 0, from = slotShift; slot < slotCount; slot++) {
                    bits[slot] = (slot(from) >>>  leftShift) | (slot(++from) <<  rightShift);
                }
            }
        }

        resize(length);
    }

    /**
     * Shift bits to the right by shift.
     * @param shift  Amount of shift.
     * @param length Length of vector after shift.
     */
    public void shiftRight(final long shift, final long length) {
        // Make room.
        resize(length);

        if (shift != 0) {
            final int rightShift  = (int)(shift & BITMASK);
            final int leftShift = BITSPERSLOT - rightShift;
            final int slotShift  = (int)(shift >> BITSHIFT);
            int slot, from;

            if (leftShift == 0) {
                for (slot = bits.length, from = slot - slotShift; slot >= slotShift;) {
                    slot--; from--;
                    bits[slot] = slot(from);
                }
            } else {
                for (slot = bits.length, from = slot - slotShift; slot > 0;) {
                    slot--; from--;
                    bits[slot] = (slot(from - 1) >>>  leftShift) | (slot(from) <<  rightShift);
                }
            }
        }

        // Mask out surplus.
        resize(length);
    }

    /**
     * Set a bit range.
     * @param fromIndex  from index (inclusive)
     * @param toIndex    to index (exclusive)
     */
    public void setRange(final long fromIndex, final long toIndex) {
        if (fromIndex < toIndex) {
            final int firstWord = (int)(fromIndex >> BITSHIFT);
            final int lastWord = (int)(toIndex - 1 >> BITSHIFT);
            final long firstBits = (~0L << fromIndex);
            final long lastBits = (~0L >>> -toIndex);
            if (firstWord == lastWord) {
                bits[firstWord] |= firstBits & lastBits;
            } else {
                bits[firstWord] |= firstBits;
                Arrays.fill(bits, firstWord + 1, lastWord, ~0L);
                bits[lastWord] |= lastBits;
            }
        }
    }
}
