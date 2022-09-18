package com.github.fge.filesystem.box;

import com.box.sdk.BoxItem;
import com.github.fge.filesystem.driver.ExtendedFileSystemDriverBase.ExtendedFileAttributesFactory;

public final class BoxFileAttributesFactory
    extends ExtendedFileAttributesFactory
{
    public BoxFileAttributesFactory()
    {
        setMetadataClass(BoxItem.Info.class);
        addImplementation("basic", BoxBasicFileAttributesProvider.class);
    }
}
