package com.universalimporter.infrastructure.storage;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Deletes a directory tree without ever leaving it. {@code Files.walk} goes into an NTFS junction (reported as a
 * directory that is "other", not a symbolic link) and deletes what it points to; here any link or junction is
 * removed as a link, and what it points to is never visited.
 */
public final class FileTrees {

    private FileTrees() {
    }

    public static void deleteTree(Path start) throws IOException {
        if (Files.notExists(start, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                if (isLink(attributes)) {
                    Files.delete(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** A symbolic link, or a junction or other reparse point. */
    public static boolean isLink(BasicFileAttributes attributes) {
        return attributes.isSymbolicLink() || attributes.isOther();
    }
}
