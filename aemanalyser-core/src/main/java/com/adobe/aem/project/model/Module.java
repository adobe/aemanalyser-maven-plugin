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

import com.adobe.aem.project.ServiceType;

public class Module extends AbstractModule {

    private String name;

    private ModuleType type;

    public Module(final File directory) {
        super(directory);
    }

    public ServiceType getServiceType() {
        if ( this.getDirectory().getName().contains("_author_") ) {
            return ServiceType.AUTHOR;
        } else if ( this.getDirectory().getName().contains("_publish_") ) {
            return ServiceType.PUBLISH;
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public ModuleType getType() {
        return type;
    }

    public void setType(final ModuleType type) {
        this.type = type;
    }
}
