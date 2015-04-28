/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.distribution.serialization.impl.vlt;


import java.io.File;
import java.io.InputStream;
import java.util.UUID;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.JcrPackage;
import org.apache.jackrabbit.vault.packaging.JcrPackageManager;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.Packaging;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.distribution.DistributionRequest;
import org.apache.sling.distribution.packaging.DistributionPackage;
import org.apache.sling.distribution.serialization.DistributionPackageBuilder;
import org.apache.sling.distribution.serialization.DistributionPackageBuildingException;
import org.apache.sling.distribution.serialization.DistributionPackageReadingException;
import org.apache.sling.distribution.serialization.impl.AbstractDistributionPackageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * a {@link org.apache.sling.distribution.serialization.DistributionPackageBuilder} based on Apache Jackrabbit FileVault.
 * <p/>
 * Each {@link org.apache.sling.distribution.packaging.DistributionPackage} created by {@link JcrVaultDistributionPackageBuilder} is
 * backed by a {@link org.apache.jackrabbit.vault.packaging.JcrPackage}. 
 */
public class JcrVaultDistributionPackageBuilder  extends AbstractDistributionPackageBuilder implements
        DistributionPackageBuilder {
    private final Logger log = LoggerFactory.getLogger(getClass());


    private static final String VERSION = "0.0.1";
    private static final String PACKAGE_GROUP = "sling/distribution";

    private final Packaging packaging;
    private ImportMode importMode;
    private AccessControlHandling aclHandling;
    private final String[] packageRoots;

    public JcrVaultDistributionPackageBuilder(String type, Packaging packaging, ImportMode importMode, AccessControlHandling aclHandling, String[] packageRoots) {
        super(type);

        this.packaging = packaging;

        this.importMode = importMode;
        this.aclHandling = aclHandling;
        this.packageRoots = packageRoots;
    }

    @Override
    protected DistributionPackage createPackageForAdd(ResourceResolver resourceResolver, DistributionRequest request) throws DistributionPackageBuildingException {
        Session session = null;
        try {
            session = getSession(resourceResolver);

            String packageGroup = PACKAGE_GROUP;
            String packageName = getType() + "_" + System.currentTimeMillis() + "_" +  UUID.randomUUID();

            WorkspaceFilter filter = VltUtils.createFilter(request);
            ExportOptions opts = VltUtils.getExportOptions(filter, packageRoots, packageGroup, packageName, VERSION);

            log.debug("assembling package {}", packageGroup + '/' + packageName + "-" + VERSION);

            VaultPackage vaultPackage = packaging.getPackageManager().assemble(session, opts, (File) null);

            JcrPackageManager packageManager = packaging.getPackageManager(session);
            JcrPackage jcrPackage = packageManager.upload(vaultPackage.getFile(), true, true, null);
            vaultPackage.close();

            return new JcrVaultDistributionPackage(getType(), jcrPackage, session);
        } catch (Exception e) {
            throw new DistributionPackageBuildingException(e);
        } finally {
            ungetSession(session);
        }
    }

    @Override
    protected DistributionPackage readPackageInternal(ResourceResolver resourceResolver, InputStream stream) throws DistributionPackageReadingException {
        Session session = null;
        try {
            session = getSession(resourceResolver);
            JcrPackageManager packageManager = packaging.getPackageManager(session);

            JcrPackage jcrPackage = packageManager.upload(stream, true);

            return new JcrVaultDistributionPackage(getType(), jcrPackage, session);
        } catch (Exception e) {
            throw new DistributionPackageReadingException(e);
        } finally {
            ungetSession(session);
        }
    }

    @Override
    protected boolean installPackageInternal(ResourceResolver resourceResolver, DistributionPackage distributionPackage) throws DistributionPackageReadingException {
        Session session = null;
        try {
            session = getSession(resourceResolver);
            JcrPackageManager packageManager = packaging.getPackageManager(session);



            String packageName = distributionPackage.getId();
            JcrPackage jcrPackage = packageManager.open(new PackageId(PACKAGE_GROUP, packageName, VERSION));

            ImportOptions importOptions = VltUtils.getImportOptions(aclHandling, importMode);
            jcrPackage.extract(importOptions);

            return true;
        } catch (Exception e) {
            throw new DistributionPackageReadingException(e);
        } finally {
            ungetSession(session);
        }
    }

    @Override
    protected DistributionPackage getPackageInternal(ResourceResolver resourceResolver, String id) {
        Session session = null;
        try {
            session = getSession(resourceResolver);
            JcrPackageManager packageManager = packaging.getPackageManager(session);

            String packageName = id;
            JcrPackage jcrPackage = packageManager.open(new PackageId(PACKAGE_GROUP, packageName, VERSION));

            if (jcrPackage == null) {
                return null;
            }
            return new JcrVaultDistributionPackage(getType(), jcrPackage, session);
        } catch (RepositoryException e) {
            log.error("cannot ge package with id {}", id, e);
            return null;
        } finally {
            ungetSession(session);
        }
    }
}
