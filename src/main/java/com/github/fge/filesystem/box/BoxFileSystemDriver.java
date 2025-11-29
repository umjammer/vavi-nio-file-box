package com.github.fge.filesystem.box;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.FileStore;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import javax.annotation.ParametersAreNonnullByDefault;

import com.box.sdkgen.box.errors.BoxAPIError;
import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.files.CopyFileQueryParams;
import com.box.sdkgen.managers.files.CopyFileRequestBody;
import com.box.sdkgen.managers.files.CopyFileRequestBodyParentField;
import com.box.sdkgen.managers.files.UpdateFileByIdQueryParams;
import com.box.sdkgen.managers.files.UpdateFileByIdRequestBody;
import com.box.sdkgen.managers.files.UpdateFileByIdRequestBodyParentField;
import com.box.sdkgen.managers.folders.CopyFolderQueryParams;
import com.box.sdkgen.managers.folders.CopyFolderRequestBody;
import com.box.sdkgen.managers.folders.CopyFolderRequestBodyParentField;
import com.box.sdkgen.managers.folders.CreateFolderQueryParams;
import com.box.sdkgen.managers.folders.CreateFolderRequestBody;
import com.box.sdkgen.managers.folders.CreateFolderRequestBodyParentField;
import com.box.sdkgen.managers.folders.GetFolderItemsQueryParams;
import com.box.sdkgen.managers.folders.UpdateFolderByIdQueryParams;
import com.box.sdkgen.managers.folders.UpdateFolderByIdRequestBody;
import com.box.sdkgen.managers.folders.UpdateFolderByIdRequestBodyParentField;
import com.box.sdkgen.managers.uploads.UploadFileRequestBody;
import com.box.sdkgen.managers.uploads.UploadFileRequestBodyAttributesField;
import com.box.sdkgen.managers.uploads.UploadFileRequestBodyAttributesParentField;
import com.box.sdkgen.schemas.filefull.FileFullPermissionsField;
import com.box.sdkgen.schemas.files.Files;
import com.box.sdkgen.schemas.item.Item;
import com.box.sdkgen.schemas.items.Items;
import com.github.fge.filesystem.driver.DoubleCachedFileSystemDriver;
import com.github.fge.filesystem.provider.FileSystemFactoryProvider;
import vavi.nio.file.Util;

import static com.github.fge.filesystem.box.BoxFileSystemProvider.ENV_USE_SYSTEM_WATCHER;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static vavi.nio.file.Util.toFilenameString;


/**
 * Box filesystem driver
 *
 * @version 0.00 2021/10/31 umjammer update <br>
 */
@ParametersAreNonnullByDefault
public final class BoxFileSystemDriver extends DoubleCachedFileSystemDriver<Item> {

    private static final Logger logger = System.getLogger(BoxFileSystemDriver.class.getName());

    private BoxWatchService systemWatcher;

    private final BoxClient client;
    private final Item root;

    public BoxFileSystemDriver(FileStore fileStore,
                               FileSystemFactoryProvider factoryProvider,
                               BoxClient client,
                               Map<String, ?> env) throws IOException {

        super(fileStore, factoryProvider);
        this.client = Objects.requireNonNull(client);
        this.root = new Item(client.folders.getFolderById("0"));
        setEnv(env);

        @SuppressWarnings("unchecked")
        boolean useSystemWatcher = (Boolean) ((Map<String, Object>) env).getOrDefault(ENV_USE_SYSTEM_WATCHER, false);
        if (useSystemWatcher) {
            systemWatcher = new BoxWatchService(client);
            systemWatcher.setNotificationListener(this::processNotification);
        }
    }

