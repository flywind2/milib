/*
 * Copyright 2019 MiLaboratory, LLC
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
package com.milaboratory.util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class LambdaSemaphore {
    final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    /** (number of queued objects) : 32 bits | (number of permits) : 32 bits */
    final AtomicLong state;

    public LambdaSemaphore(int initialPermits) {
        this.state = new AtomicLong(encode(0, initialPermits));
    }

    private static long encode(int queued, int permits) {
        return ((long) queued) << 32 | (0xFFFFFFFFL & permits);
    }

    /** Extracts number of permits from 64-bit state encoded value */
    private static int permits(long state) {
        return (int) (0xFFFFFFFFL & state);
    }

    /** Extracts number of queued lambdas from 64-bit state encoded value */
    private static int queued(long state) {
        return (int) (state >>> 32);
    }

    private static boolean hasPermitsAndPendingRequests(long state) {
        return permits(state) > 0 && queued(state) > 0;
    }

    private void executePending() {
        long stateValue;
        while (hasPermitsAndPendingRequests(stateValue = state.get())) {
            if (state.compareAndSet(stateValue, stateValue - encode(1, 1))) {
                Runnable next = queue.poll();
                if (next == null)
                    throw new InternalError("Impossible state.");
                next.run();
            }
        }
    }

    public void acquire(Runnable callback) {
        queue.offer(callback);
        state.addAndGet(encode(1, 0));
        executePending();
    }

    public void release() {
        state.addAndGet(encode(0, 1));
        executePending();
    }
}