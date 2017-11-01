package com.milaboratory.core.alignment;

import com.milaboratory.core.mutations.*;
import com.milaboratory.core.sequence.*;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Test;

import java.util.Arrays;

import static com.milaboratory.core.alignment.AlignerTest.sAbs;
import static com.milaboratory.core.alignment.PatternAndTargetAligner.*;
import static com.milaboratory.core.sequence.NucleotideSequenceCaseSensitive.fromNucleotideSequence;
import static com.milaboratory.test.TestUtil.its;
import static com.milaboratory.test.TestUtil.randomSequence;
import static org.junit.Assert.*;

public class PatternAndTargetAlignerTest {
    private static final PatternAndTargetAlignmentScoring simpleScoring = new PatternAndTargetAlignmentScoring(
            0, -9, -10, false, (byte)0, (byte)0, 0);

    private static void assertScore(int expected, float score) {
        assertEquals((float)expected, score, 0.001);
    }

    @Test
    public void testGlobal() throws Exception {
        assertScore(-19, alignGlobal(simpleScoring,
                new NucleotideSequenceCaseSensitive("ATTagaca"), new NSequenceWithQuality("ATTAAGA")).getScore());
        assertScore(-1000009, alignGlobal(simpleScoring,
                new NucleotideSequenceCaseSensitive("ATTAGACA"), new NSequenceWithQuality("ATTAAGA")).getScore());

        NucleotideSequenceCaseSensitive seq1 = new NucleotideSequenceCaseSensitive("ATttTAtaCa");
        NSequenceWithQuality seq2 = new NSequenceWithQuality("GGGAGGCATTAGACCAAT");
        assertEquals(fromNucleotideSequence(seq2.getSequence(), true),
                alignGlobal(simpleScoring, seq1, seq2).getAbsoluteMutations().mutate(seq1));

        NucleotideSequenceCaseSensitive seq3 = new NucleotideSequenceCaseSensitive("TGTC");
        NSequenceWithQuality seq4 = new NSequenceWithQuality("ACCTTTATTGACCAGGATTGCAGGACGGCCAGCCAG");
        assertEquals(fromNucleotideSequence(seq4.getSequence(), true),
                alignGlobal(simpleScoring, seq3, seq4).getAbsoluteMutations().mutate(seq3));
    }

    @Test
    public void testGlobalMove() throws Exception {
        NucleotideSequenceCaseSensitive seq1 = new NucleotideSequenceCaseSensitive("tagaattaGACA");
        NSequenceWithQuality seq2 = new NSequenceWithQuality("ATTAGTACA");
        NucleotideSequenceCaseSensitive s = new NucleotideSequenceCaseSensitive("gACATAC");
        Alignment<NucleotideSequenceCaseSensitive> a = alignGlobal(simpleScoring, seq1, seq2);
        Mutations<NucleotideSequenceCaseSensitive> muts = a.getAbsoluteMutations();
        assertEquals(fromNucleotideSequence(seq2.getSequence(), true).concatenate(s),
                muts.mutate(seq1.concatenate(s)));
        assertEquals(s.concatenate(fromNucleotideSequence(seq2.getSequence(), true)),
                muts.move(s.size()).mutate(s.concatenate(seq1)));
    }

    @Test
    public void testGlobalPosition() throws Exception {
        NucleotideSequenceCaseSensitive seq1 = new NucleotideSequenceCaseSensitive("tagaattagaca");
        NSequenceWithQuality seq2 = new NSequenceWithQuality("ATTAGTAA");

        Alignment<NucleotideSequenceCaseSensitive> a = alignGlobal(new PatternAndTargetAlignmentScoring(0,
                -10, -9, false, (byte)0, (byte)0, 0), seq1, seq2);
        Mutations<NucleotideSequenceCaseSensitive> muts = a.getAbsoluteMutations();

        int[] v = {-1, -1, -1, -1, 0, 1, 2, 3, 4, 6, -8, 7};
        for (int i = 0; i < seq1.size(); ++i)
            assertEquals(v[i], muts.convertToSeq2Position(i));
    }

    @Test
    public void testGlobalExtract() throws Exception {
        NucleotideSequenceCaseSensitive seq1 = new NucleotideSequenceCaseSensitive("gaaggaaagccccaattg");
        NSequenceWithQuality seq2 = new NSequenceWithQuality("CGTGGAGATTATGTTAGA");
        Alignment<NucleotideSequenceCaseSensitive> a = alignGlobal(simpleScoring, seq1, seq2);
        Mutations<NucleotideSequenceCaseSensitive> muts = a.getAbsoluteMutations();

        assertEquals(1, muts.extractRelativeMutationsForRange(17, 18).size());

        NucleotideSequenceCaseSensitive seq3 = new NucleotideSequenceCaseSensitive("tagaaatagaca");
        NSequenceWithQuality seq4 = new NSequenceWithQuality("ATTAGTACA");
        NucleotideSequenceCaseSensitive seq4cs = fromNucleotideSequence(seq4.getSequence(), true);

        a = alignGlobal(simpleScoring, seq3, seq4);
        muts = a.getAbsoluteMutations();

        assertEquals(seq4cs.getRange(
                sAbs(muts.convertToSeq2Position(2)),
                sAbs(muts.convertToSeq2Position(10))),
                muts.extractRelativeMutationsForRange(2, 10).mutate(seq3.getRange(2, 10)));

        assertEquals(seq4cs.getRange(
                sAbs(muts.convertToSeq2Position(2)),
                sAbs(muts.convertToSeq2Position(11))),
                muts.extractRelativeMutationsForRange(2, 11).mutate(seq3.getRange(2, 11)));
    }

