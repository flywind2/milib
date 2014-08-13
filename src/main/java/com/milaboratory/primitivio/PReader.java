package com.milaboratory.primitivio;

import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.CountingInputStream;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class PReader implements CanReportProgress, AutoCloseable {
    protected final PrimitivI input;
    protected final AtomicBoolean closed = new AtomicBoolean(false);
    protected final CountingInputStream countingInputStream;
    protected final long totalSize;

    protected PReader(String fileName) throws IOException {
        this(new FileInputStream(fileName));
    }

    protected PReader(File file) throws IOException {
        this(new FileInputStream(file));
    }

    protected PReader(FileInputStream stream) throws IOException {
        this.countingInputStream = new CountingInputStream(new BufferedInputStream(stream, 32768));
        this.input = new PrimitivI(this.countingInputStream);
        this.totalSize = stream.getChannel().size();
    }

    protected PReader(InputStream stream) {
        this(stream, -1);
    }

    protected PReader(InputStream stream, long totalSize) {
        this.countingInputStream = new CountingInputStream(stream);
        this.input = new PrimitivI(this.countingInputStream);
        this.totalSize = totalSize;
    }

    @Override
    public double getProgress() {
        return 1.0 * countingInputStream.getBytesRead() / totalSize;
    }

    @Override
    public boolean isFinished() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            input.close();
        }
    }
}
