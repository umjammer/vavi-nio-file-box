/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.filesystem.box;

import java.net.URI;
import java.nio.file.FileSystem;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.github.fge.filesystem.box.BoxFileSystemProvider;

import vavi.net.auth.oauth2.OAuth2AppCredential;
import vavi.net.auth.oauth2.box.BoxLocalAppCredential;
import vavi.net.fuse.Fuse;


/**
 * BoxFS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/02/29 umjammer initial version <br>
 */
public class BoxFS {

    /**
     * @param args 0: mount point, 1: email
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: BoxFS <mountpoint> <email>");
            System.exit(1);
        }

        String email = args[1];

        URI uri = URI.create("box:///?id=" + email);

        OAuth2AppCredential appCredential = new BoxLocalAppCredential();

//        System.setProperty("vavi.net.fuse.FuseProvider.class", "vavi.net.fuse.javafs.JavaFSFuseProvider");
//        System.setProperty("vavi.net.fuse.FuseProvider.class", "vavi.net.fuse.jnrfuse.JnrFuseFuseProvider");

        Map<String, Object> env = new HashMap<>();
        env.put(BoxFileSystemProvider.ENV_APP_CREDENTIAL, appCredential);
        env.put("ignoreAppleDouble", true);

        FileSystem fs = new BoxFileSystemProvider().newFileSystem(uri, env);

        Fuse.getFuse().mount(fs, args[0], Collections.emptyMap());
    }
}
