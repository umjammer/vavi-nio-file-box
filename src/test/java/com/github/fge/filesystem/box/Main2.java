/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.filesystem.box;

import java.net.URI;
import java.nio.file.FileSystem;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.github.fge.filesystem.box.BoxFileSystemProvider;

import static vavi.nio.file.Base.testLargeFile;


/**
 * Box. (v2)
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/03/xx umjammer initial version <br>
 */
public class Main2 {

    @Test
    void test01() throws Exception {
        String email = System.getenv("BOX_TEST_ACCOUNT");

        URI uri = URI.create("box:///?id=" + email);
        FileSystem fs = new BoxFileSystemProvider().newFileSystem(uri, Collections.emptyMap());

        testLargeFile(fs, null);
    }
}