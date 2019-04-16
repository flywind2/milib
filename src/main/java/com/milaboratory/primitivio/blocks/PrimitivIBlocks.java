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
package com.milaboratory.primitivio.blocks;

import cc.redberry.pipe.OutputPort;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivIState;
import com.milaboratory.util.LambdaLatch;
import com.milaboratory.util.LambdaSemaphore;
import com.milaboratory.util.io.ByteBufferDataInputAdapter;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static com.milaboratory.util.io.IOUtil.readIntBE;

public final class PrimitivIBlocks<O> extends PrimitivIOBlocksAbstract {
    /**
     * Class of target objects
     */
    private final Class<O> clazz;

    /**
     * LZ4 compressor to compress data blocks
     */
    private final LZ4FastDecompressor decompressor;

    /**
     * PrimitivI stream state
     */
    private final PrimitivIState inputState;

    /**
     * Concurrency limiting object, to keep number of concurrent IO and CPU intensive tasks within a certain limit.
     *
     * Throttled tasks are queued for execution when execution permits become available.
     */
    private final LambdaSemaphore concurrencyLimiter;

    public PrimitivIBlocks(ExecutorService executor, int concurrency, Class<O> clazz, LZ4FastDecompressor decompressor, PrimitivIState inputState) {
        super(executor, concurrency);
        this.clazz = clazz;
        this.decompressor = decompressor;
        this.inputState = inputState;
        this.concurrencyLimiter = new LambdaSemaphore(concurrency);
    }

    /**
     * Block deserialization, CPU intensive part
     */
    private List<O> deserializeBlock(byte[] header, byte[] blockAndNextHeader) {

        // Reading header
        int numberOfObjects = readIntBE(header, 1);
        int checksum = readIntBE(header, 13);
        int blockLength = blockAndNextHeader.length - BLOCK_HEADER_SIZE;
        assert blockLength == readIntBE(header, 9);

        byte[] data;
        int dataLen;
        if ((header[0] & 0x2) != 0) { // Compressed block
            int decompressedLength = readIntBE(header, 9);
            data = new byte[decompressedLength];
            // TODO correct method ???
            decompressor.decompress(blockAndNextHeader, data);
            dataLen = decompressedLength;
        } else {// Uncompressed block
            data = blockAndNextHeader;
            dataLen = blockLength;
        }

        int actualChecksum = xxHash32.hash(data, 0, dataLen, HASH_SEED);

        if (actualChecksum != checksum)
            throw new RuntimeException("Checksum mismatch. Malformed file.");

        ByteBufferDataInputAdapter dataInput = new ByteBufferDataInputAdapter(ByteBuffer.wrap(data, 0, dataLen));
        PrimitivI primitivI = inputState.createPrimitivI(dataInput);

        // Deserialization
        ArrayList<O> content = new ArrayList<>(numberOfObjects);
        for (int i = 0; i < numberOfObjects; i++)
            content.add(primitivI.readObject(clazz));

        return content;
    }

    /**
     * Helper method to create async channel for reading with this object's execution service
     */
    public AsynchronousFileChannel createAsyncChannel(Path path, OpenOption... additionalOptions) throws IOException {
        return createAsyncChannel(path, additionalOptions, StandardOpenOption.READ);
    }

    public Reader newReader(Path channel, int readAheadBlocks) throws IOException {
        return new Reader(createAsyncChannel(channel), readAheadBlocks, 0, true);
    }

    public Reader newReader(AsynchronousFileChannel channel, int readAheadBlocks, long position) {
        return new Reader(channel, readAheadBlocks, position, false);
    }

    public final class Reader {
        final AsynchronousFileChannel channel;
        final int readAheadBlocks;
        final boolean closeUnderlyingChannel;

        // Accessed from synchronized method, initially opened
        LambdaLatch currentIOLatch = new LambdaLatch(true);

        /**
         * Current read position
         */
        volatile long position;

        /**
         * End of stream was detected during previous IO operation
         */
        volatile boolean eof = false;

        /**
         * Stores current header
         */
        volatile byte[] nextHeader;

