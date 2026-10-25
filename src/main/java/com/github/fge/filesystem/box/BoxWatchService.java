/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package com.github.fge.filesystem.box;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.box.sdkgen.box.errors.BoxAPIError;
import com.box.sdkgen.box.errors.BoxSDKError;
import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.folders.GetFolderItemsQueryParams;
import com.box.sdkgen.managers.webhooks.CreateWebhookRequestBody;
import com.box.sdkgen.managers.webhooks.CreateWebhookRequestBodyTargetField;
import com.box.sdkgen.managers.webhooks.CreateWebhookRequestBodyTargetTypeField;
import com.box.sdkgen.managers.webhooks.GetWebhooksQueryParams;
import com.box.sdkgen.schemas.item.Item;
import com.box.sdkgen.schemas.items.Items;
import com.box.sdkgen.schemas.webhook.Webhook;
import com.box.sdkgen.schemas.webhook.WebhookTriggersField;
import com.box.sdkgen.schemas.webhookmini.WebhookMini;
import com.box.sdkgen.schemas.webhooks.Webhooks;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vavi.nio.file.watch.webhook.WebHookBaseWatchService;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;


/**
 * BoxWatchService.
 * <p>
 * notification sources are webhooks for each folder just under the root,
 * because a webhook cannot be created for the root folder.
 * so changes of files just under the root folder are not notified.
 * folders just under the root are rescanned periodically.
 * <p>
 * system properties
 * <ul>
 * <li> vavi.nio.file.watch.webhook.NotificationProvider.box
 * </ul>
 * </p>
 * <p>
 * environment variables
 * <ul>
 * <li> VAVI_APPS_WEBHOOK_WEBHOOK_BOX_URL
 * </ul>
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/07/22 umjammer initial version <br>
 * @see "https://developer.box.com/guides/webhooks/v2/"
 */
public class BoxWatchService extends WebHookBaseWatchService<String> {

    private static final Logger logger = System.getLogger(BoxWatchService.class.getName());

    private static final String WEBHOOK_NOTIFICATION_PROVIDER =
            System.getProperty("vavi.nio.file.watch.webhook.NotificationProvider.box", ".box.webhook.websocket");

    private static final String webhooktUrl = System.getenv("VAVI_APPS_WEBHOOK_WEBHOOK_BOX_URL");

    /** the root folder id, webhooks cannot be created for this */
    private static final String ROOT_ID = "0";

    /** interval for rescanning folders just under the root */
    private static final int RESCAN_INTERVAL_MINUTES = 5;

    private static final List<WebhookTriggersField> TRIGGERS = List.of(
            WebhookTriggersField.FILE_UPLOADED,
            WebhookTriggersField.FILE_TRASHED,
            WebhookTriggersField.FILE_DELETED,
            WebhookTriggersField.FILE_RESTORED,
            WebhookTriggersField.FILE_COPIED,
            WebhookTriggersField.FILE_MOVED,
            WebhookTriggersField.FILE_RENAMED,
            WebhookTriggersField.FOLDER_CREATED,
            WebhookTriggersField.FOLDER_RENAMED,
            WebhookTriggersField.FOLDER_RESTORED,
            WebhookTriggersField.FOLDER_DELETED,
            WebhookTriggersField.FOLDER_COPIED,
            WebhookTriggersField.FOLDER_MOVED,
            WebhookTriggersField.FOLDER_TRASHED
    );

    /**
     * box allows only one webhook for a target per an application, so webhooks are shared by instances.
     * all followings are guarded by this lock.
     */
    private static final Object lock = new Object();
    /** target folder id → webhook id */
    private static final Map<String, String> webhooks = new HashMap<>();
    /** webhook id → target folder id */
    private static final Map<String, String> targets = new HashMap<>();
    /**
     * target folder id → instances.
     * a notification is delivered only to the first instance's callback (see {@link WebHookBaseWatchService#setupNotification}),
     * so dispatch it to the instances by webhook id.
     */
    private static final Map<String, Set<BoxWatchService>> subscribers = new HashMap<>();

    private final BoxClient client;

