package com.github.fge.filesystem.box;

import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import javax.annotation.ParametersAreNonnullByDefault;

import com.box.sdkgen.schemas.item.Item;
import com.github.fge.filesystem.attributes.provider.BasicFileAttributesProvider;


@ParametersAreNonnullByDefault
public final class BoxBasicFileAttributesProvider extends BasicFileAttributesProvider implements PosixFileAttributes {
    private final Item entry;

    public BoxBasicFileAttributesProvider(final Item entry) throws IOException {
        this.entry = entry;
    }

    @Override
    public FileTime lastModifiedTime() {
        if (isRegularFile())
            return FileTime.from(entry.getFileFull().getModifiedAt().toInstant());
        else
            return entry.getFolderFull().getModifiedAt() != null ?  FileTime.from(entry.getFolderFull().getModifiedAt().toInstant()) : FileTime.fromMillis(0);
    }

    @Override
    public FileTime creationTime() {
        if (isRegularFile())
            return FileTime.from(entry.getFileFull().getCreatedAt().toInstant());
        else
            return FileTime.from(entry.getFolderFull().getCreatedAt().toInstant());
    }

    /**
     * Tells whether the file is a regular file with opaque content.
     */
    @Override
    public boolean isRegularFile() {
        return entry.getType().equals("file");
    }

    /**
     * Tells whether the file is a directory.
     */
    @Override
    public boolean isDirectory() {
        return entry.getType().equals("folder");
    }

    /**
     * Returns the size of the file (in bytes). The size may differ from the
     * actual size on the file system due to compression, support for sparse
     * files, or other reasons. The size of files that are not {@link
     * #isRegularFile regular} files is implementation specific and
     * therefore unspecified.
     *
     * @return the file size, in bytes
     */
    @Override
    public long size() {
        return isRegularFile() ? entry.getFileFull().getSize() : 0;
    }

    /* @see java.nio.file.attribute.PosixFileAttributes#owner() */
    @Override
    public UserPrincipal owner() {
        return null;
    }

    /* @see java.nio.file.attribute.PosixFileAttributes#group() */
    @Override
    public GroupPrincipal group() {
        return null;
    }

    /* @see java.nio.file.attribute.PosixFileAttributes#permissions() */
    @Override
    public Set<PosixFilePermission> permissions() {
        return isDirectory() ? PosixFilePermissions.fromString("rwxr-xr-x") : PosixFilePermissions.fromString("rw-r--r--");
    }
}
