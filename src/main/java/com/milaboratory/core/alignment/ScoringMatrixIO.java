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
package com.milaboratory.core.alignment;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.milaboratory.core.sequence.Alphabet;
import com.milaboratory.util.IntArrayList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

final class ScoringMatrixIO {
    public static final class Deserializer extends JsonDeserializer<int[]> {
        @Override
        public int[] deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            String strValue = jp.readValueAs(String.class);
            strValue = strValue.replaceAll("\\s", "").toLowerCase();

            if (strValue.startsWith("raw(")) {

                if (!strValue.endsWith(")"))
                    throw new IOException("Not balanced brackets in : " + strValue);

                strValue = strValue.substring(4, strValue.length() - 1);
                String split[] = strValue.split(",");

                int[] values = new int[split.length];
                for (int i = 0; i < split.length; ++i)
                    values[i] = Integer.parseInt(split[i], 10);

                return values;
            }

            if (strValue.startsWith("simple(")) {
                if (!strValue.endsWith(")"))
                    throw new IOException("Not balanced brackets in : " + strValue);

                strValue = strValue.substring(7, strValue.length() - 1);

                String split[] = strValue.split(",");

                int match = Integer.MIN_VALUE, mismatch = Integer.MIN_VALUE;

                for (int i = 0; i < split.length; ++i) {
                    if (split[i].startsWith("match="))
                        match = Integer.parseInt(split[i].substring(6), 10);
                    if (split[i].startsWith("mismatch="))
                        mismatch = Integer.parseInt(split[i].substring(9), 10);
                }

                if (match == Integer.MIN_VALUE)
                    throw new IOException("Match value not set in : " + strValue);

                if (mismatch == Integer.MIN_VALUE)
                    throw new IOException("Mismatch value not set in : " + strValue);

                return new int[]{match, mismatch};
            }

            throw new IOException("Can't parse: " + strValue);
        }
    }

    public static final class Serializer extends JsonSerializer<int[]> {
        @Override
        public void serialize(int[] value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            int size = (int) (Math.sqrt(value.length));

            if (value.length != size * size)
                throw new IOException("Wrong matrix size.");

            int diagonalValue = value[0];
            int otherValue = value[1];

            boolean isSymmetric = true;

            for (int i = 0; i < size; ++i)
                for (int j = 0; j < size; ++j) {

                    if (i == j)
                        isSymmetric &= (value[size * i + j] == diagonalValue);
                    else
                        isSymmetric &= (value[size * i + j] == otherValue);

                    if (!isSymmetric)
                        break;
                }

            if (isSymmetric)
                jgen.writeString("simple(match = " + diagonalValue + ", mismatch = " + otherValue + ")");
            else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < value.length; ++i) {
                    sb.append(value[i]);
                    sb.append(", ");
                }
                sb.delete(sb.length() - 2, sb.length());
                jgen.writeString("raw(" + sb.toString() + ")");
            }
        }
    }

    /**
     * Reads BLAST AminoAcid substitution matrix from InputStream
     *
     * @param stream   InputStream
     * @param alphabet alphabet
     * @param xChars   alphabet letters
     * @return BLAST AminoAcid substitution matrix
     * @throws java.io.IOException
     */
    public static int[] readAABlastMatrix(InputStream stream, Alphabet alphabet, char... xChars) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String line;

        //Creating xValues array
        int[] xValues = new int[xChars.length];
        for (int i = 0; i < xValues.length; ++i)
            if ((xValues[i] = alphabet.symbolToCode(xChars[i])) == -1)
                throw new IllegalArgumentException("XChar not from this alphabet.");

        //~AminoAcidAlphabet.
        IntArrayList mappings = new IntArrayList(30);

        int alSize = alphabet.size();
        int[] matrix = new int[alSize * alSize];
        Arrays.fill(matrix, Integer.MIN_VALUE);

        String[] cells;
        while ((line = br.readLine()) != null) {
            //Processing comment
            if (line.startsWith("#"))
                continue;

            //Processing header
            if (line.startsWith(" ")) {
                String[] letters = line.trim().split("\\s+");
                for (int i = 0; i < letters.length; ++i)
                    mappings.add(getAACode(letters[i], alphabet));
                continue;
            }

            //Processing line with values
            cells = line.trim().split("\\s+");

            //Parsing letter in the first column
            for (int from : getVals(getAACode(cells[0], alphabet), xValues)) {
                for (int i = 1; i < cells.length; ++i)
                    for (int to : getVals(mappings.get(i - 1), xValues))
                        matrix[from * alSize + to] = Integer.parseInt(cells[i]);
            }
        }

        //Checking for matrix fullness
        for (int val : matrix)
            if (val == Integer.MIN_VALUE)
                throw new IllegalArgumentException("Some letters are missing in matrix.");

        return matrix;
    }

    private static int[] getVals(int ll, int[] xValues) {
        if (ll == -1)
            return new int[0];
        if (ll == -2)
            return xValues;
        return new int[]{ll};
    }

    /**
     * Returns AminoAcid code
     *
     * @param letter   letter
     * @param alphabet alphabet
     * @return code
     */
    private static byte getAACode(String letter, Alphabet alphabet) {
        if (letter.length() != 1)
            throw new IllegalArgumentException();
        char l = letter.charAt(0);
        if (l == 'x' || l == 'X')
            return -2;
        return alphabet.symbolToCode(l);
    }
}
