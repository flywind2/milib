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
package com.milaboratory.core.clustering;

import com.milaboratory.core.sequence.Sequence;
import com.milaboratory.core.tree.MutationGuide;
import com.milaboratory.core.tree.NeighborhoodIterator;
import com.milaboratory.core.tree.TreeSearchParameters;

import java.util.Comparator;

public interface ClusteringStrategy<T, S extends Sequence<S>>
        extends Comparator<T>, java.io.Serializable {
    boolean canAddToCluster(Cluster<T> cluster, T minorObject,
                            NeighborhoodIterator<S, T[]> iterator);

    @Deprecated
    default TreeSearchParameters getSearchParameters() {
        return null;
    }

    /**
     * Must return tree search parameters to search potential children for the cluster.
     *
     * @param cluster target cluster
     * @return tree search parameters
     */
    default TreeSearchParameters getSearchParameters(Cluster<T> cluster) {
        return getSearchParameters();
    }

    /**
     * Should return mutation guide to search potential children for the cluster.
     *
     * Cluster.head sequence will be passed to this MutationGuide as reference parameter.
     *
     * Return null to disable guided search.
     *
     * @param cluster target cluster
     * @return mutation guide or null
     */
    default MutationGuide<S> getMutationGuide(Cluster<T> cluster) {
        return null;
    }

    int getMaxClusterDepth();
}
