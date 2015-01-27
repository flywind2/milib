/*
 * Copyright 2015 MiLaboratory.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.milaboratory.core.mutations;

import com.milaboratory.core.sequence.*;
import com.milaboratory.util.IntArrayList;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.milaboratory.core.mutations.Mutation.*;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */

public final class MutationsUtil {
    static final Map<Alphabet, Pattern> mutationPatterns = Collections.synchronizedMap(new HashMap<Alphabet, Pattern>());

    private MutationsUtil() {
    }

    public static NSequenceWithQuality mutate(NSequenceWithQuality seq, Mutations<NucleotideSequence> mutations) {
        NucleotideSequence sequence = seq.getSequence();
        SequenceQuality quality = seq.getQuality();
        int size = seq.size();
        int newSize = seq.size() + mutations.getLengthDelta();
        SequenceQualityBuilder qualityBuilder = new SequenceQualityBuilder().ensureCapacity(newSize);
        NucleotideSequenceBuilder sequenceBuilder = new NucleotideSequenceBuilder().ensureCapacity(newSize);
        int pointer = 0;
        int mutPointer = 0;
        int mut;
        while (pointer < size || mutPointer < mutations.size()) {
            if (mutPointer < mutations.size() && ((mut = mutations.getMutation(mutPointer)) >>> POSITION_OFFSET) <= pointer)
                switch (mut & MUTATION_TYPE_MASK) {
                    case RAW_MUTATION_TYPE_SUBSTITUTION:
                        if (((mut >> FROM_OFFSET) & LETTER_MASK) != sequence.codeAt(pointer))
                            throw new IllegalArgumentException("Mutation = " + Mutation.toString(sequence.getAlphabet(), mut) +
                                    " but seq[" + pointer + "]=" + sequence.charFromCodeAt(pointer));

                        sequenceBuilder.append((byte) (mut & LETTER_MASK));
                        qualityBuilder.append(quality.value(pointer));
                        ++pointer;
                        ++mutPointer;
                        break;
                    case RAW_MUTATION_TYPE_DELETION:
                        if (((mut >> FROM_OFFSET) & LETTER_MASK) != sequence.codeAt(pointer))
                            throw new IllegalArgumentException("Mutation = " + Mutation.toString(sequence.getAlphabet(), mut) +
                                    " but seq[" + pointer + "]=" + sequence.charFromCodeAt(pointer));

                        ++pointer;
                        ++mutPointer;
                        break;
                    case RAW_MUTATION_TYPE_INSERTION:
                        sequenceBuilder.append((byte) (mut & LETTER_MASK));
                        if (pointer == 0)
                            qualityBuilder.append(quality.value(pointer));
                        else
                            qualityBuilder.append((byte) ((quality.value(pointer - 1) + quality.value(pointer)) / 2));
                        ++mutPointer;
                        break;
                }
            else {
                qualityBuilder.append(quality.value(pointer));
                sequenceBuilder.append(sequence.codeAt(pointer++));
            }
        }
        return new NSequenceWithQuality(sequenceBuilder.createAndDestroy(), qualityBuilder.createAndDestroy());
    }

    /**
     * This one shifts indels to the left at homopolymer regions Applicable to KAligner data, which normally put indels
     * randomly along such regions Required for filterMutations algorithm to work correctly Works inplace
     *
     * @param seq       reference sequence for the mutations
     * @param mutations array of mutations
     */
    public static void shiftIndelsAtHomopolymers(Sequence seq, int[] mutations) {
        int prevPos = 0;

        for (int i = 0; i < mutations.length; i++) {
            int code = mutations[i];
            if (!isSubstitution(code)) {
                int pos = getPosition(code), offset = 0;
                int nt = isDeletion(code) ? getFrom(code) : getTo(code);
                while (pos > prevPos && seq.codeAt(pos - 1) == nt) {
                    pos--;
                    offset--;
                }
                mutations[i] = move(code, offset);
                prevPos = getPosition(mutations[i]);
                if (isDeletion(mutations[i]))
                    prevPos++;
            } else {
                prevPos = getPosition(mutations[i]) + 1;
            }
        }
    }

    public static boolean check(Mutations mutations) {
        return check(mutations.mutations);
    }