        /**
         * Blocks being red ahead
         */
        final ArrayDeque<Block<O>> blocks = new ArrayDeque<>();

        OutputPort<O> currentBlock = null;

        public Reader(AsynchronousFileChannel channel, int readAheadBlocks, long position, boolean closeUnderlyingChannel) {
            this.channel = channel;
            this.readAheadBlocks = readAheadBlocks;
            this.position = position;
            this.closeUnderlyingChannel = closeUnderlyingChannel;
            readHeader();
            readBlocksIfNeeded();
        }

        private synchronized void readHeader() {
            checkException();

            LambdaLatch previousLatch = currentIOLatch;
            LambdaLatch nextLatch = currentIOLatch = new LambdaLatch();

            previousLatch.setCallback(() -> {
                byte[] header = new byte[BLOCK_HEADER_SIZE];
                ByteBuffer buffer = ByteBuffer.wrap(header);
                channel.read(buffer, position, null, new CHAbstract() {
                    @Override
                    public void completed(Integer result, Object attachment) {
                        // Assert
                        if (result != BLOCK_HEADER_SIZE) {
                            exception = new RuntimeException("Premature EOF.");
                            exception.printStackTrace();

                            // TODO ?????????
                            // Releasing a permit for the next operation to detect the error
                            concurrencyLimiter.release();

                            return;
                        }

                        setHeader(header);

                        // Because write operations are serialized using latches,
                        // there is no concurrent access to the position variable here
                        // noinspection NonAtomicOperationOnVolatileField
                        position += BLOCK_HEADER_SIZE;

                        // Allowing next IO operation
                        nextLatch.open();
                    }
                });
            });
        }

        private synchronized void readBlock() {
            checkException();

            LambdaLatch previousLatch = currentIOLatch;
            LambdaLatch nextLatch = currentIOLatch = new LambdaLatch();

            // Creating unpopulated block and adding it to the queue
            // Block is created before actual parsing to preserve the order
            Block<O> block = new Block<>();
            blocks.offer(block);

            previousLatch.setCallback(() -> // IO operation will be enqueued after the previous one finished
                    concurrencyLimiter.acquire( // and after there will be an execution slot available
                            () -> {
                                byte[] currentHeader = nextHeader;
                                byte[] blockAndNextHeader = new byte[getNextBlockLength() + BLOCK_HEADER_SIZE];
                                ByteBuffer buffer = ByteBuffer.wrap(blockAndNextHeader);
                                channel.read(buffer, position, null, new CHAbstract() {
                                    @Override
                                    public void completed(Integer result, Object attachment) {
                                        // Assert
                                        if (result != blockAndNextHeader.length) {
                                            exception = new RuntimeException("Premature EOF.");
                                            exception.printStackTrace();

                                            // TODO ?????????
                                            // Releasing a permit for the next operation to detect the error
                                            concurrencyLimiter.release();

                                            return;
                                        }

                                        // Extracting next header from the blob
                                        byte[] header = Arrays.copyOfRange(
                                                blockAndNextHeader,
                                                blockAndNextHeader.length - BLOCK_HEADER_SIZE,
                                                blockAndNextHeader.length);
                                        setHeader(header);

                                        // Because write operations are serialized using latches,
                                        // there is no concurrent access to the position variable here
                                        // noinspection NonAtomicOperationOnVolatileField
                                        position += blockAndNextHeader.length;

                                        // Releasing next IO operation
                                        nextLatch.open();

                                        // CPU intensive task
                                        block.content = deserializeBlock(currentHeader, blockAndNextHeader);

                                        // Signaling that this block is populated
                                        block.latch.countDown();
                                    }
                                });
                            })
            );
        }

        private synchronized void readBlocksIfNeeded() {
            while (blocks.size() < readAheadBlocks) {
                readBlock();
            }
        }

        private int getNextBlockLength() {
            return readIntBE(nextHeader, 9);
        }

        private void setHeader(byte[] header) {
            this.nextHeader = header;
            if (header[0] == 0)
                eof = true;
        }
    }

    private static final class Block<O> {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile List<O> content;
    }
}
