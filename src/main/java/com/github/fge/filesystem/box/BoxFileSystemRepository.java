package com.github.fge.filesystem.box;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.FileStore;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.box.sdkgen.client.BoxClient;
import com.github.fge.filesystem.driver.FileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemRepositoryBase;
import vavi.net.auth.UserCredential;
import vavi.net.auth.oauth2.OAuth2;
import vavi.net.auth.oauth2.OAuth2AppCredential;
import vavi.net.auth.oauth2.box.BoxLocalAppCredential;
import vavi.net.auth.web.box.BoxLocalUserCredential;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;


@ParametersAreNonnullByDefault
@PropsEntity(useSystem = true)
public final class BoxFileSystemRepository extends FileSystemRepositoryBase {

    private static final Logger logger = System.getLogger(BoxFileSystemRepository.class.getName());

    @Property(name = "vavi.nio.file.box.BoxFileSystemRepository.oauth2", value = "vavi.net.auth.oauth2.box.BoxOAuth2")
    private String oAuth2ClassName;

    public BoxFileSystemRepository()
    {
        super("box", new BoxFileSystemFactoryProvider());
    }

    @Nonnull
    @Override
    public FileSystemDriver createDriver(URI uri, Map<String, ?> env) throws IOException {

        // 1. user credential
        UserCredential userCredential = null;

        if (env.containsKey(BoxFileSystemProvider.ENV_USER_CREDENTIAL)) {
            userCredential = (UserCredential) env.get(BoxFileSystemProvider.ENV_USER_CREDENTIAL);
        }

        Map<String, String> params = getParamsMap(uri);
        if (userCredential == null && params.containsKey(BoxFileSystemProvider.PARAM_ID)) {
            String email = params.get(BoxFileSystemProvider.PARAM_ID);
            userCredential = new BoxLocalUserCredential(email);
        }

        if (userCredential == null) {
            throw new NoSuchElementException("uri not contains a param " + BoxFileSystemProvider.PARAM_ID + " nor " +
                                             "env not contains a param " + BoxFileSystemProvider.ENV_USER_CREDENTIAL);
        }

        // 2. app credential
        OAuth2AppCredential appCredential = null;

        if (env.containsKey(BoxFileSystemProvider.ENV_APP_CREDENTIAL)) {
            appCredential = (OAuth2AppCredential) env.get(BoxFileSystemProvider.ENV_APP_CREDENTIAL);
        }

        if (appCredential == null) {
            appCredential = new BoxLocalAppCredential(); // TODO use prop
        }

        // 3. process
        PropsEntity.Util.bind(this);
        BoxClient client = getOAuth2(appCredential).authorize(userCredential);
        FileStore store = new BoxFileStore(client, factoryProvider.getAttributesFactory());
        return new BoxFileSystemDriver(store, factoryProvider, client, env);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private OAuth2<UserCredential, BoxClient> getOAuth2(OAuth2AppCredential appCredential) {
        try {
logger.log(Level.TRACE, "oAuth2ClassName: " + oAuth2ClassName);
            return (OAuth2) Class.forName(oAuth2ClassName).getDeclaredConstructor(OAuth2AppCredential.class).newInstance(appCredential);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
