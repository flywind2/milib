package com.milaboratory.core.alignment.kaligner2;

import cc.redberry.pipe.OutputPort;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.mutations.generator.GenericNucleotideMutationModel;
import com.milaboratory.core.mutations.generator.MutationsGenerator;
import com.milaboratory.core.mutations.generator.NucleotideMutationModel;
import com.milaboratory.core.mutations.generator.SubstitutionModels;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.milaboratory.test.TestUtil.randomSequence;

/**
 * Created by dbolotin on 27/10/15.
 */
public class ChallengeProvider implements OutputPort<Challenge> {
    public static int MAX_RERUNS = 200;
    final ChallengeParameters parameters;
    final RandomGenerator gen;
    final RandomDataGenerator rdg;

    public ChallengeProvider(ChallengeParameters parameters, long seed) {
        this.parameters = parameters;
        this.gen = new Well19937c(seed);
        this.rdg = new RandomDataGenerator(gen);
    }

    @Override
    public Challenge take() {
        NucleotideSequence[] db = generateDB(rdg, parameters);

        long seed = gen.nextLong();
        final RandomGenerator rg = new Well19937c(seed);
        final RandomDataGenerator generator = new RandomDataGenerator(rg);
        final AtomicInteger counter = new AtomicInteger();

        List<KAligner2Query> queries = new ArrayList<>();

        int consequentReruns = 0;

        for (int n = 0; n < parameters.queryCount; n++) {
            MutationsBuilder<NucleotideSequence> totalMutations = new MutationsBuilder<>(NucleotideSequence.ALPHABET);

            NucleotideMutationModel model = parameters.mutationModel.clone();
            model.reseed(generator.getRandomGenerator().nextLong());
            int targetId = generator.nextInt(0, db.length - 1);
            NucleotideSequence target = db[targetId];
            SequenceBuilder<NucleotideSequence> queryBuilder = NucleotideSequence.ALPHABET.getBuilder();
            if (generator.nextUniform(0, 1) < parameters.boundaryInsertProbability)
                queryBuilder.append(randomSequence(NucleotideSequence.ALPHABET, generator,
                        parameters.minIndelLength, parameters.maxIndelLength, true));

            List<Range> tRanges = new ArrayList<>(), qRanges = new ArrayList<>();
            List<Mutations<NucleotideSequence>> muts = new ArrayList<>();

            int tOffset = generator.nextInt(0, parameters.maxIndelLength), qOffset = queryBuilder.size();
            Range r;
            Mutations<NucleotideSequence> m;
            NucleotideSequence ins;
            double v;
            for (int i = parameters.minClusters == parameters.maxClusters ? parameters.minClusters :
                    generator.nextInt(parameters.minClusters, parameters.maxClusters); i > 0; --i)
                if (tRanges.isEmpty()) {
                    r = new Range(tOffset, tOffset += generator.nextInt(parameters.minClusterLength,
                            parameters.maxClusterLength));
                    if (r.getTo() > target.size())
                        break;
                    tRanges.add(r);
                    muts.add(m = MutationsGenerator.generateMutations(target, model, r));
                    NucleotideSequence queryPart = m.move(-r.getFrom()).mutate(target.getRange(r));
                    qRanges.add(new Range(qOffset, qOffset += queryPart.size()));
                    queryBuilder.append(queryPart);
                    totalMutations.append(m);
                } else {
                    MutationsBuilder<NucleotideSequence> tempMutations =
                            new MutationsBuilder<>(NucleotideSequence.ALPHABET);
                    if ((v = generator.nextUniform(0, 1.0)) < parameters.insertionProbability) {
                        // Insertion into target
                        ins = randomSequence(NucleotideSequence.ALPHABET, generator, parameters.minIndelLength,
                                parameters.maxIndelLength, true);
                        tempMutations.appendInsertion(tOffset, ins);
                    } else if (v < parameters.insertionProbability + parameters.deletionProbability) {
                        // Deletion from target
                        int previousOffset = tOffset;
                        tOffset += generator.nextInt(parameters.minIndelLength, parameters.maxIndelLength);
                        ins = NucleotideSequence.EMPTY;
                        if (tOffset <= target.size())
                            tempMutations.appendDeletion(previousOffset, tOffset - previousOffset, target);
                    } else {
                        // Big mismatch
                        ins = randomSequence(NucleotideSequence.ALPHABET, generator, parameters.minIndelLength,
                                parameters.maxIndelLength, true);
                        int previousOffset = tOffset;
                        tOffset += generator.nextInt(parameters.minIndelLength, parameters.maxIndelLength);
                        Alignment<NucleotideSequence> result =
                                Aligner.alignGlobalAffine(parameters.scoring, target.getRange(previousOffset, tOffset),
                                        ins);
                        if (tOffset <= target.size())
                            tempMutations.append(result.getAbsoluteMutations().move(previousOffset));
                    }
                    r = new Range(tOffset, tOffset += generator.nextInt(parameters.minClusterLength,
                            parameters.maxClusterLength));
                    if (r.getTo() > target.size())
                        break;
                    totalMutations.append(tempMutations);
                    tRanges.add(r);
                    muts.add(m = MutationsGenerator.generateMutations(target, model, r));
                    qRanges.add(new Range(qOffset += ins.size(), qOffset += r.length() + m.getLengthDelta()));
                    queryBuilder.append(ins).append(m.move(-r.getFrom()).mutate(target.getRange(r)));
                    totalMutations.append(m);
                }

            if (generator.nextUniform(0, 1) < parameters.boundaryInsertProbability)
                queryBuilder.append(randomSequence(NucleotideSequence.ALPHABET, generator, parameters.minIndelLength,
                        parameters.maxIndelLength, true));

            Alignment<NucleotideSequence> expectedAlignment = new Alignment<>(target,
                    totalMutations.createAndDestroy(),
                    new Range(tRanges.get(0).getFrom(), tRanges.get(tRanges.size() - 1).getTo()),
                    new Range(qRanges.get(0).getFrom(), qRanges.get(qRanges.size() - 1).getTo()),
                    parameters.scoring);

            if (expectedAlignment.getScore() < parameters.minAlignmentScoring ||
                    expectedAlignment.getScore() > parameters.maxAlignmentScoring) {
                --n;
                if (++consequentReruns > MAX_RERUNS)
                    throw new RuntimeException("Too many reruns.");
                continue;
            }

            consequentReruns = 0;

            NucleotideSequence q = queryBuilder.createAndDestroy();

            assert AlignmentUtils.getAlignedSequence2Part(expectedAlignment)
                    .equals(q.getRange(expectedAlignment.getSequence2Range()));

            KAligner2Query query = new KAligner2Query(targetId, qRanges, tRanges, muts,
                    q, expectedAlignment);
            queries.add(query);
        }

        return new Challenge(db, queries, parameters, seed);
    }

