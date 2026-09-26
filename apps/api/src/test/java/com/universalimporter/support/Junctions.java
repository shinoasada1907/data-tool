package com.universalimporter.support;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** NTFS junctions for tests (Windows only): unlike a symbolic link they need no privilege, and Files.walk goes into them. */
public final class Junctions {

    private Junctions() {
    }

    public static void create(Path link, Path target) throws Exception {
        Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true).start();
        assertThat(mklink.waitFor()).as(new String(mklink.getInputStream().readAllBytes())).isZero();
    }
}
