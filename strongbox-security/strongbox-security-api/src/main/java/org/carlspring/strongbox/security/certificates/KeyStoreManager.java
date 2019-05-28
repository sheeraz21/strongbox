package org.carlspring.strongbox.security.certificates;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;

/**
 * @author Przemyslaw Fusik
 */
public class KeyStoreManager
        extends AbstractKeyStoreManager
{

    @Value("${server.ssl.key-store-type:PKCS12}")
    private String keyStoreType;

    @Value("${server.ssl.key-store-password:password}")
    private String keyStorePassword;

    @Value("${server.ssl.key-store:keystore.p12}")
    private FileSystemResource keyStoreResource;

    @Override
    protected String getKeyStoreType()
    {
        return keyStoreType;
    }

    @Override
    protected FileSystemResource getKeyStoreResource()
    {
        return keyStoreResource;
    }

    @Override
    protected char[] getKeyStorePassword()
    {
        return keyStorePassword.toCharArray();
    }

}