    //TODO add more checks
    public static boolean check(int[] mutations) {
        for (int i = 0; i < mutations.length; ++i) {
            if (i > 0) {
                if (isDeletion(mutations[i - 1]) && isInsertion(mutations[i]) &&
                        getPosition(mutations[i - 1]) == getPosition(mutations[i]) - 1)
                    return false;
                if (isDeletion(mutations[i]) && isInsertion(mutations[i - 1]) &&
                        getPosition(mutations[i - 1]) == getPosition(mutations[i]))
                    return false;
            }
            if (isSubstitution(mutations[i]) && getFrom(mutations[i]) == getTo(mutations[i]))
                return false;
        }
        return true;
    }

    public static boolean isSorted(int[] mutations) {
        if (mutations.length == 0)
            return true;
        int position = getPosition(mutations[0]);
        int p;
        for (int i = 1; i < mutations.length; ++i) {
            if ((p = getPosition(mutations[i])) < position)
                return false;
            position = p;
        }
        return true;
    }

    public static <S extends Sequence> boolean isCompatibleWithSequence(S sequence, int[] mutations) {
        for (int mutation : mutations) {
            int position = getPosition(mutation);
            if (isInsertion(mutation)) {
                if (position >= sequence.size() + 1)
                    return false;
            } else if (position >= sequence.size() || sequence.codeAt(position) != getFrom(mutation))
                return false;
        }
        return true;
    }

    static String getMutationPatternStringForAlphabet(Alphabet alphabet) {
        StringBuilder sb = new StringBuilder();
        StringBuilder t = new StringBuilder("([\\Q");
        for (byte i = 0; i < alphabet.size(); ++i)
            t.append(alphabet.symbolFromCode(i));
        t.append("\\E])");
        sb.append("S").append(t).append("(\\d+)").append(t);
        sb.append("|");
        sb.append("D").append(t).append("(\\d+)");
        sb.append("|");
        sb.append("I").append("(\\d+)").append(t);
        return sb.toString();
    }

    static Pattern getMutationPatternForAlphabet(Alphabet alphabet) {
        Pattern pattern = mutationPatterns.get(alphabet);
        if (pattern == null) {
            mutationPatterns.put(alphabet, pattern = Pattern.compile(getMutationPatternStringForAlphabet(alphabet)));
        }
        return pattern;
    }

    /**
     * Encodes mutations in compact human-readable string, that can be decoded by method {@link #decode(String,
     * com.milaboratory.core.sequence.Alphabet)}. <p/> <p>For format see {@link com.milaboratory.core.mutations.Mutation#encode(int,
     * com.milaboratory.core.sequence.Alphabet)}.</p> <p/> <p>Mutations are just concatenated. The following RegExp can
     * be used for simple parsing of resulting string for nucleotide sequences: {@code ([SDI])([ATGC]?)(\d+)([ATGC]?)}
     * .</p>
     *
     * @param mutations mutations to encode
     * @return mutations in a human-readable string
     */
    public static String encode(int[] mutations, Alphabet alphabet) {
        StringBuilder builder = new StringBuilder();
        for (int mut : mutations)
            builder.append(Mutation.encode(mut, alphabet));
        return builder.toString();
    }

    public static String encodeFixed(int[] mutations, Alphabet alphabet) {
        StringBuilder builder = new StringBuilder();
        for (int mut : mutations) {
            builder.append(Mutation.encodeFixed(mut, alphabet));
            builder.append(":");
        }
        if (builder.length() > 0)
            builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    /**
     * Decodes mutations encoded using format described in {@link com.milaboratory.core.mutations.Mutation#encode(int,
     * com.milaboratory.core.sequence.Alphabet)}.
     *
     * @param mutations
     * @return
     */
    public static int[] decode(String mutations, Alphabet alphabet) {
        Matcher matcher = getMutationPatternForAlphabet(alphabet).matcher(mutations);
        IntArrayList list = new IntArrayList();
        while (matcher.find()) {
            switch (matcher.group(0).charAt(0)) {
                case 'S':
                    list.add(createSubstitution(Integer.parseInt(matcher.group(2)),
                            alphabet.codeFromSymbol(matcher.group(1).charAt(0)),
                            alphabet.codeFromSymbol(matcher.group(3).charAt(0))));
                    break;
                case 'D':
                    list.add(createDeletion(Integer.parseInt(matcher.group(5)),
                            alphabet.codeFromSymbol(matcher.group(4).charAt(0))));
                    break;
                case 'I':
                    list.add(createInsertion(Integer.parseInt(matcher.group(6)),
                            alphabet.codeFromSymbol(matcher.group(7).charAt(0))));
                    break;
            }
        }
        return list.toArray();
    }
}
