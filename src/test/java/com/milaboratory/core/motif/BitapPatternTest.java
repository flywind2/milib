package com.milaboratory.core.motif;

import com.milaboratory.core.mutations.MutationType;
import com.milaboratory.core.mutations.generator.UniformMutationsGenerator;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequencesUtils;
import com.milaboratory.test.TestUtil;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BitapPatternTest {
    @Test
    public void testExact1() throws Exception {
        Motif<NucleotideSequence> motif = new Motif<>(NucleotideSequence.ALPHABET, "ATTAGACA");
        NucleotideSequence seq = new NucleotideSequence("ACTGCGATAAATTAGACAGTACGTA");
        assertEquals(10, motif.toBitapPattern().exactSearch(seq));
    }

    @Test
    public void testExact2() throws Exception {
        Motif<NucleotideSequence> motif = new Motif<>(NucleotideSequence.ALPHABET, "ATTRGACA");
        NucleotideSequence seq = new NucleotideSequence("ACTGCGATAAATTAGACAGTACGTA");
        assertEquals(10, motif.toBitapPattern().exactSearch(seq));
        seq = new NucleotideSequence("ACTGCGATAAATTGGACAGTACGTA");
        assertEquals(10, motif.toBitapPattern().exactSearch(seq));
    }

    @Test
    public void testMismatchIndel1() throws Exception {
        Motif<NucleotideSequence> motif = new Motif<>(NucleotideSequence.ALPHABET, "ATTAGACA");
        NucleotideSequence seq;
        BitapMatcher bitapMatcher;

        // Exact
        seq = new NucleotideSequence("ACTGCGATAAATTAGACAGTACGTA");
        bitapMatcher = motif.toBitapPattern().mismatchAndIndelMatcherLast(1, seq);
        boolean t = false;
        int pos;
        while ((pos = bitapMatcher.findNext()) > 0)
            if (bitapMatcher.getNumberOfErrors() == 0) {
                t = true;
                break;
            }
        assertTrue(t);
        assertEquals(17, pos);

        // Deletion
        seq = new NucleotideSequence("ACTGCGATAAATAGACAGTACGTA");
        bitapMatcher = motif.toBitapPattern().mismatchAndIndelMatcherLast(1, seq);
        assertEquals(16, bitapMatcher.findNext());
        assertEquals(1, bitapMatcher.getNumberOfErrors());
        assertEquals(-1, bitapMatcher.findNext());

        // Insertion
        seq = new NucleotideSequence("ACTGCGATAAATTATGACAGTACGTA");
        bitapMatcher = motif.toBitapPattern().mismatchAndIndelMatcherLast(1, seq);
        assertEquals(18, bitapMatcher.findNext());
        assertEquals(1, bitapMatcher.getNumberOfErrors());
        assertEquals(-1, bitapMatcher.findNext());

        // Mismatch
        seq = new NucleotideSequence("ACTGCGATAAATTACACAGTACGTA");
        bitapMatcher = motif.toBitapPattern().mismatchAndIndelMatcherLast(1, seq);
        assertEquals(17, bitapMatcher.findNext());
        assertEquals(1, bitapMatcher.getNumberOfErrors());
        assertEquals(-1, bitapMatcher.findNext());
    }

    @Test
    public void testMismatchIndel2() throws Exception {
        Motif<NucleotideSequence> motif = new Motif<>(NucleotideSequence.ALPHABET, "ATTAGACA");
        NucleotideSequence seq;
        BitapMatcher bitapMatcher;

        // Exact
        seq = new NucleotideSequence("ACTGCGATAAATTAGACAGTACGTA");
        bitapMatcher = motif.toBitapPattern().mismatchAndIndelMatcherFirst(1, seq);
        boolean t = false;
        int pos;
        while ((pos = bitapMatcher.findNext()) > 0)
            if (bitapMatcher.getNumberOfErrors() == 0) {
                t = true;
                break;
            }
        assertTrue(t);
        assertEquals(10, pos);

        // Deletion
        seq = new NucleotideSequence("ACTGCGATAAATAGACAGTACGTA");
        bitapMatcher = motif.toBitapPattern().mismatchAndIndelMatcherFirst(1, seq);
        assertEquals(10, bitapMatcher.findNext());
        assertEquals(1, bitapMatcher.getNumberOfErrors());
        assertEquals(9, bitapMatcher.findNext());
        assertEquals(1, bitapMatcher.getNumberOfErrors());
        assertEquals(-1, bitapMatcher.findNext());

        // Insertion
        seq = new NucleotideSequence("ACTGCGATAAATTATGACAGTACGTA");
        bitapMatcher = motif.toBitapPattern().mismatchAndIndelMatcherFirst(1, seq);
        assertEquals(10, bitapMatcher.findNext());
        assertEquals(1, bitapMatcher.getNumberOfErrors());
        assertEquals(-1, bitapMatcher.findNext());

        // Mismatch
        seq = new NucleotideSequence("ACTGCGATAAATTACACAGTACGTA");
        bitapMatcher = motif.toBitapPattern().mismatchAndIndelMatcherFirst(1, seq);
        assertEquals(10, bitapMatcher.findNext());
        assertEquals(1, bitapMatcher.getNumberOfErrors());
        assertEquals(-1, bitapMatcher.findNext());
    }

    @Test
    public void testMismatch1() throws Exception {
        Motif<NucleotideSequence> motif = new Motif<>(NucleotideSequence.ALPHABET,
                "ATTRGACA");
        NucleotideSequence seq = new NucleotideSequence("ACTGCGATAAATTAGACAGTACGTA");
        BitapMatcher matcher = motif.toBitapPattern().mismatchOnlyMatcherFirst(1, seq);
        Assert.assertEquals(10, matcher.findNext());
        Assert.assertEquals(0, matcher.getNumberOfErrors());
    }

    @Test
    public void testMismatch2() throws Exception {
        Motif<NucleotideSequence> motif = new Motif<>(NucleotideSequence.ALPHABET,
                "ATTRGACA");
        NucleotideSequence seq = new NucleotideSequence("ACTGCGATAAATCAGACAGTACGTA");
        BitapMatcher matcher = motif.toBitapPattern().mismatchOnlyMatcherFirst(1, seq);
        Assert.assertEquals(10, matcher.findNext());
        Assert.assertEquals(1, matcher.getNumberOfErrors());
    }

    @Test
    public void testRandomMM1() throws Exception {
        RandomGenerator rg = new Well19937c();
        long seed = rg.nextLong();
        rg = new Well19937c(seed);
        int its = TestUtil.its(1000, 100000);

        out:
        for (int i = 0; i < its; ++i) {
            NucleotideSequence seq = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 5, 60);

            NucleotideSequence seqM = seq;
            int mms = 1 + rg.nextInt(Math.min(10, seq.size()));
            for (int j = 0; j < mms; ++j)
                seqM = UniformMutationsGenerator.createUniformMutationAsObject(seqM, rg, MutationType.Substitution).mutate(seqM);

            int realMMs = SequencesUtils.mismatchCount(seq, 0, seqM, 0, seqM.size());

            NucleotideSequence seqLeft = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 0, 40);
            NucleotideSequence seqRight = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 0, 40);
            NucleotideSequence fullSeq = SequencesUtils.concatenate(seqLeft, seqM, seqRight);

            Motif<NucleotideSequence> motif = new Motif<>(seq);
            BitapPattern bitapPattern = motif.toBitapPattern();

            // Not filtered

            BitapMatcher bitapMatcher = bitapPattern.mismatchOnlyMatcherFirst(mms, fullSeq);

            boolean found = false;

            int pos;
            while ((pos = bitapMatcher.findNext()) >= 0) {
                if (pos == seqLeft.size()) {
                    found = true;
                    assertEquals(realMMs, bitapMatcher.getNumberOfErrors());
                }
                assertTrue("On iteration = " + i + " with seed " + seed, SequencesUtils.mismatchCount(fullSeq, pos, seq, 0, seq.size()) <= mms);
            }

            assertTrue("On iteration = " + i + " with seed " + seed, found);
        }
    }

    @Test
    public void testRandomMM2() throws Exception {
        RandomGenerator rg = new Well19937c();
        long seed = rg.nextLong();
        rg = new Well19937c(seed);
        int its = TestUtil.its(1000, 100000);

        int e = 0;

        out:
        for (int i = 0; i < its; ++i) {
            NucleotideSequence seq = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 10, 60);

            NucleotideSequence seqM = seq;
            int mms = 1 + rg.nextInt(3);
            for (int j = 0; j < mms; ++j)
                seqM = UniformMutationsGenerator.createUniformMutationAsObject(seqM, rg, MutationType.Substitution).mutate(seqM);

            int realMMs = SequencesUtils.mismatchCount(seq, 0, seqM, 0, seqM.size());

            NucleotideSequence seqLeft = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 0, 40);
            NucleotideSequence seqRight = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 0, 40);
            NucleotideSequence fullSeq = SequencesUtils.concatenate(seqLeft, seqM, seqRight);

            Motif<NucleotideSequence> motif = new Motif<>(seq);
            BitapPattern bitapPattern = motif.toBitapPattern();

            // Filtered

            BitapMatcherFilter bitapMatcher = new BitapMatcherFilter(bitapPattern.mismatchOnlyMatcherFirst(mms, fullSeq));

            boolean found = false;

            int pos;
            while ((pos = bitapMatcher.findNext()) >= 0) {
                if (pos == seqLeft.size()) {
                    found = true;
                    assertEquals(realMMs, bitapMatcher.getNumberOfErrors());
                }
                assertTrue("On iteration = " + i + " with seed " + seed, SequencesUtils.mismatchCount(fullSeq, pos, seq, 0, seq.size()) <= mms);
            }

            if (!found)
                ++e;
        }

        assertTrue(e <= Math.max(5E-5 * its, 1.0));
    }

    @Test
    public void testRandomMMIndelLast1() throws Exception {
        RandomGenerator rg = new Well19937c();
        long seed = rg.nextLong();
        rg = new Well19937c(seed);
        int its = TestUtil.its(1000, 100000);

        out:
        for (int i = 0; i < its; ++i) {
            NucleotideSequence seq = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 5, 60);

            NucleotideSequence seqM = seq;
            int muts = 1 + rg.nextInt(Math.min(10, seq.size()));
            for (int j = 0; j < muts; ++j)
                seqM = UniformMutationsGenerator.createUniformMutationAsObject(seqM, rg).mutate(seqM);

            NucleotideSequence seqLeft = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 0, 40);
            NucleotideSequence seqRight = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 0, 40);
            NucleotideSequence fullSeq = SequencesUtils.concatenate(seqLeft, seqM, seqRight);

            Motif<NucleotideSequence> motif = new Motif<>(seq);
            BitapPattern bitapPattern = motif.toBitapPattern();
            BitapMatcher bitapMatcher = bitapPattern.mismatchAndIndelMatcherLast(muts, fullSeq);

            boolean found = false;

            int pos;
            while ((pos = bitapMatcher.findNext()) >= 0) {
                if (pos == seqLeft.size() + seqM.size() - 1)
                    found = true;
            }

            assertTrue("On iteration = " + i + " with seed " + seed, found);
        }
    }

    @Test
    public void testRandomMMIndelFirst1() throws Exception {
        RandomGenerator rg = new Well19937c();
        long seed = rg.nextLong();
        rg = new Well19937c(seed);
        int its = TestUtil.its(1000, 100000);

        out:
        for (int i = 0; i < its; ++i) {
            NucleotideSequence seq = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 5, 60);

            NucleotideSequence seqM = seq;
            int muts = 1 + rg.nextInt(Math.min(10, seq.size()));
            for (int j = 0; j < muts; ++j)
                seqM = UniformMutationsGenerator.createUniformMutationAsObject(seqM, rg).mutate(seqM);

            NucleotideSequence seqLeft = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 0, 40);
            NucleotideSequence seqRight = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 0, 40);
            NucleotideSequence fullSeq = SequencesUtils.concatenate(seqLeft, seqM, seqRight);

            Motif<NucleotideSequence> motif = new Motif<>(seq);
            BitapPattern bitapPattern = motif.toBitapPattern();
            BitapMatcher bitapMatcher = bitapPattern.mismatchAndIndelMatcherFirst(muts, fullSeq);

            boolean found = false;

            int pos;
            while ((pos = bitapMatcher.findNext()) >= 0) {
                if (pos == seqLeft.size())
                    found = true;
            }

            assertTrue("On iteration = " + i + " with seed " + seed, found);
        }
    }
}