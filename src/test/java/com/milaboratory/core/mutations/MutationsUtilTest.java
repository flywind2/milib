package com.milaboratory.core.mutations;

import com.milaboratory.core.sequence.*;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well44497a;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public class MutationsUtilTest {
    @Test
    public void test1() throws Exception {
        String m = "SA2TDC3";
        Assert.assertEquals(m,
                MutationsUtil.encode(MutationsUtil.decode(m, NucleotideSequence.ALPHABET), NucleotideSequence.ALPHABET));

        m = "SA2TD*3I12A";
        Assert.assertEquals(m,
                MutationsUtil.encode(MutationsUtil.decode(m, AminoAcidSequence.ALPHABET), AminoAcidSequence.ALPHABET));

        m = "SA2TD*3I12.";
        Assert.assertEquals(m,
                MutationsUtil.encode(MutationsUtil.decode(m, IncompleteAminoAcidSequence.ALPHABET),
                        IncompleteAminoAcidSequence.ALPHABET));
    }

    @Test
    public void testRandomEncodeDecode() throws Exception {
        RandomGenerator rnd = new Well44497a();
        int n = 1000;
        for (Alphabet alphabet : Alphabets.getAll()) {
            for (int i = 0; i < n; ++i) {
                int[] r = createRandomMutations(alphabet, rnd.nextInt(100), rnd);
                Assert.assertArrayEquals(r, MutationsUtil.decode(MutationsUtil.encode(r, alphabet), alphabet));
            }
        }
    }

    private static int[] createRandomMutations(Alphabet alphabet, int size, RandomGenerator rnd) {
        int[] r = new int[size];
        int pos = 0;
        byte type;
        for (int i = 0; i < size; ++i) {
            type = (byte) rnd.nextInt(3);
            switch (type) {
                case 0:
                    r[i] = Mutation.createSubstitution(pos, rnd.nextInt(alphabet.size()), rnd.nextInt(alphabet.size()));
                    break;
                case 1:
                    r[i] = Mutation.createDeletion(pos, rnd.nextInt(alphabet.size()));
                    break;
                case 2:
                    r[i] = Mutation.createInsertion(pos, rnd.nextInt(alphabet.size()));
                    break;
            }

            if (type != 2)
                ++pos;
            pos += rnd.nextInt(10);
        }
        return r;
    }
}