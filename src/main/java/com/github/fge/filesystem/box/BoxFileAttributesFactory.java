package com.github.fge.filesystem.box;

import com.box.sdkgen.schemas.item.Item;
import com.github.fge.filesystem.driver.ExtendedFileSystemDriverBase.ExtendedFileAttributesFactory;


public final class BoxFileAttributesFactory extends ExtendedFileAttributesFactory {

    public BoxFileAttributesFactory() {
        setMetadataClass(Item.class);
        addImplementation("basic", BoxBasicFileAttributesProvider.class);
    }
}
