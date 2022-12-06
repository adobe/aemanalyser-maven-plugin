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
import java.util.ArrayList;
import java.util.List;

public class Project {

    private final List<Module> modules = new ArrayList<>();

    private final File rootDirectory;

    private Application application;

    public Project(final File rootDir) {
        this.rootDirectory = rootDir;
    }
  
    /**
     * 
     */
    public void scan() {
        for(final File f : this.getRootDirectory().listFiles()) {
            if ( f.isDirectory() ) {
                if ( "application".equals(f.getName()) ) {
                    this.setApplication(new Application(f));
                }
            }
        }
    } 

    public File getRootDirectory() {
        return this.rootDirectory;
    }

    public List<Module> getModules() {
        return this.modules;
    }

    public Application getApplication() {
      return application;
    }

    public void setApplication(final Application application) {
        this.application = application;
    }
}
