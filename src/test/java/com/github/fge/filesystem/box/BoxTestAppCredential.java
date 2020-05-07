/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.filesystem.box;

import vavi.net.auth.oauth2.BaseLocalAppCredential;
import vavi.net.auth.oauth2.BasicAppCredential;
import vavi.util.properties.annotation.Env;
import vavi.util.properties.annotation.PropsEntity;


/**
 * BoxTestAppCredential.
 * <p>
 * environment variables
 * <ul>
 * <li> TEST_CLIENT_ID
 * <li> TEST_CLIENT_SECRET
 * <li> TEST_REDIRECT_URL
 * </ul>
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/05/01 umjammer initial version <br>
 * @see "https://app.box.com/developers/console"
 */
@PropsEntity
public class BoxTestAppCredential extends BaseLocalAppCredential implements BasicAppCredential {

    @Env(name = "TEST_CLIENT_ID")
    private transient String clientId;

    @Env(name = "TEST_CLIENT_SECRET")
    private transient String clientSecret;

    @Env(name = "TEST_REDIRECT_URL")
    private String redirectUrl;

    @Override
    public String getApplicationName() {
        return "vavi-apps-fuse";
    }

    @Override
    public String getScheme() {
        return "box";
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public String getClientSecret() {
        return clientSecret;
    }

    @Override
    public String getRedirectUrl() {
        return redirectUrl;
    }

    @Override
    public String getOAuthAuthorizationUrl() {
        return null;
    }

    @Override
    public String getOAuthTokenUrl() {
        return null;
    }

    @Override
    public String getScope() {
        return null;
    }
}

/* */