    public static NucleotideSequence[] generateDB(RandomDataGenerator generator, ChallengeParameters params) {
        NucleotideSequence[] db = new NucleotideSequence[params.dbSize];
        for (int i = 0; i < params.dbSize; i++)
            db[i] = randomSequence(NucleotideSequence.ALPHABET, generator, params.dbMinSeqLength, params.dbMaxSeqLength);
        return db;
    }

    public static ChallengeParameters getParams1(AffineGapAlignmentScoring<NucleotideSequence> scoring,
                                                 int minAlignmentScoring, int maxAlignmentScoring,
                                                 double multiplier) {
        return new ChallengeParameters(100, 100, 500,
                100000,
                1, 4, 15, 50, 3, 30,
                0.45, 0.45, 0.5,
                new GenericNucleotideMutationModel(
                        SubstitutionModels.getEmpiricalNucleotideSubstitutionModel(),
                        0.000522, 0.000198).multiplyProbabilities(multiplier),
                minAlignmentScoring, maxAlignmentScoring,
                scoring
        );
    }

    public static ChallengeParameters getParams1NoGap(AffineGapAlignmentScoring<NucleotideSequence> scoring,
                                                      int minAlignmentScoring, int maxAlignmentScoring,
                                                      double multiplier) {
        return new ChallengeParameters(100, 100, 500,
                100000,
                1, 4, 15, 50, 3, 30,
                0.45, 0.45, 0.5,
                new GenericNucleotideMutationModel(
                        SubstitutionModels.getEmpiricalNucleotideSubstitutionModel(),
                        0, 0).multiplyProbabilities(multiplier),
                minAlignmentScoring, maxAlignmentScoring,
                scoring
        );
    }

    public static ChallengeParameters getParams2OneCluster(AffineGapAlignmentScoring<NucleotideSequence> scoring,
                                                           int minAlignmentScoring, int maxAlignmentScoring,
                                                           double multiplier) {
        return new ChallengeParameters(100, 100, 500,
                100000,
                1, 1, 30, 80, 3, 30,
                0.45, 0.45, 0.5,
                new GenericNucleotideMutationModel(
                        SubstitutionModels.getEmpiricalNucleotideSubstitutionModel(),
                        0.000522, 0.000198).multiplyProbabilities(multiplier),
                minAlignmentScoring, maxAlignmentScoring,
                scoring
        );
    }
}
