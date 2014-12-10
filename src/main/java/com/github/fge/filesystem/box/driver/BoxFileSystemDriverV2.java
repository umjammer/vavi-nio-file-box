package com.github.fge.filesystem.box.driver;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxItem;
import com.github.fge.filesystem.box.exceptions.BoxIOException;
import com.github.fge.filesystem.box.io.BoxFileInputStream;
import com.github.fge.filesystem.box.io.BoxFileOutputStream;
import com.github.fge.filesystem.driver.UnixLikeFileSystemDriverBase;
import com.github.fge.filesystem.exceptions.IsDirectoryException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@SuppressWarnings("OverloadedVarargsMethod")
@ParametersAreNonnullByDefault
public final class BoxFileSystemDriverV2
    extends UnixLikeFileSystemDriverBase
{
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final BoxAPIConnection api;
    private final BoxFolder rootFolder;

    public BoxFileSystemDriverV2(final URI uri, final FileStore fileStore,
        final BoxAPIConnection api, final BoxFolder rootFolder)
    {
        super(uri, fileStore);
        this.api = Objects.requireNonNull(api);
        this.rootFolder = Objects.requireNonNull(rootFolder);
    }

    /**
     * Obtain a new {@link InputStream} from a path for this filesystem
     *
     * @param path the path
     * @param options the set of open options
     * @return a new input stream
     *
     * @throws IOException filesystem level error, or plain I/O error
     * @see FileSystemProvider#newInputStream(Path, OpenOption...)
     */
    @Nonnull
    @Override
    public InputStream newInputStream(final Path path,
        final OpenOption... options)
        throws IOException
    {
        final Path realPath = path.toRealPath();
        final String target = realPath.toString();

        final BoxItem.Info info = lookupPath(realPath);

        if (info == null)
            throw new NoSuchFileException(target);

        if (BoxType.getType(info) == BoxType.DIRECTORY)
            throw new IsDirectoryException(target);

        final BoxFile file = (BoxFile) info.getResource();

        final PipedOutputStream out = new PipedOutputStream();

        final Future<Void> future = executor.submit(new Callable<Void>()
        {
            @Override
            public Void call()
                throws BoxIOException
            {
                try {
                    file.download(out);
                    return null;
                } catch (BoxAPIException e) {
                    throw BoxIOException.wrap(e);
                }
            }
        });

        return new BoxFileInputStream(future, out);
    }

    /**
     * Obtain a new {@link OutputStream} from a path for this filesystem
     *
     * @param path the path
     * @param options the set of open options
     * @return a new output stream
     *
     * @throws IOException filesystem level error, or plain I/O error
     * @see FileSystemProvider#newOutputStream(Path, OpenOption...)
     */
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    @Nonnull
    @Override
    public OutputStream newOutputStream(final Path path,
        final OpenOption... options)
        throws IOException
    {
        final Set<OpenOption> set = new HashSet<>();
        Collections.addAll(set, options);

        if (set.contains(StandardOpenOption.DELETE_ON_CLOSE))
            throw new UnsupportedOperationException();
        if (set.contains(StandardOpenOption.APPEND))
            throw new UnsupportedOperationException();

        final Path realPath = path.toRealPath();

        final OutputStream ret;
        final String target = realPath.toString();
        final BoxItem.Info info = lookupPath(realPath);
        final boolean create = info == null;

        if (!create) {
            if (set.contains(StandardOpenOption.CREATE_NEW))
                throw new FileAlreadyExistsException(target);
            if (BoxType.getType(info) == BoxType.DIRECTORY)
                throw new IsDirectoryException(target);
            ret = new BoxFileOutputStream(executor,
                (BoxFile) info.getResource());
        } else {
            if (!set.contains(StandardOpenOption.CREATE))
                throw new NoSuchFileException(target);
            // TODO: check; parent should always exist
            final Path parent = realPath.getParent();
            final BoxItem.Info parentInfo = lookupPath(parent);
            if (parentInfo == null)
                throw new NoSuchFileException(parent.toString());
            ret = new BoxFileOutputStream(executor,
                (BoxFolder) parentInfo.getResource(),
                realPath.getFileName().toString());
        }

        return ret;
    }

    /**
     * Create a new directory stream from a path for this filesystem
     *
     * @param dir the directory
     * @param filter a directory entry filter
     * @return a directory stream
     *
     * @throws IOException filesystem level error, or a plain I/O error
     * @see FileSystemProvider#newDirectoryStream(Path, DirectoryStream.Filter)
     */
    @Nonnull
    @Override
    public DirectoryStream<Path> newDirectoryStream(final Path dir,
        final DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        final Path realPath = dir.toRealPath();
        final String target = realPath.toString();
        final BoxItem.Info info = lookupPath(realPath);

        if (info == null)
            throw new NoSuchFileException(target);
        if (BoxType.getType(info) != BoxType.DIRECTORY)
            throw new NotDirectoryException(target);

        final BoxFolder folder = (BoxFolder) info.getResource();

        final Iterator<BoxItem.Info> children;

        // TODO: check that this call can do that
        try {
            children = folder.getChildren().iterator();
        } catch (BoxAPIException e) {
            throw BoxIOException.wrap(e);
        }

        // TODO: context?
        @SuppressWarnings("AnonymousInnerClassWithTooManyMethods")
        final Iterator<Path> iterator = new Iterator<Path>()
        {
            @Override
            public boolean hasNext()
            {
                return children.hasNext();
            }

            @Override
            public Path next()
            {
                // Note: relies on children throwing NoSuchElementException
                final String name = children.next().getName();
                return dir.resolve(name);
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        };

        return new DirectoryStream<Path>()
        {
            @Override
            public Iterator<Path> iterator()
            {
                return iterator;
            }

            @Override
            public void close()
                throws IOException
            {
                // TODO: is there anything to do here?
            }
        };
    }

    /**
     * Create a new directory from a path on this filesystem
     *
     * @param dir the directory to create
     * @param attrs the attributes with which the directory should be created
     * @throws IOException filesystem level error, or a plain I/O error
     * @see FileSystemProvider#newDirectoryStream(Path, DirectoryStream.Filter)
     */
    @Override
    public void createDirectory(final Path dir, final FileAttribute<?>... attrs)
        throws IOException
    {
        final Path realPath = dir.toRealPath();
        final BoxItem.Info info = lookupPath(realPath);

        //noinspection VariableNotUsedInsideIf
        if (info != null)
            throw new FileAlreadyExistsException(realPath.toString());

        final Path parent = realPath.getParent();
        final BoxItem.Info parentInfo = lookupPath(parent);

        if (parentInfo == null)
            throw new NoSuchFileException(parent.toString());

        final BoxFolder folder = (BoxFolder) parentInfo.getResource();
        final String name = realPath.getFileName().toString();

        try {
            folder.createFolder(name);
        } catch (BoxAPIException e) {
            throw BoxIOException.wrap(e);
        }
    }

    /**
     * Delete a file, or empty directory, matching a path on this filesystem
     *
     * @param path the victim
     * @throws IOException filesystem level error, or a plain I/O error
     * @see FileSystemProvider#delete(Path)
     */
    @Override
    public void delete(final Path path)
        throws IOException
    {

    }

    /**
     * Copy a file, or empty directory, from one path to another on this
     * filesystem
     *
     * @param source the source path
     * @param target the target path
     * @param options the copy options
     * @throws IOException filesystem level error, or a plain I/O error
     * @see FileSystemProvider#copy(Path, Path, CopyOption...)
     */
    @Override
    public void copy(final Path source, final Path target,
        final CopyOption... options)
        throws IOException
    {

    }

    /**
     * Move a file, or empty directory, from one path to another on this
     * filesystem
     *
     * @param source the source path
     * @param target the target path
     * @param options the copy options
     * @throws IOException filesystem level error, or a plain I/O error
     * @see FileSystemProvider#move(Path, Path, CopyOption...)
     */
    @Override
    public void move(final Path source, final Path target,
        final CopyOption... options)
        throws IOException
    {

    }

    /**
     * Check access modes for a path on this filesystem
     * <p>If no modes are provided to check for, this simply checks for the
     * existence of the path.</p>
     *
     * @param path the path to check
     * @param modes the modes to check for, if any
     * @throws IOException filesystem level error, or a plain I/O error
     * @see FileSystemProvider#checkAccess(Path, AccessMode...)
     */
    @Override
    public void checkAccess(final Path path, final AccessMode... modes)
        throws IOException
    {

    }

    /**
     * Read an attribute view for a given path on this filesystem
     *
     * @param path the path to read attributes from
     * @param type the class of attribute view to return
     * @param options the link options
     * @return the attributes view; {@code null} if this view is not supported
     *
     * @see FileSystemProvider#getFileAttributeView(Path, Class, LinkOption...)
     */
    @Nullable
    @Override
    public <V extends FileAttributeView> V getFileAttributeView(final Path path,
        final Class<V> type, final LinkOption... options)
    {
        return null;
    }

    /**
     * Read attributes from a path on this filesystem
     *
     * @param path the path to read attributes from
     * @param type the class of attributes to read
     * @param options the link options
     * @return the attributes
     *
     * @throws IOException filesystem level error, or a plain I/O error
     * @throws UnsupportedOperationException attribute type not supported
     * @see FileSystemProvider#readAttributes(Path, Class, LinkOption...)
     */
    @Override
    public <A extends BasicFileAttributes> A readAttributes(final Path path,
        final Class<A> type, final LinkOption... options)
        throws IOException
    {
        return null;
    }

    /**
     * Read a list of attributes from a path on this filesystem
     *
     * @param path the path to read attributes from
     * @param attributes the list of attributes to read
     * @param options the link options
     * @return the relevant attributes as a map
     *
     * @throws IOException filesystem level error, or a plain I/O error
     * @throws IllegalArgumentException malformed attributes string; or a
     * specified attribute does not exist
     * @throws UnsupportedOperationException one or more attribute(s) is/are not
     * supported
     * @see Files#readAttributes(Path, String, LinkOption...)
     * @see FileSystemProvider#readAttributes(Path, String, LinkOption...)
     */
    @Override
    public Map<String, Object> readAttributes(final Path path,
        final String attributes, final LinkOption... options)
        throws IOException
    {
        return null;
    }

    /**
     * Set an attribute for a path on this filesystem
     *
     * @param path the victim
     * @param attribute the name of the attribute to set
     * @param value the value to set
     * @param options the link options
     * @throws IOException filesystem level error, or a plain I/O error
     * @throws IllegalArgumentException malformed attribute, or the specified
     * attribute does not exist
     * @throws UnsupportedOperationException the attribute to set is not
     * supported by this filesystem
     * @throws ClassCastException attribute value is of the wrong class for the
     * specified attribute
     * @see Files#setAttribute(Path, String, Object, LinkOption...)
     * @see FileSystemProvider#setAttribute(Path, String, Object, LinkOption...)
     */
    @Override
    public void setAttribute(final Path path, final String attribute,
        final Object value, final LinkOption... options)
        throws IOException
    {

    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close()
        throws IOException
    {

    }

    // Note: path must be absolute
    @Nullable
    private BoxItem.Info lookupPath(final Path path)
        throws BoxIOException
    {
        BoxFolder dir = rootFolder;
        BoxItem.Info info = rootFolder.getInfo();

        final int nameCount = path.getNameCount();

        int count = 0;
        String name;

        for (final Path entry: path) {
            count++;
            name = entry.toString();
            try {
                info = BoxUtil.findEntryByName(dir, name);
            } catch (BoxAPIException e) {
                throw BoxIOException.wrap(e);
            }
            if (info == null)
                return null;
            if (BoxType.getType(info) == BoxType.FILE)
                break;
            // TODO: check, but that shouldn't throw an exception
            dir = (BoxFolder) info.getResource();
        }

        return count == nameCount ? info : null;
    }

    @Nonnull
    private static <T> Set<T> toSet(final T... options)
    {
        final Set<T> set = new HashSet<>();
        Collections.addAll(set, options);
        return set;
    }
}
