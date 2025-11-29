/*
 * Copyright (c) 2019 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.filesystem.box;

import java.net.URI;
import java.nio.file.FileSystem;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static vavi.nio.file.Base.testMoveFolder;


/**
 * box move folder
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2019/07/11 umjammer initial version <br>
 */
@DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
class MoveFolderTest {

    @Test
    void test01() throws Exception {
        String email = System.getenv("TEST_ACCOUNT");

        URI uri = URI.create("box:///?id=" + email);
        FileSystem fs = new BoxFileSystemProvider().newFileSystem(uri, Collections.emptyMap());

        testMoveFolder(fs);
    }
}