    @Test
    public void testGlobalRandom() throws Exception {
        NucleotideAlphabetCaseSensitive alphabet = NucleotideSequenceCaseSensitive.ALPHABET;
        Well19937c rdi = new Well19937c();
        RandomDataGenerator random = new RandomDataGenerator(rdi);
        int z;
        int iterations = its(1000, 1000000), checkFails = 0;
        for (int i = 0; i < iterations; i++) {
            NucleotideSequenceCaseSensitive
                    seq1 = randomSequence(alphabet, rdi, 30, 40),
                    seq2 = randomSequence(alphabet, rdi, 30, 40),
                    seq3 = randomSequence(alphabet, rdi, 30, 40);
            NSequenceWithQuality
                    seq1q = new NSequenceWithQuality(seq1.toString()),
                    seq2q = new NSequenceWithQuality(seq2.toString()),
                    seq3q = new NSequenceWithQuality(seq3.toString());
            NucleotideSequenceCaseSensitive
                    seq1l = fromNucleotideSequence(seq1q.getSequence(), true),
                    seq2l = fromNucleotideSequence(seq2q.getSequence(), true),
                    seq3l = fromNucleotideSequence(seq3q.getSequence(), true);
            Mutations<NucleotideSequenceCaseSensitive>
                    m1 = alignGlobal(simpleScoring, seq1, seq2q).getAbsoluteMutations(),
                    m2 = alignGlobal(simpleScoring, seq2, seq3q).getAbsoluteMutations();

            // Testing move
            NucleotideSequenceCaseSensitive seq4 = randomSequence(alphabet, rdi, 30, 40);
            assertEquals(seq2l.concatenate(seq4), m1.mutate(seq1.concatenate(seq4)));
            assertEquals(seq4.concatenate(seq2l), m1.move(seq4.size()).mutate(seq4.concatenate(seq1)));

            // Mutate method
            assertEquals(seq2l, m1.mutate(seq1));

            // Mutate method for inverted mutations
            assertEquals(seq1, m1.invert().mutate(seq2l));

            // Extract mutations
            int divPointsCount = random.nextInt(2, seq1.size() / 3);
            int[] divPoints = new int[divPointsCount];
            divPoints[0] = -1;
            divPoints[1] = seq1.size();
            for (z = 2; z < divPointsCount; z++)
                divPoints[z] = random.nextInt(0, seq1.size());
            Arrays.sort(divPoints);
            int totalMutations = 0;
            for (z = 1; z < divPointsCount; ++z)
                totalMutations += m1.extractRelativeMutationsForRange(divPoints[z - 1], divPoints[z]).size();

            assertEquals(m1.size(), totalMutations);

            for (z = 0; z < 100; z++) {
                int from = random.nextInt(0, seq1.size() - 1);
                int to = random.nextInt(0, seq1.size() - from) + from;
                int from2 = m1.convertToSeq2Position(from);
                int to2 = m1.convertToSeq2Position(to);
                if (from2 >= 0 && to2 >= 0) {
                    if (to2 < from2)
                        to2 = from2;
                    assertEquals(seq2l.getRange(from2, to2),
                            m1.extractRelativeMutationsForRange(from, to).mutate(seq1.getRange(from, to)));
                    break;
                }
            }

            // Testing convertToSeq2Position
            TIntHashSet positions = new TIntHashSet();
            for (int j = 0; j < seq1.size(); j++)
                positions.add(j);
            for (int mut : m1.getRAWMutations())
                positions.remove(Mutation.getPosition(mut));
            TIntIterator it = positions.iterator();
            int position;
            while (it.hasNext()) {
                position = it.next();
                assertEquals(seq1.codeAt(position), seq2l.codeAt(m1.convertToSeq2Position(position)));
            }
        }
    }

    @Test
    public void testGlobalSpecialCases() throws Exception {
        alignGlobal(simpleScoring, new NucleotideSequenceCaseSensitive("ggacTgACacGGcAcTtacgTaGtGtcGTGTcGGacTc"),
                new NSequenceWithQuality("CGGATTGTTCTGGCGATAACTGAGAGTCCTGCC"));
        alignGlobal(simpleScoring, new NucleotideSequenceCaseSensitive("gCgCcaGCTTaGCCaTGCcaTttCTtaAGAtAaatCT"),
                new NSequenceWithQuality("CCTGAGATAACTACTGCGCATTAAACGCTTCCA"));
    }
}