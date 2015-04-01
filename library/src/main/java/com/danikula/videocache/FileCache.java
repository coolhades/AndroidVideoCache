package com.danikula.videocache;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static com.danikula.videocache.Preconditions.checkNotNull;

/**
 * {@link Cache} that uses file for storing data.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
public class FileCache implements Cache {

    private static final String TEMP_POSTFIX = ".download";

    private RandomAccessFile dataFile;
    private File file;

    public FileCache(File file) throws ProxyCacheException {
        try {
            Preconditions.checkNotNull(file);
            boolean partialFile = isTempFile(file);
            boolean completed = file.exists() && !partialFile;
            if (completed) {
                this.dataFile = new RandomAccessFile(file, "r");
                this.file = file;
            } else {
                ProxyCacheUtils.createDirectory(file.getParentFile());
                this.file = partialFile ? file : new File(file.getParentFile(), file.getName() + TEMP_POSTFIX);
                this.dataFile = new RandomAccessFile(this.file, "rw");
            }
        } catch (IOException e) {
            throw new ProxyCacheException("Error using file " + file + " as disc cache", e);
        }
    }

    @Override
    public synchronized int available() throws ProxyCacheException {
        try {
            return (int) dataFile.length();
        } catch (IOException e) {
            throw new ProxyCacheException("Error reading length of file " + dataFile, e);
        }
    }

    @Override
    public synchronized int read(byte[] buffer, long offset, int length) throws ProxyCacheException {
        try {
            dataFile.seek(offset);
            return dataFile.read(buffer, 0, length);
        } catch (IOException e) {
            String format = "Error reading %d bytes with offset %d from file[%d bytes] to buffer[%d bytes]";
            throw new ProxyCacheException(String.format(format, length, offset, available(), buffer.length), e);
        }
    }

    @Override
    public synchronized void append(byte[] data, int length) throws ProxyCacheException {
        try {
            if (isCompleted()) {
                throw new ProxyCacheException("Error append cache: cache file " + file + " is completed!");
            }
            dataFile.seek(available());
            dataFile.write(data, 0, length);
        } catch (IOException e) {
            String format = "Error writing %d bytes to %s from buffer with size %d";
            throw new ProxyCacheException(String.format(format, length, dataFile, data.length), e);
        }
    }

    @Override
    public synchronized void close() throws ProxyCacheException {
        try {
            dataFile.close();
        } catch (IOException e) {
            throw new ProxyCacheException("Error closing file " + file, e);
        }
    }

    @Override
    public synchronized void complete() throws ProxyCacheException {
        if (isCompleted()) {
            return;
        }

        close();
        String fileName = file.getName().substring(0, file.getName().length() - TEMP_POSTFIX.length());
        File completedFile = new File(file.getParentFile(), fileName);
        boolean renamed = file.renameTo(completedFile);
        if (!renamed) {
            throw new ProxyCacheException("Error renaming file " + file + " to " + completedFile + " for completion!");
        }
        file = completedFile;
        try {
            dataFile = new RandomAccessFile(file, "r");
        } catch (IOException e) {
            throw new ProxyCacheException("Error opening " + file + " as disc cache", e);
        }
    }

    @Override
    public synchronized boolean isCompleted() {
        return !isTempFile(file);
    }

    private boolean isTempFile(File file) {
        return file.getName().endsWith(TEMP_POSTFIX);
    }
}