/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.filesystem.box;

import java.net.URI;
import java.util.List;

import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.folders.CreateFolderRequestBody;
import com.box.sdkgen.managers.folders.CreateFolderRequestBodyParentField;
import com.box.sdkgen.managers.webhooks.CreateWebhookRequestBody;
import com.box.sdkgen.managers.webhooks.CreateWebhookRequestBodyTargetField;
import com.box.sdkgen.managers.webhooks.UpdateWebhookByIdRequestBody;
import com.box.sdkgen.schemas.folderfull.FolderFull;
import com.box.sdkgen.schemas.item.Item;
import com.box.sdkgen.schemas.webhook.Webhook;
import com.box.sdkgen.schemas.webhook.WebhookTriggersField;
import com.box.sdkgen.schemas.webhookmini.WebhookMini;
import com.box.sdkgen.schemas.webhooks.Webhooks;
import vavi.net.auth.UserCredential;
import vavi.net.auth.oauth2.OAuth2AppCredential;
import vavi.net.auth.oauth2.box.BoxLocalAppCredential;
import vavi.net.auth.oauth2.box.BoxOAuth2;
import vavi.net.auth.web.box.BoxLocalUserCredential;
import vavi.util.Debug;


/**
 * WebHookApiTest. box v2
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/07/03 umjammer initial version <br>
 * @see "https://app.box.com/developers/console/app/216798/webhooks"
 * @see "https://developer.box.com/guides/webhooks/"
 * @see "https://github.com/box/box-java-sdk/blob/main/docs/webhooks.md"
 */
public class WebHookApiTest {

    static String websocketBaseUrl = System.getenv("VAVI_APPS_WEBHOOK_WEBSOCKET_BASE_URL");
    static String websocketPath = System.getenv("VAVI_APPS_WEBHOOK_WEBSOCKET_BOX_PATH");
    static String email = System.getenv("TEST_ACCOUNT");

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {

        UserCredential userCredential = new BoxLocalUserCredential(email);
        OAuth2AppCredential appCredential = new BoxLocalAppCredential();

        BoxClient client = new BoxOAuth2(appCredential).authorize(userCredential);

        FolderFull rootFolder = client.folders.getFolderById("0");

        // create
        URI uri = URI.create(websocketBaseUrl + websocketPath);
        // Listen for file upload events in the specified folder
        for (Item i : rootFolder.getItemCollection().getEntries()) {
            if (i.getName().equals("TEST_WEBHOOK")) {
System.out.println("rmdir " + i.getName());
                client.folders.deleteFolderById(i.getId());
            }
        }
System.out.println("mkdir " + "TEST_WEBHOOK");
        FolderFull newFolder = client.folders.createFolder(new CreateFolderRequestBody.Builder("NEW TEST_WEBHOOK", new CreateFolderRequestBodyParentField(rootFolder.getId())).build());
        // cannot set to root folder!
System.out.println("[create] webhook");
        Webhook info = client.webhooks.createWebhook(new CreateWebhookRequestBody(new CreateWebhookRequestBodyTargetField.Builder().id(newFolder.getId()).build(), uri.toString(), List.of(
                WebhookTriggersField.FILE_UPLOADED,
                WebhookTriggersField.FILE_DELETED,
                WebhookTriggersField.FILE_RENAMED,
                WebhookTriggersField.FOLDER_CREATED,
                WebhookTriggersField.FOLDER_DELETED,
                WebhookTriggersField.FOLDER_RENAMED
        )));
Debug.println(info.getId());

        // list
System.out.println("[ls] webhook");
        WebhookMini webhookMini = null;
        Webhooks webhooks = client.webhooks.getWebhooks();
        for (WebhookMini i : webhooks.getEntries()) {
Debug.println(i.getId());
            webhookMini = i;
        }
        assert webhookMini != null;

System.out.println("mkdir " + "TEST_WEBHOOK/" + "NEW FOLDER");
        client.folders.createFolder(new CreateFolderRequestBody.Builder("NEW FOLDER", new CreateFolderRequestBodyParentField(newFolder.getId())).build());

        // update
System.out.println("[update] webhook");
        Webhook preInfo = client.webhooks.getWebhookById(webhookMini.getId());
        client.webhooks.updateWebhookById(preInfo.getId(), new UpdateWebhookByIdRequestBody.Builder().address(uri.toString()).build());

        // delete
System.out.println("[delete] webhook");
        client.webhooks.deleteWebhookById(preInfo.getId());

        Thread.sleep(5000);

System.out.println("rm -rf " + newFolder.getName());
        client.folders.deleteFolderById(newFolder.getId());
    }
}