    /** for system watcher */
    private void processNotification(String id, Kind<?> kind) {
        if (ENTRY_DELETE == kind) {
            try {
                Path path = cache.getEntry(e -> id.equals(e.getId()));
                cache.removeEntry(path);
            } catch (NoSuchElementException e) {
logger.log(Level.TRACE, "NOTIFICATION: already deleted: " + id);
            }
        } else {
            try {
                try {
                    Path path = cache.getEntry(e -> id.equals(e.getId()));
logger.log(Level.TRACE, "NOTIFICATION: maybe updated: " + path);
                    cache.removeEntry(path);
                    cache.getEntry(path);
                } catch (NoSuchElementException e) {
// TODO impl
//                    BoxItem entry = BoxFile.client.files().getMetadata(pathString);
//                    Path path = parent.resolve(pathString);
//logger.log(Level.TRACE, "NOTIFICATION: maybe created: " + path);
//                    cache.addEntry(path, entry);
                }
            } catch (NoSuchElementException e) {
logger.log(Level.TRACE, "NOTIFICATION: parent not found: " + e);
            } catch (IOException e) {
                logger.log(Level.ERROR, e.getMessage(), e);
            }
        }
    }

    /** */
    private static final List<String> ENTRY_FIELDS = List.of("name", "size", "created_at", "modified_at", "permissions");

    @Override
    protected String getFilenameString(Item entry) {
        return entry.getName();
    }

    @Override
    protected boolean isFolder(Item entry) {
        return entry.getType().equals("folder");
    }

    @Override
    protected Item getRootEntry(Path root) throws IOException {
        return this.root;
    }

    @Override
    protected Item getEntry(Item parentEntry, Path path) throws IOException {
        try {
            // TODO use query (when a directory has huge amount of files, this breaks down)
            Items items = client.folders.getFolderItems(parentEntry.getId(), new GetFolderItemsQueryParams.Builder().fields(ENTRY_FIELDS).build());
            if (items == null) {
logger.log(Level.TRACE, "empty folder: " + parentEntry.getName());
                return null;
            }

            return items.getEntries().stream().filter(i -> i.getName().equals(path.getFileName().toString())).findFirst().orElse(null);

        } catch (BoxAPIError e) {
            if (e.getMessage().contains("404")) {
                return null;
            } else {
                throw e;
            }
        }
    }

    @Override
    protected InputStream downloadEntryImpl(Item entry, Path path, Set<? extends OpenOption> options) throws IOException {
        return new BufferedInputStream(client.downloads.downloadFile(entry.getId()));
    }

    @Override
    protected OutputStream uploadEntry(Item parentEntry, Path path, Set<? extends OpenOption> options) throws IOException {
        return new Util.OutputStreamForUploading() {
            @Override
            protected void onClosed() throws IOException {
                InputStream is = getInputStream();
                Files files = client.uploads.uploadFile(new UploadFileRequestBody.Builder(
                        new UploadFileRequestBodyAttributesField.Builder(toFilenameString(path),
                                new UploadFileRequestBodyAttributesParentField(parentEntry.getId())).build(), is).build());
                Item newEntry = new Item(files.getEntries().get(0));
                updateEntry(path, newEntry);
            }
        };
    }

    @Override
    protected List<Item> getDirectoryEntries(Item dirEntry, Path dir) throws IOException {
logger.log(Level.DEBUG, dirEntry.getName());
        // TODO box api has pagination
        Items items = client.folders.getFolderItems(dirEntry.getId(), new GetFolderItemsQueryParams.Builder().fields(ENTRY_FIELDS).build());
        return items != null ? items.getEntries() : Collections.emptyList();
    }

    @Override
    protected Item createDirectoryEntry(Item parentEntry, Path dir) throws IOException {
        return new Item(client.folders.createFolder(new CreateFolderRequestBody.Builder(toFilenameString(dir), new CreateFolderRequestBodyParentField(parentEntry.getId())).build(), new CreateFolderQueryParams.Builder().fields(ENTRY_FIELDS).build()));
    }

    @Override
    protected boolean hasChildren(Item dirEntry, Path dir) throws IOException {
        return !getDirectoryEntries(dir, false).isEmpty();
    }

    @Override
    protected void removeEntry(Item entry, Path path) throws IOException {
        if (isFolder(entry))
            client.folders.deleteFolderById(entry.getId());
        else
            client.files.deleteFileById(entry.getId());
    }