    /** target folder ids this instance subscribes */
    private final Set<String> folderIds = new HashSet<>();

    private final ScheduledExecutorService rescanner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "box-webhook-rescanner");
        thread.setDaemon(true);
        return thread;
    });

    /** */
    public BoxWatchService(BoxClient client) throws IOException {
        this.client = client;

        setupNotification(this, WEBHOOK_NOTIFICATION_PROVIDER);

        try {
            rescan();
        } catch (BoxSDKError e) {
            close();
            throw new IOException(e);
        }

        rescanner.scheduleWithFixedDelay(() -> {
            try {
                rescan();
            } catch (BoxSDKError e) {
                logger.log(Level.ERROR, e.getMessage(), e);
            }
        }, RESCAN_INTERVAL_MINUTES, RESCAN_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /** subscribes folders just under the root, unsubscribes vanished ones */
    private synchronized void rescan() {
        if (!isOpen()) {
            return;
        }

        Set<String> currentIds = new HashSet<>();
        String marker = null;
        do {
            Items items = client.folders.getFolderItems(ROOT_ID, new GetFolderItemsQueryParams.Builder()
                    .fields(List.of("name"))
                    .usemarker(true)
                    .limit(1000L)
                    .marker(marker)
                    .build());
            if (items == null || items.getEntries() == null) {
                break;
            }
            for (Item item : items.getEntries()) {
                if (item.isFolderFull()) {
                    currentIds.add(item.getId());
                }
            }
            marker = items.getNextMarker();
        } while (marker != null && !marker.isEmpty());

        for (String folderId : currentIds) {
            if (!folderIds.contains(folderId)) {
                subscribe(folderId);
            }
        }
        for (String folderId : new HashSet<>(folderIds)) {
            if (!currentIds.contains(folderId)) {
                unsubscribe(folderId);
            }
        }
    }

    /** creates a webhook for the folder if it does not exist */
    private void subscribe(String folderId) {
        synchronized (lock) {
            if (!webhooks.containsKey(folderId)) {
                String webhookId = createWebhook(folderId);
                webhooks.put(folderId, webhookId);
                targets.put(webhookId, folderId);
            }
            subscribers.computeIfAbsent(folderId, k -> new HashSet<>()).add(this);
            folderIds.add(folderId);
        }
logger.log(Level.TRACE, "BOX: subscribe: " + folderId);
    }

    /** deletes the webhook for the folder when no one subscribes */
    private void unsubscribe(String folderId) {
        synchronized (lock) {
            folderIds.remove(folderId);
            Set<BoxWatchService> services = subscribers.get(folderId);
            if (services != null) {
                services.remove(this);
                if (!services.isEmpty()) {
                    return;
                }
                subscribers.remove(folderId);
            }
            String webhookId = webhooks.remove(folderId);
            if (webhookId != null) {
                targets.remove(webhookId);
                try {
                    client.webhooks.deleteWebhookById(webhookId);
logger.log(Level.TRACE, "BOX: webhook deleted: " + webhookId + " for " + folderId);
                } catch (BoxAPIError e) {
                    // a webhook is deleted by box when the target is deleted
logger.log(Level.DEBUG, "BOX: webhook delete failed: " + webhookId + ", " + e.getMessage());
                }
            }
        }
    }

    /** @return webhook id */
    private String createWebhook(String folderId) {
        try {
            Webhook webhook = client.webhooks.createWebhook(new CreateWebhookRequestBody(
                    new CreateWebhookRequestBodyTargetField.Builder()
                            .id(folderId)
                            .type(CreateWebhookRequestBodyTargetTypeField.FOLDER)
                            .build(),
                    webhooktUrl,
                    TRIGGERS));
logger.log(Level.TRACE, "BOX: webhook created: " + webhook.getId() + " for " + folderId);
            return webhook.getId();
        } catch (BoxAPIError e) {
            if (e.getResponseInfo() != null && e.getResponseInfo().getStatusCode() == 409) {
                // already exists (e.g. left by a previous process), reuse it
                String webhookId = findWebhook(folderId);
                if (webhookId != null) {
logger.log(Level.TRACE, "BOX: webhook reused: " + webhookId + " for " + folderId);
                    return webhookId;
                }
            }
            throw e;
        }
    }

    /** @return null if not found */
    private String findWebhook(String folderId) {
        String marker = null;
        do {
            Webhooks webhooks = client.webhooks.getWebhooks(new GetWebhooksQueryParams.Builder()
                    .limit(200L)
                    .marker(marker)
                    .build());
            if (webhooks == null || webhooks.getEntries() == null) {
                break;
            }
            for (WebhookMini webhook : webhooks.getEntries()) {
                if (webhook.getTarget() != null && folderId.equals(webhook.getTarget().getId())) {
                    return webhook.getId();
                }
            }
            marker = webhooks.getNextMarker();
        } while (marker != null && !marker.isEmpty());
        return null;
    }

    /**
     * <pre>
     * {
     *   "type": "webhook_event",
     *   "id": "...",
     *   "created_at": "...",
     *   "trigger": "FILE.UPLOADED",
     *   "webhook": { "id": "...", "type": "webhook" },
     *   "created_by": { ... },
     *   "source": { "id": "...", "type": "file", "name": "...", "parent": { "id": "...", ... }, ... }
     * }
     * </pre>
     */
    @Override
    protected void onNotifyMessage(String notification) throws IOException {
logger.log(Level.TRACE, ">> notification: " + notification);

        JsonObject root;
        try {
            root = JsonParser.parseString(notification).getAsJsonObject();
        } catch (RuntimeException e) {
logger.log(Level.TRACE, ">> notification: unknown format");
            return;
        }
        String trigger = getString(root, "trigger");
        String webhookId = getObject(root, "webhook") != null ? getString(getObject(root, "webhook"), "id") : null;
        JsonObject source = getObject(root, "source");
        if (trigger == null || webhookId == null || source == null) {
logger.log(Level.TRACE, ">> notification: unknown format");
            return;
        }

        Set<BoxWatchService> services;
        synchronized (lock) {
            String folderId = targets.get(webhookId);
            services = folderId != null ? new HashSet<>(subscribers.getOrDefault(folderId, Set.of())) : Set.of();
        }
        if (services.isEmpty()) {
logger.log(Level.TRACE, ">> notification is not for this webhook: " + webhookId);
            return;
        }

        for (BoxWatchService service : services) {
            service.processNotification(trigger, source);
        }

logger.log(Level.TRACE, ">> notification: done");
    }

    /** */
    private void processNotification(String trigger, JsonObject source) {
        if (!isOpen()) {
            return;
        }

        String id = getString(source, "id");
        boolean deleted = trigger.endsWith(".TRASHED") || trigger.endsWith(".DELETED");
logger.log(Level.TRACE, ">> " + getString(source, "type") + "[" + (getString(source, "name") != null ? getString(source, "name") : id) + "] " + trigger);

        listener.accept(id, deleted ? ENTRY_DELETE : ENTRY_MODIFY);

        // follow folders moved from/into just under the root
        if ("folder".equals(getString(source, "type"))) {
            JsonObject parent = getObject(source, "parent");
            boolean underRoot = parent != null && ROOT_ID.equals(getString(parent, "id"));
            synchronized (this) {
                try {
                    if (!deleted && underRoot && !folderIds.contains(id)) {
                        subscribe(id);
                    } else if ((deleted || !underRoot) && folderIds.contains(id)) {
                        unsubscribe(id);
                    }
                } catch (BoxSDKError e) {
                    logger.log(Level.ERROR, e.getMessage(), e);
                }
            }
        }
    }

    /** @return null when the member does not exist */
    private static String getString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** @return null when the member does not exist */
    private static JsonObject getObject(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    @Override
    public void close() throws IOException {
        if (isOpen()) {
            super.close();

            rescanner.shutdownNow();
            synchronized (this) {
                for (String folderId : new HashSet<>(folderIds)) {
                    try {
                        unsubscribe(folderId);
                    } catch (BoxSDKError e) {
                        logger.log(Level.ERROR, e.getMessage(), e);
                    }
                }
            }
        }
    }
}
