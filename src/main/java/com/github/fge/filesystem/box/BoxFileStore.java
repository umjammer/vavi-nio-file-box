package com.github.fge.filesystem.box;

import java.io.IOException;

import com.box.sdkgen.client.BoxClient;
import com.github.fge.filesystem.attributes.FileAttributesFactory;
import com.github.fge.filesystem.filestore.FileStoreBase;


public final class BoxFileStore extends FileStoreBase {

    private final long totalSize;

    private final BoxClient client;

    public BoxFileStore(BoxClient client, FileAttributesFactory factory) {
        super("box", factory, false);
        this.client = client;
        totalSize = client.users.getUserMe().getSpaceAmount();
    }

    /**
     * Returns the size, in bytes, of the file store.
     *
     * @return the size of the file store, in bytes
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getTotalSpace() throws IOException {
        return totalSize;
    }

    /**
     * Returns the number of bytes available to this Java virtual machine on the
     * file store.
     * <p> The returned number of available bytes is a hint, but not a
     * guarantee, that it is possible to use most or any of these bytes.  The
     * number of usable bytes is most likely to be accurate immediately
     * after the space attributes are obtained. It is likely to be made
     * inaccurate
     * by any external I/O operations including those made on the system outside
     * of this Java virtual machine.
     *
     * @return the number of bytes available
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getUsableSpace() throws IOException {
        return totalSize - client.users.getUserMe().getSpaceUsed();
    }

    /**
     * Returns the number of unallocated bytes in the file store.
     * <p> The returned number of unallocated bytes is a hint, but not a
     * guarantee, that it is possible to use most or any of these bytes.  The
     * number of unallocated bytes is most likely to be accurate immediately
     * after the space attributes are obtained. It is likely to be
     * made inaccurate by any external I/O operations including those made on
     * the system outside of this virtual machine.
     *
     * @return the number of unallocated bytes
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long getUnallocatedSpace() throws IOException {
        return 0;
    }
}
