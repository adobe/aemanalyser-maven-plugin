/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.project.model;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import com.adobe.aem.project.ServiceType;

/**
 * A repoinit file
 */
public final class RepoinitFile implements Serializable {

    public static final String REPOINIT_FACTORY_PID = "org.apache.sling.jcr.repoinit.RepositoryInitializer";

    public static final String REPOINIT_PID = "org.apache.sling.jcr.repoinit.impl.RepositoryInitializer";

    private ServiceType serviceType;
    private String contents;
    private final File source;

    public RepoinitFile(final File source) {
        this.source = source;
    }

    /**
     * @return the source
     */
    public File getSource() {
        return source;
    }

    public void resetContents() {
        this.contents = null;
    }

    public String getContents() {
        return this.contents;
    }

    public String readContents() throws IOException {
        if ( this.contents == null ) {
            this.contents = Files.readString(this.source.toPath());
        }
        return this.contents;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(final ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public String getPid() {
        return REPOINIT_FACTORY_PID.concat("~").concat(serviceType == null ? "global" : serviceType.asString());
    }
}