    @Override
    protected Item copyEntry(Item sourceEntry, Item targetParentEntry, Path source, Path target, Set<CopyOption> options) throws IOException {
        if (isFolder(sourceEntry))
            return new Item(client.folders.copyFolder(sourceEntry.getId(), new CopyFolderRequestBody.Builder(new CopyFolderRequestBodyParentField(targetParentEntry.getId())).build(), new CopyFolderQueryParams.Builder().fields(ENTRY_FIELDS).build()));
        else
            return new Item(client.files.copyFile(sourceEntry.getId(), new CopyFileRequestBody.Builder(new CopyFileRequestBodyParentField(targetParentEntry.getId())).build(), new CopyFileQueryParams.Builder().fields(ENTRY_FIELDS).build()));
    }

    @Override
    protected Item moveEntry(Item sourceEntry, Item targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        if (targetIsParent) {
            return new Item(client.files.updateFileById(sourceEntry.getId(), new UpdateFileByIdRequestBody.Builder().parent(new UpdateFileByIdRequestBodyParentField.Builder().id(targetParentEntry.getId()).build()).build(), new UpdateFileByIdQueryParams.Builder().fields(ENTRY_FIELDS).build()));
        } else {
            return new Item(client.files.updateFileById(sourceEntry.getId(), new UpdateFileByIdRequestBody.Builder().parent(new UpdateFileByIdRequestBodyParentField.Builder().id(targetParentEntry.getId()).build()).name(toFilenameString(target)).build(), new UpdateFileByIdQueryParams.Builder().fields(ENTRY_FIELDS).build()));
        }
    }

    @Override
    protected Item moveFolderEntry(Item sourceEntry, Item targetParentEntry, Path source, Path target, boolean targetIsParent) throws IOException {
        Item patchedEntry = new Item(client.folders.updateFolderById(sourceEntry.getId(), new UpdateFolderByIdRequestBody.Builder().parent(new UpdateFolderByIdRequestBodyParentField.Builder().id(targetParentEntry.getId()).build()).build(), new UpdateFolderByIdQueryParams.Builder().fields(ENTRY_FIELDS).build()));
logger.log(Level.TRACE, patchedEntry.getId() + ", " + (patchedEntry.getFolderFull().getParent() != null ? patchedEntry.getFolderFull().getParent().getName() : "") + "/" + patchedEntry.getName());
        return patchedEntry;
    }

    @Override
    protected Item renameEntry(Item sourceEntry, Item targetParentEntry, Path source, Path target) throws IOException {
        if (isFolder(sourceEntry))
            return new Item(client.folders.updateFolderById(sourceEntry.getId(), new UpdateFolderByIdRequestBody.Builder().name(toFilenameString(target)).build(), new UpdateFolderByIdQueryParams.Builder().fields(ENTRY_FIELDS).build()));
        else
            return new Item(client.files.updateFileById(sourceEntry.getId(), new UpdateFileByIdRequestBody.Builder().name(toFilenameString(target)).build(), new UpdateFileByIdQueryParams.Builder().fields(ENTRY_FIELDS).build()));
    }

    @Override
    protected void checkAccessEntry(Item entry, Path path, AccessMode... modes) throws IOException {

        final Set<AccessMode> set = EnumSet.noneOf(AccessMode.class);

        FileFullPermissionsField permissions = entry.getFileFull().getPermissions();
        if (permissions != null) {
            for (AccessMode mode : modes) {
                switch (mode) {
                case READ:
                    if (!permissions.getCanDownload()) {
                        set.add(AccessMode.READ);
                    }
                    break;
                case WRITE:
                    if (!permissions.getCanUpload()) {
                        set.add(AccessMode.WRITE);
                    }
                    break;
                case EXECUTE:
                    if (!permissions.getCanDownload()) { // TODO
                        set.add(AccessMode.EXECUTE);
                    }
                    break;
                }
            }
        }

        if (!set.isEmpty()) {
            throw new AccessDeniedException(path + ": " + set);
        }
    }

    @Override
    public WatchService newWatchService() {
        try {
            return new BoxWatchService(client);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
